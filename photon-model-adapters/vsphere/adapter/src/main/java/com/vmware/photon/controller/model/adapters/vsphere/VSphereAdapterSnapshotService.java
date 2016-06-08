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

import com.vmware.photon.controller.model.adapterapi.SnapshotRequest;
import com.vmware.photon.controller.model.adapters.util.TaskManager;
import com.vmware.photon.controller.model.adapters.vsphere.util.connection.Connection;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeStateWithDescription;
import com.vmware.photon.controller.model.resources.SnapshotService.SnapshotState;
import com.vmware.vim25.ManagedObjectReference;
import com.vmware.vim25.TaskInfo;
import com.vmware.vim25.TaskInfoState;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.OperationSequence;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.common.TaskState.TaskStage;
import com.vmware.xenon.common.UriUtils;

/**
 */
public class VSphereAdapterSnapshotService extends StatelessService {

    public static final String SELF_LINK = VSphereUriPaths.SNAPSHOT_SERVICE;

    @Override
    public void handlePatch(Operation op) {
        if (!op.hasBody()) {
            op.fail(new IllegalArgumentException("body is required"));
            return;
        }

        op.setStatusCode(Operation.STATUS_CODE_CREATED);
        op.complete();

        SnapshotRequest request = op.getBody(SnapshotRequest.class);

        TaskManager mgr = new TaskManager(this, request.snapshotTaskReference);

        if (request.isMockRequest) {
            mgr.patchTask(TaskStage.FINISHED);
            return;
        }

        Operation.createGet(request.snapshotReference)
                .setCompletion(o -> {
                    thenWithSnapshotState(o.getBody(SnapshotState.class), mgr);
                }, mgr)
                .sendWith(this);
    }

    private void thenWithSnapshotState(SnapshotState state, TaskManager mgr) {
        URI computeUri = UriUtils.buildUri(this.getHost(), state.computeLink);

        Operation.createGet(ComputeStateWithDescription.buildUri(computeUri))
                .setCompletion(o -> {
                    thenWithParentState(state, o.getBody(ComputeStateWithDescription.class), mgr);
                }, mgr).sendWith(this);
    }

    private void thenWithParentState(SnapshotState state, ComputeStateWithDescription compute,
            TaskManager mgr) {
        URI computeUri = UriUtils.buildUri(this.getHost(), compute.parentLink);

        Operation.createGet(ComputeStateWithDescription.buildUri(computeUri))
                .setCompletion(o -> {
                    thenWithComputeState(state, compute,
                            o.getBody(ComputeStateWithDescription.class),
                            mgr);
                }, mgr).sendWith(this);
    }

    private void thenWithComputeState(SnapshotState snapshot, ComputeStateWithDescription compute,
            ComputeStateWithDescription parent, TaskManager mgr) {
        VSphereIOThreadPool pool = VSphereIOThreadPoolAllocator.getPool(this);

        pool.submit(this, parent.adapterManagementReference,
                parent.description.authCredentialsLink,
                (connection, e) -> {
                    if (e != null) {
                        mgr.patchTaskToFailure(e);
                    } else {
                        createSnapshot(connection, compute, snapshot, mgr);
                    }
                });
    }

    private void createSnapshot(Connection connection, ComputeStateWithDescription compute,
            SnapshotState snapshot, TaskManager mgr) {

        ManagedObjectReference vm = CustomProperties.of(compute)
                .getMoRef(CustomProperties.VM_MOREF);

        if (vm == null) {
            mgr.patchTaskToFailure(new IllegalStateException("Cannot find VM to snapshot"));
            return;
        }

        ManagedObjectReference task;
        try {
            task = connection.getVimPort()
                    .createSnapshotTask(vm, snapshot.name, snapshot.description, false, false);
        } catch (Exception e) {
            mgr.patchTaskToFailure(e);
            return;
        }

        TaskInfo info;
        try {
            info = VimUtils.waitTaskEnd(connection, task);
            if (info.getState() != TaskInfoState.SUCCESS) {
                VimUtils.rethrow(info.getError());
            }
        } catch (Exception e) {
            mgr.patchTaskToFailure(e);
            return;
        }

        CustomProperties.of(snapshot)
                .put(CustomProperties.SNAPSHOT_MOREF, (ManagedObjectReference) info.getResult());

        Operation patchSnapshot = Operation
                .createPatch(UriUtils.buildUri(getHost(), snapshot.documentSelfLink))
                .setBody(snapshot);

        Operation patchTask = mgr.createTaskPatch(TaskStage.FINISHED);

        OperationSequence
                .create(patchSnapshot)
                .next(patchTask)
                .setCompletion((o, e) -> {
                    if (e != null && !e.isEmpty()) {
                        mgr.patchTaskToFailure(e.values().iterator().next());
                    }
                })
                .sendWith(this);
    }
}
