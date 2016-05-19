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

package com.vmware.photon.controller.model.adapters.azureadapter.enumeration;

import static com.vmware.photon.controller.model.ComputeProperties.CUSTOM_DISPLAY_NAME;
import static com.vmware.photon.controller.model.adapters.azureadapter.AzureConstants.AZURE_OSDISK_CACHING;
import static com.vmware.photon.controller.model.adapters.azureadapter.AzureConstants.AZURE_STORAGE_ACCOUNT_NAME;
import static com.vmware.photon.controller.model.adapters.azureadapter.AzureConstants.AZURE_STORAGE_ACCOUNT_TYPE;
import static com.vmware.photon.controller.model.adapters.azureadapter.AzureConstants.AZURE_TENANT_ID;
import static com.vmware.photon.controller.model.adapters.azureadapter.AzureConstants.AZURE_VM_SIZE;
import static com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription.ENVIRONMENT_NAME_AZURE;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.logging.Level;

import com.microsoft.azure.credentials.ApplicationTokenCredentials;
import com.microsoft.azure.credentials.AzureEnvironment;
import com.microsoft.azure.management.compute.ComputeManagementClient;
import com.microsoft.azure.management.compute.ComputeManagementClientImpl;
import com.microsoft.azure.management.compute.models.VirtualMachine;
import com.microsoft.azure.management.resources.ResourceManagementClient;
import com.microsoft.azure.management.resources.ResourceManagementClientImpl;
import com.microsoft.rest.ServiceResponse;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.vmware.photon.controller.model.adapterapi.EnumerationAction;
import com.vmware.photon.controller.model.adapters.azureadapter.AzureInstanceService;
import com.vmware.photon.controller.model.adapters.azureadapter.AzureUriPaths;
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
import com.vmware.photon.controller.model.tasks.ProvisionComputeTaskService;
import com.vmware.photon.controller.model.tasks.ProvisionComputeTaskService.ProvisionComputeTaskState;
import com.vmware.photon.controller.model.tasks.ProvisionComputeTaskService.ProvisionComputeTaskState.SubStage;
import com.vmware.photon.controller.model.tasks.ProvisioningUtils;
import com.vmware.photon.controller.model.tasks.ResourceEnumerationTaskService;
import com.vmware.photon.controller.model.tasks.ResourceEnumerationTaskService.ResourceEnumerationTaskState;
import com.vmware.photon.controller.model.tasks.ResourceRemovalTaskService;
import com.vmware.photon.controller.model.tasks.ResourceRemovalTaskService.ResourceRemovalTaskState;
import com.vmware.photon.controller.model.tasks.TestUtils;
import com.vmware.xenon.common.BasicReusableHostTestCase;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.services.common.AuthCredentialsService;
import com.vmware.xenon.services.common.AuthCredentialsService.AuthCredentialsServiceState;
import com.vmware.xenon.services.common.QueryTask.QuerySpecification;

public class TestAzureEnumerationTask extends BasicReusableHostTestCase {
    public static final String DEFAULT_OS_DISK_CACHING = "None";

    public String clientID = "clientID";
    public String clientKey = "clientKey";
    public String subscriptionId = "subscriptionId";
    public String tenantId = "tenantId";

    public String azureVMNamePrefix = "enumtest-";
    public String azureVMName;
    public String azureAdminUsername = "azureuser";
    public String azureAdminPassword = "Pa$$word1";
    public String azureVMSize = "Basic_A0";
    public String imageReference = "Canonical:UbuntuServer:14.04.3-LTS:latest";
    public String azureResourceGroupLocation = "westus";
    public String azureStorageAccountName = "storage";
    public String azureStorageAccountType = "Standard_RAGRS";
    public boolean isMock = true;

    // fields that are used across method calls, stash them as private fields
    private String resourcePoolLink;
    private String parentResourceId;
    private ComputeState vmState;
    private ComputeState computeHost;
    private ComputeManagementClient computeManagementClient;
    private ResourceManagementClient resourceManagementClient;

