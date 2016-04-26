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

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import com.vmware.photon.controller.model.adapterapi.ComputeInstanceRequest;
import com.vmware.photon.controller.model.adapterapi.ComputeInstanceRequest.InstanceRequestType;
import com.vmware.photon.controller.model.adapterapi.ComputePowerRequest;
import com.vmware.photon.controller.model.adapters.util.TaskManager;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.resources.DiskService.DiskState;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.OperationJoin;
import com.vmware.xenon.common.OperationSequence;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.common.TaskState.TaskStage;
import com.vmware.xenon.common.UriUtils;

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

        op.setStatusCode(Operation.STATUS_CODE_CREATED);
        op.complete();

        ComputeInstanceRequest request = op.getBody(ComputeInstanceRequest.class);

        TaskManager mgr = new TaskManager(this, request.provisioningTaskReference);

        // mark task as started
        mgr.patchTask(TaskStage.STARTED);

        ProvisionContext.populateContextThen(this, createInitialContext(request), ctx -> {
            if (request.isMockRequest) {
                handleMockRequest(mgr, request, ctx);
                return;
            }

            switch (request.requestType) {
            case CREATE:
                handleCreateInstance(ctx, request);
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
            TaskManager mgr = new TaskManager(this, request.provisioningTaskReference);
            mgr.patchTaskToFailure(failure);
        };

        initialContext.pool = VSphereIOThreadPoolAllocator.getPool(this);
        return initialContext;
    }

    private void handleCreateInstance(ProvisionContext ctx, ComputeInstanceRequest req) {
        TaskManager mgr = new TaskManager(this, ctx.provisioningTaskReference);

        ctx.pool.submit(this, ctx.getAdapterManagementReference(), ctx.vSphereCredentials,
                (connection, ce) -> {
                    if (ctx.fail(ce)) {
                        return;
                    }

                    try {
                        InstanceClient client = new InstanceClient(connection, ctx.child, ctx.parent);
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
                        Operation patchResource = patchComputeResource(state, ctx.computeReference);
                        Operation finishTask = mgr.createTaskPatch(TaskStage.FINISHED);

                        OperationSequence
                                .create(patchDisks)
                                .next(patchResource)
                                .next(finishTask)
                                .setCompletion(ctx.failOnError())
                                .sendWith(this);
                    } catch (Exception e) {
                        ctx.fail(e);
                    }
                });
    }

    private Operation powerOnVm(ComputeInstanceRequest req) {
        ComputePowerRequest cpr = new ComputePowerRequest();
        cpr.isMockRequest = req.isMockRequest;
        cpr.computeReference = req.computeReference;
        cpr.provisioningTaskReference = req.provisioningTaskReference;

        URI uri = UriUtils.buildUri(getHost(), VSphereAdapterPowerService.class);
        return Operation.createPatch(uri).setBody(cpr);
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
        TaskManager mgr = new TaskManager(this, ctx.provisioningTaskReference);

        ctx.pool.submit(this, ctx.getAdapterManagementReference(), ctx.vSphereCredentials,
                (conn, ce) -> {
                    if (ctx.fail(ce)) {
                        return;
                    }

                    try {
                        InstanceClient client = new InstanceClient(conn, ctx.child, ctx.parent);
                        client.deleteInstance();

                        // complete task
                        mgr.patchTask(TaskStage.FINISHED);
                    } catch (Exception e) {
                        ctx.fail(e);
                    }
                });
    }

    private void handleMockRequest(TaskManager mgr, ComputeInstanceRequest req,
            ProvisionContext ctx) {
        // clean up the compute state
        if (req.requestType == InstanceRequestType.DELETE) {
            deleteComputeState(ctx);
        } else {
            // just report the task as finished
            mgr.patchTask(TaskStage.FINISHED);
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
