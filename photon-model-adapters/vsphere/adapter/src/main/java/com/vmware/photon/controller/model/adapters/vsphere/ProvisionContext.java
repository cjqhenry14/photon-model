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
import java.util.function.Consumer;

import com.vmware.photon.controller.model.adapterapi.ComputeInstanceRequest;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeStateWithDescription;
import com.vmware.photon.controller.model.resources.ResourcePoolService.ResourcePoolState;
import com.vmware.xenon.services.common.AuthCredentialsService.AuthCredentialsServiceState;

/**
 */
public class ProvisionContext {
    public ComputeStateWithDescription parent;
    public ComputeStateWithDescription child;

    public ResourcePoolState resourcePool;
    public VSphereIOThreadPool pool;
    public final ComputeInstanceRequest request;
    public AuthCredentialsServiceState vSphereCredentials;
    public Consumer<Throwable> errorHandler;

    public ProvisionContext(ComputeInstanceRequest request) {
        this.request = request;
    }

    /**
     * Fails the provisioning by invoking the errorHandler.
     * @return tre if t is defined, false otherwise
     * @param t
     */
    public boolean fail(Throwable t) {
        if (t != null) {
            this.errorHandler.accept(t);
            return true;
        } else {
            return false;
        }
    }

    public URI getAdapterManagementReference() {
        if (child.adapterManagementReference != null) {
            return child.adapterManagementReference;
        } else {
            return parent.adapterManagementReference;
        }
    }

    /**
     * zoneID is interpreted as a resource pool. Specific zoneId is preferred, else the parents zone
     * is used.
     * @return
     */
    public String getResourcePoolId() {
        if (child.description.zoneId != null) {
            return child.description.zoneId;
        } else {
            return parent.description.zoneId;
        }
    }
}