    @Before
    public void setUp() throws Exception {
        try {
            azureVMName = azureVMName == null ? generateName(azureVMNamePrefix) : azureVMName;
            ProvisioningUtils.startProvisioningServices(this.host);
            this.host.setTimeoutSeconds(1200);
            host.startService(
                    Operation.createPost(UriUtils.buildUri(host,
                            AzureInstanceService.class)),
                    new AzureInstanceService());

            host.startService(
                    Operation.createPost(UriUtils.buildUri(host,
                            AzureEnumerationAdapterService.class)),
                    new AzureEnumerationAdapterService());

            ProvisioningUtils.waitForServiceStart(host, AzureInstanceService.SELF_LINK,
                    AzureEnumerationAdapterService.SELF_LINK);

            ApplicationTokenCredentials credentials = new ApplicationTokenCredentials(clientID,
                    tenantId, clientKey, AzureEnvironment.AZURE);
            computeManagementClient = new ComputeManagementClientImpl(credentials);
            computeManagementClient.setSubscriptionId(subscriptionId);

            resourceManagementClient = new ResourceManagementClientImpl(credentials);
            resourceManagementClient.setSubscriptionId(subscriptionId);
        } catch (Throwable e) {
            throw new Exception(e);
        }
    }

    @After
    public void tearDown() throws InterruptedException {
        // try to delete the VMs
        if (vmState != null) {
            try {
                deleteVMs(vmState.documentSelfLink);
            } catch (Throwable deleteEx) {
                // just log and move on
                host.log(Level.WARNING, "Exception deleting VM - %s", deleteEx.getMessage());
            }
        }
    }

    // Creates a Azure instance via a provision task.
    @Test
    public void testEnumeration() throws Throwable {
        // Create a resource pool where the VM will be housed
        ResourcePoolState outPool = createAzureResourcePool();
        this.resourcePoolLink = outPool.documentSelfLink;

        // create a compute host for the Azure
        computeHost = createAzureComputeHost();
        parentResourceId = computeHost.documentSelfLink;

        // create a Azure VM compute resoruce
        vmState = createAzureVMResource();

        // kick off a provision task to do the actual VM creation
        ProvisionComputeTaskState provisionTask = new ProvisionComputeTaskState();

        provisionTask.computeLink = vmState.documentSelfLink;
        provisionTask.isMockRequest = isMock;
        provisionTask.taskSubStage = SubStage.CREATING_HOST;

        ProvisionComputeTaskState outTask = TestUtils
                .doPost(this.host, provisionTask, ProvisionComputeTaskState.class,
                        UriUtils.buildUri(this.host, ProvisionComputeTaskService.FACTORY_LINK));

        List<URI> uris = new ArrayList<>();
        uris.add(UriUtils.buildUri(this.host, outTask.documentSelfLink));
        ProvisioningUtils.waitForTaskCompletion(this.host, uris, ProvisionComputeTaskState.class);

        // check that the VM has been created
        // 1 compute host instance + 1 vm compute state
        ProvisioningUtils.queryComputeInstances(this.host, 2);

        if (isMock) {
            runEnumeration();
            deleteVMs(vmState.documentSelfLink);
            vmState = null;
            ProvisioningUtils.queryComputeInstances(this.host, 1);
            return;
        }

        int count = getAzureVMCount();

        runEnumeration();

        // VM count + 1 compute host instance
        count = count + 1;

        ProvisioningUtils.queryComputeInstances(this.host, count);

        // delete vm directly on azure
        computeManagementClient.getVirtualMachinesOperations().beginDelete(azureVMName, azureVMName);

        runEnumeration();

        // after data collection the deleted vm should go away
        count = count - 1;
        ProvisioningUtils.queryComputeInstances(this.host, count);

        // clean up
        vmState = null;
        resourceManagementClient.getResourceGroupsOperations().beginDelete(azureVMName);
    }

    private void runEnumeration() throws Throwable {
        ResourceEnumerationTaskState enumerationTaskState = new ResourceEnumerationTaskState();

        enumerationTaskState.computeDescriptionLink = computeHost.descriptionLink;
        enumerationTaskState.parentComputeLink = computeHost.documentSelfLink;
        enumerationTaskState.enumerationAction = EnumerationAction.START;
        enumerationTaskState.adapterManagementReference = UriUtils
                .buildUri(AzureEnumerationAdapterService.SELF_LINK);
        enumerationTaskState.resourcePoolLink = resourcePoolLink;
        enumerationTaskState.isMockRequest = isMock;

        ResourceEnumerationTaskState enumTask = TestUtils
                .doPost(host, enumerationTaskState, ResourceEnumerationTaskState.class,
                        UriUtils.buildUri(host, ResourceEnumerationTaskService.FACTORY_LINK));

        host.waitFor("Error waiting for enumeration task", () -> {
            try {
                ResourceEnumerationTaskState state = host
                        .waitForFinishedTask(ResourceEnumerationTaskState.class,
                                enumTask.documentSelfLink);
                if (state != null) {
                    return true;
                }
            } catch (Throwable e) {
                return false;
            }
            return false;
        });
    }

