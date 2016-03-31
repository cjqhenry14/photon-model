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

package com.vmware.photon.controller.model.adapters.vsphere.util.connection.helpers.builders;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;

import com.vmware.vim25.PropertySpec;

/**
 * a simple builder that creates a property spec
 */
public class PropertySpecBuilder extends PropertySpec {
    private void init() {
        if (this.pathSet == null) {
            this.pathSet = new ArrayList<String>();
        }
    }

    public PropertySpecBuilder all(final Boolean all) {
        this.setAll(all);
        return this;
    }

    public PropertySpecBuilder type(final String type) {
        this.setType(type);
        return this;
    }

    public PropertySpecBuilder pathSet(final String... paths) {
        init();
        this.pathSet.addAll(Arrays.asList(paths));
        return this;
    }

    public PropertySpecBuilder addToPathSet(final Collection<String> paths) {
        init();
        this.pathSet.addAll(paths);
        return this;
    }
}
