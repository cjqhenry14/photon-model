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
import java.util.UUID;

import com.vmware.photon.controller.model.resources.ComputeDescriptionService;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription.ComputeType;
import com.vmware.photon.controller.model.resources.ComputeService;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.resources.DiskService;
import com.vmware.photon.controller.model.resources.DiskService.DiskState;
import com.vmware.photon.controller.model.resources.DiskService.DiskType;
import com.vmware.photon.controller.model.resources.ResourcePoolService;
import com.vmware.photon.controller.model.resources.ResourcePoolService.ResourcePoolState;
import com.vmware.photon.controller.model.tasks.ProvisioningUtils;
import com.vmware.photon.controller.model.tasks.ResourceRemovalTaskService;
import com.vmware.photon.controller.model.tasks.ResourceRemovalTaskService.ResourceRemovalTaskState;
import com.vmware.photon.controller.model.tasks.TestUtils;

import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.test.VerificationHost;
import com.vmware.xenon.services.common.AuthCredentialsService;
import com.vmware.xenon.services.common.AuthCredentialsService.AuthCredentialsServiceState;
import com.vmware.xenon.services.common.QueryTask;
import com.vmware.xenon.services.common.QueryTask.Query;
import com.vmware.xenon.services.common.QueryTask.QuerySpecification;

public class TestAWSSetupUtils {

    public static final String awsEndPoint = "http://ec2.us-east-1.amazonaws.com";
    public static final String imageId = "ami-0d4cfd66";
    public static final String securityGroup = "aws-security-group";
    public static final String instanceType_t2_micro = "t2.micro";
    public static final String zoneId = "us-east-1";
    public static final String userData = null;
    public static final String aws = "Amazon Web Services";

    public static final String DEFAULT_AUTH_TYPE = "PublicKey";
    public static final String DEFAULT_ROOT_DISK_NAME = "CoreOS root disk";
    public static final String DEFAULT_CONFIG_LABEL = "cidata";
    public static final String DEFAULT_CONFIG_PATH = "user-data";
    public static final String DEFAULT_USER_DATA_FILE = "cloud_config_coreos.yml";
    public static final String DEFAULT_COREOS_USER = "core";
    public static final String DEFAULT_COREOS_PRIVATE_KEY_FILE = "private_coreos.key";

    /**
     * Create a compute host description for an AWS instance
     */
    public static ComputeService.ComputeState createAWSComputeHost(VerificationHost host,
            String resourcePoolLink, String accessKey, String secretKey)
            throws Throwable {

        AuthCredentialsServiceState auth = new AuthCredentialsServiceState();
        auth.type = DEFAULT_AUTH_TYPE;
        auth.privateKeyId = accessKey;
        auth.privateKey = secretKey;
        auth.documentSelfLink = UUID.randomUUID().toString();
        TestUtils.doPost(host, auth, AuthCredentialsService.AuthCredentialsServiceState.class,
                UriUtils.buildUri(host, AuthCredentialsService.FACTORY_LINK));
        String authLink = UriUtils.buildUriPath(AuthCredentialsService.FACTORY_LINK,
                auth.documentSelfLink);

        ComputeDescriptionService.ComputeDescription awshostDescription = new ComputeDescriptionService.ComputeDescription();

        awshostDescription.id = UUID.randomUUID().toString();
        awshostDescription.name = aws;
        awshostDescription.documentSelfLink = awshostDescription.id;
        awshostDescription.supportedChildren = new ArrayList<String>();
        awshostDescription.supportedChildren.add(ComputeType.VM_GUEST.name());
        awshostDescription.instanceAdapterReference = UriUtils.buildUri(host,
                AWSUriPaths.AWS_INSTANCE_SERVICE);
        awshostDescription.enumerationAdapterReference = UriUtils.buildUri(host,
                AWSUriPaths.AWS_ENUMERATION_SERVICE);
        awshostDescription.zoneId = zoneId;
        awshostDescription.authCredentialsLink = authLink;
        TestUtils.doPost(host, awshostDescription,
                ComputeDescriptionService.ComputeDescription.class,
                UriUtils.buildUri(host, ComputeDescriptionService.FACTORY_LINK));


        ComputeService.ComputeState awsComputeHost = new ComputeService.ComputeState();

        awsComputeHost.id = UUID.randomUUID().toString();
        awsComputeHost.documentSelfLink = awsComputeHost.id;
        awsComputeHost.descriptionLink = UriUtils.buildUriPath(
                ComputeDescriptionService.FACTORY_LINK, awshostDescription.id);
        awsComputeHost.resourcePoolLink = resourcePoolLink;

        awsComputeHost.adapterManagementReference = UriUtils.buildUri(awsEndPoint);

        ComputeService.ComputeState returnState = TestUtils.doPost(host, awsComputeHost,
                ComputeService.ComputeState.class,
                UriUtils.buildUri(host, ComputeService.FACTORY_LINK));
        return returnState;
    }

