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

import com.vmware.photon.controller.model.adapters.vsphere.util.connection.BasicConnection;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.resources.ResourcePoolService;
import com.vmware.photon.controller.model.resources.ResourcePoolService.ResourcePoolState;
import com.vmware.photon.controller.model.tasks.ProvisionComputeTaskService;
import com.vmware.photon.controller.model.tasks.ProvisionComputeTaskService.ProvisionComputeTaskState;
import com.vmware.photon.controller.model.tasks.ProvisioningUtils;
import com.vmware.photon.controller.model.tasks.TestUtils;
import com.vmware.xenon.common.BasicReusableHostTestCase;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.services.common.AuthCredentialsService;
import com.vmware.xenon.services.common.AuthCredentialsService.AuthCredentialsServiceState;

public class BaseVSphereAdapterTest extends BasicReusableHostTestCase {

    public static final String DEFAULT_AUTH_TYPE = "Username/Password";

    public String vcUrl;
    public String vcUsername = System.getProperty(TestProperties.VC_USERNAME);
    public String vcPassword = System.getProperty(TestProperties.VC_PASSWORD);

    public String zoneId = System.getProperty(TestProperties.VC_ZONE_ID);
    public String dataStoreId = System.getProperty(TestProperties.VC_DATASTORE_ID);
    public String networkId = System.getProperty(TestProperties.VC_NETWORK_ID);

    public String vcFolder = System.getProperty("vc.folder");

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

    public boolean isMock() {
        return vcUsername == null || vcUsername.length() == 0;
    }

    public BasicConnection createConnection() {
        if (isMock()) {
            throw new IllegalStateException("Cannot create connection in while mock is true");
        }

        BasicConnection connection = new BasicConnection();
        connection.setIgnoreSslErrors(true);
        connection.setUsername(vcUsername);
        connection.setPassword(vcPassword);
        connection.setURI(URI.create(vcUrl));
        connection.connect();
        return connection;
    }

    protected ProvisionComputeTaskState createProvisionTask(ComputeState vm) throws Throwable {
        ProvisionComputeTaskState provisionTask = new ProvisionComputeTaskState();

        provisionTask.computeLink = vm.documentSelfLink;
        provisionTask.isMockRequest = isMock();
        provisionTask.taskSubStage = ProvisionComputeTaskState.SubStage.CREATING_HOST;

        ProvisionComputeTaskState outTask = TestUtils.doPost(this.host,
                provisionTask,
                ProvisionComputeTaskState.class,
                UriUtils.buildUri(this.host,
                        ProvisionComputeTaskService.FACTORY_LINK));

        return outTask;
    }

    protected ComputeState getComputeState(ComputeState vm) throws Throwable {
        return host.getServiceState(null, ComputeState.class,
                UriUtils.buildUri(host, vm.documentSelfLink));
    }

    protected ResourcePoolState createResourcePool()
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

    protected void awaitTaskEnd(ProvisionComputeTaskState outTask) throws Throwable {
        List<URI> uris = new ArrayList<>();
        uris.add(UriUtils.buildUri(this.host, outTask.documentSelfLink));
        ProvisioningUtils.waitForTaskCompletion(this.host, uris, ProvisionComputeTaskState.class);
    }

    protected AuthCredentialsServiceState createAuth() throws Throwable {
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
}
