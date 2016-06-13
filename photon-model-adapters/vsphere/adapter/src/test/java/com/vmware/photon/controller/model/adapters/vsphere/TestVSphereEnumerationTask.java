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

package com.vmware.photon.controller.model.adapters.vsphere;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import com.vmware.photon.controller.model.adapterapi.EnumerationAction;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription.ComputeType;
import com.vmware.photon.controller.model.resources.ComputeService;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.resources.ResourcePoolService;
import com.vmware.photon.controller.model.resources.ResourcePoolService.ResourcePoolState;
import com.vmware.photon.controller.model.tasks.ProvisioningUtils;
import com.vmware.photon.controller.model.tasks.ResourceEnumerationTaskService;
import com.vmware.photon.controller.model.tasks.ResourceEnumerationTaskService.ResourceEnumerationTaskState;
import com.vmware.photon.controller.model.tasks.TestUtils;
import com.vmware.xenon.common.BasicReusableHostTestCase;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.services.common.AuthCredentialsService;
import com.vmware.xenon.services.common.AuthCredentialsService.AuthCredentialsServiceState;

/**
 *
 */
public class TestVSphereEnumerationTask extends BasicReusableHostTestCase {

    private static final String DEFAULT_AUTH_TYPE = "Username/Password";

    public String vcUrl;
    public String vcUsername = System.getProperty(TestProperties.VC_USERNAME);
    public String vcPassword = System.getProperty(TestProperties.VC_PASSWORD);

    public String zoneId = System.getProperty(TestProperties.VC_ZONE_ID);
    public String dataStoreId = System.getProperty(TestProperties.VC_DATASTORE_ID);
    public String networkId = System.getProperty(TestProperties.VC_NETWORK_ID);

    // fields that are used across method calls, stash them as private fields
    private ResourcePoolState resourcePool;

    private AuthCredentialsServiceState auth;
    private ComputeDescription computeHostDescription;
    private ComputeState computeHost;
    private ComputeDescription vmDescription;

