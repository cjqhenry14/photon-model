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

import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import com.vmware.photon.controller.model.adapters.vsphere.util.connection.BaseHelper;
import com.vmware.photon.controller.model.adapters.vsphere.util.connection.Connection;
import com.vmware.photon.controller.model.adapters.vsphere.util.connection.GetMoRef;
import com.vmware.photon.controller.model.adapters.vsphere.util.finders.Element;
import com.vmware.photon.controller.model.adapters.vsphere.util.finders.Finder;
import com.vmware.photon.controller.model.adapters.vsphere.util.finders.FinderException;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeStateWithDescription;
import com.vmware.photon.controller.model.resources.ComputeService.PowerState;
import com.vmware.photon.controller.model.resources.DiskService.DiskState;
import com.vmware.photon.controller.model.resources.DiskService.DiskStatus;
import com.vmware.photon.controller.model.resources.DiskService.DiskType;
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
import com.vmware.vim25.VirtualCdrom;
import com.vmware.vim25.VirtualCdromAtapiBackingInfo;
import com.vmware.vim25.VirtualCdromIsoBackingInfo;
import com.vmware.vim25.VirtualDevice;
import com.vmware.vim25.VirtualDeviceConfigSpec;
import com.vmware.vim25.VirtualDeviceConfigSpecFileOperation;
import com.vmware.vim25.VirtualDeviceConfigSpecOperation;
import com.vmware.vim25.VirtualDeviceConnectInfo;
import com.vmware.vim25.VirtualDisk;
import com.vmware.vim25.VirtualDiskFlatVer2BackingInfo;
import com.vmware.vim25.VirtualDiskMode;
import com.vmware.vim25.VirtualDiskSpec;
import com.vmware.vim25.VirtualE1000;
import com.vmware.vim25.VirtualEthernetCard;
import com.vmware.vim25.VirtualEthernetCardMacType;
import com.vmware.vim25.VirtualEthernetCardNetworkBackingInfo;
import com.vmware.vim25.VirtualFloppy;
import com.vmware.vim25.VirtualFloppyDeviceBackingInfo;
import com.vmware.vim25.VirtualFloppyImageBackingInfo;
import com.vmware.vim25.VirtualIDEController;
import com.vmware.vim25.VirtualLsiLogicController;
import com.vmware.vim25.VirtualMachineConfigSpec;
import com.vmware.vim25.VirtualMachineFileInfo;
import com.vmware.vim25.VirtualSCSIController;
import com.vmware.vim25.VirtualSCSISharing;
import com.vmware.vim25.VirtualSIOController;

/**
 * A simple client for vsphere. Consist of a valid connection and some context.
 * This class does blocking IO but doesn't talk back to xenon.
 * A client operates in the context of a datacenter. If the datacenter cannot be determined at
 * construction time a ClientException is thrown.
 */
public class InstanceClient extends BaseHelper {
    private static final Logger logger = Logger.getLogger(InstanceClient.class.getName());
    private final ComputeStateWithDescription state;
    private final ComputeStateWithDescription parent;

    private final GetMoRef get;
    private final Finder finder;
    private ManagedObjectReference vm;
    private ManagedObjectReference datastore;

    public InstanceClient(Connection connection,
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

        // store reference to created vm for further processing
        this.vm = vm;

        ComputeState state = new ComputeState();
        state.powerState = PowerState.OFF;
        state.resourcePoolLink = firstNonNull(state.resourcePoolLink, parent.resourcePoolLink);

        enrichStateFromVm(state, vm);

        return state;
    }

