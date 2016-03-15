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

package com.vmware.photon.controller.model.tasks.monitoring;

import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

import com.vmware.photon.controller.model.helpers.BaseModelTest;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription;
import com.vmware.photon.controller.model.resources.ComputeService;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.resources.ResourcePoolService;
import com.vmware.photon.controller.model.resources.ResourcePoolService.ResourcePoolState;
import com.vmware.photon.controller.model.tasks.TaskServices;
import com.vmware.photon.controller.model.tasks.monitoring.StatsCollectionTaskSchedulerService.StatsCollectionTaskServiceSchedulerState;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocumentQueryResult;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.ServiceStats;
import com.vmware.xenon.common.UriUtils;

public class StatsCollectionTaskSchedulerServiceTest extends BaseModelTest {

    public int numResources = 200;

    @Override
    protected void startRequiredServices() throws Throwable {
        TaskServices.startFactories(this);
        super.startRequiredServices();
        // set the monitoring interval to 250 ms
        System.setProperty(StatsCollectionTaskSchedulerService.STATS_MONITORING_INTERVAL, "250");
        this.host.startService(
                Operation.createPost(UriUtils.buildFactoryUri(this.host,
                        StatsCollectionTaskSchedulerService.class)),
                StatsCollectionTaskSchedulerService.createFactory());
        this.host.startService(
                Operation.createPost(UriUtils.buildFactoryUri(this.host,
                        StatsCollectionTaskService.class)),
                StatsCollectionTaskService.createFactory());
        this.host.startService(
                Operation.createPost(UriUtils.buildFactoryUri(this.host,
                        SingleResourceStatsCollectionTaskService.class)),
                SingleResourceStatsCollectionTaskService.createFactory());
        host.startService(
                Operation.createPost(UriUtils.buildUri(host,
                        MockStatsAdapter.class)),
                new MockStatsAdapter());
        this.host.waitForServiceAvailable(StatsCollectionTaskService.FACTORY_LINK);
        this.host.waitForServiceAvailable(SingleResourceStatsCollectionTaskService.FACTORY_LINK);
        this.host.waitForServiceAvailable(StatsCollectionTaskSchedulerService.FACTORY_LINK);
        this.host.waitForServiceAvailable(MockStatsAdapter.SELF_LINK);
    }

    @Test
    public void testStatsCollectorCreation() throws Throwable {
        // create a resource pool
        ResourcePoolState rpState = new ResourcePoolState();
        ResourcePoolState rpReturnState = postServiceSynchronously(
                ResourcePoolService.FACTORY_LINK, rpState,
                ResourcePoolState.class);
        ComputeDescription cDesc = new ComputeDescription();
        cDesc.statsAdapterReference = UriUtils.buildUri(this.host, MockStatsAdapter.SELF_LINK);
        ComputeDescription descReturnState = postServiceSynchronously(
                ComputeDescriptionService.FACTORY_LINK, cDesc,
                ComputeDescription.class);
        ComputeState computeState = new ComputeState();
        computeState.descriptionLink = descReturnState.documentSelfLink;
        computeState.resourcePoolLink = rpReturnState.documentSelfLink;
        List<String> computeLinks = new ArrayList<String>();
        for (int i = 0; i < numResources; i++) {
            ComputeState res = postServiceSynchronously(
                    ComputeService.FACTORY_LINK, computeState,
                    ComputeState.class);
            computeLinks.add(res.documentSelfLink);
        }
        // create a stats collection scheduler task
        StatsCollectionTaskServiceSchedulerState schedulerState =
                new StatsCollectionTaskServiceSchedulerState();
        schedulerState.resourcePoolLink = rpReturnState.documentSelfLink;
        postServiceSynchronously(
                StatsCollectionTaskSchedulerService.FACTORY_LINK, schedulerState,
                StatsCollectionTaskServiceSchedulerState.class);
        ServiceDocumentQueryResult res = host.getFactoryState(UriUtils
                .buildExpandLinksQueryUri(UriUtils.buildUri(host,
                        StatsCollectionTaskSchedulerService.FACTORY_LINK)));
        assertTrue(res.documents.size() == 1);

        // get stats from resources; make sure maintenance has run more than once
        for (int i = 0; i < numResources; i++) {
            String statsUriPath = UriUtils.buildUriPath(computeLinks.get(i),
                    ServiceHost.SERVICE_URI_SUFFIX_STATS);
            this.host.waitFor("Error waiting for stats", () -> {
                ServiceStats resStats = getServiceSynchronously(statsUriPath, ServiceStats.class);
                if (resStats.entries.get(MockStatsAdapter.KEY_1) != null &&
                        resStats.entries.get(MockStatsAdapter.KEY_1).latestValue > numResources &&
                        resStats.entries.get(MockStatsAdapter.KEY_2) != null &&
                        resStats.entries.get(MockStatsAdapter.KEY_2).latestValue > numResources) {
                    return true;
                }
                return false;
            });
        }
    }
}
