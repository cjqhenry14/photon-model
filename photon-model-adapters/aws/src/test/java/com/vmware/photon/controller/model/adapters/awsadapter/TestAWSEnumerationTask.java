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

import static com.vmware.photon.controller.model.adapters.awsadapter.AWSConstants.setQueryPageSize;
import static com.vmware.photon.controller.model.adapters.awsadapter.AWSConstants.setQueryResultLimit;
import static com.vmware.photon.controller.model.adapters.awsadapter.TestAWSSetupUtils.createAWSComputeHost;
import static com.vmware.photon.controller.model.adapters.awsadapter.TestAWSSetupUtils.createAWSResourcePool;
import static com.vmware.photon.controller.model.adapters.awsadapter.TestAWSSetupUtils.createAWSVMResource;
import static com.vmware.photon.controller.model.adapters.awsadapter.TestAWSSetupUtils.deleteDocument;
import static com.vmware.photon.controller.model.adapters.awsadapter.TestAWSSetupUtils.deleteVMsUsingEC2Client;
import static com.vmware.photon.controller.model.adapters.awsadapter.TestAWSSetupUtils.enumerateResources;
import static com.vmware.photon.controller.model.adapters.awsadapter.TestAWSSetupUtils.getBaseLineInstanceCount;
import static com.vmware.photon.controller.model.adapters.awsadapter.TestAWSSetupUtils.instanceType_t2_micro;
import static com.vmware.photon.controller.model.adapters.awsadapter.TestAWSSetupUtils.provisionAWSVMWithEC2Client;
import static com.vmware.photon.controller.model.adapters.awsadapter.TestAWSSetupUtils.provisionMachine;
import static com.vmware.photon.controller.model.adapters.awsadapter.TestAWSSetupUtils.zoneId;
import static com.vmware.photon.controller.model.adapters.awsadapter.TestUtils.getExecutor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;

import com.amazonaws.services.ec2.AmazonEC2AsyncClient;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.vmware.photon.controller.model.adapters.awsadapter.TestAWSSetupUtils.BaseLineState;
import com.vmware.photon.controller.model.adapters.awsadapter.enumeration.AWSEnumerationAdapterService;
import com.vmware.photon.controller.model.resources.ComputeService;
import com.vmware.photon.controller.model.resources.ResourcePoolService.ResourcePoolState;
import com.vmware.photon.controller.model.tasks.ProvisioningUtils;

import com.vmware.xenon.common.BasicReusableHostTestCase;
import com.vmware.xenon.common.CommandLineArgumentParser;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.services.common.AuthCredentialsService.AuthCredentialsServiceState;

/**
 * Test to enumerate instances on AWS and tear it down. The test creates VM using the Provisioning task as well as
 * directly creating instances on AWS using the EC2 client.It then invokes the AWS enumeration adapter to enumerate
 * all the resources on the AWS endpoint and validates that all the updates to the local state are as expected.If the 'isMock'
 * flag is set to true the test runs the adapter in mock mode and does not actually create a VM.
 * Minimally the accessKey and secretKey for AWS must be specified to run the test.
 *
 */
public class TestAWSEnumerationTask extends BasicReusableHostTestCase {
    public static final int instanceCount1 = 1;
    public static final int instanceCount2 = 2;
    public static final int instanceCount3 = 3;
    public static final int instanceCount4 = 4;
    public static final int instanceCount5 = 5;
    public static final int instanceCount7 = 7;
    public static final int instanceCount8 = 8;
    public int instanceCountAtScale = 10;
    public ComputeService.ComputeState vmState;
    public ResourcePoolState outPool;
    public ComputeService.ComputeState outComputeHost;
    public AuthCredentialsServiceState creds;
    public static final String EC2_IMAGEID = "ami-0d4cfd66";
    public static final String T2_NANO_INSTANCE_TYPE = "t2.nano";
    public static final String DEFAULT_SECURITY_GROUP_NAME = "cell-manager-security-group";
    public static List<String> instancesToCleanUp = new ArrayList<String>();
    public static List<String> instanceIds = new ArrayList<String>();
    public List<String> instanceIdsToDeleteFirstTime = new ArrayList<String>();
    public List<String> instanceIdsToDeleteSecondTime = new ArrayList<String>();
    public AmazonEC2AsyncClient client;
    public static List<Boolean> provisioningFlags;
    public static List<Boolean> deletionFlags = new ArrayList<Boolean>();
    public boolean isMock = true;
    public BaseLineState baseLineState;
    public String accessKey = "accessKey";
    public String secretKey = "secretKey";
    public static List<String> testComputeDescriptions = new ArrayList<String>(
            Arrays.asList(zoneId + "~" + T2_NANO_INSTANCE_TYPE,
                    zoneId + "~" + instanceType_t2_micro));

    @Before
    public void setUp() throws Exception {
        CommandLineArgumentParser.parseFromProperties(this);
        // create credentials
        creds = new AuthCredentialsServiceState();
        creds.privateKey = secretKey;
        creds.privateKeyId = accessKey;
        client = AWSUtils.getAsyncClient(creds, TestAWSSetupUtils.zoneId,
                isMock, getExecutor());
        List<String> serviceSelfLinks = new ArrayList<String>();
        try {
            ProvisioningUtils.startProvisioningServices(this.host);
            host.setTimeoutSeconds(200);
            host.startService(
                    Operation.createPost(UriUtils.buildUri(host,
                            AWSInstanceService.class)),
                    new AWSInstanceService());
            serviceSelfLinks.add(AWSInstanceService.SELF_LINK);

            host.startService(
                    Operation.createPost(UriUtils.buildUri(host,
                            AWSEnumerationAdapterService.class)),
                    new AWSEnumerationAdapterService());
            serviceSelfLinks.add(AWSEnumerationAdapterService.SELF_LINK);


            // create the compute host, resource pool and the VM state to be used in the test.
            createResourcePoolComputeHostAndVMState();
            ProvisioningUtils.waitForServiceStart(host, serviceSelfLinks.toArray(new String[] {}));
        } catch (Throwable e) {
            host.log("Error starting up services for the test %s", e.getMessage());
            throw new Exception(e);
        }
    }

