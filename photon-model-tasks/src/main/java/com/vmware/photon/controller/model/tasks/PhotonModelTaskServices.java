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

import java.util.function.Consumer;

import com.vmware.photon.controller.model.tasks.monitoring.SingleResourceStatsCollectionTaskService;
import com.vmware.photon.controller.model.tasks.monitoring.StatsCollectionTaskSchedulerService;
import com.vmware.photon.controller.model.tasks.monitoring.StatsCollectionTaskService;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.ServiceHost;

/**
 * Helper class that starts all the photon model Task services
 */
public class PhotonModelTaskServices {

    public static void startServices(ServiceHost host,
            Consumer<Class<? extends Service>> addPrivilegedService) throws Throwable {

        host.startService(
                Operation.createPost(host,
                        ResourceAllocationTaskService.FACTORY_LINK),
                ResourceAllocationTaskService.createFactory());

        host.startService(
                Operation.createPost(host,
                        ResourceEnumerationTaskService.FACTORY_LINK),
                ResourceEnumerationTaskService.createFactory());

        host.startService(
                Operation.createPost(host,
                        ScheduledTaskService.FACTORY_LINK),
                ScheduledTaskService.createFactory());

        host.startService(
                Operation.createPost(host,
                        ResourceRemovalTaskService.FACTORY_LINK),
                ResourceRemovalTaskService.createFactory());

        host.startService(
                Operation.createPost(host,
                        ProvisionComputeTaskService.FACTORY_LINK),
                ProvisionComputeTaskService.createFactory());

        host.startService(
                Operation.createPost(host,
                        ProvisionNetworkTaskService.FACTORY_LINK),
                ProvisionNetworkTaskService.createFactory());

        host.startService(
                Operation.createPost(host,
                        SnapshotTaskService.FACTORY_LINK),
                SnapshotTaskService.createFactory());

        host.startService(
                Operation.createPost(host,
                        ProvisionFirewallTaskService.FACTORY_LINK),
                ProvisionFirewallTaskService.createFactory());

        host.startService(
                Operation.createPost(host,
                        StatsCollectionTaskSchedulerService.FACTORY_LINK),
                StatsCollectionTaskSchedulerService.createFactory());

        host.startService(
                Operation.createPost(host,
                        StatsCollectionTaskService.FACTORY_LINK),
                StatsCollectionTaskService.createFactory());

        host.startService(
                Operation.createPost(host,
                        SingleResourceStatsCollectionTaskService.FACTORY_LINK),
                SingleResourceStatsCollectionTaskService.createFactory());

    }
}
