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

package com.vmware.photon.controller.model.adapters.util;

import static com.vmware.xenon.common.Service.STAT_NAME_OPERATION_DURATION;

import java.net.URI;
import java.util.function.Consumer;

import com.vmware.photon.controller.model.tasks.ProvisionComputeTaskService.ProvisionComputeTaskState;
import com.vmware.photon.controller.model.tasks.ProvisionNetworkTaskService;
import com.vmware.photon.controller.model.tasks.ResourceEnumerationTaskService.ResourceEnumerationTaskState;

import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.Utils;

/**
 * Common utility methods for different adapters.
 */
public class AdapterUtils {

    public static void sendFailurePatchToProvisioningTask(StatelessService service,
            URI taskLink, Throwable t) {
        service.logWarning(Utils.toString(t));
        sendPatchToProvisioningTask(service, taskLink, t);
    }

    public static void sendPatchToProvisioningTask(StatelessService service, URI taskLink) {
        sendPatchToProvisioningTask(service, taskLink, null);
    }

    private static void sendPatchToProvisioningTask(StatelessService service, URI taskLink,
            Throwable t) {
        ProvisionComputeTaskState provisioningTaskBody = new ProvisionComputeTaskState();
        TaskState taskInfo = new TaskState();
        if (t == null) {
            taskInfo.stage = TaskState.TaskStage.FINISHED;
        } else {
            taskInfo.failure = Utils.toServiceErrorResponse(t);
            taskInfo.stage = TaskState.TaskStage.FAILED;
        }
        provisioningTaskBody.taskInfo = taskInfo;
        service.sendRequest(Operation.createPatch(taskLink).setBody(
                provisioningTaskBody));
    }

    /**
     * Sends failure patch to the enumeration task.
     * @param service The service from which the patch is originating.
     * @param taskLink The enumeration task service to which the patch is to be sent
     * @param t The exception that was encountered.
     */
    public static void sendFailurePatchToEnumerationTask(StatelessService service,
            URI taskLink, Throwable t) {
        service.logWarning(Utils.toString(t));
        sendPatchToEnumerationTask(service, taskLink, t);
    }

    /**
     * Sends patch to the enumeration task.
     * @param service The service from which the patch is originating.
     * @param taskLink The enumeration task service to which the patch is to be sent
     */
    public static void sendPatchToEnumerationTask(StatelessService service, URI taskLink) {
        sendPatchToEnumerationTask(service, taskLink, null);
    }

    /**
     * Sends patch to the enumeration task.
     * @param service The service from which the patch is originating.
     * @param taskLink The enumeration task service to which the patch is to be sent
     * @param t The exception that was encountered.
     */
    private static void sendPatchToEnumerationTask(StatelessService service, URI taskLink,
            Throwable t) {
        ResourceEnumerationTaskState enumerationTaskBody = new ResourceEnumerationTaskState();
        TaskState taskInfo = new TaskState();
        if (t == null) {
            taskInfo.stage = TaskState.TaskStage.FINISHED;
        } else {
            taskInfo.failure = Utils.toServiceErrorResponse(t);
            taskInfo.stage = TaskState.TaskStage.FAILED;
        }
        enumerationTaskBody.taskInfo = taskInfo;
        service.sendRequest(Operation.createPatch(taskLink).setBody(
                enumerationTaskBody));
    }

    /**
     * Method will be responsible for getting the service state for the
     * requested resource and invoke Consumer callback for success and
     * failure
     */
    public static void getServiceState(Service service, URI computeUri, Consumer<Operation> success,
            Consumer<Throwable> failure) {
        service.sendRequest(Operation.createGet(computeUri).setCompletion((o, e) -> {
            if (e != null) {
                failure.accept(e);
                return;
            }
            success.accept(o);
        }));
    }

    public static void sendNetworkFinishPatch(StatelessService service,
            URI taskLink) {
        ProvisionNetworkTaskService.ProvisionNetworkTaskState pn = new ProvisionNetworkTaskService.ProvisionNetworkTaskState();
        TaskState taskInfo = new TaskState();
        taskInfo.stage = TaskState.TaskStage.FINISHED;
        pn.taskInfo = taskInfo;
        service.sendRequest(Operation.createPatch(taskLink).setBody(pn));

    }

    /**
     * Updates the time spent in the said operation in a stateless service.
     * @param the start time from which the duration will be computed.
     */
    public static void updateDurationStats(StatelessService service, long startTime) {
        long duration = Utils.getNowMicrosUtc() - startTime;
        service.setStat(STAT_NAME_OPERATION_DURATION, duration);
    }
}