    @After
    public void tearDown() throws InterruptedException {
        if (host == null) {
            return;
        }
        try {
            // Delete all vms from the endpoint that w
            host.log("Deleting %d instance created from the test ", instancesToCleanUp.size());
            TestAWSSetupUtils.deleteAllVMsOnThisEndpoint(host, isMock,
                    outComputeHost.documentSelfLink, instancesToCleanUp);
            // Leave the system in the same state as when the test started.
            ProvisioningUtils.queryComputeInstances(this.host, baseLineState.baselineVMCount + 1);
            // Delete the reference to the compute host as each individual test creates one.
            deleteDocument(this.host, outComputeHost.documentSelfLink);
            instancesToCleanUp.clear();
            if (client != null) {
                client.shutdown();
                client = null;
            }
        } catch (Throwable deleteEx) {
            // just log and move on
            host.log(Level.WARNING, "Exception deleting VMs - %s", deleteEx.getMessage());
        }
    }

    // Runs the enumeration task on the AWS endpoint to list all the instances on the endpoint.
    @Test
    public void testEnumeration() throws Throwable {
        if (!isMock) {
            host.setTimeoutSeconds(600);
            // Overriding the page size to test the pagination logic with limited instances on AWS.
            // This is a functional test
            // so the latency numbers maybe higher from this test due to low page size.
            setQueryPageSize(5);
            setQueryResultLimit(5);
            baseLineState = getBaseLineInstanceCount(host, client, testComputeDescriptions);
            host.log(baseLineState.toString());
            // Provision a single VM . Check initial state.
            provisionMachine(host, vmState, isMock, instancesToCleanUp);
            ProvisioningUtils.queryComputeInstances(this.host, instanceCount2);
            ProvisioningUtils.queryComputeDescriptions(this.host, instanceCount2);

            // CREATION directly on AWS
            instanceIdsToDeleteFirstTime = provisionAWSVMWithEC2Client(client, host, instanceCount5,
                    T2_NANO_INSTANCE_TYPE);

            // Xenon does not know about the new instances.
            ProvisioningUtils.queryComputeInstances(this.host, instanceCount2);

            enumerateResources(host, isMock, outPool.documentSelfLink,
                    outComputeHost.descriptionLink, outComputeHost.documentSelfLink);
            // 5 new resources should be discovered. Mapping to 1 new compute description and 5 new
            // compute states.
            ProvisioningUtils.queryComputeDescriptions(this.host,
                    instanceCount3 + baseLineState.baselineComputeDescriptionCount);
            ProvisioningUtils.queryComputeInstances(this.host,
                    instanceCount7 + baseLineState.baselineVMCount);

            // Provision an additional VM that has a compute description already present in the
            // system.
            instanceIdsToDeleteSecondTime = provisionAWSVMWithEC2Client(client, host,
                    instanceCount1,
                    TestAWSSetupUtils.instanceType_t2_micro);
            enumerateResources(host, isMock, outPool.documentSelfLink,
                    outComputeHost.descriptionLink, outComputeHost.documentSelfLink);
            // One additional compute state and no new compute descriptions should be created.
            ProvisioningUtils.queryComputeDescriptions(this.host,
                    instanceCount3 + baseLineState.baselineComputeDescriptionCount);
            ProvisioningUtils.queryComputeInstances(this.host,
                    instanceCount8 + baseLineState.baselineVMCount);

            // Verify Deletion flow
            // Delete 5 VMs spawned above of type T2_NANO
            deleteVMsUsingEC2Client(client, host, instanceIdsToDeleteFirstTime);
            enumerateResources(host, isMock, outPool.documentSelfLink,
                    outComputeHost.descriptionLink, outComputeHost.documentSelfLink);
            // Counts should go down 5 compute states.
            ProvisioningUtils.queryComputeInstances(this.host,
                    instanceCount3 + baseLineState.baselineVMCount);

            // Verify Deletion flow
            // Delete 1 VMs spawned above of type T2_Micro
            deleteVMsUsingEC2Client(client, host, instanceIdsToDeleteSecondTime);
            enumerateResources(host, isMock, outPool.documentSelfLink,
                    outComputeHost.descriptionLink, outComputeHost.documentSelfLink);
            // Compute state count should go down by 1
            ProvisioningUtils.queryComputeInstances(this.host,
                    instanceCount2 + baseLineState.baselineVMCount);

        } else {
            // Create basic state for kicking off enumeration
            createResourcePoolComputeHostAndVMState();
            // Just make a call to the enumeration service and make sure that the adapter patches
            // the parent with completion.
            enumerateResources(host, isMock, outPool.documentSelfLink,
                    outComputeHost.descriptionLink, outComputeHost.documentSelfLink);
        }
    }

    /**
     * Creates the state associated with the resource pool, compute host and the VM to be created.
     * @throws Throwable
     */
    public void createResourcePoolComputeHostAndVMState() throws Throwable {
        // Create a resource pool where the VM will be housed
        outPool = createAWSResourcePool(host);

        // create a compute host for the AWS EC2 VM
        outComputeHost = createAWSComputeHost(host, outPool.documentSelfLink, accessKey, secretKey);

        // create a AWS VM compute resource
        vmState = createAWSVMResource(host, outComputeHost.documentSelfLink,
                outPool.documentSelfLink, TestAWSSetupUtils.class);
    }
}
