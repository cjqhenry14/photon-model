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

import static com.vmware.photon.controller.model.ComputeProperties.CUSTOM_DISPLAY_NAME;
import static com.vmware.photon.controller.model.adapters.azureadapter.AzureConstants.AZURE_OSDISK_CACHING;
import static com.vmware.photon.controller.model.adapters.azureadapter.AzureConstants.AZURE_TENANT_ID;
import static com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription.ENVIRONMENT_NAME_AZURE;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Random;
import java.util.UUID;

import com.vmware.photon.controller.model.resources.ComputeDescriptionService;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription.ComputeType;
import com.vmware.photon.controller.model.resources.ComputeService;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.resources.DiskService;
import com.vmware.photon.controller.model.resources.DiskService.DiskState;
import com.vmware.photon.controller.model.resources.DiskService.DiskType;
import com.vmware.photon.controller.model.resources.ResourcePoolService;
import com.vmware.photon.controller.model.resources.ResourcePoolService.ResourcePoolState;
import com.vmware.photon.controller.model.tasks.ProvisioningUtils;
import com.vmware.photon.controller.model.tasks.ResourceRemovalTaskService;
import com.vmware.photon.controller.model.tasks.ResourceRemovalTaskService.ResourceRemovalTaskState;
import com.vmware.photon.controller.model.tasks.TestUtils;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.test.VerificationHost;
import com.vmware.xenon.services.common.AuthCredentialsService;
import com.vmware.xenon.services.common.AuthCredentialsService.AuthCredentialsServiceState;
import com.vmware.xenon.services.common.QueryTask.QuerySpecification;

public class AzureTestUtil {
    public static final String AZURE_ADMIN_USERNAME = "azureuser";
    public static final String AZURE_ADMIN_PASSWORD = "Pa$$word1";
    public static final String AZURE_VM_SIZE = "Basic_A0";
    public static final String IMAGE_REFERENCE = "Canonical:UbuntuServer:14.04.3-LTS:latest";
    public static final String AZURE_RESOURCE_GROUP_LOCATION = "westus";
    public static final String AZURE_STORAGE_ACCOUNT_NAME = "storage";
    public static final String AZURE_STORAGE_ACCOUNT_TYPE = "Standard_RAGRS";
    public static final String DEFAULT_OS_DISK_CACHING = "None";

    public static ResourcePoolState createDefaultResourcePool(
            VerificationHost host)
            throws Throwable {
        ResourcePoolState inPool = new ResourcePoolState();
        inPool.name = UUID.randomUUID().toString();
        inPool.id = inPool.name;

        inPool.minCpuCount = 1;
        inPool.minMemoryBytes = 1024;

        ResourcePoolState returnPool =
                TestUtils.doPost(host, inPool, ResourcePoolState.class,
                        UriUtils.buildUri(host, ResourcePoolService.FACTORY_LINK));

        return returnPool;
    }

    public static void deleteVMs(VerificationHost host, String documentSelfLink, boolean isMock)
            throws Throwable {
        host.testStart(1);
        ResourceRemovalTaskState deletionState = new ResourceRemovalTaskState();
        QuerySpecification resourceQuerySpec = new QuerySpecification();
        // query all ComputeState resources for the cluster
        resourceQuerySpec.query
                .setTermPropertyName(ServiceDocument.FIELD_NAME_SELF_LINK)
                .setTermMatchValue(documentSelfLink);
        deletionState.resourceQuerySpec = resourceQuerySpec;
        deletionState.isMockRequest = isMock;
        host.send(Operation
                .createPost(
                        UriUtils.buildUri(host,
                                ResourceRemovalTaskService.FACTORY_LINK))
                .setBody(deletionState)
                .setCompletion(host.getCompletion()));
        host.testWait();
        // check that the VMs are gone
        ProvisioningUtils.queryComputeInstances(host, 1);
    }

    /**
     * Create a compute host description for an Azure instance
     */
    public static ComputeState createDefaultComputeHost(
            VerificationHost host, String clientID, String clientKey, String subscriptionId,
            String tenantId, String resourcePoolLink) throws Throwable {
        AuthCredentialsServiceState auth = new AuthCredentialsServiceState();
        auth.privateKeyId = clientID;
        auth.privateKey = clientKey;
        auth.userLink = subscriptionId;
        auth.customProperties = new HashMap<>();
        auth.customProperties.put(AZURE_TENANT_ID, tenantId);
        auth.documentSelfLink = UUID.randomUUID().toString();

        TestUtils.doPost(host, auth, AuthCredentialsServiceState.class,
                UriUtils.buildUri(host, AuthCredentialsService.FACTORY_LINK));
        String authLink = UriUtils.buildUriPath(AuthCredentialsService.FACTORY_LINK,
                auth.documentSelfLink);

        ComputeDescription azureHostDescription = new ComputeDescription();
        azureHostDescription.id = UUID.randomUUID().toString();
        azureHostDescription.documentSelfLink = azureHostDescription.id;
        azureHostDescription.supportedChildren = new ArrayList<>();
        azureHostDescription.supportedChildren.add(ComputeType.VM_GUEST.name());
        azureHostDescription.instanceAdapterReference = UriUtils.buildUri(host,
                AzureUriPaths.AZURE_INSTANCE_ADAPTER);
        azureHostDescription.enumerationAdapterReference = UriUtils.buildUri(
                host,
                AzureUriPaths.AZURE_ENUMERATION_ADAPTER);
        azureHostDescription.authCredentialsLink = authLink;

        TestUtils.doPost(host, azureHostDescription,
                ComputeDescription.class,
                UriUtils.buildUri(host, ComputeDescriptionService.FACTORY_LINK));

        ComputeState azureComputeHost = new ComputeState();
        azureComputeHost.id = UUID.randomUUID().toString();
        azureComputeHost.documentSelfLink = azureComputeHost.id;
        azureComputeHost.descriptionLink = UriUtils.buildUriPath(
                ComputeDescriptionService.FACTORY_LINK, azureHostDescription.id);
        azureComputeHost.resourcePoolLink = resourcePoolLink;

        ComputeState returnState = TestUtils.doPost(host, azureComputeHost, ComputeState.class,
                UriUtils.buildUri(host, ComputeService.FACTORY_LINK));
        return returnState;
    }

