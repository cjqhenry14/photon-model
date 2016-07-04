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

import java.util.Map;

import com.vmware.photon.controller.model.adapters.vsphere.util.VimNames;
import com.vmware.photon.controller.model.adapters.vsphere.util.VimPath;
import com.vmware.photon.controller.model.resources.ComputeService.PowerState;

import com.vmware.vim25.ArrayOfOptionValue;
import com.vmware.vim25.ArrayOfVirtualDevice;
import com.vmware.vim25.ManagedObjectReference;
import com.vmware.vim25.ObjectContent;
import com.vmware.vim25.OptionValue;
import com.vmware.vim25.VirtualDevice;
import com.vmware.vim25.VirtualEthernetCard;
import com.vmware.vim25.VirtualMachinePowerState;

/**
 * Type-safe wrapper of a VM represented by a set of fetched properties.
 */
public class VmOverlay extends AbstractOverlay {

    public VmOverlay(ObjectContent cont) {
        super(cont);
        ensureType(VimNames.TYPE_VM);
    }

    public VmOverlay(ManagedObjectReference ref, Map<String, Object> props) {
        super(ref, props);
        ensureType(VimNames.TYPE_VM);
    }

    public PowerState getPowerState() {
        return VSphereToPhotonMapping.convertPowerState(
                (VirtualMachinePowerState) getOrFail(VimPath.vm_runtime_powerState));
    }

    public String getInstanceUuid() {
        return (String) getOrFail(VimPath.vm_config_instanceUuid);
    }

    public String getName() {
        return (String) getOrFail(VimPath.vm_config_name);
    }

    public String getParentLink() {
        ArrayOfOptionValue arr = (ArrayOfOptionValue) getOrFail(VimPath.vm_config_extraConfig);
        for (OptionValue ov : arr.getOptionValue()) {
            if (InstanceClient.CONFIG_PARENT_LINK.equals(ov.getKey())) {
                return (String) ov.getValue();
            }
        }

        return null;
    }

    public boolean isTempalte() {
        return (boolean) getOrFail(VimPath.vm_config_template);
    }

    public String getDescriptionLink() {
        ArrayOfOptionValue arr = (ArrayOfOptionValue) getOrFail(VimPath.vm_config_extraConfig);
        for (OptionValue ov : arr.getOptionValue()) {
            if (InstanceClient.CONFIG_DESC_LINK.equals(ov.getKey())) {
                return (String) ov.getValue();
            }
        }

        return null;
    }

    public String getPrimaryMac() {
        ArrayOfVirtualDevice devices = (ArrayOfVirtualDevice) getOrFail(
                VimPath.vm_config_hardware_device);
        for (VirtualDevice dev : devices.getVirtualDevice()) {
            if (dev instanceof VirtualEthernetCard) {
                return ((VirtualEthernetCard) dev).getMacAddress();
            }
        }

        return null;
    }
}