    @Before
    public void setUp() throws Throwable {
        ProvisioningUtils.startProvisioningServices(this.host);
        this.host.setTimeoutSeconds(600);
        List<String> serviceSelfLinks = new ArrayList<>();

        host.startService(
                Operation.createPost(UriUtils.buildUri(host,
                        VSphereAdapterInstanceService.class)),
                new VSphereAdapterInstanceService());

        host.startService(
                Operation.createPost(UriUtils.buildUri(host,
                        VSphereAdapterPowerService.class)),
                new VSphereAdapterPowerService());

        host.startService(
                Operation.createPost(UriUtils.buildUri(host,
                        VSphereAdapterSnapshotService.class)),
                new VSphereAdapterSnapshotService());

        host.startService(
                Operation.createPost(UriUtils.buildUri(host,
                        VSphereAdapterResourceEnumerationService.class)),
                new VSphereAdapterResourceEnumerationService());

        serviceSelfLinks.add(VSphereAdapterInstanceService.SELF_LINK);
        serviceSelfLinks.add(VSphereAdapterPowerService.SELF_LINK);
        serviceSelfLinks.add(VSphereAdapterSnapshotService.SELF_LINK);
        serviceSelfLinks.add(VSphereAdapterResourceEnumerationService.SELF_LINK);

        ProvisioningUtils.waitForServiceStart(host, serviceSelfLinks.toArray(new String[] {}));

        vcUrl = System.getProperty("vc.url");
        if (vcUrl == null) {
            vcUrl = "http://not-configured";
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

    private ResourcePoolState createResourcePool()
            throws Throwable {
        ResourcePoolState inPool = new ResourcePoolState();
        inPool.name = "resourcePool-" + UUID.randomUUID().toString();
        inPool.id = inPool.name;

        inPool.minCpuCount = 1;
        inPool.minMemoryBytes = 1024;

        ResourcePoolState returnPool =
                TestUtils.doPost(this.host, inPool, ResourcePoolState.class,
                        UriUtils.buildUri(this.host, ResourcePoolService.FACTORY_LINK));

        return returnPool;
    }

    @Ignore
    @Test
    public void testRefresh() throws Throwable {
        // Create a resource pool where the VM will be housed
        resourcePool = createResourcePool();

        auth = createAuth();

        computeHostDescription = createComputeDescription();
        computeHost = createComputeHost();

        vmDescription = createVmDescription();

        doRefresh();

        Thread.sleep(2000);

        // do a second refresh to test update path
        doRefresh();
    }

    private void doRefresh() throws Throwable {
        ResourceEnumerationTaskState task = new ResourceEnumerationTaskState();
        task.adapterManagementReference = computeHost.adapterManagementReference;
        task.computeDescriptionLink = vmDescription.documentSelfLink;

        task.isMockRequest = isMock();
        task.enumerationAction = EnumerationAction.REFRESH;
        task.parentComputeLink = computeHost.documentSelfLink;
        task.resourcePoolLink = resourcePool.documentSelfLink;

        ResourceEnumerationTaskState outTask = TestUtils.doPost(this.host,
                task,
                ResourceEnumerationTaskState.class,
                UriUtils.buildUri(this.host,
                        ResourceEnumerationTaskService.FACTORY_LINK));

        List<URI> uris = new ArrayList<>();
        uris.add(UriUtils.buildUri(this.host, outTask.documentSelfLink));

        ProvisioningUtils
                .waitForTaskCompletion(this.host, uris, ResourceEnumerationTaskState.class);
    }

    private ComputeDescription createVmDescription() throws Throwable {
        ComputeDescription computeDesc = new ComputeDescription();

        computeDesc.id = "description-for-refresh";
        computeDesc.documentSelfLink = computeDesc.id;
        computeDesc.supportedChildren = new ArrayList<>();
        computeDesc.instanceAdapterReference = UriUtils
                .buildUri(this.host, VSphereUriPaths.INSTANCE_SERVICE);

        computeDesc.enumerationAdapterReference = UriUtils
                .buildUri(this.host, VSphereUriPaths.ENUMERATION_SERVICE);
        computeDesc.authCredentialsLink = this.auth.documentSelfLink;
        computeDesc.name = computeDesc.id;
        computeDesc.dataStoreId = dataStoreId;
        computeDesc.networkId = networkId;

        return TestUtils.doPost(this.host, computeDesc,
                ComputeDescription.class,
                UriUtils.buildUri(this.host, ComputeDescriptionService.FACTORY_LINK));
    }

    /**
     * Create a compute host representing a vcenter server
     */
    private ComputeState createComputeHost() throws Throwable {
        ComputeState computeState = new ComputeState();
        computeState.id = UUID.randomUUID().toString();
        computeState.documentSelfLink = computeState.id;
        computeState.descriptionLink = computeHostDescription.documentSelfLink;
        computeState.resourcePoolLink = this.resourcePool.documentSelfLink;
        computeState.adapterManagementReference = UriUtils.buildUri(vcUrl);

        ComputeState returnState = TestUtils.doPost(this.host, computeState,
                ComputeState.class,
                UriUtils.buildUri(this.host, ComputeService.FACTORY_LINK));
        return returnState;
    }

    private AuthCredentialsServiceState createAuth() throws Throwable {
        AuthCredentialsServiceState auth = new AuthCredentialsServiceState();
        auth.type = DEFAULT_AUTH_TYPE;
        auth.privateKeyId = vcUsername;
        auth.privateKey = vcPassword;
        auth.documentSelfLink = UUID.randomUUID().toString();

        AuthCredentialsServiceState result = TestUtils
                .doPost(this.host, auth, AuthCredentialsServiceState.class,
                        UriUtils.buildUri(this.host, AuthCredentialsService.FACTORY_LINK));
        return result;
    }

    private ComputeDescription createComputeDescription() throws Throwable {
        ComputeDescription computeDesc = new ComputeDescription();

        computeDesc.id = UUID.randomUUID().toString();
        computeDesc.documentSelfLink = computeDesc.id;
        computeDesc.supportedChildren = new ArrayList<>();
        computeDesc.supportedChildren.add(ComputeType.VM_GUEST.name());
        computeDesc.instanceAdapterReference = UriUtils
                .buildUri(this.host, VSphereUriPaths.INSTANCE_SERVICE);

        computeDesc.enumerationAdapterReference = UriUtils
                .buildUri(this.host, VSphereUriPaths.ENUMERATION_SERVICE);
        computeDesc.authCredentialsLink = this.auth.documentSelfLink;

        computeDesc.zoneId = zoneId;

        return TestUtils.doPost(this.host, computeDesc,
                ComputeDescription.class,
                UriUtils.buildUri(this.host, ComputeDescriptionService.FACTORY_LINK));
    }

    public boolean isMock() {
        return vcUsername == null || vcUsername.length() == 0;
    }
}
