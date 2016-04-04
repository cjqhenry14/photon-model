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

package com.vmware.photon.controller.model.adapters.azureadapter;

import com.microsoft.azure.credentials.ApplicationTokenCredentials;
import com.microsoft.azure.management.network.models.NetworkInterface;
import com.microsoft.azure.management.network.models.NetworkSecurityGroup;
import com.microsoft.azure.management.network.models.PublicIPAddress;
import com.microsoft.azure.management.network.models.VirtualNetwork;
import com.microsoft.azure.management.resources.models.ResourceGroup;
import com.microsoft.azure.management.storage.models.StorageAccount;

import com.vmware.photon.controller.model.adapterapi.ComputeInstanceRequest;
import com.vmware.photon.controller.model.resources.ComputeService;
import com.vmware.xenon.services.common.AuthCredentialsService;

/**
 * Context object to store relevant information during different stages.
 */
public class AzureAllocationContext {

    public AzureStages stage;

    public ComputeInstanceRequest computeRequest;
    public ComputeService.ComputeStateWithDescription child;
    public ComputeService.ComputeStateWithDescription parent;
    public AuthCredentialsService.AuthCredentialsServiceState parentAuth;
    public ApplicationTokenCredentials credentials;

    public ResourceGroup resourceGroup;
    public StorageAccount storage;
    public VirtualNetwork network;
    public PublicIPAddress publicIP;
    public NetworkInterface nic;
    public String storageAccountName;
    public NetworkSecurityGroup securityGroup;

    public Throwable error;

    /**
     * Initialize with request info and first stage.
     */
    public AzureAllocationContext(ComputeInstanceRequest computeReq) {
        this.computeRequest = computeReq;
        this.stage = AzureStages.VMDESC;
    }
}