    /**
     * Creates disks and attaches them to the vm created by {@link #createInstance()}.
     * The given diskStates are enriched with data from vSphere and can be patched back to xenon.
     */
    public void attachDisks(List<DiskState> diskStates) throws Exception {
        if (this.vm == null) {
            throw new IllegalStateException("Cannot attach diskStates if VM is not created");
        }

        EnumSet<DiskType> notSupportedTypes = EnumSet.of(DiskType.SSD, DiskType.NETWORK);
        List<DiskState> unsupportedDisks = diskStates.stream()
                .filter(d -> notSupportedTypes.contains(d.type))
                .collect(Collectors.toList());
        if (!unsupportedDisks.isEmpty()) {
            throw new IllegalStateException(
                    "Some diskStates cannot be created: " + unsupportedDisks.stream()
                            .map(d -> d.documentSelfLink).collect(Collectors.toList()));
        }

        // the path to folder holding all vm files
        String dir = get.entityProp(vm, "summary.config.vmPathName");
        dir = Paths.get(dir).getParent().toString();

        ArrayOfVirtualDevice devices = get.entityProp(vm, "config.hardware.device");

        VirtualDevice scsiController = getFirstScsiController(devices);
        int scsiUnit = findFreeUnit(scsiController, devices.getVirtualDevice());

        VirtualDevice ideController = getFirstIdeController(devices);
        int ideUnit = findFreeUnit(ideController, devices.getVirtualDevice());

        VirtualDevice sioController = getFirstSioController(devices);
        int sioUnit = findFreeUnit(sioController, devices.getVirtualDevice());

        List<VirtualDeviceConfigSpec> newDisks = new ArrayList<>();

        for (DiskState ds : diskStates) {
            String diskPath = VimUtils.uriToDatastorePath(ds.sourceImageReference);

            if (ds.type == DiskType.HDD) {
                if (diskPath != null) {
                    // create full clone of given disk
                    VirtualDeviceConfigSpec hdd = createFullCloneAndAttach(diskPath, ds, dir,
                            scsiController, scsiUnit);
                    newDisks.add(hdd);
                } else {
                    VirtualDeviceConfigSpec hdd = createHdd(scsiController, ds, dir, scsiUnit);
                    newDisks.add(hdd);
                }

                scsiUnit = nextUnitNumber(scsiUnit);
            }
            if (ds.type == DiskType.CDROM) {
                VirtualDeviceConfigSpec cdrom = createCdrom(ideController, ideUnit);
                ideUnit = nextUnitNumber(ideUnit);
                if (diskPath != null) {
                    // mount iso image
                    insertCdrom((VirtualCdrom) cdrom.getDevice(), diskPath);
                }
                newDisks.add(cdrom);
            }
            if (ds.type == DiskType.FLOPPY) {
                VirtualDeviceConfigSpec floppy = createFloppy(sioController, sioUnit);
                sioUnit = nextUnitNumber(sioUnit);
                if (diskPath != null) {
                    // mount iso image
                    insertFloppy((VirtualFloppy) floppy.getDevice(), diskPath);
                }
                newDisks.add(floppy);
            }

            // mark disk as attached
            ds.status = DiskStatus.ATTACHED;
        }

        // add disks one at a time
        for (VirtualDeviceConfigSpec newDisk : newDisks) {
            VirtualMachineConfigSpec spec = new VirtualMachineConfigSpec();
            spec.getDeviceChange().add(newDisk);

            ManagedObjectReference reconfigureTask = getVimPort().reconfigVMTask(this.vm, spec);
            TaskInfo info = waitTaskEnd(reconfigureTask);
            if (info.getState() == TaskInfoState.ERROR) {
                VimUtils.rethrow(info.getError());
            }
        }
    }

    private TaskInfo waitTaskEnd(ManagedObjectReference task)
            throws InvalidCollectorVersionFaultMsg, InvalidPropertyFaultMsg, RuntimeFaultFaultMsg {
        return VimUtils.waitTaskEnd(this.connection, task);
    }

