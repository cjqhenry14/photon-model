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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.fail;

import java.net.URI;

import org.junit.Test;

import com.vmware.vim25.ConnectedIso;
import com.vmware.vim25.DuplicateName;
import com.vmware.vim25.DuplicateNameFaultMsg;
import com.vmware.vim25.LocalizedMethodFault;
import com.vmware.vim25.ManagedObjectReference;

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

    @Test(expected = IllegalArgumentException.class)
    public void uriToDatastorePathBadSchemeHttp() {
        VimUtils.uriToDatastorePath(URI.create("http://hello/world"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void uriToDatastorePathBadSchemeFile() {
        VimUtils.uriToDatastorePath(URI.create("file://hello/world"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void uriToDatastoreMissingPath() {
        VimUtils.uriToDatastorePath(URI.create("datastore://hello"));
    }

    @Test
    public void uriToDatastoreManySlashes() {
        String s = VimUtils.uriToDatastorePath(URI.create("datastore:////ds1/folder/vm.vmx"));
        assertEquals("[ds1] folder/vm.vmx", s);
    }

    @Test
    public void uriToDatastore() {
        String s = VimUtils.uriToDatastorePath(URI.create("datastore:///ds_test/folder/vm.vmx"));
        assertEquals("[ds_test] folder/vm.vmx", s);
    }

    @Test
    public void uriToDatastoreNull() {
        String s = VimUtils.uriToDatastorePath(null);
        assertNull(s);
    }

    @Test
    public void uriToDatastoreRelativeWithUnderscore() {
        String s = VimUtils.uriToDatastorePath(URI.create("datastore://ds_test/folder/vm.vmx"));
        assertEquals("[ds_test] folder/vm.vmx", s);
    }

    @Test
    public void convertStringToMoRef() {
        ManagedObjectReference ref = new ManagedObjectReference();
        String type = "Datastore";
        String value = "ds-123";

        ref.setType(type);
        ref.setValue(value);
        String s = VimUtils.convertMoRefToString(ref);

        assertEquals(type + ":" + value, s);

        ManagedObjectReference conv = VimUtils.convertStringToMoRef(s);
        assertEquals(type, conv.getType());
        assertEquals(value, conv.getValue());
    }

    @Test(expected = IllegalArgumentException.class)
    public void convertStringToMoRefNoValue() {
        VimUtils.convertStringToMoRef("Datastore:");
    }

    @Test(expected = IllegalArgumentException.class)
    public void convertStringToMoRefNoDelimiter() {
        VimUtils.convertStringToMoRef("Datastore");
    }

    @Test(expected = IllegalArgumentException.class)
    public void convertStringToMoRefTooManyParts() {
        VimUtils.convertStringToMoRef("Datastore:too:many");
    }

    @Test(expected = IllegalArgumentException.class)
    public void convertStringToMoRefNoType() {
        VimUtils.convertStringToMoRef(":ds-123");
    }

    @Test
    public void convertStringToMoRefNulls() {
        assertNull(VimUtils.convertMoRefToString(null));
        assertNull(VimUtils.convertStringToMoRef(null));
    }
}
