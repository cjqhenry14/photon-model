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

package com.vmware.photon.controller.model.adapters.azure;

import java.util.logging.Level;

import com.vmware.photon.controller.model.adapters.azure.enumeration.AzureEnumerationAdapterService;
import com.vmware.photon.controller.model.adapters.azure.instance.AzureInstanceService;
import com.vmware.photon.controller.model.adapters.azure.stats.AzureStatsService;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;

/**
 * Helper class that starts provisioning adapters
 */
public class AzureAdapters {

    public static void startServices(ServiceHost host) throws Throwable {

        try {
            host.startService(
                    Operation.createPost(
                            UriUtils.buildUri(host, AzureEnumerationAdapterService.class)),
                    new AzureEnumerationAdapterService());
            host.startService(
                    Operation.createPost(UriUtils.buildUri(host, AzureInstanceService.class)),
                    new AzureInstanceService());
            host.startService(
                    Operation.createPost(UriUtils.buildUri(host, AzureStatsService.class)),
                    new AzureStatsService());

        } catch (Exception e) {
            host.log(Level.WARNING, "Exception staring provisioning adapters: %s",
                    Utils.toString(e));
        }
    }
}
