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

import static org.junit.Assert.assertEquals;

import static com.vmware.photon.controller.model.adapters.awsadapter.TestAWSSetupUtils.AWS_VM_REQUEST_TIMEOUT_MINUTES;
import static com.vmware.photon.controller.model.adapters.awsadapter.TestAWSSetupUtils.createAWSComputeHost;
import static com.vmware.photon.controller.model.adapters.awsadapter.TestAWSSetupUtils.createAWSResourcePool;
import static com.vmware.photon.controller.model.adapters.awsadapter.TestAWSSetupUtils.createAWSVMResource;
import static com.vmware.photon.controller.model.adapters.awsadapter.TestUtils.getExecutor;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

import com.amazonaws.services.ec2.AmazonEC2AsyncClient;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import com.vmware.photon.controller.model.PhotonModelServices;
import com.vmware.photon.controller.model.adapterapi.ComputeStatsRequest;
import com.vmware.photon.controller.model.adapterapi.ComputeStatsResponse;
import com.vmware.photon.controller.model.adapterapi.ComputeStatsResponse.ComputeStats;
import com.vmware.photon.controller.model.adapters.awsadapter.TestAWSSetupUtils.BaseLineState;
import com.vmware.photon.controller.model.constants.PhotonModelConstants;
import com.vmware.photon.controller.model.resources.ComputeService;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.resources.ResourcePoolService.ResourcePoolState;
import com.vmware.photon.controller.model.tasks.PhotonModelTaskServices;
import com.vmware.photon.controller.model.tasks.ProvisionComputeTaskService;
import com.vmware.photon.controller.model.tasks.ProvisionComputeTaskService.ProvisionComputeTaskState;
import com.vmware.photon.controller.model.tasks.ProvisioningUtils;
import com.vmware.photon.controller.model.tasks.TestUtils;

import com.vmware.xenon.common.CommandLineArgumentParser;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceStats.ServiceStat;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.common.test.VerificationHost;
import com.vmware.xenon.services.common.AuthCredentialsService.AuthCredentialsServiceState;

/**
 * Test to provision a VM instance on AWS and tear it down
 * The test exercises the AWS instance adapter to create the VM
 * All public fields below can be specified via command line arguments
 * If the 'isMock' flag is set to true the test runs the adapter in mock
 * mode and does not actually create a VM.
 * Minimally the accessKey and secretKey for AWS must be specified.
 *
 */
public class TestAWSProvisionTask {

    private static final String INSTANCEID_PREFIX = "i-";
    private VerificationHost host;
    // fields that are used across method calls, stash them as private fields
    private ComputeService.ComputeState vmState;
    public String accessKey = "accessKey";
    public String secretKey = "secretKey";
    public boolean isMock = true;

    @Before
    public void setUp() throws Exception {
        CommandLineArgumentParser.parseFromProperties(this);

        this.host = VerificationHost.create(0);
        try {
            this.host.setMaintenanceIntervalMicros(TimeUnit.MILLISECONDS.toMicros(250));
            this.host.start();
            PhotonModelServices.startServices(this.host);
            PhotonModelTaskServices.startServices(this.host);
            AWSAdapters.startServices(this.host);

            this.host.setTimeoutSeconds(600);

            this.host.waitForServiceAvailable(PhotonModelServices.LINKS);
            this.host.waitForServiceAvailable(PhotonModelTaskServices.LINKS);
            this.host.waitForServiceAvailable(AWSAdapters.LINKS);
        } catch (Throwable e) {
            throw new Exception(e);
        }
    }

    @After
    public void tearDown() throws InterruptedException {
        if (this.host == null) {
            return;
        }
        // try to delete the VMs
        if (this.vmState != null && this.vmState.id.startsWith(INSTANCEID_PREFIX)) {
            try {
                TestAWSSetupUtils.deleteVMs(this.vmState.documentSelfLink, this.isMock, this.host);
            } catch (Throwable deleteEx) {
                // just log and move on
                this.host.log(Level.WARNING, "Exception deleting VM - %s", deleteEx.getMessage());
            }
        }
        this.host.tearDownInProcessPeers();
        this.host.toggleNegativeTestMode(false);
        this.host.tearDown();
    }

