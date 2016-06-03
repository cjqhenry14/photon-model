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

import com.vmware.photon.controller.model.adapterapi.ComputeStatsRequest;
import com.vmware.photon.controller.model.adapterapi.ComputeStatsResponse;
import com.vmware.photon.controller.model.adapterapi.ComputeStatsResponse.ComputeStats;
import com.vmware.photon.controller.model.adapters.awsadapter.TestAWSSetupUtils.BaseLineState;
import com.vmware.photon.controller.model.constants.PhotonModelConstants;
import com.vmware.photon.controller.model.resources.ComputeService;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.resources.ResourcePoolService.ResourcePoolState;
import com.vmware.photon.controller.model.tasks.ProvisionComputeTaskService;
import com.vmware.photon.controller.model.tasks.ProvisionComputeTaskService.ProvisionComputeTaskState;
import com.vmware.photon.controller.model.tasks.ProvisioningUtils;
import com.vmware.photon.controller.model.tasks.TestUtils;

import com.vmware.xenon.common.CommandLineArgumentParser;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceStats.ServiceStat;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.common.UriUtils;
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
public class TestAWSProvisionTask  {

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
            ProvisioningUtils.startProvisioningServices(this.host);
            this.host.setTimeoutSeconds(600);
            List<String> serviceSelfLinks = new ArrayList<String>();

            host.startService(
                    Operation.createPost(UriUtils.buildUri(host,
                            AWSInstanceService.class)),
                    new AWSInstanceService());
            serviceSelfLinks.add(AWSInstanceService.SELF_LINK);

            host.startService(
                    Operation.createPost(UriUtils.buildUri(host,
                            AWSStatsService.class)),
                    new AWSStatsService());
            serviceSelfLinks.add(AWSStatsService.SELF_LINK);

            ProvisioningUtils.waitForServiceStart(host, serviceSelfLinks.toArray(new String[] {}));
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
        if (vmState != null) {
            try {
                TestAWSSetupUtils.deleteVMs(vmState.documentSelfLink, isMock, this.host);
            } catch (Throwable deleteEx) {
                // just log and move on
                host.log(Level.WARNING, "Exception deleting VM - %s", deleteEx.getMessage());
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
        ResourcePoolState outPool =
                TestAWSSetupUtils.createAWSResourcePool(this.host);

        // create a compute host for the AWS EC2 VM
        ComputeService.ComputeState outComputeHost =
                TestAWSSetupUtils.createAWSComputeHost(this.host, outPool.documentSelfLink,
                        accessKey, secretKey);

        // create a AWS VM compute resoruce
        vmState = TestAWSSetupUtils.createAWSVMResource(this.host, outComputeHost.documentSelfLink,
                outPool.documentSelfLink, this.getClass());

        // kick off a provision task to do the actual VM creation
        ProvisionComputeTaskState provisionTask = new ProvisionComputeTaskService.ProvisionComputeTaskState();

        provisionTask.computeLink = vmState.documentSelfLink;
        provisionTask.isMockRequest = isMock;
        provisionTask.taskSubStage =
                ProvisionComputeTaskState.SubStage.CREATING_HOST;

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

        host.setTimeoutSeconds(600);
        host.waitFor("Error waiting for stats", () -> {
            try {
                issueStatsRequest(vmState);
            } catch (Throwable t) {
                return false;
            }
            return true;
        });

        host.waitFor("Error waiting for host stats", () -> {
            try {
                issueStatsRequest(outComputeHost);
            } catch (Throwable t) {
                return false;
            }
            return true;
        });

        // delete vm
        TestAWSSetupUtils.deleteVMs(vmState.documentSelfLink, isMock, this.host);

        // create another AWS VM
        List<String> instanceIdList = new ArrayList<String>();
        vmState = TestAWSSetupUtils.createAWSVMResource(this.host, outComputeHost.documentSelfLink,
                outPool.documentSelfLink, this.getClass());
        TestAWSSetupUtils.provisionMachine(host, vmState, isMock, instanceIdList);
        AmazonEC2AsyncClient client = null;
        BaseLineState remoteStateBefore = null;
        if (!isMock) {
            // reach out to AWS and get the current state
            AuthCredentialsServiceState creds = new AuthCredentialsServiceState();
            creds.privateKey = secretKey;
            creds.privateKeyId = accessKey;
            client = AWSUtils.getAsyncClient(creds, TestAWSSetupUtils.zoneId,
                isMock, getExecutor());
            remoteStateBefore = TestAWSSetupUtils.getBaseLineInstanceCount(this.host, client, null);
        }
        // delete just the local representation of the resource
        TestAWSSetupUtils.deleteVMs(vmState.documentSelfLink, isMock, this.host, true);
        if (!isMock) {
            try {
                BaseLineState remoteStateAfter = TestAWSSetupUtils.getBaseLineInstanceCount(this.host, client, null);
                assertEquals(remoteStateBefore.baselineVMCount, remoteStateAfter.baselineVMCount);
            } finally {
                TestAWSSetupUtils.deleteVMsUsingEC2Client(client, this.host, instanceIdList);
            }
        }
        vmState  = null;
    }


    private void issueStatsRequest(ComputeState vm) throws Throwable {
        // spin up a stateless service that acts as the parent link to patch back to
        StatelessService parentService = new StatelessService() {
            public void handleRequest(Operation op) {
                if (op.getAction() == Action.PATCH) {
                    if (!isMock) {
                        ComputeStatsResponse resp = op.getBody(ComputeStatsResponse.class);
                        if (resp.statsList.size() != 1) {
                            host.failIteration(new IllegalStateException("response size was incorrect."));
                            return;
                        }
                        // Size == 1, because APICallCount is always added.
                        if (resp.statsList.get(0).statValues.size() == 1) {
                            host.failIteration(new IllegalStateException("incorrect number of metrics received."));
                            return;
                        }
                        if (!resp.statsList.get(0).computeLink.equals(vm.documentSelfLink)) {
                            host.failIteration(new IllegalStateException("Incorrect computeLink returned."));
                            return;
                        }
                        verifyCollectedStats(resp);
                    }
                    host.completeIteration();
                }
            }
        };
        String servicePath = UUID.randomUUID().toString();
        Operation startOp = Operation.createPost(UriUtils.buildUri(this.host, servicePath));
        this.host.startService(startOp, parentService);
        ComputeStatsRequest statsRequest = new ComputeStatsRequest();
        statsRequest.computeLink = vm.documentSelfLink;
        statsRequest.isMockRequest = isMock;
        statsRequest.parentTaskLink = servicePath;
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