    private ResourcePoolState createAzureResourcePool()
            throws Throwable {
        ResourcePoolState inPool = new ResourcePoolState();
        inPool.name = UUID.randomUUID().toString();
        inPool.id = inPool.name;

        inPool.minCpuCount = 1;
        inPool.minMemoryBytes = 1024;

        ResourcePoolState returnPool =
                TestUtils.doPost(this.host, inPool, ResourcePoolState.class,
                        UriUtils.buildUri(this.host, ResourcePoolService.FACTORY_LINK));

        return returnPool;
    }

    /**
     * Create a compute host description for an Azure instance
     */
    private ComputeState createAzureComputeHost() throws Throwable {
        AuthCredentialsServiceState auth = new AuthCredentialsServiceState();
        auth.privateKeyId = clientID;
        auth.privateKey = clientKey;
        auth.userLink = subscriptionId;
        auth.customProperties = new HashMap<>();
        auth.customProperties.put(AZURE_TENANT_ID, tenantId);
        auth.documentSelfLink = UUID.randomUUID().toString();

        TestUtils.doPost(this.host, auth, AuthCredentialsServiceState.class,
                UriUtils.buildUri(this.host, AuthCredentialsService.FACTORY_LINK));
        String authLink = UriUtils.buildUriPath(AuthCredentialsService.FACTORY_LINK,
                auth.documentSelfLink);

        ComputeDescription azureHostDescription = new ComputeDescription();
        azureHostDescription.id = UUID.randomUUID().toString();
        azureHostDescription.documentSelfLink = azureHostDescription.id;
        azureHostDescription.supportedChildren = new ArrayList<>();
        azureHostDescription.supportedChildren.add(ComputeType.VM_GUEST.name());
        azureHostDescription.instanceAdapterReference = UriUtils.buildUri(this.host,
                AzureUriPaths.AZURE_INSTANCE_ADAPTER);
        azureHostDescription.enumerationAdapterReference = UriUtils.buildUri(this.host,
                AzureUriPaths.AZURE_ENUMERATION_ADAPTER);
        azureHostDescription.authCredentialsLink = authLink;

        TestUtils.doPost(this.host, azureHostDescription,
                ComputeDescription.class,
                UriUtils.buildUri(this.host, ComputeDescriptionService.FACTORY_LINK));

        ComputeState azureComputeHost = new ComputeState();
        azureComputeHost.id = UUID.randomUUID().toString();
        azureComputeHost.documentSelfLink = azureComputeHost.id;
        azureComputeHost.descriptionLink = UriUtils.buildUriPath(
                ComputeDescriptionService.FACTORY_LINK, azureHostDescription.id);
        azureComputeHost.resourcePoolLink = this.resourcePoolLink;

        ComputeState returnState = TestUtils.doPost(this.host, azureComputeHost, ComputeState.class,
                UriUtils.buildUri(this.host, ComputeService.FACTORY_LINK));
        return returnState;
    }

