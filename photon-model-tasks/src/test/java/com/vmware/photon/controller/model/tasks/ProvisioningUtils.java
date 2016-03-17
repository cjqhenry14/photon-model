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
                Operation.createPost(host,
                        ResourceAllocationTaskService.FACTORY_LINK),
                ResourceAllocationTaskService.createFactory());
        serviceSelfLinks.add(ResourceAllocationTaskService.FACTORY_LINK);

        host.startService(
                Operation.createPost(host,
                        ResourceEnumerationTaskService.FACTORY_LINK),
                ResourceEnumerationTaskService.createFactory());
        serviceSelfLinks.add(ResourceEnumerationTaskService.FACTORY_LINK);

        host.startService(
                Operation.createPost(host,
                        ResourceRemovalTaskService.FACTORY_LINK),
                ResourceRemovalTaskService.createFactory());
        serviceSelfLinks.add(ResourceRemovalTaskService.FACTORY_LINK);

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
                Operation.createPost(host,
                        ProvisionComputeTaskService.FACTORY_LINK),
                ProvisionComputeTaskService.createFactory());
        serviceSelfLinks.add(ProvisionComputeTaskService.FACTORY_LINK);

        host.startService(
                Operation.createPost(host,
                        NetworkInterfaceService.FACTORY_LINK),
                NetworkInterfaceService.createFactory());
        serviceSelfLinks.add(NetworkInterfaceService.FACTORY_LINK);

        host.startService(
                Operation.createPost(host,
                        ProvisionNetworkTaskService.FACTORY_LINK),
                ProvisionNetworkTaskService.createFactory());
        serviceSelfLinks.add(ProvisionNetworkTaskService.FACTORY_LINK);

        host.startService(
                Operation.createPost(host,
                        SnapshotTaskService.FACTORY_LINK),
                SnapshotTaskService.createFactory());
        serviceSelfLinks.add(SnapshotTaskService.FACTORY_LINK);

        host.startService(
                Operation.createPost(host,
                        ResourceDescriptionService.FACTORY_LINK),
                ResourceDescriptionService.createFactory());
        serviceSelfLinks.add(ResourceDescriptionService.FACTORY_LINK);


        host.startService(
                Operation.createPost(host,
                        ProvisionFirewallTaskService.FACTORY_LINK),
                ProvisionFirewallTaskService.createFactory());
        serviceSelfLinks.add(ProvisionFirewallTaskService.FACTORY_LINK);

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