    private VirtualDeviceConfigSpec createFullCloneAndAttach(String sourcePath, DiskState ds,
            String dir, VirtualDevice scsiController, int unitNumber)
            throws Exception {

        ManagedObjectReference diskManager = connection.getServiceContent().getVirtualDiskManager();

        // put full clone in the vm folder
        String destName = makePathToVmdkFile(ds, dir);

        // all ops are withing a datacenter
        ManagedObjectReference sourceDc = finder.getDatacenter().object;
        ManagedObjectReference destDc = sourceDc;

        Boolean force = true;

        // spec is not supported, should use null for now
        VirtualDiskSpec spec = null;

        ManagedObjectReference task = getVimPort()
                .copyVirtualDiskTask(diskManager, sourcePath, sourceDc, destName, destDc, spec,
                        force);

        // wait for the disk to be copied
        TaskInfo taskInfo = waitTaskEnd(task);
        if (taskInfo.getState() == TaskInfoState.ERROR) {
            return VimUtils.rethrow(taskInfo.getError());
        }

        VirtualDisk disk = new VirtualDisk();

        VirtualDiskFlatVer2BackingInfo backing = new VirtualDiskFlatVer2BackingInfo();
        backing.setDiskMode(VirtualDiskMode.PERSISTENT.value());
        backing.setThinProvisioned(true);
        backing.setFileName(destName);
        backing.setDatastore(getDatastore());

        disk.setBacking(backing);
        disk.setControllerKey(scsiController.getKey());
        disk.setUnitNumber(unitNumber);
        disk.setKey(-1);

        VirtualDeviceConfigSpec change = new VirtualDeviceConfigSpec();
        change.setDevice(disk);
        change.setOperation(VirtualDeviceConfigSpecOperation.ADD);

        return change;
    }

    private VirtualDeviceConfigSpec createCdrom(VirtualDevice ideController, int unitNumber) {
        VirtualCdrom cdrom = new VirtualCdrom();

        cdrom.setControllerKey(ideController.getKey());
        cdrom.setUnitNumber(unitNumber);

        VirtualDeviceConnectInfo info = new VirtualDeviceConnectInfo();
        info.setAllowGuestControl(true);
        info.setConnected(true);
        info.setStartConnected(true);
        cdrom.setConnectable(info);

        VirtualCdromAtapiBackingInfo backing = new VirtualCdromAtapiBackingInfo();
        backing.setDeviceName(String.format("cdrom-%d-%d", ideController.getKey(), unitNumber));
        backing.setUseAutoDetect(false);
        cdrom.setBacking(backing);

        VirtualDeviceConfigSpec spec = new VirtualDeviceConfigSpec();
        spec.setDevice(cdrom);
        spec.setOperation(VirtualDeviceConfigSpecOperation.ADD);

        return spec;
    }

    /**
     * Changes to backing of the cdrom to an iso-backed one.
     *
     * @param cdrom
     * @param imagePath path to iso on disk, sth. like "[datastore] /images/ubuntu-16.04-amd64.iso"
     */
    private void insertCdrom(VirtualCdrom cdrom, String imagePath) {
        VirtualCdromIsoBackingInfo backing = new VirtualCdromIsoBackingInfo();
        backing.setFileName(imagePath);

        cdrom.setBacking(backing);
    }

    /**
     * Changes to backing of the floppy to an image-backed one.
     *
     * @param floppy
     * @param imagePath
     */
    private void insertFloppy(VirtualFloppy floppy, String imagePath) {
        VirtualFloppyImageBackingInfo backingInfo = new VirtualFloppyImageBackingInfo();
        backingInfo.setFileName(imagePath);
        floppy.setBacking(backingInfo);
    }

    private VirtualDeviceConfigSpec createFloppy(VirtualDevice sioController, int unitNumber) {
        VirtualFloppy floppy = new VirtualFloppy();

        floppy.setControllerKey(sioController.getKey());
        floppy.setUnitNumber(unitNumber);

        VirtualDeviceConnectInfo info = new VirtualDeviceConnectInfo();
        info.setAllowGuestControl(true);
        info.setConnected(true);
        info.setStartConnected(true);
        floppy.setConnectable(info);

        VirtualFloppyDeviceBackingInfo backing = new VirtualFloppyDeviceBackingInfo();
        backing.setDeviceName(String.format("floppy-%d", unitNumber));
        floppy.setBacking(backing);

        VirtualDeviceConfigSpec spec = new VirtualDeviceConfigSpec();
        spec.setDevice(floppy);
        spec.setOperation(VirtualDeviceConfigSpecOperation.ADD);
        return spec;
    }

    private VirtualSIOController getFirstSioController(ArrayOfVirtualDevice devices) {
        for (VirtualDevice dev : devices.getVirtualDevice()) {
            if (dev instanceof VirtualSIOController) {
                return (VirtualSIOController) dev;
            }
        }

        throw new IllegalStateException("No SIO controller found");
    }

