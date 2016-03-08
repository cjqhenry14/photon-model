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

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.vmware.photon.controller.model.adapterapi.ComputeStatsRequest;
import com.vmware.photon.controller.model.adapterapi.ComputeStatsResponse;
import com.vmware.photon.controller.model.resources.ComputeDescriptionFactoryService;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription.ComputeType;
import com.vmware.photon.controller.model.resources.ComputeFactoryService;
import com.vmware.photon.controller.model.resources.ComputeService;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.resources.DiskFactoryService;
import com.vmware.photon.controller.model.resources.DiskService;
import com.vmware.photon.controller.model.resources.DiskService.DiskState;
import com.vmware.photon.controller.model.resources.DiskService.DiskType;
import com.vmware.photon.controller.model.resources.ResourcePoolFactoryService;
import com.vmware.photon.controller.model.resources.ResourcePoolService.ResourcePoolState;
import com.vmware.photon.controller.model.tasks.ProvisionComputeTaskFactoryService;
import com.vmware.photon.controller.model.tasks.ProvisionComputeTaskService;
import com.vmware.photon.controller.model.tasks.ProvisionComputeTaskService.ProvisionComputeTaskState;
import com.vmware.photon.controller.model.tasks.ProvisioningUtils;
import com.vmware.photon.controller.model.tasks.ResourceRemovalTaskFactoryService;
import com.vmware.photon.controller.model.tasks.ResourceRemovalTaskService.ResourceRemovalTaskState;
import com.vmware.photon.controller.model.tasks.TestUtils;

