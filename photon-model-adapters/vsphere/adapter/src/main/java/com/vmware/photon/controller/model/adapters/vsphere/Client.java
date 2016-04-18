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
import java.util.logging.Level;
import java.util.logging.Logger;

import com.vmware.photon.controller.model.adapters.vsphere.util.connection.BaseHelper;
import com.vmware.photon.controller.model.adapters.vsphere.util.connection.Connection;
import com.vmware.photon.controller.model.adapters.vsphere.util.connection.GetMoRef;
import com.vmware.photon.controller.model.adapters.vsphere.util.connection.WaitForValues;
import com.vmware.photon.controller.model.adapters.vsphere.util.finders.Element;
import com.vmware.photon.controller.model.adapters.vsphere.util.finders.Finder;
import com.vmware.photon.controller.model.adapters.vsphere.util.finders.FinderException;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeStateWithDescription;
import com.vmware.photon.controller.model.resources.ComputeService.PowerState;
import com.vmware.vim25.ArrayOfVirtualDevice;
import com.vmware.vim25.DuplicateNameFaultMsg;
import com.vmware.vim25.FileAlreadyExists;
import com.vmware.vim25.InsufficientResourcesFaultFaultMsg;
import com.vmware.vim25.InvalidCollectorVersionFaultMsg;
import com.vmware.vim25.InvalidNameFaultMsg;
import com.vmware.vim25.InvalidPropertyFaultMsg;
import com.vmware.vim25.ManagedObjectReference;
import com.vmware.vim25.MethodFault;
import com.vmware.vim25.OptionValue;
import com.vmware.vim25.ResourceAllocationInfo;
import com.vmware.vim25.ResourceConfigSpec;
import com.vmware.vim25.RuntimeFaultFaultMsg;
import com.vmware.vim25.SharesInfo;
import com.vmware.vim25.SharesLevel;
import com.vmware.vim25.TaskInfo;
import com.vmware.vim25.TaskInfoState;
import com.vmware.vim25.VimPortType;
import com.vmware.vim25.VirtualDevice;
import com.vmware.vim25.VirtualDeviceConfigSpec;
import com.vmware.vim25.VirtualDeviceConfigSpecOperation;
import com.vmware.vim25.VirtualE1000;
import com.vmware.vim25.VirtualEthernetCard;
import com.vmware.vim25.VirtualEthernetCardNetworkBackingInfo;
import com.vmware.vim25.VirtualLsiLogicController;
import com.vmware.vim25.VirtualMachineConfigSpec;
import com.vmware.vim25.VirtualMachineFileInfo;
import com.vmware.vim25.VirtualSCSISharing;

/**
 * A simple client for vsphere. Consist of a valid connection and some context.
 * This class does blocking IO but doesn't talk back to xenon.
 * A client operates in the context of a datacenter. If the datacenter cannot be determined at
 * construction time a ClientException is thrown.
 */
public class Client extends BaseHelper {
    private static final Logger logger = Logger.getLogger(Client.class.getName());
    private final ComputeStateWithDescription state;
    private final ComputeStateWithDescription parent;

    private final GetMoRef get;
    private final Finder finder;

    public Client(Connection connection,
            ComputeStateWithDescription resource,
            ComputeStateWithDescription parent)
            throws ClientException, FinderException {
        super(connection);

        this.state = resource;
        this.parent = parent;

        // the datacenterId is used as a ref to a vSphere datacenter name
        String id = resource.description.dataCenterId;

        try {
            this.finder = new Finder(connection, id);
        } catch (RuntimeFaultFaultMsg | InvalidPropertyFaultMsg e) {
            throw new ClientException(
                    String.format("Error looking for datacenter for id '%s'", id), e);
        }

        this.get = new GetMoRef(this.connection);
    }

    public void deleteInstance() {
        // TODO
    }

