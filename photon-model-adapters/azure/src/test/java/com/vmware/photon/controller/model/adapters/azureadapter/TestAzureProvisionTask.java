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

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.vmware.photon.controller.model.resources.ComputeDescriptionService;
import com.vmware.photon.controller.model.resources.ComputeService;
import com.vmware.photon.controller.model.resources.ResourcePoolService;
import com.vmware.photon.controller.model.tasks.ProvisionComputeTaskService;
import com.vmware.photon.controller.model.tasks.ProvisioningUtils;
import com.vmware.photon.controller.model.tasks.ResourceRemovalTaskService;
import com.vmware.photon.controller.model.tasks.TestUtils;
import com.vmware.xenon.common.BasicReusableHostTestCase;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.services.common.AuthCredentialsService;
import com.vmware.xenon.services.common.QueryTask;

public class TestAzureProvisionTask extends BasicReusableHostTestCase {

    public String clientID = "clientID";
    public String clientKey = "clientKey";
    public String subscriptionId = "subscriptionId";
    public String tenantId = "tenantId";
    public String azureAdminUsername = "admin";
    public String azureAdminPassword = "Pa$$word1";
    public String azureResourceGroupLocation = "EastUS";
    public boolean isMock = true;

    // fields that are used across method calls, stash them as private fields
    private String resourcePoolLink;
    private String parentResourceId;
    private ComputeService.ComputeState vmState;

