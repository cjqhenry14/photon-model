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

import com.vmware.vim25.ManagedObjectReference;
import com.vmware.xenon.common.Utils;

/**
 * Represents a MoRef and a the full path to the object in terms of names of the parent managed
 * entities.
 */
public class Element {
    public final String path;

    public final ManagedObjectReference object;

    private Element(String path, ManagedObjectReference object) {
        this.path = path;
        this.object = object;
    }

    public static Element make(ManagedObjectReference object, String fullPath) {
        return new Element(fullPath, object);
    }

    public static Element make(ManagedObjectReference object, String name, String path) {
        if (!path.endsWith("/") && !name.startsWith("/")) {
            path += "/";
        }
        return new Element(path + name, object);
    }

    public static Element asRoot(ManagedObjectReference rootFolder) {
        return new Element("/", rootFolder);
    }

    @Override
    public String toString() {
        return Utils.toJson(this);
    }
}