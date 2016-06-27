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

import com.vmware.photon.controller.model.adapters.vsphere.util.VimPath;
import com.vmware.vim25.ObjectContent;

public class ResourcePoolOverlay extends AbstractOverlay {
    private static final int MB_to_bytes = 1024 * 1024;

    public ResourcePoolOverlay(ObjectContent cont) {
        super(cont);
    }

    public String getName() {
        return (String) getOrFail("name");
    }

    public long getMemoryLimitBytes() {
        return ((Long) getOrFail(VimPath.rp_summary_config_memoryAllocation_limit))
                * MB_to_bytes;
    }

    public long getMemoryReservationBytes() {
        return ((Long) getOrFail(VimPath.rp_summary_config_memoryAllocation_reservation))
                * MB_to_bytes;
    }
}