    private ComputeState createAzureVMResource() throws Throwable {
        AuthCredentialsServiceState auth = new AuthCredentialsServiceState();
        auth.userEmail = azureAdminUsername;
        auth.privateKey = azureAdminPassword;
        auth.documentSelfLink = UUID.randomUUID().toString();

        TestUtils.doPost(this.host, auth, AuthCredentialsServiceState.class,
                UriUtils.buildUri(this.host, AuthCredentialsService.FACTORY_LINK));
        String authLink = UriUtils.buildUriPath(AuthCredentialsService.FACTORY_LINK,
                auth.documentSelfLink);

        // Create a VM desc
        ComputeDescription azureVMDesc = new ComputeDescription();
        azureVMDesc.id = UUID.randomUUID().toString();
        azureVMDesc.name = azureVMDesc.id;
        azureVMDesc.regionId = azureResourceGroupLocation;
        azureVMDesc.authCredentialsLink = authLink;
        azureVMDesc.documentSelfLink = azureVMDesc.id;
        azureVMDesc.environmentName = ENVIRONMENT_NAME_AZURE;
        azureVMDesc.customProperties = new HashMap<>();
        azureVMDesc.customProperties.put(AZURE_VM_SIZE, azureVMSize);

        // set the create service to the azure instance service
        azureVMDesc.instanceAdapterReference = UriUtils.buildUri(this.host,
                AzureUriPaths.AZURE_INSTANCE_ADAPTER);

        ComputeDescription vmComputeDesc = TestUtils
                .doPost(this.host, azureVMDesc, ComputeDescription.class,
                        UriUtils.buildUri(this.host, ComputeDescriptionService.FACTORY_LINK));

        List<String> vmDisks = new ArrayList<>();
        DiskState rootDisk = new DiskState();
        rootDisk.name = azureVMName + "-boot-disk";
        rootDisk.id = UUID.randomUUID().toString();
        rootDisk.documentSelfLink = rootDisk.id;
        rootDisk.type = DiskType.HDD;
        rootDisk.sourceImageReference = URI.create(imageReference);
        rootDisk.bootOrder = 1;
        rootDisk.documentSelfLink = rootDisk.id;
        rootDisk.customProperties = new HashMap<>();
        rootDisk.customProperties.put(AZURE_OSDISK_CACHING, DEFAULT_OS_DISK_CACHING);
        rootDisk.customProperties
                .put(AZURE_STORAGE_ACCOUNT_NAME, generateName(azureStorageAccountName));
        rootDisk.customProperties.put(AZURE_STORAGE_ACCOUNT_TYPE, azureStorageAccountType);

        TestUtils.doPost(host, rootDisk,
                DiskService.DiskState.class,
                UriUtils.buildUri(host, DiskService.FACTORY_LINK));
        vmDisks.add(UriUtils.buildUriPath(DiskService.FACTORY_LINK, rootDisk.id));

        ComputeState resource = new ComputeState();
        resource.id = UUID.randomUUID().toString();
        resource.parentLink = parentResourceId;
        resource.descriptionLink = vmComputeDesc.documentSelfLink;
        resource.resourcePoolLink = this.resourcePoolLink;
        resource.diskLinks = vmDisks;
        resource.documentSelfLink = resource.id;
        resource.customProperties = new HashMap<>();
        resource.customProperties.put(CUSTOM_DISPLAY_NAME, azureVMName);

        ComputeState vmComputeState = TestUtils.doPost(this.host, resource,
                ComputeState.class,
                UriUtils.buildUri(this.host, ComputeService.FACTORY_LINK));
        return vmComputeState;
    }

    private void deleteVMs(String documentSelfLink)
            throws Throwable {
        this.host.testStart(1);
        ResourceRemovalTaskState deletionState = new ResourceRemovalTaskState();
        QuerySpecification resourceQuerySpec = new QuerySpecification();
        // query all ComputeState resources for the cluster
        resourceQuerySpec.query
                .setTermPropertyName(ServiceDocument.FIELD_NAME_SELF_LINK)
                .setTermMatchValue(documentSelfLink);
        deletionState.resourceQuerySpec = resourceQuerySpec;
        deletionState.isMockRequest = isMock;
        this.host.send(Operation
                .createPost(
                        UriUtils.buildUri(this.host,
                                ResourceRemovalTaskService.FACTORY_LINK))
                .setBody(deletionState)
                .setCompletion(this.host.getCompletion()));
        this.host.testWait();
    }

    /**
     * Generate random names. For Azure, storage account names need to be unique across
     * Azure.
     */
    private String generateName(String prefix) {
        return prefix + randomString(5);
    }

    private String randomString(int length) {
        Random random = new Random();
        StringBuilder stringBuilder = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            stringBuilder.append((char) ('a' + random.nextInt(26)));
        }
        return stringBuilder.toString();
    }

    private int getAzureVMCount() throws Exception {
        ServiceResponse<List<VirtualMachine>> response = computeManagementClient
                .getVirtualMachinesOperations().listAll();

        int count = 0;
        for (VirtualMachine virtualMachine : response.getBody()) {
            if (AzureEnumerationAdapterService.AZURE_VM_TERMINATION_STATES
                    .contains(virtualMachine.getProvisioningState())) {
                continue;
            }
            count++;
        }

        host.log("Initial VM count: %d", count);
        return count;
    }
}
