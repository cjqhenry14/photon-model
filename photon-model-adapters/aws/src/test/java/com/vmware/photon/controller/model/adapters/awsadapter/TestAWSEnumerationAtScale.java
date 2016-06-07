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

import static com.vmware.photon.controller.model.adapters.awsadapter.AWSUtils.AWS_TAG_NAME;
import static com.vmware.photon.controller.model.adapters.awsadapter.AWSUtils.cleanupEC2ClientResources;
import static com.vmware.photon.controller.model.adapters.awsadapter.AWSUtils.createTags;
import static com.vmware.photon.controller.model.adapters.awsadapter.TestAWSSetupUtils.createAWSComputeHost;
import static com.vmware.photon.controller.model.adapters.awsadapter.TestAWSSetupUtils.createAWSResourcePool;
import static com.vmware.photon.controller.model.adapters.awsadapter.TestAWSSetupUtils.deleteAllVMsOnThisEndpoint;
import static com.vmware.photon.controller.model.adapters.awsadapter.TestAWSSetupUtils.deleteVMsUsingEC2Client;
import static com.vmware.photon.controller.model.adapters.awsadapter.TestAWSSetupUtils.enumerateResources;
import static com.vmware.photon.controller.model.adapters.awsadapter.TestAWSSetupUtils.getBaseLineInstanceCount;
import static com.vmware.photon.controller.model.adapters.awsadapter.TestAWSSetupUtils.instanceType_t2_micro;
import static com.vmware.photon.controller.model.adapters.awsadapter.TestAWSSetupUtils.provisionAWSVMWithEC2Client;
import static com.vmware.photon.controller.model.adapters.awsadapter.TestAWSSetupUtils.waitForInstancesToBeTerminated;
import static com.vmware.photon.controller.model.adapters.awsadapter.TestAWSSetupUtils.waitForProvisioningToComplete;
import static com.vmware.photon.controller.model.adapters.awsadapter.TestAWSSetupUtils.zoneId;
import static com.vmware.photon.controller.model.adapters.awsadapter.TestUtils.getExecutor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;

import com.amazonaws.services.ec2.AmazonEC2AsyncClient;
import com.amazonaws.services.ec2.model.Tag;

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
 *
 * Minimally the accessKey and secretKey for AWS must be specified must be provided to run the test and the isMock
 * flag needs to be turned OFF.
 *
 */
public class TestAWSEnumerationAtScale extends BasicReusableHostTestCase {
    private static final float HUNDERED = 100.0f;
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
    public BaseLineState baseLineState;
    public static final String SCALE_VM_NAME = "scale-test-vm";
    public static final String TEST_CASE_BASELINE_VMs = "Baseline VMs on AWS ";
    public static final String TEST_CASE_INITIAL_RUN_AT_SCALE = "Initial Run at Scale ";
    public static final String TEST_CASE_DISCOVER_UPDATES_AT_SCALE = "Discover Updates at Scale ";
    public static final String TEST_CASE_DISCOVER_DELETES_AT_SCALE = "Discover Deletes at Scale ";
    public boolean isMock = true;
    public String accessKey = "accessKey";
    public String secretKey = "secretKey";
    public int instanceCountAtScale = 10;
    public int batchSize = 50;
    public int errorRate = 5;
    public int modifyRate = 10;
    public int awsAccountLimit = 1000;
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