    /**
     * Does provisioning and return a patchable state to patch the resource.
     *
     * @return
     */
    public ComputeState createInstance() throws FinderException, Exception {
        ManagedObjectReference vm = createVm();

        if (vm == null) {
            // vm was created by someone else
            return null;
        }

        // TODO add disks
        // TODO patch disk state

        ComputeState state = new ComputeState();
        state.powerState = PowerState.OFF;
        state.resourcePoolLink = firstNonNull(state.resourcePoolLink, parent.resourcePoolLink);

        enrichStateFromVm(state, vm);

        return state;
    }

    /**
     * Once a vm is provisioned this method collects vsphere-assigned properties and stores them
     * in the {@link ComputeState#customProperties}
     *
     * @param state
     * @param ref
     * @throws InvalidPropertyFaultMsg
     * @throws RuntimeFaultFaultMsg
     */
    @SuppressWarnings("unchecked")
    private void enrichStateFromVm(ComputeState state, ManagedObjectReference ref)
            throws InvalidPropertyFaultMsg, RuntimeFaultFaultMsg {
        Map<String, Object> props =
                get.entityProps(ref, "config.instanceUuid", "config.hardware.device");
        state.id = (String) props.get("config.instanceUuid");

        ArrayOfVirtualDevice devices = (ArrayOfVirtualDevice) props.get("config.hardware.device");
        for (VirtualDevice dev : devices.getVirtualDevice()) {
            if (dev instanceof VirtualEthernetCard) {
                state.primaryMAC = ((VirtualEthernetCard) dev).getMacAddress();
                break;
            }
        }

        state.customProperties = new HashMap<>();
        state.customProperties.put("managedObjectReference", convertMoRefToString(ref));
    }

    /**
     * Return the first non-null value or null if all values are null.
     * @param values
     * @return
     */
    private String firstNonNull(String... values) {
        for (String s : values) {
            if (s != null) {
                return s;
            }
        }

        return null;
    }

    /**
     * Creates a VM in vsphere. This method will block untill the CreateVM_Task completes.
     * The path to the .vmx file is explicitly set and its existence is iterpreted as if the VM has
     * been successfully created and returns null.
     *
     * @return
     * @throws FinderException
     * @throws Exception
     */
    private ManagedObjectReference createVm() throws FinderException, Exception {
        ManagedObjectReference folder = finder.vmFolder().object;
        ManagedObjectReference resourcePool = getOrCreateResourcePoolForVm();
        ManagedObjectReference datastore = getDatastore();

        String datastoreName = get.entityProp(datastore, "name");
        VirtualMachineConfigSpec spec = buildVirtualMachineConfigSpec(datastoreName);

        ManagedObjectReference vmTask = getVimPort().createVMTask(folder, spec, resourcePool, null);

        TaskInfo info = waitTaskEnd(vmTask);

        if (info.getState() == TaskInfoState.ERROR) {
            MethodFault fault = info.getError().getFault();
            if (fault instanceof FileAlreadyExists) {
                // a .vmx file already exists, assume someone won the race to create the vm
                return null;
            } else {
                throw new RuntimeException(info.getError().getLocalizedMessage());
            }
        }

        return (ManagedObjectReference) info.getResult();
    }

    private TaskInfo waitTaskEnd(ManagedObjectReference task)
            throws InvalidCollectorVersionFaultMsg, InvalidPropertyFaultMsg, RuntimeFaultFaultMsg {

        WaitForValues waitForValues = new WaitForValues(this.connection);

        waitForValues.wait(task, new String[] { "info.state", "info.error" },
                new String[] { "state" }, new Object[][] { new Object[] {
                        TaskInfoState.SUCCESS, TaskInfoState.ERROR } });

        return get.entityProp(task, "info");
    }

    /**
     * Serializes a MoRef into a String.
     *
     * @param ref
     * @return
     */
    private String convertMoRefToString(ManagedObjectReference ref) {
        return ref.getType() + "-" + ref.getValue();
    }

