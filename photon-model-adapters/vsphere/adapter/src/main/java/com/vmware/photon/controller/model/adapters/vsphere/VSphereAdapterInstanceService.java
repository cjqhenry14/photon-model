/*
 * Copyright (c) 2015-2016 VMware, Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License.  You may obtain a copy of
 * the License at http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed
 * under the License is distributed on an "AS IS" BASIS, without warranties or
 * conditions of any kind, EITHER EXPRESS OR IMPLIED.  See the License for the
 * specific language governing permissions and limitations under the License.
 */

package com.vmware.photon.controller.model.adapters.vsphere;

import java.net.HttpURLConnection;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;

import com.vmware.photon.controller.model.adapterapi.ComputeInstanceRequest;
import com.vmware.photon.controller.model.adapterapi.ComputeInstanceRequest.InstanceRequestType;
import com.vmware.photon.controller.model.adapters.util.AdapterUtils;
import com.vmware.photon.controller.model.adapters.vsphere.Client.ClientException;
import com.vmware.photon.controller.model.adapters.vsphere.util.finders.FinderException;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeStateWithDescription;
import com.vmware.photon.controller.model.resources.DiskService.DiskState;
import com.vmware.photon.controller.model.resources.ResourcePoolService.ResourcePoolState;
import com.vmware.photon.controller.model.tasks.ComputeSubTaskService.ComputeSubTaskState;
import com.vmware.photon.controller.model.tasks.ProvisionComputeTaskService.ProvisionComputeTaskState;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.OperationJoin;
import com.vmware.xenon.common.OperationJoin.JoinedCompletionHandler;
import com.vmware.xenon.common.OperationSequence;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.TaskState.TaskStage;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.services.common.AuthCredentialsService.AuthCredentialsServiceState;

/**
 */
public class VSphereAdapterInstanceService extends StatelessService {
    public static final String SELF_LINK = VSphereUriPaths.INSTANCE_SERVICE;

    @Override
    public void handlePatch(Operation op) {
        if (!op.hasBody()) {
            op.fail(new IllegalArgumentException("body is required"));
            return;
        }

        op.setStatusCode(HttpURLConnection.HTTP_CREATED);
        op.complete();

        ComputeInstanceRequest request = op.getBody(ComputeInstanceRequest.class);
        ProvisionContext initialContext = createInitialContext(request);

        // mark task as started
        patchSubstageAsync(request.computeReference, TaskStage.STARTED);

        withContext(initialContext, ctx -> {
            if (request.isMockRequest) {
                handleMockRequest(ctx);
                return;
            }

            switch (request.requestType) {
            case CREATE:
                handleCreateInstance(ctx);
                break;
            case DELETE:
                handleDeleteInstance(ctx);
                break;
            default:
                Throwable error = new IllegalStateException(
                        "Unsupported requestType " + request.requestType);
                ctx.fail(error);
            }
        });
    }

    private ProvisionContext createInitialContext(ComputeInstanceRequest request) {
        ProvisionContext initialContext = new ProvisionContext(request);

        // global error handler: it marks the task as failed
        initialContext.errorHandler = failure -> {
            AdapterUtils.sendFailurePatchToTask(this, request.provisioningTaskReference, failure);
        };

        initialContext.pool = VSphereIOThreadPoolAllocator.getPool(this);
        return initialContext;
    }

    /**
     * Populates the given initial context and invoke the onSuccess handler when built. At every step,
     * if failure occurs the ProvisionContext's errorHandler is invoked to cleanup.
     *
     * @param ctx
     * @param onSuccess
     */
    private void withContext(ProvisionContext ctx,
            Consumer<ProvisionContext> onSuccess) {
        if (ctx.child == null) {
            URI computeUri = UriUtils
                    .extendUriWithQuery(ctx.request.computeReference,
                            UriUtils.URI_PARAM_ODATA_EXPAND,
                            Boolean.TRUE.toString());
            AdapterUtils.getServiceState(this, computeUri, op -> {
                ctx.child = op.getBody(ComputeStateWithDescription.class);
                withContext(ctx, onSuccess);
            }, ctx.errorHandler);
            return;
        }

        if (ctx.resourcePool == null) {
            if (ctx.child.resourcePoolLink == null) {
                ctx.fail(new IllegalStateException(
                        "resourcePoolLink is not defined for resource "
                                + ctx.child.documentSelfLink));
                return;
            }

            URI rpUri = UriUtils.buildUri(getHost(), ctx.child.resourcePoolLink);
            AdapterUtils.getServiceState(this, rpUri, op -> {
                ctx.resourcePool = op.getBody(ResourcePoolState.class);
                withContext(ctx, onSuccess);
            }, ctx.errorHandler);
            return;
        }

        if (ctx.parent == null) {
            if (ctx.child.parentLink == null) {
                ctx.fail(new IllegalStateException(
                        "parentLink is not defined for resource "
                                + ctx.child.documentSelfLink));
                return;
            }

            URI computeUri = UriUtils
                    .extendUriWithQuery(
                            UriUtils.buildUri(getHost(), ctx.child.parentLink),
                            UriUtils.URI_PARAM_ODATA_EXPAND,
                            Boolean.TRUE.toString());

            AdapterUtils.getServiceState(this, computeUri, op -> {
                ctx.parent = op.getBody(ComputeStateWithDescription.class);
                withContext(ctx, onSuccess);
            }, ctx.errorHandler);
            return;
        }

        if (ctx.vSphereCredentials == null) {
            URI credUri = UriUtils
                    .buildUri(getHost(), ctx.parent.description.authCredentialsLink);
            AdapterUtils.getServiceState(this, credUri, op -> {
                ctx.vSphereCredentials = op.getBody(AuthCredentialsServiceState.class);
                withContext(ctx, onSuccess);
            }, ctx.errorHandler);
        }

        if (ctx.disks == null) {
            // no disks attached
            if (ctx.child.diskLinks == null || ctx.child.diskLinks
                    .isEmpty()) {
                ctx.disks = Collections.emptyList();
                withContext(ctx, onSuccess);
                return;
            }

            ctx.disks = new ArrayList<>(ctx.child.diskLinks.size());

            // collect disks in parallel
            Stream<Operation> opsGetDisk = ctx.child.diskLinks.stream()
                    .map(link -> Operation.createGet(this, link));

            OperationJoin join = OperationJoin.create(opsGetDisk)
                    .setCompletion((os, errors) -> {
                        if (errors != null && !errors.isEmpty()) {
                            // fail on first error
                            ctx.errorHandler
                                    .accept(new IllegalStateException("Cannot get disk state",
                                            errors.values().iterator().next()));
                            return;
                        }

                        os.values().forEach(op -> ctx.disks.add(op.getBody(DiskState.class)));

                        onSuccess.accept(ctx);
                    });

            join.sendWith(this);
        }
    }

