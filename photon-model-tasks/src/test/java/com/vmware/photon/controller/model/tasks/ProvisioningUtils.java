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


import java.net.URI;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.TimeoutException;

import com.vmware.photon.controller.model.resources.ComputeDescriptionService;
import com.vmware.photon.controller.model.resources.ComputeService;
import com.vmware.photon.controller.model.resources.DiskService;
import com.vmware.photon.controller.model.resources.NetworkInterfaceService;
import com.vmware.photon.controller.model.resources.ResourceDescriptionService;
import com.vmware.photon.controller.model.resources.ResourcePoolService;
import com.vmware.photon.controller.model.resources.SnapshotService;
import com.vmware.photon.controller.model.tasks.ProvisionComputeTaskFactoryService;
import com.vmware.photon.controller.model.tasks.ResourceAllocationTaskFactoryService;
import com.vmware.photon.controller.model.tasks.ResourceEnumerationTaskFactoryService;
import com.vmware.photon.controller.model.tasks.ResourceRemovalTaskFactoryService;
import com.vmware.photon.controller.model.tasks.SnapshotTaskFactoryService;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocumentQueryResult;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.common.test.VerificationHost;

/**
 * Helper class for VM provisioning tests.
 *
 */
public class ProvisioningUtils {

    public static void startProvisioningServices(VerificationHost host) throws Throwable {
        List<String> serviceSelfLinks = new ArrayList<String>();

        host.setSystemAuthorizationContext();

        host.startService(
                Operation.createPost(host, ComputeService.FACTORY_LINK),
                ComputeService.createFactory());
        serviceSelfLinks.add(ComputeService.FACTORY_LINK);

        host.startService(
                Operation.createPost(host, ResourcePoolService.FACTORY_LINK),
                ResourcePoolService.createFactory());
        serviceSelfLinks.add(ResourcePoolService.FACTORY_LINK);

        host.startService(
                Operation.createPost(host, SnapshotService.FACTORY_LINK),
                SnapshotService.createFactory());
        serviceSelfLinks.add(SnapshotService.FACTORY_LINK);

        host.startService(
                Operation.createPost(UriUtils.buildUri(host,
                        ResourceAllocationTaskFactoryService.class)),
                new ResourceAllocationTaskFactoryService());
        serviceSelfLinks.add(ResourceAllocationTaskFactoryService.SELF_LINK);

        host.startService(
                Operation.createPost(UriUtils.buildUri(host,
                        ResourceEnumerationTaskFactoryService.class)),
                new ResourceEnumerationTaskFactoryService());
        serviceSelfLinks.add(ResourceEnumerationTaskFactoryService.SELF_LINK);

        host.startService(
                Operation.createPost(UriUtils.buildUri(host,
                        ResourceRemovalTaskFactoryService.class)),
                new ResourceRemovalTaskFactoryService());
        serviceSelfLinks.add(ResourceRemovalTaskFactoryService.SELF_LINK);

        host.startService(
                Operation.createPost(host,
                        ComputeDescriptionService.FACTORY_LINK),
                ComputeDescriptionService.createFactory());
        serviceSelfLinks.add(ComputeDescriptionService.FACTORY_LINK);

        host.startService(
                Operation.createPost(host,
                        DiskService.FACTORY_LINK),
                DiskService.createFactory());
        serviceSelfLinks.add(DiskService.FACTORY_LINK);

        host.startService(
                Operation.createPost(UriUtils.buildUri(host,
                        ProvisionComputeTaskFactoryService.class)),
                new ProvisionComputeTaskFactoryService());
        serviceSelfLinks.add(ProvisionComputeTaskFactoryService.SELF_LINK);

        host.startService(
                Operation.createPost(host,
                        NetworkInterfaceService.FACTORY_LINK),
                NetworkInterfaceService.createFactory());
        serviceSelfLinks.add(NetworkInterfaceService.FACTORY_LINK);

        host.startService(
                Operation.createPost(UriUtils.buildUri(host,
                        ProvisionNetworkTaskFactoryService.class)),
                new ProvisionNetworkTaskFactoryService());
        serviceSelfLinks.add(ProvisionNetworkTaskFactoryService.SELF_LINK);

        host.startService(
                Operation.createPost(UriUtils.buildUri(host,
                        SnapshotTaskFactoryService.class)),
                new SnapshotTaskFactoryService());
        serviceSelfLinks.add(SnapshotTaskFactoryService.SELF_LINK);

        host.startService(
                Operation.createPost(host,
                        ResourceDescriptionService.FACTORY_LINK),
                ResourceDescriptionService.createFactory());
        serviceSelfLinks.add(ResourceDescriptionService.FACTORY_LINK);


        host.startService(
                Operation.createPost(UriUtils.buildUri(host,
                        ProvisionFirewallTaskFactoryService.class)),
                new ProvisionFirewallTaskFactoryService());
        serviceSelfLinks.add(ProvisionFirewallTaskFactoryService.SELF_LINK);

        waitForServiceStart(host, serviceSelfLinks.toArray(new String[]{}));

        host.resetSystemAuthorizationContext();
    }

    public static void waitForServiceStart(VerificationHost host, String... selfLinks) throws Throwable {
        host.testStart(selfLinks.length);
        host.registerForServiceAvailability(host.getCompletion(),
                selfLinks);
        host.testWait();
    }

    public static ServiceDocumentQueryResult queryComputeInstances(VerificationHost host, int desiredCount)
            throws Throwable {
        Date expiration = host.getTestExpiration();
        do {
            ServiceDocumentQueryResult res = host.getFactoryState(UriUtils
                    .buildExpandLinksQueryUri(UriUtils.buildUri(host,
                            ComputeService.FACTORY_LINK)));
            if (res.documents.size() == desiredCount) {
                return res;
            }
        } while (new Date().before(expiration));
        throw new TimeoutException();
    }

    public static void waitForTaskCompletion(VerificationHost host,
            Map<URI, ProvisionComputeTaskService.ProvisionComputeTaskState> provisioningTasks)
            throws Throwable, InterruptedException, TimeoutException {
        Date expiration = host.getTestExpiration();
        Map<String, ProvisionComputeTaskService.ProvisionComputeTaskState> pendingTasks = new HashMap<>();
        do {
            pendingTasks.clear();
            // grab in parallel, all task state, from all running tasks
            provisioningTasks = host.getServiceState(null,
                    ProvisionComputeTaskService.ProvisionComputeTaskState.class,
                    provisioningTasks.keySet());

            boolean isConverged = true;
            for (Entry<URI, ProvisionComputeTaskService.ProvisionComputeTaskState> e : provisioningTasks
                    .entrySet()) {
                ProvisionComputeTaskService.ProvisionComputeTaskState currentState = e.getValue();

                if (currentState.taskInfo.stage == TaskState.TaskStage.FAILED) {
                    throw new IllegalStateException("Task failed:" + Utils.toJsonHtml(currentState));
                }

                if (currentState.taskInfo.stage != TaskState.TaskStage.FINISHED) {
                    pendingTasks.put(currentState.documentSelfLink, currentState);
                    isConverged = false;
                }
            }

            if (isConverged) {
                return;
            }

            Thread.sleep(1000);
        } while (new Date().before(expiration));

        for (ProvisionComputeTaskService.ProvisionComputeTaskState t : pendingTasks.values()) {
            host.log("Pending task:\n%s", Utils.toJsonHtml(t));
        }

        throw new TimeoutException("Some tasks never finished");
    }
}
