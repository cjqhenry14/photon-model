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

import java.util.concurrent.TimeUnit;

import com.vmware.photon.controller.model.adapters.vsphere.util.connection.BaseHelper;
import com.vmware.photon.controller.model.adapters.vsphere.util.connection.Connection;
import com.vmware.photon.controller.model.adapters.vsphere.util.connection.GetMoRef;
import com.vmware.photon.controller.model.adapters.vsphere.util.connection.WaitForValues;
import com.vmware.photon.controller.model.resources.ComputeService.PowerState;
import com.vmware.photon.controller.model.resources.ComputeService.PowerTransition;
import com.vmware.vim25.InvalidPropertyFaultMsg;
import com.vmware.vim25.ManagedObjectReference;
import com.vmware.vim25.RuntimeFaultFaultMsg;
import com.vmware.vim25.TaskInfo;
import com.vmware.vim25.TaskInfoState;
import com.vmware.vim25.ToolsUnavailableFaultMsg;
import com.vmware.vim25.VirtualMachinePowerState;
import com.vmware.xenon.common.Utils;

/**
 * Manages power state of VMs.
 */
public class PowerStateClient extends BaseHelper {

    private final GetMoRef get;

    public PowerStateClient(Connection connection) {
        super(connection);
        this.get = new GetMoRef(connection);
    }

    /**
     *
     * @param vm
     * @param targetState
     * @param transition
     * @param politenessDeadlineMicros Used only when doing a soft power-off,
     *         how long to be polite before doing hard power-off.
     * @throws Exception
     */
    public void changePowerState(ManagedObjectReference vm, PowerState targetState,
            PowerTransition transition, long politenessDeadlineMicros) throws Exception {
        PowerState currentState = getPowerState(vm);

        if (currentState == targetState) {
            return;
        }

        ManagedObjectReference task;

        if (targetState == PowerState.ON) {
            task = getVimPort().powerOnVMTask(vm, null);
            awaitTaskEnd(task);
            return;
        }

        if (currentState == PowerState.ON && targetState == PowerState.SUSPEND) {
            task = getVimPort().suspendVMTask(vm);
            awaitTaskEnd(task);
            return;
        }

        if (currentState == PowerState.ON && targetState == PowerState.OFF) {
            if (transition == PowerTransition.SOFT) {
                softPowerOff(vm, politenessDeadlineMicros);
            } else {
                hardPowerOff(vm);
            }
        }
    }

    private void awaitTaskEnd(ManagedObjectReference task) throws Exception {
        TaskInfo taskInfo = VimUtils.waitTaskEnd(this.connection, task);
        if (taskInfo.getState() == TaskInfoState.ERROR) {
            VimUtils.rethrow(taskInfo.getError());
        }
    }

    private void hardPowerOff(ManagedObjectReference vm) throws Exception {
        ManagedObjectReference task = getVimPort().powerOffVMTask(vm);
        awaitTaskEnd(task);
    }

    private void softPowerOff(ManagedObjectReference vm, long politenessDeadlineMicros)
            throws Exception {
        try {
            getVimPort().shutdownGuest(vm);
        } catch (ToolsUnavailableFaultMsg e) {
            // no vmtoools present, try harder
            hardPowerOff(vm);
            return;
        }

        // wait for guest to shutdown
        WaitForValues wait = new WaitForValues(connection);

        int timeout = (int) TimeUnit.MICROSECONDS
                .toSeconds(politenessDeadlineMicros - Utils.getNowMicrosUtc());

        if (timeout <= 0) {
            // maybe try anyway?
            return;
        }

        Object[] currentPowerState = wait.wait(vm,
                new String[] { VimNames.PATH_POWER_STATE },
                new String[] { VimNames.PATH_POWER_STATE },
                new Object[][] {
                        new Object[] {
                                VirtualMachinePowerState.POWERED_OFF
                        }
                }, timeout);

        if (currentPowerState == null
                || currentPowerState[0] != VirtualMachinePowerState.POWERED_OFF) {
            // vm not shutdown on time
            hardPowerOff(vm);
        }
    }

    public PowerState getPowerState(ManagedObjectReference vm) throws InvalidPropertyFaultMsg,
            RuntimeFaultFaultMsg {
        VirtualMachinePowerState vmps = get.entityProp(vm, VimNames.PATH_POWER_STATE);
        return VSphereToPhotonMapping.convertPowerState(vmps);
    }
}
