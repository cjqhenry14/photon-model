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

import com.vmware.photon.controller.model.tasks.monitoring.SingleResourceStatsCollectionTaskService;
import com.vmware.photon.controller.model.tasks.monitoring.StatsCollectionTaskSchedulerService;
import com.vmware.photon.controller.model.tasks.monitoring.StatsCollectionTaskService;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceHost;

/**
 * Helper class that starts all the photon model Task services
 */
public class PhotonModelTaskServices {

    public static final String[] LINKS = {
            SshCommandTaskService.FACTORY_LINK,
            ResourceAllocationTaskService.FACTORY_LINK,
            ResourceEnumerationTaskService.FACTORY_LINK,
            ScheduledTaskService.FACTORY_LINK,
            ResourceRemovalTaskService.FACTORY_LINK,
            ProvisionComputeTaskService.FACTORY_LINK,
            ProvisionNetworkTaskService.FACTORY_LINK,
            SnapshotTaskService.FACTORY_LINK,
            ProvisionFirewallTaskService.FACTORY_LINK,
            StatsCollectionTaskSchedulerService.FACTORY_LINK,
            StatsCollectionTaskService.FACTORY_LINK,
            SingleResourceStatsCollectionTaskService.FACTORY_LINK };

    public static void startServices(ServiceHost host) throws Throwable {

        host.startService(Operation.createPost(host,
                SshCommandTaskService.FACTORY_LINK),
                SshCommandTaskService.createFactory());
        host.startFactory(new ResourceAllocationTaskService());
        host.startFactory(new ResourceEnumerationTaskService());
        host.startFactory(new ScheduledTaskService());
        host.startFactory(new ResourceRemovalTaskService());
        host.startFactory(new ProvisionComputeTaskService());
        host.startFactory(new ProvisionNetworkTaskService());
        host.startFactory(new SnapshotTaskService());
        host.startFactory(new ProvisionFirewallTaskService());
        host.startFactory(new StatsCollectionTaskSchedulerService());
        host.startFactory(new StatsCollectionTaskService());
        host.startFactory(new SingleResourceStatsCollectionTaskService());
    }
}