    @Before
    public void setUp() throws Exception {
        try {
            ProvisioningUtils.startProvisioningServices(this.host);
            this.host.setTimeoutSeconds(1200);
            host.startService(
                    Operation.createPost(UriUtils.buildUri(host,
                            AzureInstanceService.class)),
                    new AzureInstanceService());
            ProvisioningUtils.waitForServiceStart(host, AzureInstanceService.SELF_LINK);
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
    public void testProvision() throws Throwable {

        // Create a resource pool where the VM will be housed
        ResourcePoolService.ResourcePoolState outPool =
                createAzureResourcePool();
        this.resourcePoolLink = outPool.documentSelfLink;

        // create a compute host for the Azure
        ComputeService.ComputeState outComputeHost =
                createAzureComputeHost();
        parentResourceId = outComputeHost.documentSelfLink;

        // create a Azure VM compute resoruce
        vmState = createAzureVMResource();

        // kick off a provision task to do the actual VM creation
        ProvisionComputeTaskService.ProvisionComputeTaskState provisionTask = new ProvisionComputeTaskService.ProvisionComputeTaskState();

        provisionTask.computeLink = vmState.documentSelfLink;
        provisionTask.isMockRequest = isMock;
        provisionTask.taskSubStage =
                ProvisionComputeTaskService.ProvisionComputeTaskState.SubStage.CREATING_HOST;

        ProvisionComputeTaskService.ProvisionComputeTaskState outTask = TestUtils.doPost(this.host,
                provisionTask,
                ProvisionComputeTaskService.ProvisionComputeTaskState.class,
                UriUtils.buildUri(this.host,
                        ProvisionComputeTaskService.FACTORY_LINK));

        List<URI> uris = new ArrayList<>();
        uris.add(UriUtils.buildUri(this.host, outTask.documentSelfLink));
        ProvisioningUtils.waitForTaskCompletion(this.host, uris, ProvisionComputeTaskService.ProvisionComputeTaskState.class);

        // check that the VM has been created
        ProvisioningUtils.queryComputeInstances(this.host, 2);

        // delete vm
        deleteVMs(vmState.documentSelfLink);
        vmState = null;
        // check that the VMs are gone
        ProvisioningUtils.queryComputeInstances(this.host, 1);
    }

    private ResourcePoolService.ResourcePoolState createAzureResourcePool()
            throws Throwable {
        ResourcePoolService.ResourcePoolState inPool = new ResourcePoolService.ResourcePoolState();
        inPool.name = UUID.randomUUID().toString();
        inPool.id = inPool.name;

        inPool.minCpuCount = 1;
        inPool.minMemoryBytes = 1024;

        ResourcePoolService.ResourcePoolState returnPool =
                TestUtils.doPost(this.host, inPool, ResourcePoolService.ResourcePoolState.class,
                        UriUtils.buildUri(this.host, ResourcePoolService.FACTORY_LINK));

        return returnPool;
    }

    /**
     * Create a compute host description for an Azure instance
     */
    private ComputeService.ComputeState createAzureComputeHost() throws Throwable {
        AuthCredentialsService.AuthCredentialsServiceState auth = new AuthCredentialsService.AuthCredentialsServiceState();
        auth.privateKeyId = clientID;
        auth.privateKey = clientKey;
        auth.userLink = subscriptionId;
        auth.customProperties = new HashMap<>();
        auth.customProperties.put(AzureConstants.AZURE_TENANT_ID, tenantId);
        auth.documentSelfLink = UUID.randomUUID().toString();
        TestUtils.doPost(this.host, auth, AuthCredentialsService.AuthCredentialsServiceState.class,
                UriUtils.buildUri(this.host, AuthCredentialsService.FACTORY_LINK));
        String authLink = UriUtils.buildUriPath(AuthCredentialsService.FACTORY_LINK,
                auth.documentSelfLink);

        ComputeDescriptionService.ComputeDescription azureHostDescription =
                new ComputeDescriptionService.ComputeDescription();

        azureHostDescription.id = UUID.randomUUID().toString();
        azureHostDescription.documentSelfLink = azureHostDescription.id;
        azureHostDescription.supportedChildren = new ArrayList<String>();
        azureHostDescription.supportedChildren.add(
                ComputeDescriptionService.ComputeDescription.ComputeType.VM_GUEST.name());
        azureHostDescription.instanceAdapterReference = UriUtils.buildUri(this.host,
                AzureUriPaths.AZURE_INSTANCE_SERVICE);
        azureHostDescription.authCredentialsLink = authLink;
        TestUtils.doPost(this.host, azureHostDescription,
                ComputeDescriptionService.ComputeDescription.class,
                UriUtils.buildUri(this.host, ComputeDescriptionService.FACTORY_LINK));

        ComputeService.ComputeState azureComputeHost =
                new ComputeService.ComputeState();

        azureComputeHost.id = UUID.randomUUID().toString();
        azureComputeHost.documentSelfLink = azureComputeHost.id;
        azureComputeHost.descriptionLink = UriUtils.buildUriPath(
                ComputeDescriptionService.FACTORY_LINK, azureHostDescription.id);
        azureComputeHost.resourcePoolLink = this.resourcePoolLink;

        ComputeService.ComputeState returnState = TestUtils.doPost(this.host, azureComputeHost,
                ComputeService.ComputeState.class,
                UriUtils.buildUri(this.host, ComputeService.FACTORY_LINK));
        return returnState;
    }

    private ComputeService.ComputeState createAzureVMResource() throws Throwable {
        // Create a VM desc
        ComputeDescriptionService.ComputeDescription azureVMDesc =
                new ComputeDescriptionService.ComputeDescription();

        azureVMDesc.id = UUID.randomUUID().toString();
        // VM name on Azure cannot be more that 15 chars.
        azureVMDesc.name = azureVMDesc.id.substring(0, 15);

        azureVMDesc.supportedChildren = new ArrayList<>();
        azureVMDesc.supportedChildren.add(ComputeDescriptionService.ComputeDescription.ComputeType.DOCKER_CONTAINER.name());

        azureVMDesc.customProperties = new HashMap<>();
        azureVMDesc.customProperties.put(AzureConstants.AZURE_RESOURCE_GROUP_NAME, azureVMDesc.id);
        azureVMDesc.customProperties.put(AzureConstants.AZURE_VM_ADMIN_USERNAME, azureAdminUsername);
        azureVMDesc.customProperties.put(AzureConstants.AZURE_VM_ADMIN_PASSWORD, azureAdminPassword);

        // set zone to east
        azureVMDesc.zoneId = azureResourceGroupLocation;

        // set the create service to the azure instance service
        azureVMDesc.instanceAdapterReference = UriUtils.buildUri(this.host,
                AzureUriPaths.AZURE_INSTANCE_SERVICE);

        ComputeDescriptionService.ComputeDescription vmComputeDesc = TestUtils.doPost(this.host, azureVMDesc,
                ComputeDescriptionService.ComputeDescription.class,
                UriUtils.buildUri(this.host, ComputeDescriptionService.FACTORY_LINK));

        ComputeService.ComputeState resource = new ComputeService.ComputeState();
        resource.id = UUID.randomUUID().toString();
        resource.parentLink = parentResourceId;
        resource.descriptionLink = vmComputeDesc.documentSelfLink;
        resource.resourcePoolLink = this.resourcePoolLink;

        ComputeService.ComputeState vmComputeState = TestUtils.doPost(this.host, resource,
                ComputeService.ComputeState.class,
                UriUtils.buildUri(this.host, ComputeService.FACTORY_LINK));
        return vmComputeState;
    }

    private void deleteVMs(String documentSelfLink)
            throws Throwable {
        this.host.testStart(1);
        ResourceRemovalTaskService.ResourceRemovalTaskState deletionState = new ResourceRemovalTaskService.ResourceRemovalTaskState();
        QueryTask.QuerySpecification resourceQuerySpec = new QueryTask.QuerySpecification();
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
}
