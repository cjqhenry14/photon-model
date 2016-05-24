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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import static com.vmware.photon.controller.model.adapters.awsadapter.AWSConstants.AWS_GATEWAY_ID;
import static com.vmware.photon.controller.model.adapters.awsadapter.AWSConstants.AWS_SUBNET_ID;
import static com.vmware.photon.controller.model.adapters.awsadapter.AWSConstants.AWS_TAGS;
import static com.vmware.photon.controller.model.adapters.awsadapter.AWSConstants.AWS_VPC_ID;
import static com.vmware.photon.controller.model.adapters.awsadapter.AWSConstants.AWS_VPC_ROUTE_TABLE_ID;
import static com.vmware.photon.controller.model.adapters.awsadapter.AWSConstants.setQueryPageSize;
import static com.vmware.photon.controller.model.adapters.awsadapter.AWSConstants.setQueryResultLimit;
import static com.vmware.photon.controller.model.adapters.awsadapter.AWSUtils.AWS_TAG_NAME;
import static com.vmware.photon.controller.model.adapters.awsadapter.AWSUtils.cleanupEC2ClientResources;
import static com.vmware.photon.controller.model.adapters.awsadapter.AWSUtils.setResourceName;
import static com.vmware.photon.controller.model.adapters.awsadapter.TestAWSSetupUtils.createAWSComputeHost;
import static com.vmware.photon.controller.model.adapters.awsadapter.TestAWSSetupUtils.createAWSResourcePool;
import static com.vmware.photon.controller.model.adapters.awsadapter.TestAWSSetupUtils.createAWSVMResource;
import static com.vmware.photon.controller.model.adapters.awsadapter.TestAWSSetupUtils.deleteAllVMsOnThisEndpoint;
import static com.vmware.photon.controller.model.adapters.awsadapter.TestAWSSetupUtils.deleteVMsUsingEC2Client;
import static com.vmware.photon.controller.model.adapters.awsadapter.TestAWSSetupUtils.enumerateResources;
import static com.vmware.photon.controller.model.adapters.awsadapter.TestAWSSetupUtils.getBaseLineInstanceCount;
import static com.vmware.photon.controller.model.adapters.awsadapter.TestAWSSetupUtils.instanceType_t2_micro;
import static com.vmware.photon.controller.model.adapters.awsadapter.TestAWSSetupUtils.provisionAWSVMWithEC2Client;
import static com.vmware.photon.controller.model.adapters.awsadapter.TestAWSSetupUtils.provisionMachine;
import static com.vmware.photon.controller.model.adapters.awsadapter.TestAWSSetupUtils.waitForInstancesToBeTerminated;
import static com.vmware.photon.controller.model.adapters.awsadapter.TestAWSSetupUtils.waitForProvisioningToComplete;
import static com.vmware.photon.controller.model.adapters.awsadapter.TestAWSSetupUtils.zoneId;
import static com.vmware.photon.controller.model.adapters.awsadapter.TestUtils.getExecutor;
import static com.vmware.photon.controller.model.tasks.ProvisioningUtils.queryComputeDescriptions;
import static com.vmware.photon.controller.model.tasks.ProvisioningUtils.queryComputeInstances;
import static com.vmware.photon.controller.model.tasks.ProvisioningUtils.queryNetworkStates;
import static com.vmware.photon.controller.model.tasks.ProvisioningUtils.startProvisioningServices;
import static com.vmware.photon.controller.model.tasks.ProvisioningUtils.waitForServiceStart;
import static com.vmware.xenon.common.UriUtils.buildUri;
import static com.vmware.xenon.common.UriUtils.extendUri;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

import com.amazonaws.services.ec2.AmazonEC2AsyncClient;
import com.amazonaws.services.ec2.model.Tag;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.vmware.photon.controller.model.adapters.awsadapter.TestAWSSetupUtils.BaseLineState;
import com.vmware.photon.controller.model.adapters.awsadapter.enumeration.AWSComputeStateCreationAdapterService.AWSTags;
import com.vmware.photon.controller.model.adapters.awsadapter.enumeration.AWSEnumerationAdapterService;
import com.vmware.photon.controller.model.resources.ComputeService;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.resources.NetworkInterfaceService.NetworkInterfaceState;
import com.vmware.photon.controller.model.resources.NetworkService;
import com.vmware.photon.controller.model.resources.NetworkService.NetworkState;
import com.vmware.photon.controller.model.resources.ResourcePoolService.ResourcePoolState;
import com.vmware.photon.controller.model.tasks.ProvisioningUtils;

