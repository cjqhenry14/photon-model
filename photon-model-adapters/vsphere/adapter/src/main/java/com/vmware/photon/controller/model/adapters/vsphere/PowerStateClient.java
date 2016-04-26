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

import com.vmware.photon.controller.model.adapters.vsphere.util.connection.BaseHelper;
import com.vmware.photon.controller.model.adapters.vsphere.util.connection.Connection;
import com.vmware.photon.controller.model.adapters.vsphere.util.connection.GetMoRef;
import com.vmware.photon.controller.model.resources.ComputeService.PowerState;
import com.vmware.photon.controller.model.resources.ComputeService.PowerTransition;
import com.vmware.vim25.InvalidPropertyFaultMsg;
import com.vmware.vim25.ManagedObjectReference;
import com.vmware.vim25.RuntimeFaultFaultMsg;
import com.vmware.vim25.TaskInProgressFaultMsg;
import com.vmware.vim25.TaskInfo;
import com.vmware.vim25.TaskInfoState;
import com.vmware.vim25.VirtualMachinePowerState;

/**
 * Manages power state of VMs.
 */
public class PowerStateClient extends BaseHelper {

    private final GetMoRef get;

    public PowerStateClient(Connection connection) {
        super(connection);
        this.get = new GetMoRef(connection);
    }

    public void changePowerState(ManagedObjectReference vm, PowerState targetState,
            PowerTransition transition) throws Exception {

        PowerState currentState = getPowerState(vm);

        if (currentState == targetState) {
            return;
        }

        ManagedObjectReference task;

        try {
            // TODO https://jira-hzn.eng.vmware.com/browse/VSYM-512 respect the transition param
            if (targetState == PowerState.ON) {
                task = getVimPort().powerOnVMTask(vm, null);
            } else {
                task = getVimPort().powerOffVMTask(vm);
            }
        } catch (TaskInProgressFaultMsg e) {
            // task in progress, give up
            // TODO maybe retry after a timeout
            return;
        }

        TaskInfo taskInfo = VimUtils.waitTaskEnd(this.connection, task);
        if (taskInfo.getState() == TaskInfoState.ERROR) {
            VimUtils.rethrow(taskInfo.getError());
        }
    }

    public PowerState getPowerState(ManagedObjectReference vm)
            throws InvalidPropertyFaultMsg, RuntimeFaultFaultMsg {
        VirtualMachinePowerState vmps = get.entityProp(vm, "summary.runtime.powerState");
        return VSphereToPhotonMapping.convertPowerState(vmps);
    }
}
