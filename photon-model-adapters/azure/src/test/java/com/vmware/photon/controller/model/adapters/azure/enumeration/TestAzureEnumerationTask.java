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

package com.vmware.photon.controller.model.adapters.azure.enumeration;

import static com.vmware.photon.controller.model.adapters.azure.instance.AzureTestUtil.createDefaultComputeHost;
import static com.vmware.photon.controller.model.adapters.azure.instance.AzureTestUtil.createDefaultResourcePool;
import static com.vmware.photon.controller.model.adapters.azure.instance.AzureTestUtil.createDefaultVMResource;
import static com.vmware.photon.controller.model.adapters.azure.instance.AzureTestUtil.deleteVMs;
import static com.vmware.photon.controller.model.adapters.azure.instance.AzureTestUtil.generateName;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
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

import com.vmware.photon.controller.model.PhotonModelServices;
import com.vmware.photon.controller.model.adapterapi.EnumerationAction;
import com.vmware.photon.controller.model.adapters.azure.AzureAdapters;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.resources.ResourcePoolService.ResourcePoolState;
import com.vmware.photon.controller.model.tasks.PhotonModelTaskServices;
import com.vmware.photon.controller.model.tasks.ProvisionComputeTaskService;
import com.vmware.photon.controller.model.tasks.ProvisionComputeTaskService.ProvisionComputeTaskState;
import com.vmware.photon.controller.model.tasks.ProvisionComputeTaskService.ProvisionComputeTaskState.SubStage;
import com.vmware.photon.controller.model.tasks.ProvisioningUtils;
import com.vmware.photon.controller.model.tasks.ResourceEnumerationTaskService;
import com.vmware.photon.controller.model.tasks.ResourceEnumerationTaskService.ResourceEnumerationTaskState;
import com.vmware.photon.controller.model.tasks.TestUtils;
import com.vmware.xenon.common.BasicReusableHostTestCase;
import com.vmware.xenon.common.UriUtils;

/**
 * NOTE: Testing pagination related changes requires manual setup due to account limits, slowness
 * of vm creation on azure (this slowness is on azure), and cost associated.
 *
 * For manual tests use Azure CLI to create multiple VMs using this bash command line:
 *
 * for i in {1..55}; do azure vm quick-create resourcegroup vm$i westus linux canonical:UbuntuServer:12.04.3-LTS:12.04.201401270 azureuser Pa$$word% -z Standard_A0; done
 */
public class TestAzureEnumerationTask extends BasicReusableHostTestCase {
    private static final int STALE_VM_RESOURCES_COUNT = 100;

    public String clientID = "clientID";
    public String clientKey = "clientKey";
    public String subscriptionId = "subscriptionId";
    public String tenantId = "tenantId";

    public String azureVMNamePrefix = "enumtest-";
    public String azureVMName;
    public boolean isMock = true;

    // fields that are used across method calls, stash them as private fields
    private String resourcePoolLink;
    private ComputeState vmState;
    private ComputeState computeHost;
    private ComputeManagementClient computeManagementClient;
    private ResourceManagementClient resourceManagementClient;

    @Before
    public void setUp() throws Exception {
        try {
            this.azureVMName = this.azureVMName == null ? generateName(this.azureVMNamePrefix) : this.azureVMName;
            PhotonModelServices.startServices(this.host);
            PhotonModelTaskServices.startServices(this.host);
            AzureAdapters.startServices(this.host);
            // TODO: VSYM-992 - improve test/fix arbitrary timeout
            this.host.setTimeoutSeconds(1200);

            this.host.waitForServiceAvailable(PhotonModelServices.LINKS);
            this.host.waitForServiceAvailable(PhotonModelTaskServices.LINKS);
            this.host.waitForServiceAvailable(AzureAdapters.LINKS);

            ApplicationTokenCredentials credentials = new ApplicationTokenCredentials(this.clientID,
                    this.tenantId, this.clientKey, AzureEnvironment.AZURE);
            this.computeManagementClient = new ComputeManagementClientImpl(credentials);
            this.computeManagementClient.setSubscriptionId(this.subscriptionId);

            this.resourceManagementClient = new ResourceManagementClientImpl(credentials);
            this.resourceManagementClient.setSubscriptionId(this.subscriptionId);
        } catch (Throwable e) {
            throw new Exception(e);
        }
    }

