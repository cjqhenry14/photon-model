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

import com.vmware.photon.controller.model.adapters.vsphere.util.VimNames;
import com.vmware.photon.controller.model.adapters.vsphere.util.VimPath;
import com.vmware.vim25.ObjectContent;

public class HostSystemOverlay extends AbstractOverlay {
    protected HostSystemOverlay(ObjectContent cont) {
        super(cont);
        ensureType(VimNames.TYPE_HOST);
    }

    public String getName() {
        return (String) getOrFail("name");
    }

    public int getCoreCount() {
        return (short) getOrFail(VimPath.host_summary_hardware_numCpuCores);
    }

    public int getCpuMhz() {
        return (int) getOrFail(VimPath.host_summary_hardware_cpuMhz);
    }

    public long getTotalMemoryBytes() {
        return (long) getOrFail(VimPath.host_summary_hardware_memorySize);
    }

    public String getHardwareUuid() {
        return (String) getOrFail(VimPath.host_summary_hardware_uuid);
    }
}