    public static ResourcePoolState createAWSResourcePool(VerificationHost host)
            throws Throwable {
        ResourcePoolState inPool = new ResourcePoolState();
        inPool.name = UUID.randomUUID().toString();
        inPool.id = inPool.name;

        inPool.minCpuCount = 1;
        inPool.minMemoryBytes = 1024;

        ResourcePoolState returnPool = TestUtils.doPost(host, inPool, ResourcePoolState.class,
                UriUtils.buildUri(host, ResourcePoolService.FACTORY_LINK));

        return returnPool;
    }

    /**
     * Create a compute resource for an AWS instance
     */
    public static ComputeService.ComputeState createAWSVMResource(VerificationHost host,
            String parentLink, String resourcePoolLink, @SuppressWarnings("rawtypes") Class clazz)
            throws Throwable {

        // Step 1: Create an auth credential to login to the VM
        AuthCredentialsServiceState auth = new AuthCredentialsServiceState();
        auth.type = DEFAULT_AUTH_TYPE;
        auth.userEmail = DEFAULT_COREOS_USER;
        auth.privateKey = TestUtils.loadTestResource(clazz,
                DEFAULT_COREOS_PRIVATE_KEY_FILE);
        auth.documentSelfLink = UUID.randomUUID().toString();
        TestUtils.doPost(host, auth, AuthCredentialsService.AuthCredentialsServiceState.class,
                UriUtils.buildUri(host, AuthCredentialsService.FACTORY_LINK));
        String authCredentialsLink = UriUtils.buildUriPath(AuthCredentialsService.FACTORY_LINK,
                auth.documentSelfLink);

        // Step 2: Create a VM desc
        ComputeDescriptionService.ComputeDescription awsVMDesc = new ComputeDescriptionService.ComputeDescription();

        awsVMDesc.id = instanceType_t2_micro;
        awsVMDesc.name = instanceType_t2_micro;
        awsVMDesc.documentSelfLink = awsVMDesc.id;

        awsVMDesc.supportedChildren = new ArrayList<String>();
        awsVMDesc.supportedChildren.add(ComputeType.DOCKER_CONTAINER.name());

        awsVMDesc.customProperties = new HashMap<String, String>();
        awsVMDesc.customProperties
                .put(AWSConstants.AWS_SECURITY_GROUP, securityGroup);
        awsVMDesc.environmentName = AWSInstanceService.AWS_ENVIRONMENT_NAME;

        // set zone to east
        awsVMDesc.zoneId = zoneId;

        awsVMDesc.authCredentialsLink = authCredentialsLink;

        // set the create service to the aws instance service
        awsVMDesc.instanceAdapterReference = UriUtils.buildUri(host,
                AWSUriPaths.AWS_INSTANCE_SERVICE);
        awsVMDesc.statsAdapterReference = UriUtils.buildUri(host,
                AWSUriPaths.AWS_STATS_SERVICE);

        ComputeDescriptionService.ComputeDescription vmComputeDesc = TestUtils.doPost(host,
                awsVMDesc,
                ComputeDescriptionService.ComputeDescription.class,
                UriUtils.buildUri(host, ComputeDescriptionService.FACTORY_LINK));
        // Step 3: create boot disk
        List<String> vmDisks = new ArrayList<String>();
        DiskState rootDisk = new DiskState();
        rootDisk.id = UUID.randomUUID().toString();
        rootDisk.documentSelfLink = rootDisk.id;
        rootDisk.name = DEFAULT_ROOT_DISK_NAME;
        rootDisk.type = DiskType.HDD;
        rootDisk.sourceImageReference = URI.create(imageId);
        rootDisk.bootConfig = new DiskState.BootConfig();
        rootDisk.bootConfig.label = DEFAULT_CONFIG_LABEL;
        DiskState.BootConfig.FileEntry file = new DiskState.BootConfig.FileEntry();
        file.path = DEFAULT_CONFIG_PATH;
        file.contents = TestUtils.loadTestResource(clazz, DEFAULT_USER_DATA_FILE);
        rootDisk.bootConfig.files = new DiskState.BootConfig.FileEntry[] { file };

        TestUtils.doPost(host, rootDisk,
                DiskService.DiskState.class,
                UriUtils.buildUri(host, DiskService.FACTORY_LINK));
        vmDisks.add(UriUtils.buildUriPath(DiskService.FACTORY_LINK, rootDisk.id));

        ComputeService.ComputeState resource = new ComputeService.ComputeState();
        resource.id = UUID.randomUUID().toString();
        resource.parentLink = parentLink;
        resource.descriptionLink = vmComputeDesc.documentSelfLink;
        resource.resourcePoolLink = resourcePoolLink;
        resource.diskLinks = vmDisks;

        ComputeService.ComputeState vmComputeState = TestUtils.doPost(host, resource,
                ComputeService.ComputeState.class,
                UriUtils.buildUri(host, ComputeService.FACTORY_LINK));
        return vmComputeState;
    }

