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

package com.vmware.photon.controller.model.tasks;

import com.vmware.photon.controller.model.helpers.BaseModelTest;

/**
 * Service factories used in Photon Model Task package.
 */
public class TaskServices {

    public static void startFactories(BaseModelTest test) throws Throwable {
        if (test.getHost().getServiceStage(ProvisionComputeTaskService.FACTORY_LINK) != null) {
            return;
        }
        test.getHost().startFactoryServicesSynchronously(
                SshCommandTaskService.createFactory(),
                ProvisionComputeTaskService.createFactory(),
                ProvisionFirewallTaskService.createFactory(),
                ProvisionNetworkTaskService.createFactory(),
                ResourceAllocationTaskService.createFactory(),
                ResourceEnumerationTaskService.createFactory(),
                ResourceRemovalTaskService.createFactory(),
                SnapshotTaskService.createFactory(),
                ScheduledTaskService.createFactory());
    }
}
