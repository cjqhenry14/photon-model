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

import java.util.HashMap;
import java.util.Map;

import com.vmware.vim25.DynamicProperty;
import com.vmware.vim25.ManagedObjectReference;
import com.vmware.vim25.ObjectContent;

/**
 * Much like overlays in gwt. Subclasses should define the interesting methods. Getter should fail
 * and not return null to signal that a property was not fetched. Subclasses can relax this on a
 * per-need basis.
 */
public abstract class AbstractOverlay {

    public static final int MB_to_bytes = 1024 * 1024;

    private final ManagedObjectReference ref;

    private final Map<String, Object> props;

    protected AbstractOverlay(ManagedObjectReference ref, Map<String, Object> props) {
        this.ref = ref;
        this.props = props;
    }

    protected AbstractOverlay(ObjectContent cont) {
        this(cont.getObj(), new HashMap<>());

        for (DynamicProperty dp : cont.getPropSet()) {
            this.props.put(dp.getName(), dp.getVal());
        }
    }

    protected Object getOrFail(String name) {
        Object res = this.props.get(name);
        if (res == null) {
            // TODO how to handle null-valued properties
            throw new IllegalArgumentException("property '" + name + "' not fetched");
        }
        return res;
    }

    protected void ensureType(String type) {
        if (!type.equals(this.ref.getType())) {
            String msg = String.format("Cannot overlay type '%s' on top of %s", type, VimUtils
                    .convertMoRefToString(this.ref));
            throw new IllegalArgumentException(msg);
        }
    }

    public ManagedObjectReference getId() {
        return this.ref;
    }
}