    public static void deleteVMs(String documentSelfLink, boolean isMock, VerificationHost host)
            throws Throwable {
        host.testStart(1);
        ResourceRemovalTaskState deletionState = new ResourceRemovalTaskState();
        QuerySpecification resourceQuerySpec = new QueryTask.QuerySpecification();
        // query all ComputeState resources for the cluster
        resourceQuerySpec.query
                .setTermPropertyName(ServiceDocument.FIELD_NAME_SELF_LINK)
                .setTermMatchValue(documentSelfLink);
        deletionState.resourceQuerySpec = resourceQuerySpec;
        deletionState.isMockRequest = isMock;
        host.send(Operation
                .createPost(
                        UriUtils.buildUri(host,
                                ResourceRemovalTaskService.FACTORY_LINK))
                .setBody(deletionState)
                .setCompletion(host.getCompletion()));
        host.testWait();
        // check that the VMs are gone
        ProvisioningUtils.queryComputeInstances(host, 1);
    }

    /**
     * A utility method that deletes all the VMs that exist in the system
     * @throws Throwable
     */
    public static void deleteAllVMsOnThisEndpoint(VerificationHost host, boolean isMock, String parentComputeLink)
            throws Throwable {
        host.testStart(1);
        ResourceRemovalTaskState deletionState = new ResourceRemovalTaskState();
        QuerySpecification querySpec = new QueryTask.QuerySpecification();
        querySpec.query = Query.Builder.create()
                .addKindFieldClause(ComputeService.ComputeState.class)
                .addFieldClause(ComputeState.FIELD_NAME_PARENT_LINK,
                        parentComputeLink)
                .build();
        deletionState.resourceQuerySpec = querySpec;
        deletionState.isMockRequest = isMock;
        host.send(Operation
                .createPost(
                        UriUtils.buildUri(host,
                                ResourceRemovalTaskService.FACTORY_LINK))
                .setBody(deletionState)
                .setCompletion(host.getCompletion()));
        host.testWait();
    }

}