    // Creates a AWS instance via a provision task.
    @Test
    public void testProvision() throws Throwable {

        // Create a resource pool where the VM will be housed
        ResourcePoolState outPool = createAWSResourcePool(this.host);

        // create a compute host for the AWS EC2 VM
        ComputeService.ComputeState outComputeHost = createAWSComputeHost(this.host,
                outPool.documentSelfLink,
                        this.accessKey, this.secretKey);

        // create a AWS VM compute resoruce
        this.vmState = createAWSVMResource(this.host, outComputeHost.documentSelfLink,
                outPool.documentSelfLink, this.getClass());

        // kick off a provision task to do the actual VM creation
        ProvisionComputeTaskState provisionTask = new ProvisionComputeTaskService.ProvisionComputeTaskState();

        provisionTask.computeLink = this.vmState.documentSelfLink;
        provisionTask.isMockRequest = this.isMock;
        provisionTask.taskSubStage =
                ProvisionComputeTaskState.SubStage.CREATING_HOST;
        // Wait for default request timeout in minutes for the machine to be powered ON before
        // reporting failure to the parent task.
        provisionTask.documentExpirationTimeMicros = Utils.getNowMicrosUtc()
                + TimeUnit.MINUTES.toMicros(AWS_VM_REQUEST_TIMEOUT_MINUTES);

        ProvisionComputeTaskService.ProvisionComputeTaskState outTask = TestUtils.doPost(this.host,
                provisionTask,
                ProvisionComputeTaskState.class,
                UriUtils.buildUri(this.host,
                        ProvisionComputeTaskService.FACTORY_LINK));

        List<URI> uris = new ArrayList<URI>();
        uris.add(UriUtils.buildUri(this.host, outTask.documentSelfLink));
        ProvisioningUtils.waitForTaskCompletion(this.host, uris, ProvisionComputeTaskState.class );

        // check that the VM has been created
        ProvisioningUtils.queryComputeInstances(this.host, 2);

        this.host.setTimeoutSeconds(600);
        this.host.waitFor("Error waiting for stats", () -> {
            try {
                issueStatsRequest(this.vmState);
            } catch (Throwable t) {
                return false;
            }
            return true;
        });

        this.host.waitFor("Error waiting for host stats", () -> {
            try {
                issueStatsRequest(outComputeHost);
            } catch (Throwable t) {
                return false;
            }
            return true;
        });

        // delete vm
        TestAWSSetupUtils.deleteVMs(this.vmState.documentSelfLink, this.isMock, this.host);

        // create another AWS VM
        List<String> instanceIdList = new ArrayList<String>();
        this.vmState = TestAWSSetupUtils.createAWSVMResource(this.host, outComputeHost.documentSelfLink,
                outPool.documentSelfLink, this.getClass());
        TestAWSSetupUtils.provisionMachine(this.host, this.vmState, this.isMock, instanceIdList);
        AmazonEC2AsyncClient client = null;
        BaseLineState remoteStateBefore = null;
        if (!this.isMock) {
            // reach out to AWS and get the current state
            AuthCredentialsServiceState creds = new AuthCredentialsServiceState();
            creds.privateKey = this.secretKey;
            creds.privateKeyId = this.accessKey;
            client = AWSUtils.getAsyncClient(creds, TestAWSSetupUtils.zoneId, getExecutor());
            remoteStateBefore = TestAWSSetupUtils.getBaseLineInstanceCount(this.host, client, null);
        }
        // delete just the local representation of the resource
        TestAWSSetupUtils.deleteVMs(this.vmState.documentSelfLink, this.isMock, this.host, true);
        if (!this.isMock) {
            try {
                BaseLineState remoteStateAfter = TestAWSSetupUtils.getBaseLineInstanceCount(this.host, client, null);
                assertEquals(remoteStateBefore.baselineVMCount, remoteStateAfter.baselineVMCount);
            } finally {
                TestAWSSetupUtils.deleteVMsUsingEC2Client(client, this.host, instanceIdList);
            }
        }
        this.vmState  = null;
    }

    private void issueStatsRequest(ComputeState vm) throws Throwable {
        // spin up a stateless service that acts as the parent link to patch back to
        StatelessService parentService = new StatelessService() {
            @Override
            public void handleRequest(Operation op) {
                if (op.getAction() == Action.PATCH) {
                    if (!TestAWSProvisionTask.this.isMock) {
                        ComputeStatsResponse resp = op.getBody(ComputeStatsResponse.class);
                        if (resp.statsList.size() != 1) {
                            TestAWSProvisionTask.this.host.failIteration(new IllegalStateException("response size was incorrect."));
                            return;
                        }
                        // Size == 1, because APICallCount is always added.
                        if (resp.statsList.get(0).statValues.size() == 1) {
                            TestAWSProvisionTask.this.host.failIteration(new IllegalStateException("incorrect number of metrics received."));
                            return;
                        }
                        if (!resp.statsList.get(0).computeLink.equals(vm.documentSelfLink)) {
                            TestAWSProvisionTask.this.host.failIteration(new IllegalStateException("Incorrect computeReference returned."));
                            return;
                        }
                        verifyCollectedStats(resp);
                    }
                    TestAWSProvisionTask.this.host.completeIteration();
                }
            }
        };
        String servicePath = UUID.randomUUID().toString();
        Operation startOp = Operation.createPost(UriUtils.buildUri(this.host, servicePath));
        this.host.startService(startOp, parentService);
        ComputeStatsRequest statsRequest = new ComputeStatsRequest();
        statsRequest.computeReference = UriUtils.buildUri(this.host, vm.documentSelfLink);
        statsRequest.isMockRequest = this.isMock;
        statsRequest.parentTaskReference = UriUtils.buildUri(this.host, servicePath);
        this.host.sendAndWait(Operation.createPatch(UriUtils.buildUri(
                this.host, AWSUriPaths.AWS_STATS_ADAPTER))
                .setBody(statsRequest)
                .setReferer(this.host.getUri()));
    }

    private void verifyCollectedStats(ComputeStatsResponse response) {
        ComputeStats computeStats = response.statsList.get(0);
        Assert.assertTrue("Compute Link is empty", !computeStats.computeLink.isEmpty());
        Assert.assertTrue("APICallCount is not present", computeStats.statValues.keySet()
                .contains(PhotonModelConstants.API_CALL_COUNT));
        // Check that stat values are accompanied with Units.
        for (String key : computeStats.statValues.keySet()) {
            ServiceStat stat = computeStats.statValues.get(key);
            Assert.assertTrue("Unit is empty", !stat.unit.isEmpty());
        }
    }
}