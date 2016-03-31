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

import org.junit.Ignore;
import org.junit.Test;

import com.vmware.photon.controller.model.adapters.vsphere.util.connection.BasicConnection;
import com.vmware.photon.controller.model.adapters.vsphere.util.connection.helpers.GetMoRef;
import com.vmware.vim25.AboutInfo;
import com.vmware.vim25.InvalidPropertyFaultMsg;
import com.vmware.vim25.ManagedObjectReference;
import com.vmware.vim25.RuntimeFaultFaultMsg;
import com.vmware.xenon.common.Utils;

@Ignore
public class SanityCheckTest {
    @Test
    public void example() throws InvalidPropertyFaultMsg, RuntimeFaultFaultMsg {
        BasicConnection conn = new BasicConnection();

        String url = System.getProperty("vsphere.url");
        String username = System.getProperty("vsphere.username");
        String password = System.getProperty("vsphere.password");

        conn.setUrl(url);
        conn.setUsername(username);
        conn.setPassword(password);
        conn.setIgnoreSslErrors(true);

        conn.connect();

        AboutInfo about = conn.getServiceContent().getAbout();
        System.out.println(Utils.toJsonHtml(about));

        ManagedObjectReference rootFolder = conn.getServiceContent().getRootFolder();

        GetMoRef getMoRef = new GetMoRef(conn);

        String name = getMoRef.entityProp(rootFolder, "name");
        System.out.println("Root folder is called \'" + name + "\'");
    }
}
