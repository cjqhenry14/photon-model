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

import java.net.URI;

import com.vmware.photon.controller.model.tasks.ProvisionComputeTaskService.ProvisionComputeTaskState;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceRequestSender;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.TaskState.TaskStage;
import com.vmware.xenon.common.Utils;

/**
 * Manage tasks lifecycle with this.
 */
public class TaskManager {
    private final ServiceRequestSender service;
    private final URI taskReference;

    public TaskManager(ServiceRequestSender service, URI taskReference) {
        this.service = service;
        this.taskReference = taskReference;
    }

    public void patchTask(TaskStage stage) {
        Operation op = createTaskPatch(stage);

        op.sendWith(service);
    }

    public Operation createTaskPatch(TaskStage stage) {
        ProvisionComputeTaskState body = new ProvisionComputeTaskState();
        TaskState taskInfo = new TaskState();
        taskInfo.stage = stage;
        body.taskInfo = taskInfo;

        return Operation
                .createPatch(taskReference)
                .setBody(body);
    }

    public void patchTaskToFailure(Throwable failure) {
        createFailurePatch(failure).sendWith(service);
    }

    public Operation createFailurePatch(Throwable failure) {
        ProvisionComputeTaskState body = new ProvisionComputeTaskState();
        body.taskInfo = new TaskState();
        body.taskInfo.stage = TaskStage.FAILED;
        body.taskInfo.failure = Utils.toServiceErrorResponse(failure);

        return Operation
                .createPatch(taskReference)
                .setBody(body);
    }
}
