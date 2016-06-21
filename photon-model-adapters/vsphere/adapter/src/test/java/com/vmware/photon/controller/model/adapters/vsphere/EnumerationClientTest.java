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
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import org.junit.Test;

import com.vmware.photon.controller.model.adapters.vsphere.util.VimNames;
import com.vmware.photon.controller.model.adapters.vsphere.util.connection.BasicConnection;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeStateWithDescription;
import com.vmware.vim25.ObjectContent;
import com.vmware.vim25.PropertyFilterSpec;

/**
 */
public class EnumerationClientTest {
    Logger logger = Logger.getLogger(EnumerationClientTest.class.getName());

    @Test
    public void test() throws Exception {
        String url = System.getProperty(TestProperties.VC_URL);

        if (url == null) {
            return;
        }

        String username = System.getProperty(TestProperties.VC_USERNAME);
        String password = System.getProperty(TestProperties.VC_PASSWORD);

        BasicConnection conn = new BasicConnection();

        conn.setURI(URI.create(url));
        conn.setUsername(username);
        conn.setPassword(password);
        conn.setIgnoreSslErrors(true);

        conn.setRequestTimeout(30, TimeUnit.SECONDS);
        conn.connect();

        ComputeStateWithDescription parent = new ComputeStateWithDescription();
        ComputeDescription desc = new ComputeDescription();
        parent.description = desc;

        EnumerationClient client = new EnumerationClient(conn, parent);

        PropertyFilterSpec spec = client.createFullFilterSpec();

        for (List<ObjectContent> page : client.retrieveObjects(spec)) {
            this.logger.info("***");

            for (ObjectContent cont : page) {
                if (cont.getObj().getType().equals(VimNames.TYPE_VM)) {
                    VmOverlay vm = new VmOverlay(cont);
                    this.logger.info(vm.getName());
                    this.logger.info(vm.getInstanceUuid());
                    this.logger.info(vm.getDescriptionLink());
                    this.logger.info(vm.getParentLink());
                    this.logger.info("" + vm.getPowerState());
                }
            }
        }
    }
}
