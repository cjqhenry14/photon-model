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

package com.vmware.photon.controller.model.adapters.awsadapter;

import java.util.logging.Level;

import com.vmware.photon.controller.model.adapters.awsadapter.enumeration.AWSEnumerationAdapterService;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.Utils;

/**
 * Helper class that starts provisioning adapters
 */
public class AWSAdapters {

    public static final String[] LINKS = {
            AWSInstanceService.SELF_LINK,
            AWSNetworkService.SELF_LINK,
            AWSStatsService.SELF_LINK,
            AWSEnumerationAdapterService.SELF_LINK };

    public static void startServices(ServiceHost host) throws Throwable {

        try {
            host.startService(new AWSInstanceService());
            host.startService(new AWSNetworkService());
            host.startService(new AWSStatsService());
            host.startService(new AWSEnumerationAdapterService());

        } catch (Exception e) {
            host.log(Level.WARNING, "Exception staring provisioning adapters: %s",
                    Utils.toString(e));
        }
    }
}