    /**
     * Generate random names. For Azure, storage account names need to be unique across
     * Azure.
     */
    public static String generateName(String prefix) {
        return prefix + randomString(5);
    }

    public static String randomString(int length) {
        Random random = new Random();
        StringBuilder stringBuilder = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            stringBuilder.append((char) ('a' + random.nextInt(26)));
        }
        return stringBuilder.toString();
    }

    public static ComputeState createDefaultVMResource(VerificationHost host, String azureVMName,
            String parentLink, String resourcePoolLink) throws Throwable {
        AuthCredentialsServiceState auth = new AuthCredentialsServiceState();
        auth.userEmail = AZURE_ADMIN_USERNAME;
        auth.privateKey = AZURE_ADMIN_PASSWORD;
        auth.documentSelfLink = UUID.randomUUID().toString();

        TestUtils.doPost(host, auth, AuthCredentialsServiceState.class,
                UriUtils.buildUri(host, AuthCredentialsService.FACTORY_LINK));
        String authLink = UriUtils.buildUriPath(AuthCredentialsService.FACTORY_LINK,
                auth.documentSelfLink);

        // Create a VM desc
        ComputeDescription azureVMDesc = new ComputeDescription();
        azureVMDesc.id = UUID.randomUUID().toString();
        azureVMDesc.name = azureVMDesc.id;
        azureVMDesc.regionId = AZURE_RESOURCE_GROUP_LOCATION;
        azureVMDesc.authCredentialsLink = authLink;
        azureVMDesc.documentSelfLink = azureVMDesc.id;
        azureVMDesc.environmentName = ENVIRONMENT_NAME_AZURE;
        azureVMDesc.customProperties = new HashMap<>();
        azureVMDesc.customProperties.put(AzureConstants.AZURE_VM_SIZE, AZURE_VM_SIZE);

        // set the create service to the azure instance service
        azureVMDesc.instanceAdapterReference = UriUtils.buildUri(host,
                AzureUriPaths.AZURE_INSTANCE_ADAPTER);

        ComputeDescription vmComputeDesc = TestUtils
                .doPost(host, azureVMDesc, ComputeDescription.class,
                        UriUtils.buildUri(host, ComputeDescriptionService.FACTORY_LINK));

        List<String> vmDisks = new ArrayList<>();
        DiskState rootDisk = new DiskState();
        rootDisk.name = azureVMName + "-boot-disk";
        rootDisk.id = UUID.randomUUID().toString();
        rootDisk.documentSelfLink = rootDisk.id;
        rootDisk.type = DiskType.HDD;
        rootDisk.sourceImageReference = URI.create(IMAGE_REFERENCE);
        rootDisk.bootOrder = 1;
        rootDisk.documentSelfLink = rootDisk.id;
        rootDisk.customProperties = new HashMap<>();
        rootDisk.customProperties.put(AZURE_OSDISK_CACHING, DEFAULT_OS_DISK_CACHING);
        rootDisk.customProperties.put(AzureConstants.AZURE_STORAGE_ACCOUNT_NAME,
                generateName(AZURE_STORAGE_ACCOUNT_NAME));
        rootDisk.customProperties.put(AzureConstants.AZURE_STORAGE_ACCOUNT_TYPE,
                AZURE_STORAGE_ACCOUNT_TYPE);

        TestUtils.doPost(host, rootDisk, DiskState.class,
                UriUtils.buildUri(host, DiskService.FACTORY_LINK));
        vmDisks.add(UriUtils.buildUriPath(DiskService.FACTORY_LINK, rootDisk.id));

        ComputeState resource = new ComputeState();
        resource.id = UUID.randomUUID().toString();
        resource.parentLink = parentLink;
        resource.descriptionLink = vmComputeDesc.documentSelfLink;
        resource.resourcePoolLink = resourcePoolLink;
        resource.diskLinks = vmDisks;
        resource.documentSelfLink = resource.id;
        resource.customProperties = new HashMap<>();
        resource.customProperties.put(CUSTOM_DISPLAY_NAME, azureVMName);

        ComputeState vmComputeState = TestUtils.doPost(host, resource, ComputeState.class,
                UriUtils.buildUri(host, ComputeService.FACTORY_LINK));
        return vmComputeState;
    }
}
