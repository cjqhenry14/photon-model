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

import static org.junit.Assert.assertSame;
import static org.junit.Assert.fail;

import org.junit.Test;

import com.vmware.vim25.ConnectedIso;
import com.vmware.vim25.DuplicateName;
import com.vmware.vim25.DuplicateNameFaultMsg;
import com.vmware.vim25.LocalizedMethodFault;

/**
 */
public class VimUtilsTest {

    @Test
    public void retrhowKnownException() {
        DuplicateName dn = new DuplicateName();

        LocalizedMethodFault lmf = new LocalizedMethodFault();
        lmf.setLocalizedMessage("msg");
        lmf.setFault(dn);

        try {
            VimUtils.rethrow(lmf);
            fail();
        } catch (DuplicateNameFaultMsg msg) {
            assertSame(dn, msg.getFaultInfo());
            assertSame(lmf.getLocalizedMessage(), msg.getMessage());
        } catch (Exception e) {
            fail();
        }
    }

    @Test
    public void retrhowUnknownException() {
        ConnectedIso dn = new ConnectedIso();

        LocalizedMethodFault lmf = new LocalizedMethodFault();
        lmf.setLocalizedMessage("msg");
        lmf.setFault(dn);

        try {
            VimUtils.rethrow(lmf);
            fail();
        } catch (GenericVimFault msg) {
            assertSame(dn, msg.getFaultInfo());
            assertSame(lmf.getLocalizedMessage(), msg.getMessage());
        } catch (Exception e) {
            fail();
        }
    }
}
