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

package com.vmware.photon.controller.model.util;

import java.net.URI;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Operation.CompletionHandler;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.services.common.NodeGroupUtils;
import com.vmware.xenon.services.common.ServiceUriPaths;

/**
 * Utility class for Photon Model.
 *
 */
public class PhotonModelUtils {

    /**
     * Utility method to check if the specified set of factories are available
     * @param service service context to invoke this method on
     * @param op Operation to pass back on completion
     * @param factories factories to check
     * @param onSuccess Consumer class to invoke on success
     */
    public static void checkFactoryAvailability(Service service, Operation op, Set<URI> factories,
            Consumer<Operation> onSuccess) {
        AtomicInteger successfulOps = new AtomicInteger(0);
        AtomicInteger totalOps = new AtomicInteger(0);
        int expectedOps = factories.size();
        CompletionHandler handler = (waitOp, waitEx) -> {
            if (waitEx == null) {
                factories.remove(waitOp.getUri());
                successfulOps.incrementAndGet();
            }
            if (totalOps.incrementAndGet() == expectedOps) {
                if (successfulOps.get() == expectedOps) {
                    onSuccess.accept(op);
                } else {
                    service.getHost().schedule(() -> {
                        PhotonModelUtils.checkFactoryAvailability(service, op,
                                factories, onSuccess);
                    }, service.getHost().getMaintenanceIntervalMicros(), TimeUnit.MICROSECONDS);
                }
            }
        };
        for (URI factory : factories) {
            NodeGroupUtils.checkServiceAvailability(handler, service.getHost(),
                    factory, ServiceUriPaths.DEFAULT_NODE_SELECTOR);
        }
    }

}
