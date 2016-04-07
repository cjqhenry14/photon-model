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

package com.vmware.photon.controller.model.adapters.vsphere;

import java.net.URI;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.Utils;

/**
 * There is a single connection pool used for communication for all vSphere endpoints.
 * This class lazily creates a pool for a given ServiceHost thus allowing for a single JVM
 * to host many ServiceHosts.
 */
public class VSphereIOThreadPoolAllocator {
    public static final int DEFAULT_THREAD_POOL_SIZE = Utils.DEFAULT_THREAD_COUNT;

    private static Map<URI, VSphereIOThreadPool> poolsPerHost = new ConcurrentHashMap<>();

    /**
     * Get the connection pool associated with this service's host.
     * @param service
     * @return never null
     */
    public static VSphereIOThreadPool getPool(Service service) {
        return getPool(service.getHost());
    }

    /**
     * Get the connection pool associated with the given host.
     * @param host
     * @return never null
     */
    public static VSphereIOThreadPool getPool(ServiceHost host) {
        URI key = host.getPublicUri();

        return poolsPerHost.computeIfAbsent(key, u -> {
            return VSphereIOThreadPool.createDefault(host, DEFAULT_THREAD_POOL_SIZE);
        });
    }
}