    private void patchSubstageAsync(URI computeReference, TaskStage stage) {
        ComputeSubTaskState state = new ComputeSubTaskState();
        state.taskInfo = new TaskState();
        state.taskInfo.stage = stage;

        Operation.createPost(computeReference)
                .setBody(state)
                .sendWith(this);
    }

    private void handleCreateInstance(ProvisionContext ctx) {
        ctx.pool.submit(this, ctx.getAdapterManagementReference(), ctx.vSphereCredentials,
                (conn, ce) -> {
                    if (ctx.fail(ce)) {
                        return;
                    }

                    try {
                        Client client = new Client(conn, ctx.child, ctx.parent);
                        ComputeState state = client.createInstance();
                        if (state == null) {
                            // someone else won the race to create the vim
                            // assume they will patch the task if they have provisioned the vm
                            return;
                        }

                        // attach disks, collecting side effects
                        client.attachDisks(ctx.disks);

                        // all sides effect collected, patch model:

                        OperationJoin patchDisks = diskPatches(ctx.disks);
                        Operation patchResource = patchComputeResource(state,
                                ctx.request.computeReference);
                        Operation patchTask = patchTask(ctx.request.provisioningTaskReference);

                        OperationSequence
                                .create(patchDisks)
                                .next(patchResource)
                                .next(patchTask)
                                .setCompletion(failOnError(ctx))
                                .sendWith(this);

                    } catch (ClientException | FinderException e) {
                        ctx.fail(e);
                    } catch (RuntimeException e) {
                        ctx.fail(e);
                    } catch (Exception vimException) {
                        ctx.fail(vimException);
                    }
                });
    }

    /**
     * The returned JoinedCompletionHandler fails the whole provisioning context on first error
     * found.
     * @param ctx
     */
    private JoinedCompletionHandler failOnError(ProvisionContext ctx) {
        return (ops, failures) -> {
            if (failures != null && !failures.isEmpty()) {
                ctx.fail(failures.values().iterator().next());
            }
        };
    }

    private Operation patchTask(URI taskLink) {
        ProvisionComputeTaskState body = new ProvisionComputeTaskState();
        TaskState taskInfo = new TaskState();
        taskInfo.stage = TaskState.TaskStage.FINISHED;
        body.taskInfo = taskInfo;
        return Operation.createPatch(taskLink).setBody(body);
    }

    private Operation selfPatch(ServiceDocument doc) {
        return Operation.createPatch(this, doc.documentSelfLink).setBody(doc);
    }

    private OperationJoin diskPatches(List<DiskState> disks) {
        return OperationJoin.create()
                .setOperations(disks.stream().map(this::selfPatch));
    }

    private Operation patchComputeResource(ComputeState state, URI computeReference) {
        return Operation.createPatch(computeReference)
                .setBody(state);
    }

    private void handleDeleteInstance(ProvisionContext ctx) {
        ctx.pool.submit(this, ctx.getAdapterManagementReference(), ctx.vSphereCredentials,
                (conn, ce) -> {
                    if (ctx.fail(ce)) {
                        return;
                    }

                    try {
                        Client client = new Client(conn, ctx.child, ctx.parent);
                        client.deleteInstance();

                        // complete task
                        AdapterUtils.sendPatchToTask(this, ctx.request.provisioningTaskReference);
                    } catch (ClientException e) {
                        ctx.fail(e);
                    } catch (FinderException e) {
                        ctx.fail(e);
                    }
                });
    }

    private void handleMockRequest(ProvisionContext ctx) {
        // clean up the compute state
        if (ctx.request.requestType == InstanceRequestType.DELETE) {
            deleteComputeState(ctx);
        } else {
            // just report the task as finished
            AdapterUtils.sendPatchToTask(this, ctx.request.provisioningTaskReference);
        }
    }

    private void deleteComputeState(ProvisionContext ctx) {
        List<Operation> deleteOps = new ArrayList<>();

        if (ctx.child.diskLinks != null) {
            for (String link : ctx.child.diskLinks) {
                deleteOps.add(Operation.createDelete(UriUtils.buildUri(this.getHost(), link)));
            }
        }

        deleteOps.add(Operation
                .createDelete(UriUtils.buildUri(this.getHost(), ctx.child.documentSelfLink)));

        OperationJoin.create(deleteOps)
                .setCompletion((os, es) -> {
                    if (es != null && !es.isEmpty()) {
                        ctx.fail(es.values().iterator().next());
                    }
                })
                .sendWith(this);
    }
}