    @After
    public void tearDown() throws InterruptedException {
        // try to delete the VMs
        if (this.vmState != null) {
            try {
                deleteVMs(this.host, this.vmState.documentSelfLink, this.isMock);
            } catch (Throwable deleteEx) {
                // just log and move on
                this.host.log(Level.WARNING, "Exception deleting VM - %s", deleteEx.getMessage());
            }
        }
    }

    @Test
    public void testEnumeration() throws Throwable {
        // Create a resource pool where the VM will be housed
        ResourcePoolState outPool = createDefaultResourcePool(this.host);
        this.resourcePoolLink = outPool.documentSelfLink;

        // create a compute host for the Azure
        this.computeHost = createDefaultComputeHost(this.host, this.clientID, this.clientKey, this.subscriptionId, this.tenantId,
                this.resourcePoolLink);

        // create a Azure VM compute resoruce
        this.vmState = createDefaultVMResource(this.host, this.azureVMName, this.computeHost.documentSelfLink,
                this.resourcePoolLink);

        // kick off a provision task to do the actual VM creation
        ProvisionComputeTaskState provisionTask = new ProvisionComputeTaskState();

        provisionTask.computeLink = this.vmState.documentSelfLink;
        provisionTask.isMockRequest = this.isMock;
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

        if (this.isMock) {
            runEnumeration();
            deleteVMs(this.host, this.vmState.documentSelfLink, this.isMock);
            this.vmState = null;
            ProvisioningUtils.queryComputeInstances(this.host, 1);
            return;
        }

        // create some stale compute states for deletion
        // these should be deleted as part of first enumeration cycle.
        createAzureVMResources(STALE_VM_RESOURCES_COUNT);

        // stale resources + 1 compute host instance + 1 vm compute state
        ProvisioningUtils.queryComputeInstances(this.host, STALE_VM_RESOURCES_COUNT + 2);

        int count = getAzureVMCount();

        runEnumeration();

        // VM count + 1 compute host instance
        count = count + 1;

        ProvisioningUtils.queryComputeInstances(this.host, count);

        // delete vm directly on azure
        this.computeManagementClient.getVirtualMachinesOperations()
                .beginDelete(this.azureVMName, this.azureVMName);

        runEnumeration();

        // after data collection the deleted vm should go away
        count = count - 1;
        ProvisioningUtils.queryComputeInstances(this.host, count);

        // clean up
        this.vmState = null;
        this.resourceManagementClient.getResourceGroupsOperations().beginDelete(this.azureVMName);
    }

    private void createAzureVMResources(int numOfVMs) throws Throwable {
        for (int i = 0; i < numOfVMs; i++) {
            String staleVMName = "stalevm-" + i;
            createDefaultVMResource(this.host, staleVMName, this.computeHost.documentSelfLink,
                    this.resourcePoolLink);
        }
    }

    private void runEnumeration() throws Throwable {
        ResourceEnumerationTaskState enumerationTaskState = new ResourceEnumerationTaskState();

        enumerationTaskState.computeDescriptionLink = this.computeHost.descriptionLink;
        enumerationTaskState.parentComputeLink = this.computeHost.documentSelfLink;
        enumerationTaskState.enumerationAction = EnumerationAction.START;
        enumerationTaskState.adapterManagementReference = UriUtils
                .buildUri(AzureEnumerationAdapterService.SELF_LINK);
        enumerationTaskState.resourcePoolLink = this.resourcePoolLink;
        enumerationTaskState.isMockRequest = this.isMock;

        ResourceEnumerationTaskState enumTask = TestUtils
                .doPost(this.host, enumerationTaskState, ResourceEnumerationTaskState.class,
                        UriUtils.buildUri(this.host, ResourceEnumerationTaskService.FACTORY_LINK));

        this.host.waitFor("Error waiting for enumeration task", () -> {
            try {
                ResourceEnumerationTaskState state = this.host
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

    private int getAzureVMCount() throws Exception {
        ServiceResponse<List<VirtualMachine>> response = this.computeManagementClient
                .getVirtualMachinesOperations().listAll();

        int count = 0;
        for (VirtualMachine virtualMachine : response.getBody()) {
            if (AzureEnumerationAdapterService.AZURE_VM_TERMINATION_STATES
                    .contains(virtualMachine.getProvisioningState())) {
                continue;
            }
            count++;
        }

        this.host.log("Initial VM count: %d", count);
        return count;
    }
}