import com.vmware.xenon.common.CommandLineArgumentParser;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.test.VerificationHost;
import com.vmware.xenon.services.common.AuthCredentialsFactoryService;
import com.vmware.xenon.services.common.AuthCredentialsService;
import com.vmware.xenon.services.common.AuthCredentialsService.AuthCredentialsServiceState;
import com.vmware.xenon.services.common.QueryTask;
import com.vmware.xenon.services.common.QueryTask.QuerySpecification;

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
    public String awsEndPoint = "http://ec2.us-east-1.amazonaws.com";
    public String accessKey = "accessKey";
    public String secretKey = "secretKey";
    public String imageId = "ami-0d4cfd66";
    public String securityGroup = "aws-security-group";
    public String instanceType = "t2.micro";
    public String zoneId = "us-east-1";
    public String userData = null;
    public boolean isMock = true;

    private static final String DEFAULT_AUTH_TYPE = "PublicKey";
    private static final String DEFAULT_ROOT_DISK_NAME = "CoreOS root disk";
    private static final String DEFAULT_CONFIG_LABEL = "cidata";
    private static final String DEFAULT_CONFIG_PATH = "user-data";
    private static final String DEFAULT_USER_DATA_FILE = "cloud_config_coreos.yml";
    private static final String DEFAULT_COREOS_USER = "core";
    private static final String DEFAULT_COREOS_PRIVATE_KEY_FILE = "private_coreos.key";

    // fields that are used across method calls, stash them as private fields
    private String resourcePoolId;
    private String parentResourceId;

    @Before
    public void setUp() throws Exception {
        CommandLineArgumentParser.parseFromProperties(this);

        this.host = VerificationHost.create(0);
        try {
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
        this.host.tearDownInProcessPeers();
        this.host.toggleNegativeTestMode(false);
        this.host.tearDown();
    }

    // Creates a AWS instance via a provision task.
    @Test
    public void testProvision() throws Throwable {

        // Create a resource pool where the VM will be housed
        ResourcePoolState outPool =
                createAWSResourcePool();
        this.resourcePoolId = outPool.documentSourceLink;

        // create a compute host for the AWS EC2 VM
        ComputeService.ComputeState outComputeHost =
                createAWSComputeHost();
        parentResourceId = outComputeHost.documentSelfLink;

        // create a AWS VM compute resoruce
        ComputeService.ComputeState outComputeVM = createAWSVMResource();

        // kick off a provision task to do the actual VM creation
        ProvisionComputeTaskState provisionTask = new ProvisionComputeTaskService.ProvisionComputeTaskState();

        provisionTask.computeLink = outComputeVM.documentSelfLink;
        provisionTask.isMockRequest = isMock;
        provisionTask.taskSubStage =
                ProvisionComputeTaskState.SubStage.CREATING_HOST;

        ProvisionComputeTaskService.ProvisionComputeTaskState outTask = TestUtils.doPost(this.host,
                provisionTask,
                ProvisionComputeTaskState.class,
                UriUtils.buildUri(this.host,
                        ProvisionComputeTaskFactoryService.SELF_LINK));

        URI[] uris = { UriUtils.buildUri(this.host, outTask.documentSelfLink) };

        Map<URI, ProvisionComputeTaskService.ProvisionComputeTaskState> tasks =
                this.host.getServiceState(null,
                        ProvisionComputeTaskService.ProvisionComputeTaskState.class, uris);
        ProvisioningUtils.waitForTaskCompletion(this.host, tasks);

        // check that the VM has been created
        ProvisioningUtils.queryComputeInstances(this.host, 2);

        // issue a stats request via the stats adapter
        // if we are not running in mock mode wait 10 minutes for stats
        // to be published
        if (!isMock) {
            Thread.sleep(TimeUnit.MINUTES.toMillis(10));
        }
        issueStatsRequest(outComputeVM);

        // delete vm
        deleteVMs(outComputeVM.documentSelfLink);

        // check that the VMs are gone
        ProvisioningUtils.queryComputeInstances(this.host, 1);
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
                        if (resp.statsList.get(0).statValues.size() != 3) {
                            host.failIteration(new IllegalStateException("incorrect number of metrics received."));
                            return;
                        }
                        if (!resp.statsList.get(0).computeLink.equals(vm.documentSelfLink)) {
                            host.failIteration(new IllegalStateException("Incorrect computeLink returned."));
                            return;
                        }
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
                this.host, AWSUriPaths.AWS_STATS_SERVICE))
                .setBody(statsRequest)
                .setReferer(this.host.getUri()));
    }
    /**
     * Create a compute host description for an AWS instance
     */
    private ComputeService.ComputeState
            createAWSComputeHost() throws Throwable {

        AuthCredentialsServiceState auth = new AuthCredentialsServiceState();
        auth.type = DEFAULT_AUTH_TYPE;
        auth.privateKeyId = accessKey;
        auth.privateKey = secretKey;
        auth.documentSelfLink = UUID.randomUUID().toString();
        TestUtils.doPost(this.host, auth, AuthCredentialsService.AuthCredentialsServiceState.class,
                UriUtils.buildUri(this.host, AuthCredentialsFactoryService.SELF_LINK));
        String authLink = UriUtils.buildUriPath(AuthCredentialsFactoryService.SELF_LINK,
                auth.documentSelfLink);

        ComputeDescriptionService.ComputeDescription awshostDescription =
                new ComputeDescriptionService.ComputeDescription();

        awshostDescription.id = UUID.randomUUID().toString();
        awshostDescription.supportedChildren = new ArrayList<String>();
        awshostDescription.supportedChildren.add(ComputeType.VM_GUEST.name());
        awshostDescription.instanceAdapterReference = UriUtils.buildUri(this.host,
                AWSUriPaths.AWS_INSTANCE_SERVICE);
        awshostDescription.zoneId = zoneId;
        awshostDescription.authCredentialsLink = authLink;
        TestUtils.doPost(this.host, awshostDescription,
                ComputeDescriptionService.ComputeDescription.class,
                UriUtils.buildUri(this.host, ComputeDescriptionFactoryService.SELF_LINK));

        ComputeService.ComputeState awsComputeHost =
                new ComputeService.ComputeState();

        awsComputeHost.id = UUID.randomUUID().toString();
        awsComputeHost.descriptionLink = UriUtils.buildUriPath(
                ComputeDescriptionFactoryService.SELF_LINK, awshostDescription.id);
        awsComputeHost.resourcePoolLink = UriUtils.buildUriPath(
                ResourcePoolFactoryService.SELF_LINK, this.resourcePoolId);

        awsComputeHost.adapterManagementReference = UriUtils.buildUri(awsEndPoint);

        ComputeService.ComputeState returnState = TestUtils.doPost(this.host, awsComputeHost,
                ComputeService.ComputeState.class,
                UriUtils.buildUri(this.host, ComputeFactoryService.SELF_LINK));
        return returnState;
    }

    private ResourcePoolState createAWSResourcePool()
            throws Throwable {
        ResourcePoolState inPool = new ResourcePoolState();
        inPool.name = UUID.randomUUID().toString();
        inPool.id = inPool.name;

        inPool.minCpuCount = 1;
        inPool.minMemoryBytes = 1024;

        ResourcePoolState returnPool =
                TestUtils.doPost(this.host, inPool, ResourcePoolState.class,
                        UriUtils.buildUri(this.host, ResourcePoolFactoryService.SELF_LINK));

        return returnPool;
    }

    /**
     * Create a compute resource for an AWS instance
     */
    private ComputeService.ComputeState createAWSVMResource() throws Throwable {

        // Step 1: Create an auth credential to login to the VM
        AuthCredentialsServiceState auth = new AuthCredentialsServiceState();
        auth.type = DEFAULT_AUTH_TYPE;
        auth.userEmail = DEFAULT_COREOS_USER;
        auth.privateKey = TestUtils.loadTestResource(this.getClass(), DEFAULT_COREOS_PRIVATE_KEY_FILE);
        auth.documentSelfLink = UUID.randomUUID().toString();
        TestUtils.doPost(this.host, auth, AuthCredentialsService.AuthCredentialsServiceState.class,
                UriUtils.buildUri(this.host, AuthCredentialsFactoryService.SELF_LINK));
        String authCredentialsLink = UriUtils.buildUriPath(AuthCredentialsFactoryService.SELF_LINK,
                auth.documentSelfLink);

        // Step 2: Create a VM desc
        ComputeDescriptionService.ComputeDescription awsVMDesc =
                new ComputeDescriptionService.ComputeDescription();

        awsVMDesc.id = UUID.randomUUID().toString();
        awsVMDesc.name = instanceType;

        awsVMDesc.supportedChildren = new ArrayList<String>();
        awsVMDesc.supportedChildren.add(ComputeType.DOCKER_CONTAINER.name());

        awsVMDesc.customProperties = new HashMap<String, String>();
        awsVMDesc.customProperties
                .put(AWSConstants.AWS_SECURITY_GROUP, securityGroup);
        awsVMDesc.environmentName =
                AWSInstanceService.AWS_ENVIRONMENT_NAME;

        // set zone to east
        awsVMDesc.zoneId = zoneId;

        awsVMDesc.authCredentialsLink = authCredentialsLink;

        // set the create service to the aws instance service
        awsVMDesc.instanceAdapterReference = UriUtils.buildUri(this.host,
                AWSUriPaths.AWS_INSTANCE_SERVICE);
        awsVMDesc.statsAdapterReference = UriUtils.buildUri(this.host,
                AWSUriPaths.AWS_STATS_SERVICE);

        ComputeDescriptionService.ComputeDescription vmComputeDesc = TestUtils.doPost(this.host, awsVMDesc,
                ComputeDescriptionService.ComputeDescription.class,
                UriUtils.buildUri(this.host, ComputeDescriptionFactoryService.SELF_LINK));

        // Step 3: create boot disk
        List<String> vmDisks = new ArrayList<String>();
        DiskState rootDisk = new DiskState();
        rootDisk.id = UUID.randomUUID().toString();
        rootDisk.name = DEFAULT_ROOT_DISK_NAME;
        rootDisk.type = DiskType.HDD;
        rootDisk.sourceImageReference = URI.create(imageId);
        rootDisk.bootConfig = new DiskState.BootConfig();
        rootDisk.bootConfig.label = DEFAULT_CONFIG_LABEL;
        DiskState.BootConfig.FileEntry file = new DiskState.BootConfig.FileEntry();
        file.path = DEFAULT_CONFIG_PATH;
        file.contents = TestUtils.loadTestResource(this.getClass(), DEFAULT_USER_DATA_FILE);
        rootDisk.bootConfig.files = new DiskState.BootConfig.FileEntry[]{ file };

        TestUtils.doPost(this.host, rootDisk,
                DiskService.DiskState.class,
                UriUtils.buildUri(this.host, DiskFactoryService.SELF_LINK));
        vmDisks.add(UriUtils.buildUriPath(DiskFactoryService.SELF_LINK, rootDisk.id));

        ComputeService.ComputeState resource = new ComputeService.ComputeState();
        resource.id = UUID.randomUUID().toString();
        resource.parentLink = parentResourceId;
        resource.descriptionLink = vmComputeDesc.documentSelfLink;
        resource.resourcePoolLink = this.resourcePoolId;
        resource.diskLinks = vmDisks;

        ComputeService.ComputeState vmComputeState = TestUtils.doPost(this.host, resource,
                ComputeService.ComputeState.class,
                UriUtils.buildUri(this.host, ComputeFactoryService.SELF_LINK));
        return vmComputeState;
    }

    private void deleteVMs(String documentSelfLink)
            throws Throwable {
        this.host.testStart(1);
        ResourceRemovalTaskState deletionState = new ResourceRemovalTaskState();
        QuerySpecification resourceQuerySpec = new QueryTask.QuerySpecification();
        // query all ComputeState resources for the cluster
        resourceQuerySpec.query
                .setTermPropertyName(ServiceDocument.FIELD_NAME_SELF_LINK)
                .setTermMatchValue(documentSelfLink);
        deletionState.resourceQuerySpec = resourceQuerySpec;
        deletionState.isMockRequest = isMock;
        this.host.send(Operation
                .createPost(
                        UriUtils.buildUri(this.host,
                                ResourceRemovalTaskFactoryService.SELF_LINK))
                .setBody(deletionState)
                .setCompletion(this.host.getCompletion()));

        this.host.testWait();
    }
}