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

import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.runner.RunWith;

import com.vmware.photon.controller.model.adapters.vsphere.util.connection.Connection;
import com.vmware.vim25.InvalidPropertyFaultMsg;
import com.vmware.vim25.ManagedObjectReference;
import com.vmware.vim25.RuntimeFaultFaultMsg;

/**
 */
@RunWith(AutoIgnoreRunner.class)
public class FinderTest {

    @ClassRule
    public static ConnectionRule rule = new ConnectionRule();

    private Connection connection;

    @Before
    public void setup() {
        connection = rule.get();
    }

    @Test
    public void fullPath()
            throws InvalidPropertyFaultMsg, FinderException, RuntimeFaultFaultMsg {
        Finder finder = new Finder(connection, "/Datacenters/New Folder/MyDatacenter");

        ManagedObjectReference vm = new ManagedObjectReference();
        vm.setType("VirtualMachine");
        vm.setValue("vm-9274");
        Element ele =  finder.fullPath(vm);
        System.out.println(ele);
    }

    @Test
    public void find()
            throws InvalidPropertyFaultMsg, FinderException, RuntimeFaultFaultMsg {
        Finder finder = new Finder(connection, "/Datacenters/New Folder/MyDatacenter");

        Element rp = finder.defaultResourcePool();
        System.out.println(rp);
    }
}