    /**
     * Creates a spec used to create the VM.
     *
     * @param datastoreName
     * @return
     * @throws InvalidPropertyFaultMsg
     * @throws FinderException
     * @throws RuntimeFaultFaultMsg
     */
    private VirtualMachineConfigSpec buildVirtualMachineConfigSpec(String datastoreName)
            throws InvalidPropertyFaultMsg, FinderException, RuntimeFaultFaultMsg {
        String displayName = getCustomProperty("displayName", state.id);
        VirtualMachineConfigSpec spec = new VirtualMachineConfigSpec();
        spec.setName(displayName);
        spec.setNumCPUs((int) state.description.cpuCount);
        spec.setGuestId("otherGuest64");
        spec.setMemoryMB(toMb(state.description.totalMemoryBytes));

        spec.getExtraConfig().add(configEntry("photon.descriptionLink", state.descriptionLink));
        spec.getExtraConfig().add(configEntry("photon.parentLink", state.parentLink));

        VirtualMachineFileInfo files = new VirtualMachineFileInfo();
        // Use a full path to the config file to avoid creating a VM with the same name
        String path = String.format("[%s] %s/%s.vmx", datastoreName, displayName, displayName);
        files.setVmPathName(path);
        spec.setFiles(files);

        VirtualDevice nic = createNic();

        VirtualDevice scsi = createScsiController();

        for (VirtualDevice dev : new VirtualDevice[] { nic, scsi }) {
            VirtualDeviceConfigSpec change = new VirtualDeviceConfigSpec();
            change.setDevice(dev);
            change.setOperation(VirtualDeviceConfigSpecOperation.ADD);
            spec.getDeviceChange().add(change);
        }

        return spec;
    }

    private VirtualDevice createScsiController() {
        VirtualLsiLogicController scsiCtrl = new VirtualLsiLogicController();
        // first controller,
        int diskCtlrKey = 1;

        scsiCtrl.setBusNumber(0);
        scsiCtrl.setKey(diskCtlrKey);
        scsiCtrl.setSharedBus(VirtualSCSISharing.NO_SHARING);

        return scsiCtrl;
    }

    private VirtualDevice createNic()
            throws FinderException, InvalidPropertyFaultMsg, RuntimeFaultFaultMsg {

        ManagedObjectReference network = getNetwork();
        String networkName = get.entityProp(network, "name");

        VirtualEthernetCard nic = new VirtualE1000();
        nic.setAddressType("generated");
        nic.setKey(-1);

        VirtualEthernetCardNetworkBackingInfo backing = new VirtualEthernetCardNetworkBackingInfo();
        backing.setDeviceName(networkName);

        nic.setBacking(backing);

        return nic;
    }

    private OptionValue configEntry(String key, String value) {
        OptionValue res = new OptionValue();
        res.setKey(key);
        res.setValue(value);
        return res;
    }

    private Long toMb(long bytes) {
        return bytes / 1024 / 1024;
    }

    /**
     * Finds the datastore to use for the VM from the ComputeState.description.datastoreId.
     *
     * @return
     * @throws RuntimeFaultFaultMsg
     * @throws InvalidPropertyFaultMsg
     * @throws FinderException
     */
    private ManagedObjectReference getDatastore()
            throws RuntimeFaultFaultMsg, InvalidPropertyFaultMsg, FinderException {
        ManagedObjectReference datastore;

        String datastorePath = state.description.dataStoreId;

        if (datastorePath == null) {
            datastore = finder.defaultDatastore().object;
        } else {
            datastore = finder.datastore(datastorePath).object;
        }
        return datastore;
    }

