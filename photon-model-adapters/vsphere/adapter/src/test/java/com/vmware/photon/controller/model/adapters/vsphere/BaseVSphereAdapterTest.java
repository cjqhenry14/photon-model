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

import org.junit.After;
import org.junit.Before;

import com.vmware.photon.controller.model.adapters.vsphere.util.connection.BasicConnection;
import com.vmware.photon.controller.model.tasks.ProvisioningUtils;
import com.vmware.xenon.common.BasicReusableHostTestCase;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.UriUtils;

public class BaseVSphereAdapterTest extends BasicReusableHostTestCase {

    public static final String DEFAULT_AUTH_TYPE = "Username/Password";

    public String vcUrl;
    public String vcUsername = System.getProperty(TestProperties.VC_USERNAME);
    public String vcPassword = System.getProperty(TestProperties.VC_PASSWORD);

    public String zoneId = System.getProperty(TestProperties.VC_ZONE_ID);
    public String dataStoreId = System.getProperty(TestProperties.VC_DATASTORE_ID);
    public String networkId = System.getProperty(TestProperties.VC_NETWORK_ID);

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
}
