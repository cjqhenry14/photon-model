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

package com.vmware.photon.controller.model;

import java.util.function.Consumer;

import com.vmware.photon.controller.model.resources.ComputeDescriptionService;
import com.vmware.photon.controller.model.resources.ComputeService;
import com.vmware.photon.controller.model.resources.DiskService;
import com.vmware.photon.controller.model.resources.FirewallService;
import com.vmware.photon.controller.model.resources.NetworkInterfaceService;
import com.vmware.photon.controller.model.resources.NetworkService;
import com.vmware.photon.controller.model.resources.ResourceDescriptionService;
import com.vmware.photon.controller.model.resources.ResourcePoolService;
import com.vmware.photon.controller.model.resources.SnapshotService;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.ServiceHost;

/**
 * Helper class that starts all the photon model provisioning services
 */
public class PhotonModelServices {

    public static void startServices(ServiceHost host,
            Consumer<Class<? extends Service>> addPrivilegedService) throws Throwable {
        host.startService(
                Operation.createPost(host,
                        ComputeDescriptionService.FACTORY_LINK),
                ComputeDescriptionService.createFactory());

        host.startService(
                Operation.createPost(host, ComputeService.FACTORY_LINK),
                ComputeService.createFactory());

        host.startService(
                Operation.createPost(host, ResourcePoolService.FACTORY_LINK),
                ResourcePoolService.createFactory());

        host.startService(
                Operation.createPost(host,
                        ResourceDescriptionService.FACTORY_LINK),
                ResourceDescriptionService.createFactory());

        host.startService(
                Operation.createPost(host,
                        DiskService.FACTORY_LINK),
                DiskService.createFactory());

        host.startService(
                Operation.createPost(host,
                        SnapshotService.FACTORY_LINK),
                SnapshotService.createFactory());

        host.startService(
                Operation.createPost(host,
                        NetworkInterfaceService.FACTORY_LINK),
                NetworkInterfaceService.createFactory());

        host.startService(
                Operation.createPost(host,
                        ResourceDescriptionService.FACTORY_LINK),
                ResourceDescriptionService.createFactory());

        host.startService(
                Operation.createPost(host,
                        NetworkService.FACTORY_LINK),
                NetworkService.createFactory());

        host.startService(
                Operation.createPost(host,
                        FirewallService.FACTORY_LINK),
                FirewallService.createFactory());

    }
}
