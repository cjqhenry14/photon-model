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

import static com.vmware.photon.controller.model.adapters.awsadapter.TestAWSSetupUtils.createAWSComputeHost;
import static com.vmware.photon.controller.model.adapters.awsadapter.TestAWSSetupUtils.createAWSResourcePool;
import static com.vmware.photon.controller.model.adapters.awsadapter.TestAWSSetupUtils.createAWSVMResource;
import static com.vmware.photon.controller.model.adapters.awsadapter.TestAWSSetupUtils.deleteDocument;
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
import com.vmware.photon.controller.model.adapters.awsadapter.enumeration.AWSEnumerationAndCreationAdapterService;
import com.vmware.photon.controller.model.resources.ComputeService;
import com.vmware.photon.controller.model.resources.ResourcePoolService.ResourcePoolState;
import com.vmware.photon.controller.model.tasks.ProvisioningUtils;

import com.vmware.xenon.common.BasicReusableHostTestCase;
import com.vmware.xenon.common.CommandLineArgumentParser;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.services.common.AuthCredentialsService.AuthCredentialsServiceState;

/**
 *
 * Minimally the accessKey and secretKey for AWS must be specified must be provided to run the test and the isMock
 * flag needs to be turned OFF.
 *
 */
public class TestAWSEnumerationAtScale extends BasicReusableHostTestCase {
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
    public List<String> instanceIdsToDelete = new ArrayList<String>();
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
                    new AWSEnumerationAndCreationAdapterService());
            serviceSelfLinks.add(AWSEnumerationAdapterService.SELF_LINK);

            ProvisioningUtils.waitForServiceStart(host, serviceSelfLinks.toArray(new String[] {}));
            // create the compute host, resource pool and the VM state to be used in the test.
            createResourcePoolComputeHostAndVMState();
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

    @Test
    public void testEnumerationAtScale() throws Throwable {
        if (!isMock) {
            host.setTimeoutSeconds(600);
            AWSEnumerationAndCreationAdapterService.AWS_PAGE_SIZE = 50;
            baseLineState = getBaseLineInstanceCount(host, client, testComputeDescriptions);
            host.log(baseLineState.toString());
            // Provision a single VM . Check initial state.
            provisionMachine(host, vmState, isMock, instancesToCleanUp);
            // Create {instanceCountAtScale} VMs on AWS
            host.log("Running scale test by provisioning %d instances", instanceCountAtScale);
            instanceIds = provisionAWSVMWithEC2Client(client, host, instanceCountAtScale,
                    T2_NANO_INSTANCE_TYPE);
            instancesToCleanUp.addAll(instanceIds);
            enumerateResources(host, isMock, outPool.documentSelfLink,
                    outComputeHost.descriptionLink, outComputeHost.documentSelfLink);
            // {instanceCountAtScale} new resources should be discovered.
            ProvisioningUtils.queryComputeInstances(this.host,
                    instanceCountAtScale + 2 + baseLineState.baselineVMCount);
        } else {
            // Do nothing. Basic enumeration logic tested in functional test.
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
