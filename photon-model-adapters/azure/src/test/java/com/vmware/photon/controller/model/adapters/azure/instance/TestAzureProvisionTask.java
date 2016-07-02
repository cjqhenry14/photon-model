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

package com.vmware.photon.controller.model.adapters.azure.instance;

import static com.vmware.photon.controller.model.adapters.azure.instance.AzureTestUtil.createDefaultComputeHost;
import static com.vmware.photon.controller.model.adapters.azure.instance.AzureTestUtil.createDefaultResourcePool;
import static com.vmware.photon.controller.model.adapters.azure.instance.AzureTestUtil.createDefaultVMResource;
import static com.vmware.photon.controller.model.adapters.azure.instance.AzureTestUtil.deleteVMs;
import static com.vmware.photon.controller.model.adapters.azure.instance.AzureTestUtil.generateName;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.vmware.photon.controller.model.PhotonModelServices;
import com.vmware.photon.controller.model.adapterapi.ComputeStatsRequest;
import com.vmware.photon.controller.model.adapterapi.ComputeStatsResponse;
import com.vmware.photon.controller.model.adapters.azure.AzureAdapters;
import com.vmware.photon.controller.model.adapters.azure.AzureUriPaths;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.resources.ResourcePoolService;
import com.vmware.photon.controller.model.tasks.PhotonModelTaskServices;
import com.vmware.photon.controller.model.tasks.ProvisionComputeTaskService;
import com.vmware.photon.controller.model.tasks.ProvisionComputeTaskService.ProvisionComputeTaskState;
import com.vmware.photon.controller.model.tasks.ProvisioningUtils;
import com.vmware.photon.controller.model.tasks.TestUtils;
import com.vmware.xenon.common.BasicReusableHostTestCase;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.common.UriUtils;

public class TestAzureProvisionTask extends BasicReusableHostTestCase {
    public String clientID = "clientID";
    public String clientKey = "clientKey";
    public String subscriptionId = "subscriptionId";
    public String tenantId = "tenantId";

    public String azureVMNamePrefix = "test-";
    public String azureVMName;
    public boolean isMock = true;
    public boolean skipStats = true;

    // fields that are used across method calls, stash them as private fields
    private String resourcePoolLink;
    private ComputeState vmState;

    @Before
    public void setUp() throws Exception {
        try {
            this.azureVMName = this.azureVMName == null ? generateName(this.azureVMNamePrefix) : this.azureVMName;
            PhotonModelServices.startServices(this.host);
            PhotonModelTaskServices.startServices(this.host);
            AzureAdapters.startServices(this.host);

            this.host.waitForServiceAvailable(PhotonModelServices.LINKS);
            this.host.waitForServiceAvailable(PhotonModelTaskServices.LINKS);
            this.host.waitForServiceAvailable(AzureAdapters.LINKS);

            // TODO: VSYM-992 - improve test/fix arbitrary timeout
            this.host.setTimeoutSeconds(1200);
        } catch (Throwable e) {
            throw new Exception(e);
        }
    }

    @After
    public void tearDown() throws InterruptedException {
        // try to delete the VMs
        if (this.vmState != null) {
            try {
                AzureTestUtil.deleteVMs(this.host, this.vmState.documentSelfLink, this.isMock);
            } catch (Throwable deleteEx) {
                // just log and move on
                this.host.log(Level.WARNING, "Exception deleting VM - %s", deleteEx.getMessage());
            }
        }
    }

    // Creates a Azure instance via a provision task.
    @Test
    public void testProvision() throws Throwable {

        // Create a resource pool where the VM will be housed
        ResourcePoolService.ResourcePoolState outPool = createDefaultResourcePool(this.host);
        this.resourcePoolLink = outPool.documentSelfLink;

        // create a compute host for the Azure
        ComputeState computeHost = createDefaultComputeHost(this.host, this.clientID, this.clientKey,
                this.subscriptionId, this.tenantId, this.resourcePoolLink);

        // create a Azure VM compute resoruce
        this.vmState = createDefaultVMResource(this.host, this.azureVMName, computeHost.documentSelfLink,
                this.resourcePoolLink);

        // kick off a provision task to do the actual VM creation
        ProvisionComputeTaskState provisionTask = new ProvisionComputeTaskState();

        provisionTask.computeLink = this.vmState.documentSelfLink;
        provisionTask.isMockRequest = this.isMock;
        provisionTask.taskSubStage = ProvisionComputeTaskState.SubStage.CREATING_HOST;

        ProvisionComputeTaskState outTask = TestUtils.doPost(this.host,
                provisionTask,
                ProvisionComputeTaskState.class,
                UriUtils.buildUri(this.host,
                        ProvisionComputeTaskService.FACTORY_LINK));

        List<URI> uris = new ArrayList<>();
        uris.add(UriUtils.buildUri(this.host, outTask.documentSelfLink));
        ProvisioningUtils.waitForTaskCompletion(this.host, uris,
                ProvisionComputeTaskState.class);

        // check that the VM has been created
        ProvisioningUtils.queryComputeInstances(this.host, 2);

        if (!this.skipStats) {
            this.host.setTimeoutSeconds(600);
            this.host.waitFor("Error waiting for stats", () -> {
                try {
                    issueStatsRequest(this.vmState);
                } catch (Throwable t) {
                    return false;
                }
                return true;
            });
        }

        // delete vm
        deleteVMs(this.host, this.vmState.documentSelfLink, this.isMock);
        this.vmState = null;
    }

    private void issueStatsRequest(ComputeState vm) throws Throwable {
        // spin up a stateless service that acts as the parent link to patch back to
        StatelessService parentService = new StatelessService() {
            @Override
            public void handleRequest(Operation op) {
                if (op.getAction() == Action.PATCH) {
                    if (!TestAzureProvisionTask.this.isMock) {
                        ComputeStatsResponse resp = op.getBody(ComputeStatsResponse.class);
                        if (resp.statsList.size() != 1) {
                            TestAzureProvisionTask.this.host.failIteration(
                                    new IllegalStateException("response size was incorrect."));
                            return;
                        }
                        if (resp.statsList.get(0).statValues.size() == 0) {
                            TestAzureProvisionTask.this.host.failIteration(new IllegalStateException(
                                    "incorrect number of metrics received."));
                            return;
                        }
                        if (!resp.statsList.get(0).computeLink.equals(vm.documentSelfLink)) {
                            TestAzureProvisionTask.this.host.failIteration(
                                    new IllegalStateException("Incorrect computeReference returned."));
                            return;
                        }
                    }
                    TestAzureProvisionTask.this.host.completeIteration();
                }
            }
        };
        String servicePath = UUID.randomUUID().toString();
        Operation startOp = Operation.createPost(UriUtils.buildUri(this.host, servicePath));
        this.host.startService(startOp, parentService);
        ComputeStatsRequest statsRequest = new ComputeStatsRequest();
        statsRequest.resourceReference = UriUtils.buildUri(this.host, vm.documentSelfLink);
        statsRequest.isMockRequest = this.isMock;
        statsRequest.taskReference = UriUtils.buildUri(this.host, servicePath);
        this.host.sendAndWait(Operation.createPatch(UriUtils.buildUri(
                this.host, AzureUriPaths.AZURE_STATS_ADAPTER))
                .setBody(statsRequest)
                .setReferer(this.host.getUri()));
    }
}