    private int findFreeUnit(VirtualDevice controller, List<VirtualDevice> devices) {
        // TODO better find the first free slot
        int max = 0;
        for (VirtualDevice dev : devices) {
            if (dev.getControllerKey() != null && controller.getKey() == dev
                    .getControllerKey()) {
                max = Math.max(dev.getUnitNumber(), max);
            }
        }

        return max;
    }

    /**
     * Increments the given unit number. Skips the number 6 which is reserved in scsi. IDE and SIO
     * go up to 2 so it is safe to use this method for all types of controllers.
     *
     * @param unitNumber
     * @return
     */
    private int nextUnitNumber(int unitNumber) {
        if (unitNumber == 6) {
            // unit 7 is reserved
            return 8;
        }
        return unitNumber + 1;
    }

    private VirtualDeviceConfigSpec createHdd(VirtualDevice scsiController, DiskState ds,
            String dir, int unitNumber)
            throws FinderException, InvalidPropertyFaultMsg, RuntimeFaultFaultMsg {
        String diskName = makePathToVmdkFile(ds, dir);

        VirtualDisk disk = new VirtualDisk();
        disk.setCapacityInKB(toKb(ds.capacityMBytes));

        VirtualDiskFlatVer2BackingInfo backing = new VirtualDiskFlatVer2BackingInfo();
        backing.setDiskMode(VirtualDiskMode.PERSISTENT.value());
        backing.setThinProvisioned(true);
        backing.setFileName(diskName);
        backing.setDatastore(getDatastore());

        disk.setBacking(backing);
        disk.setControllerKey(scsiController.getKey());
        disk.setUnitNumber(unitNumber);
        disk.setKey(-1);

        VirtualDeviceConfigSpec change = new VirtualDeviceConfigSpec();
        change.setDevice(disk);
        change.setOperation(VirtualDeviceConfigSpecOperation.ADD);
        change.setFileOperation(VirtualDeviceConfigSpecFileOperation.CREATE);

        return change;
    }

    private String makePathToVmdkFile(DiskState ds, String dir) {
        String diskName = Paths.get(dir, ds.id).toString();
        if (!diskName.endsWith(".vmdk")) {
            diskName += ".vmdk";
        }
        return diskName;
    }

    private VirtualIDEController getFirstIdeController(ArrayOfVirtualDevice devices) {
        for (VirtualDevice dev : devices.getVirtualDevice()) {
            if (dev instanceof VirtualIDEController) {
                return (VirtualIDEController) dev;
            }
        }

        throw new IllegalStateException("No IDE controller found");
    }

    private VirtualSCSIController getFirstScsiController(ArrayOfVirtualDevice devices) {
        for (VirtualDevice dev : devices.getVirtualDevice()) {
            if (dev instanceof VirtualSCSIController) {
                return (VirtualSCSIController) dev;
            }
        }

        throw new IllegalStateException("No SCSI controller found");
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

        if (state.customProperties == null) {
            state.customProperties = new HashMap<>();
        }

        state.customProperties.put(CustomPropertyKeys.VM_MOREF, VimUtils.convertMoRefToString(ref));
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
                return VimUtils.rethrow(info.getError());
            }
        }

        return (ManagedObjectReference) info.getResult();
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
        scsiCtrl.setBusNumber(0);
        scsiCtrl.setKey(-1);
        scsiCtrl.setSharedBus(VirtualSCSISharing.NO_SHARING);

        return scsiCtrl;
    }

    private VirtualDevice createNic()
            throws FinderException, InvalidPropertyFaultMsg, RuntimeFaultFaultMsg {

        ManagedObjectReference network = getNetwork();
        String networkName = get.entityProp(network, "name");

        VirtualEthernetCard nic = new VirtualE1000();
        nic.setAddressType(VirtualEthernetCardMacType.GENERATED.value());
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

    private Long toKb(long mb) {
        return mb * 1024;
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
        if (datastore != null) {
            return datastore;
        }

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