    /**
     * Creates a resource pool for the VM. The created resource pool is a child of the resource
     * pool (zoneId) specified in the {@link #state} or {@link #parent}, whichever is defined first.
     * @return
     * @throws RuntimeFaultFaultMsg
     * @throws InvalidPropertyFaultMsg
     * @throws FinderException
     * @throws InvalidNameFaultMsg
     * @throws InsufficientResourcesFaultFaultMsg
     */
    private ManagedObjectReference getOrCreateResourcePoolForVm()
            throws RuntimeFaultFaultMsg, InvalidPropertyFaultMsg, FinderException,
            InvalidNameFaultMsg, InsufficientResourcesFaultFaultMsg {
        Element parentResourcePool;

        // if a parent resource pool is not configured used the default one.
        String parentResourcePath = firstNonNull(state.description.zoneId,
                parent.description.zoneId);

        if (parentResourcePath != null) {
            parentResourcePool = finder.resourcePool(parentResourcePath);
        } else {
            // missing parent state path: default to the (assumed) single resource pool in the dc
            parentResourcePool = finder.defaultResourcePool();
        }

        String vmName = state.description.name;

        // try to build a resource pool for the requested vm
        String resourcePoolPath = parentResourcePool.path + "/" + vmName;

        try {
            finder.resourcePool(resourcePoolPath);
        } catch (FinderException e) {
            logger.log(Level.INFO, "cannot find ResourcePool " + resourcePoolPath);
            // resource pool not found
        }

        return createResourcePool(parentResourcePool.object, vmName,
                resourcePoolPath);
    }

    /**
     * Reads the value for the given custPropKey from the ComputeState.description.customProperties.
     *
     * @param custPropKey
     * @param defaultValue
     * @return
     */
    private String getCustomProperty(String custPropKey, String defaultValue) {
        if (state.description.customProperties != null) {
            String s = state.description.customProperties.get(custPropKey);
            if (s != null) {
                return s;
            }
        }

        return defaultValue;
    }

    /**
     * Creates a resource pool with the given name as a child of the given parent. If the resource
     * pool already exists returns the existing object.
     *
     * @param parent
     * @param name
     * @param resourcePoolPath
     * @return
     * @throws FinderException
     * @throws InvalidPropertyFaultMsg
     * @throws RuntimeFaultFaultMsg
     * @throws InvalidNameFaultMsg
     * @throws InsufficientResourcesFaultFaultMsg
     */
    private ManagedObjectReference createResourcePool(ManagedObjectReference parent, String name,
            String resourcePoolPath)
            throws FinderException,
            InvalidPropertyFaultMsg,
            RuntimeFaultFaultMsg,
            InvalidNameFaultMsg,
            InsufficientResourcesFaultFaultMsg {

        ResourceConfigSpec spec = new ResourceConfigSpec();
        spec.setCpuAllocation(new ResourceAllocationInfo());
        spec.setMemoryAllocation(new ResourceAllocationInfo());

        // generous default allocations
        for (ResourceAllocationInfo rai : new ResourceAllocationInfo[] { spec.getCpuAllocation(),
                spec.getMemoryAllocation() }) {
            rai.setExpandableReservation(true);

            // VC requires the following fields to be set,
            // even though doc/wsdl lists them as optional.

            // unlimited
            rai.setLimit(-1L);

            rai.setReservation(1L);

            SharesInfo shares = new SharesInfo();
            shares.setLevel(SharesLevel.NORMAL);
            rai.setShares(shares);
        }

        try {
            return getVimPort().createResourcePool(parent, name, spec);
        } catch (DuplicateNameFaultMsg e) {
            // someone else won the race to create this pool, just return it
            return finder.resourcePool(resourcePoolPath).object;
        }
    }

    private VimPortType getVimPort() {
        return this.connection.getVimPort();
    }

    /**
     * Tries to guess the network the VM has to be part of. If ComputeState.description.networkId is
     * defined then it's used.
     *
     * @return
     * @throws FinderException
     * @throws InvalidPropertyFaultMsg
     * @throws RuntimeFaultFaultMsg
     */
    private ManagedObjectReference getNetwork()
            throws FinderException, InvalidPropertyFaultMsg, RuntimeFaultFaultMsg {
        String id = state.description.networkId;
        if (id != null) {
            return finder.network(id).object;
        } else {
            return finder.defaultNetwork().object;
        }
    }

    public static class ClientException extends Exception {
        private static final long serialVersionUID = 1L;

        public ClientException(String message, Throwable cause) {
            super(message, cause);
        }

        public ClientException(Throwable cause) {
            super(cause);
        }

        public ClientException(String message) {
            super(message);
        }
    }
}
