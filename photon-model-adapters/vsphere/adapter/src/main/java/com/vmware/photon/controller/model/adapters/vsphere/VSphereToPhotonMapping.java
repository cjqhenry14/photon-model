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

import com.vmware.photon.controller.model.resources.ComputeService.PowerState;
import com.vmware.vim25.VirtualMachinePowerState;

/**
 * Converts enum values coming from VSphere to photon-model ones.
 */
public class VSphereToPhotonMapping {

    public static PowerState convertPowerState(VirtualMachinePowerState state) {
        if (state == null) {
            return null;
        }

        switch (state) {
        case POWERED_OFF:
            return PowerState.OFF;
        case POWERED_ON:
            return PowerState.ON;
        case SUSPENDED:
            return PowerState.SUSPEND;
        default:
            return PowerState.UNKNOWN;
        }
    }
}
