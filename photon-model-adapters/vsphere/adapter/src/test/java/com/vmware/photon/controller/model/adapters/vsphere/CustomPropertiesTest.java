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

import org.junit.Test;

import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.vim25.ManagedObjectReference;

/**
 */
public class CustomPropertiesTest {

    @Test(expected = IllegalArgumentException.class)
    public void putOnNullState() {
        CustomProperties.of( null);
    }

    @Test
    public void putAndGetState() {
        ManagedObjectReference ref = new ManagedObjectReference();
        ref.setType("type");
        ref.setValue("v1");

        ComputeState state = new ComputeState();
        CustomProperties.of(state)
                .put("12", 12)
                .put("s", "s")
                .put("4", 4L)
                .put("ref", ref);

        assertEquals("12", state.customProperties.get("12"));
        assertEquals("4", state.customProperties.get("4"));
        assertEquals("s", state.customProperties.get("s"));
        assertEquals(VimUtils.convertMoRefToString(ref), state.customProperties.get("ref"));

        CustomProperties access = CustomProperties.of(state);

        assertEquals((Integer) 12, access.getInt("12", null));
        assertEquals("s", access.getString("s"));

        assertEquals(Long.valueOf(4), access.getLong("4", null));
        assertMoFefEquals(ref, access.getMoRef("ref"));
    }

    @Test
    public void putAndGetDescription() {
        ManagedObjectReference ref = new ManagedObjectReference();
        ref.setType("type");
        ref.setValue("v1");

        ComputeDescription desc = new ComputeDescription();
        CustomProperties.of(desc)
                .put("12", 12)
                .put("s", "s")
                .put("4", 4L)
                .put("ref", ref);

        assertEquals("12", desc.customProperties.get("12"));
        assertEquals("4", desc.customProperties.get("4"));
        assertEquals("s", desc.customProperties.get("s"));
        assertEquals(VimUtils.convertMoRefToString(ref), desc.customProperties.get("ref"));

        CustomProperties access = CustomProperties.of(desc);

        assertEquals((Integer) 12, access.getInt("12", null));
        assertEquals("s", access.getString("s"));

        assertEquals(Long.valueOf(4), access.getLong("4", null));
        assertMoFefEquals(ref, access.getMoRef("ref"));
    }

    private void assertMoFefEquals(ManagedObjectReference ref1, ManagedObjectReference ref2) {
        assertEquals(ref1.getValue(), ref2.getValue());
        assertEquals(ref1.getType(), ref2.getType());
    }
}
