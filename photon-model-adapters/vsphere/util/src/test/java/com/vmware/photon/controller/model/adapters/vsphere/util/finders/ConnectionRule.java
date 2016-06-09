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

package com.vmware.photon.controller.model.adapters.vsphere.util.finders;

import java.net.URI;

import org.junit.rules.ExternalResource;

import com.vmware.photon.controller.model.adapters.vsphere.util.connection.BasicConnection;
import com.vmware.photon.controller.model.adapters.vsphere.util.connection.Connection;

/**
 */
public class ConnectionRule extends ExternalResource {

    public static final String VC_URL = "vc.url";
    public static final String VC_USERNAME = "vc.username";
    public static final String VC_PASSWORD = "vc.password";

    private BasicConnection connection;

    private static BasicConnection createConnection() {
        if (!shouldRun()) {
            return null;
        }
        BasicConnection c = new BasicConnection();
        c.setIgnoreSslErrors(true);
        c.setURI(URI.create(System.getProperty(VC_URL)));
        c.setUsername(System.getProperty(VC_USERNAME));
        c.setPassword(System.getProperty(VC_PASSWORD));
        return c;
    }

    public static boolean shouldRun() {
        return System.getProperty(VC_URL) != null;
    }

    public Connection get() {
        if (this.connection == null) {
            this.connection = createConnection();
            if (this.connection != null) {
                this.connection.connect();
            }
        }

        return connection;
    }

    @Override
    protected void after() {
        if (this.connection != null) {
            this.connection.close();
        }
    }
}
