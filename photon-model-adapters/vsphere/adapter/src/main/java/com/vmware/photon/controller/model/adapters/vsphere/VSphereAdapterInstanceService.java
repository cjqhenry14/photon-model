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
import java.util.List;
import java.util.function.Consumer;

import com.vmware.photon.controller.model.adapterapi.ComputeInstanceRequest;
import com.vmware.photon.controller.model.adapterapi.ComputeInstanceRequest.InstanceRequestType;
import com.vmware.photon.controller.model.adapters.util.AdapterUtils;
import com.vmware.photon.controller.model.adapters.vsphere.Client.ClientException;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeStateWithDescription;
import com.vmware.photon.controller.model.resources.ResourcePoolService.ResourcePoolState;
import com.vmware.photon.controller.model.tasks.ComputeSubTaskService.ComputeSubTaskState;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.OperationJoin;
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
        ProvisionContext initialContext = new ProvisionContext();
        initialContext.request = request;

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
     * @param initialContext
     * @param onSuccess
     */
    private void withContext(ProvisionContext initialContext,
            Consumer<ProvisionContext> onSuccess) {
        if (initialContext.child == null) {
            URI computeUri = UriUtils
                    .extendUriWithQuery(initialContext.request.computeReference,
                            UriUtils.URI_PARAM_ODATA_EXPAND,
                            Boolean.TRUE.toString());
            AdapterUtils.getServiceState(this, computeUri, op -> {
                initialContext.child = op.getBody(ComputeStateWithDescription.class);
                withContext(initialContext, onSuccess);
            }, initialContext.errorHandler);
            return;
        }

        if (initialContext.resourcePool == null) {
            if (initialContext.child.resourcePoolLink == null) {
                initialContext.fail(new IllegalStateException(
                        "resourcePoolLink is not defined for resource "
                                + initialContext.child.documentSelfLink));
                return;
            }

            URI rpUri = UriUtils.buildUri(getHost(), initialContext.child.resourcePoolLink);
            AdapterUtils.getServiceState(this, rpUri, op -> {
                initialContext.resourcePool = op.getBody(ResourcePoolState.class);
                withContext(initialContext, onSuccess);
            }, initialContext.errorHandler);
            return;
        }

        if (initialContext.parent == null) {
            if (initialContext.child.parentLink == null) {
                initialContext.fail(new IllegalStateException(
                        "parentLink is not defined for resource "
                                + initialContext.child.documentSelfLink));
                return;
            }

            URI computeUri = UriUtils
                    .extendUriWithQuery(
                            UriUtils.buildUri(getHost(), initialContext.child.parentLink),
                            UriUtils.URI_PARAM_ODATA_EXPAND,
                            Boolean.TRUE.toString());

            AdapterUtils.getServiceState(this, computeUri, op -> {
                initialContext.parent = op.getBody(ComputeStateWithDescription.class);
                withContext(initialContext, onSuccess);
            }, initialContext.errorHandler);
            return;
        }

        if (initialContext.vSphereCredentials == null) {
            URI credUri = UriUtils
                    .buildUri(getHost(), initialContext.parent.description.authCredentialsLink);
            AdapterUtils.getServiceState(this, credUri, op -> {
                initialContext.vSphereCredentials = op.getBody(AuthCredentialsServiceState.class);
                onSuccess.accept(initialContext);
            }, initialContext.errorHandler);
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
                        Client client = new Client(conn, ctx.child);
                        client.createInstance();
                    } catch (ClientException clientEx) {
                        ctx.fail(clientEx);
                    }
                });
    }

    private void handleDeleteInstance(ProvisionContext ctx) {
        ctx.pool.submit(this, ctx.getAdapterManagementReference(), ctx.vSphereCredentials,
                (conn, ce) -> {
                    if (ctx.fail(ce)) {
                        return;
                    }

                    try {
                        Client client = new Client(conn, ctx.child);
                        client.deleteInstance();
                    } catch (ClientException clientEx) {
                        ctx.fail(clientEx);
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