import com.vmware.xenon.common.BasicReusableHostTestCase;
import com.vmware.xenon.common.CommandLineArgumentParser;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
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
    public static final int ZERO = 0;
    public static final int count1 = 1;
    public static final int count2 = 2;
    public static final int count3 = 3;
    public static final int count4 = 4;
    public static final int count5 = 5;
    public static final int count6 = 6;
    public static final int count7 = 7;
    public static final int count8 = 8;
    public int instanceCountAtScale = 10;
    public ComputeService.ComputeState vmState;
    public ResourcePoolState outPool;
    public ComputeService.ComputeState outComputeHost;
    public AuthCredentialsServiceState creds;
    public static final String EC2_IMAGEID = "ami-0d4cfd66";
    public static final String T2_NANO_INSTANCE_TYPE = "t2.nano";
    public static final String DEFAULT_SECURITY_GROUP_NAME = "cell-manager-security-group";
    public static final String VM_NAME = "aws-test-vm";
    public static List<String> instancesToCleanUp = new ArrayList<String>();
    public static List<String> instanceIds = new ArrayList<String>();
    public List<String> instanceIdsToDeleteFirstTime = new ArrayList<String>();
    public List<String> instanceIdsToDeleteSecondTime = new ArrayList<String>();
    public AmazonEC2AsyncClient client;
    public static List<Boolean> provisioningFlags;
    public static List<Boolean> deletionFlags = new ArrayList<Boolean>();
    public boolean isMock = true;
    public BaseLineState baseLineState;
    public static final String TEST_CASE_INITIAL = "Initial Run ";
    public static final String TEST_CASE_ADDITIONAL_VM = "Additional VM ";
    public static final String TEST_CASE_DELETE_VM = "Delete VM ";
    public static final String TEST_CASE_DELETE_VMS = "Delete multiple VMs ";
    public static final String TEST_CASE_MOCK_MODE = "Mock Mode ";

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
            startProvisioningServices(this.host);
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
            waitForServiceStart(host, serviceSelfLinks.toArray(new String[] {}));
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
            deleteAllVMsOnThisEndpoint(host, isMock,
                    outComputeHost.documentSelfLink, instancesToCleanUp);
            // Check that all the instances that are required to be deleted are in
            // terminated state on AWS
            waitForInstancesToBeTerminated(client, host, instancesToCleanUp);
            cleanupEC2ClientResources(client);
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
            queryComputeInstances(this.host, count2);
            queryComputeDescriptions(this.host, count2);

            // CREATION directly on AWS
            instanceIdsToDeleteFirstTime = provisionAWSVMWithEC2Client(client, host, count5,
                    T2_NANO_INSTANCE_TYPE);
            instancesToCleanUp.addAll(instanceIdsToDeleteFirstTime);
            waitForProvisioningToComplete(instanceIdsToDeleteFirstTime, host, client, ZERO);
            // Tag the first VM with a name
            tagProvisionedVM(vmState.id, VM_NAME, client);

            // Xenon does not know about the new instances.
            ProvisioningUtils.queryComputeInstances(this.host, count2);

            enumerateResources(host, isMock, outPool.documentSelfLink,
                    outComputeHost.descriptionLink, outComputeHost.documentSelfLink,
                    TEST_CASE_INITIAL);
            // 5 new resources should be discovered. Mapping to 1 new compute description and 5 new
            // compute states.
            queryComputeDescriptions(this.host,
                    count3 + baseLineState.baselineComputeDescriptionCount);
            queryComputeInstances(this.host,
                    count7 + baseLineState.baselineVMCount);

            // Update Scenario : Check that the tag information is present for the VM tagged above.
            String vpCId = validateTagAndNetworkInformation();
            validateVPCInformation(vpCId);
            // Total network states is a combination of NICs and network state
            // Count should be 2 NICs per discovered VM + 1 Network State for the VPC
            int totalNetworkStateCount = (count6 + baseLineState.baselineVMCount) * 2 + 1;
            queryNetworkStates(this.host, totalNetworkStateCount);

            // Provision an additional VM that has a compute description already present in the
            // system.
            instanceIdsToDeleteSecondTime = provisionAWSVMWithEC2Client(client, host,
                    count1, TestAWSSetupUtils.instanceType_t2_micro);
            instancesToCleanUp.addAll(instanceIdsToDeleteSecondTime);
            waitForProvisioningToComplete(instanceIdsToDeleteSecondTime, host, client, ZERO);
            enumerateResources(host, isMock, outPool.documentSelfLink,
                    outComputeHost.descriptionLink, outComputeHost.documentSelfLink,
                    TEST_CASE_ADDITIONAL_VM);
            // One additional compute state and no new compute descriptions should be created.
            queryComputeDescriptions(this.host,
                    count3 + baseLineState.baselineComputeDescriptionCount);
            queryComputeInstances(this.host,
                    count8 + baseLineState.baselineVMCount);

            // Verify Deletion flow
            // Delete 5 VMs spawned above of type T2_NANO
            deleteVMsUsingEC2Client(client, host, instanceIdsToDeleteFirstTime);
            enumerateResources(host, isMock, outPool.documentSelfLink,
                    outComputeHost.descriptionLink, outComputeHost.documentSelfLink,
                    TEST_CASE_DELETE_VMS);
            // Counts should go down 5 compute states.
            queryComputeInstances(this.host,
                    count3 + baseLineState.baselineVMCount);

            // Delete 1 VMs spawned above of type T2_Micro
            deleteVMsUsingEC2Client(client, host, instanceIdsToDeleteSecondTime);
            enumerateResources(host, isMock, outPool.documentSelfLink,
                    outComputeHost.descriptionLink, outComputeHost.documentSelfLink,
                    TEST_CASE_DELETE_VM);
            // Compute state count should go down by 1
            queryComputeInstances(this.host,
                    count2 + baseLineState.baselineVMCount);

        } else {
            // Create basic state for kicking off enumeration
            createResourcePoolComputeHostAndVMState();
            // Just make a call to the enumeration service and make sure that the adapter patches
            // the parent with completion.
            enumerateResources(host, isMock, outPool.documentSelfLink,
                    outComputeHost.descriptionLink, outComputeHost.documentSelfLink,
                    TEST_CASE_MOCK_MODE);
        }
    }

    /**
     * Tags a provisioned virtual machine with a tag.Get the latest compute state for the provisioned VM to get
     * a handle to the instance Id on AWS.This Id will be used to tag the VM with a custom property like name.
     */
    private void tagProvisionedVM(String id, String vmName, AmazonEC2AsyncClient client2)
            throws Throwable {
        URI[] computeStateURIs = { UriUtils.buildUri(host, vmState.documentSelfLink) };
        Map<URI, ComputeState> computeStateMap = host
                .getServiceState(null, ComputeState.class, computeStateURIs);
        ComputeState computeStateToTag = computeStateMap.get(computeStateURIs[0]);
        setResourceName(computeStateToTag.id, VM_NAME, client);
    }

    /**
     * Verifies if the tag information exists for a given resource. And that private and public IP addresses
     * are mapped to separate NICs.
     * @throws Throwable
     */
    private String validateTagAndNetworkInformation() throws Throwable {
        URI[] computeStateURIs = { UriUtils.buildUri(host, vmState.documentSelfLink) };
        Map<URI, ComputeState> computeStateMap = host
                .getServiceState(null, ComputeState.class, computeStateURIs);
        ComputeState taggedComputeState = computeStateMap.get(computeStateURIs[0]);
        assertTrue(taggedComputeState.customProperties.get(AWS_TAGS) != null);
        String tagsJson = taggedComputeState.customProperties.get(AWS_TAGS);
        AWSTags resultTags = Utils.fromJson(tagsJson, AWSTags.class);
        assertTrue(resultTags != null);
        Tag nameTag = resultTags.awsTags.get(0);
        assertEquals(nameTag.getKey(), AWS_TAG_NAME);
        assertEquals(nameTag.getValue(), VM_NAME);

        assertTrue(taggedComputeState.networkLinks != null);
        assertTrue(taggedComputeState.networkLinks.size() == 2);

        URI[] networkLinkURIs = new URI[2];
        for (int i = 0; i < taggedComputeState.networkLinks.size(); i++) {
            networkLinkURIs[i] = UriUtils.buildUri(host, taggedComputeState.networkLinks.get(i));
        }

        // Assert that both the public and private IP addresses have been mapped to separated NICs
        Map<URI, NetworkInterfaceState> NICMap = host
                .getServiceState(null, NetworkInterfaceState.class, networkLinkURIs);
        assertNotNull(NICMap.get(networkLinkURIs[0]).address);
        assertNotNull(NICMap.get(networkLinkURIs[1]).address);

        // get the VPC information for the provisioned VM
        assertTrue(taggedComputeState.customProperties.get(AWS_VPC_ID) != null);
        return taggedComputeState.customProperties.get(AWS_VPC_ID);

    }

    /**
     * Validates that the VPC information discovered from AWS has all the desired set of fields and the association
     * between a compute state and a network state is established correctly.
     * @throws Throwable
     */
    private void validateVPCInformation(String vpCId) throws Throwable {
        // Get the network state that maps to this VPCID. Right now the id field of the network
        // state is set to the VPC ID, so querying the network state based on that.
        URI[] VPCIDs = { extendUri(buildUri(host, NetworkService.FACTORY_LINK), vpCId) };
        Map<URI, NetworkState> networkStateMap = host
                .getServiceState(null, NetworkState.class, VPCIDs);
        NetworkState networkState = networkStateMap.get(VPCIDs[0]);
        // The network state for the VPC id of the VM should not be null
        assertNotNull(networkState);
        assertNotNull(networkState.subnetCIDR);
        assertNotNull(networkState.instanceAdapterReference);
        // This is assuming that the internet gateway is attached to the VPC by default
        assertNotNull(networkState.customProperties.get(AWS_GATEWAY_ID));
        assertNotNull(networkState.customProperties.get(AWS_SUBNET_ID));
        assertNotNull(networkState.customProperties.get(AWS_VPC_ROUTE_TABLE_ID));
        assertNotNull(networkState.customProperties.get(AWS_VPC_ID));
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