            ProvisioningUtils.waitForServiceStart(host, serviceSelfLinks.toArray(new String[] {}));
            // create the compute host, resource pool and the VM state to be used in the test.
            createResourcePoolComputeHost();
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
            // Delete all vms from the endpoint that were created from the test
            host.log("Deleting %d instance created from the test ", instancesToCleanUp.size());
            bounceAWSClient();
            if (instancesToCleanUp.size() > 0) {
                int initialCount = instancesToCleanUp.size() % batchSize > 0
                        ? (instancesToCleanUp.size() % batchSize) : batchSize;
                boolean firstDeletionCycle = true;
                int oldIndex = 0;
                List<String> instanceBatchToDelete = new ArrayList<String>();
                for (int totalDeletedInstances = initialCount; totalDeletedInstances <= instancesToCleanUp
                        .size(); totalDeletedInstances += batchSize) {
                    if (firstDeletionCycle) {
                        instanceBatchToDelete = instancesToCleanUp.subList(0, initialCount);
                        firstDeletionCycle = false;
                    } else {
                        instanceBatchToDelete = instancesToCleanUp.subList(oldIndex,
                                totalDeletedInstances);
                    }
                    oldIndex = totalDeletedInstances;
                    host.log("Deleting %d instances", instanceBatchToDelete.size());
                    deleteAllVMsOnThisEndpoint(host, isMock, outComputeHost.documentSelfLink,
                            instanceBatchToDelete);
                    // Check that all the instances that are required to be deleted are in
                    // terminated state on AWS
                    waitForInstancesToBeTerminated(client, host, instanceBatchToDelete);
                }
            }
            cleanupEC2ClientResources(client);
        } catch (Throwable deleteEx) {
            // just log and move on
            host.log(Level.WARNING, "Exception deleting VMs - %s", deleteEx.getMessage());
        }
    }

    /**
     * Re-initializes the AWS client so that the outstanding memory buffers and threads are released back.
     */
    private void bounceAWSClient() {
        cleanupEC2ClientResources(client);
        client = AWSUtils.getAsyncClient(creds, TestAWSSetupUtils.zoneId,
                isMock, getExecutor());
    }

    @Test
    public void testEnumerationAtScale() throws Throwable {
        if (!isMock) {
            host.setTimeoutSeconds(600);
            baseLineState = getBaseLineInstanceCount(host, client, testComputeDescriptions);
            // Run data collection when there are no resources on the AWS endpoint. Ensure that
            // there are no failures if the number of discovered instances is 0.
            enumerateResources(host, isMock, outPool.documentSelfLink,
                    outComputeHost.descriptionLink, outComputeHost.documentSelfLink,
                    TEST_CASE_BASELINE_VMs);
            // Check if the requested number of instances are under the set account limits
            if ((baseLineState.baselineVMCount + instanceCountAtScale) >= awsAccountLimit) {
                host.log("Requested number of resources will exceed account limit. Reducing number"
                        + " of requested instances");
                instanceCountAtScale = awsAccountLimit - baseLineState.baselineVMCount;
            }
            // Create {instanceCountAtScale} VMs on AWS
            host.log("Running scale test by provisioning %d instances", instanceCountAtScale);
            int initialCount = instanceCountAtScale % batchSize > 0
                    ? (instanceCountAtScale % batchSize) : batchSize;
            boolean firstSpawnCycle = true;
            for (int totalSpawnedInstances = initialCount; totalSpawnedInstances <= instanceCountAtScale; totalSpawnedInstances += batchSize) {
                int instancesToSpawn = batchSize;
                if (firstSpawnCycle) {
                    instancesToSpawn = initialCount;
                    firstSpawnCycle = false;
                }
                instanceIds = provisionAWSVMWithEC2Client(client, host, instancesToSpawn,
                        instanceType_t2_micro);
                instancesToCleanUp.addAll(instanceIds);
                host.log("Instances to cleanup is %d", instancesToCleanUp.size());
                waitForProvisioningToComplete(instanceIds, host, client, errorRate);
                host.log("Instances have turned on");
            }
            enumerateResources(host, isMock, outPool.documentSelfLink,
                    outComputeHost.descriptionLink, outComputeHost.documentSelfLink,
                    TEST_CASE_INITIAL_RUN_AT_SCALE);

            // UPDATE some percent of the spawned instances to have a tag
            int instancesToTagCount = (int) ((instanceCountAtScale / HUNDERED) * modifyRate);
            host.log("Updating %d instances", instancesToTagCount);
            List<String> instanceIdsToTag = instancesToCleanUp.subList(0, instancesToTagCount);
            createNameTagForResources(instanceIdsToTag);
            // Record the time taken to discover updates to a subset of the instances.
            enumerateResources(host, isMock, outPool.documentSelfLink,
                    outComputeHost.descriptionLink, outComputeHost.documentSelfLink,
                    TEST_CASE_DISCOVER_UPDATES_AT_SCALE);

            // DELETE some percent of the instances
            host.log("Deleting %d instances", instancesToTagCount);
            deleteVMsUsingEC2Client(client, host, instanceIdsToTag);
            // Record time spent in enumeration to discover the deleted instances and delete them.
            enumerateResources(host, isMock, outPool.documentSelfLink,
                    outComputeHost.descriptionLink, outComputeHost.documentSelfLink,
                    TEST_CASE_DISCOVER_DELETES_AT_SCALE);
        } else {
            // Do nothing. Basic enumeration logic tested in functional test.
        }
    }

    /**
     * Creates a name tag on AWS for the list of resources that are passed in.
     */
    private void createNameTagForResources(List<String> instanceIdsToTag) {
        Tag awsName = new Tag().withKey(AWS_TAG_NAME).withValue(SCALE_VM_NAME);
        ArrayList<Tag> tags = new ArrayList<>();
        tags.add(awsName);
        createTags(instanceIdsToTag, tags, client);
    }

    /**
     * Creates the state associated with the resource pool, compute host and the VM to be created.
     * @throws Throwable
     */
    public void createResourcePoolComputeHost() throws Throwable {
        // Create a resource pool where the VM will be housed
        outPool = createAWSResourcePool(host);

        // create a compute host for the AWS EC2 VM
        outComputeHost = createAWSComputeHost(host, outPool.documentSelfLink, accessKey, secretKey);
    }
}
