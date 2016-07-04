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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import com.vmware.photon.controller.model.adapters.vsphere.InstanceClient.ClientException;
import com.vmware.photon.controller.model.adapters.vsphere.util.VimNames;
import com.vmware.photon.controller.model.adapters.vsphere.util.VimPath;
import com.vmware.photon.controller.model.adapters.vsphere.util.connection.BaseHelper;
import com.vmware.photon.controller.model.adapters.vsphere.util.connection.Connection;
import com.vmware.photon.controller.model.adapters.vsphere.util.connection.GetMoRef;
import com.vmware.photon.controller.model.adapters.vsphere.util.finders.Finder;
import com.vmware.photon.controller.model.adapters.vsphere.util.finders.FinderException;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeStateWithDescription;
import com.vmware.vim25.InvalidPropertyFaultMsg;
import com.vmware.vim25.ManagedObjectReference;
import com.vmware.vim25.ObjectContent;
import com.vmware.vim25.ObjectSpec;
import com.vmware.vim25.PropertyFilterSpec;
import com.vmware.vim25.PropertySpec;
import com.vmware.vim25.RetrieveOptions;
import com.vmware.vim25.RetrieveResult;
import com.vmware.vim25.RuntimeFaultFaultMsg;
import com.vmware.vim25.SelectionSpec;
import com.vmware.vim25.TraversalSpec;
import com.vmware.vim25.UpdateSet;
import com.vmware.vim25.WaitOptions;

/**
 */
public class EnumerationClient extends BaseHelper {
    public static final int DEFAULT_FETCH_PAGE_SIZE = 100;

    private final ComputeStateWithDescription parent;

    private final Finder finder;

    private final GetMoRef get;

    public EnumerationClient(Connection connection, ComputeStateWithDescription parent)
            throws FinderException, ClientException {
        super(connection);

        this.parent = parent;

        // the datacenterId is used as a ref to a vSphere datacenter name
        String id = parent.description.dataCenterId;

        try {
            this.finder = new Finder(connection, id);
        } catch (RuntimeFaultFaultMsg | InvalidPropertyFaultMsg e) {
            throw new ClientException(
                    String.format("Error looking for datacenter for id '%s'", id), e);
        }

        this.get = new GetMoRef(this.connection);
    }

    private ManagedObjectReference createPropertyCollector() throws RuntimeFaultFaultMsg {
        ManagedObjectReference pc = this.connection.getServiceContent().getPropertyCollector();
        return getVimPort().createPropertyCollector(pc);
    }

    private ManagedObjectReference createPropertyCollectorWithFilter(PropertyFilterSpec spec)
            throws RuntimeFaultFaultMsg, InvalidPropertyFaultMsg {
        ManagedObjectReference pc = createPropertyCollector();
        boolean partialUpdates = false;
        getVimPort().createFilter(pc, spec, partialUpdates);
        return pc;
    }

    private SelectionSpec getSelectionSpec(String name) {
        SelectionSpec genericSpec = new SelectionSpec();
        genericSpec.setName(name);
        return genericSpec;
    }

    /**
     * @return An array of SelectionSpec covering VM, Host, Resource pool,
     * Cluster Compute Resource and Datastore.
     */
    public List<SelectionSpec> buildFullTraversal() {
        // Terminal traversal specs

        // RP -> VM
        TraversalSpec rpToVm = new TraversalSpec();
        rpToVm.setType(VimNames.TYPE_RESOURCE_POOL);
        rpToVm.setName("rpToVm");
        rpToVm.setPath("vm");
        rpToVm.setSkip(Boolean.FALSE);

        // vApp -> VM
        TraversalSpec vAppToVM = new TraversalSpec();
        vAppToVM.setType(VimNames.TYPE_VAPP);
        vAppToVM.setName("vAppToVM");
        vAppToVM.setPath("vm");

        // HostSystem -> VM
        TraversalSpec hToVm = new TraversalSpec();
        hToVm.setType(VimNames.TYPE_HOST);
        hToVm.setPath("vm");
        hToVm.setName("hToVm");
        hToVm.getSelectSet().add(getSelectionSpec("VisitFolders"));
        hToVm.setSkip(Boolean.FALSE);

        // DC -> DS
        TraversalSpec dcToDs = new TraversalSpec();
        dcToDs.setType(VimNames.TYPE_DATACENTER);
        dcToDs.setPath("datastore");
        dcToDs.setName("dcToDs");
        dcToDs.setSkip(Boolean.FALSE);

        // Recurse through all ResourcePools
        TraversalSpec rpToRp = new TraversalSpec();
        rpToRp.setType(VimNames.TYPE_RESOURCE_POOL);
        rpToRp.setPath("resourcePool");
        rpToRp.setSkip(Boolean.FALSE);
        rpToRp.setName("rpToRp");
        rpToRp.getSelectSet().add(getSelectionSpec("rpToRp"));

        TraversalSpec crToRp = new TraversalSpec();
        crToRp.setType(VimNames.TYPE_COMPUTE_RESOURCE);
        crToRp.setPath("resourcePool");
        crToRp.setSkip(Boolean.FALSE);
        crToRp.setName("crToRp");
        crToRp.getSelectSet().add(getSelectionSpec("rpToRp"));

        TraversalSpec crToH = new TraversalSpec();
        crToH.setType(VimNames.TYPE_COMPUTE_RESOURCE);
        crToH.setSkip(Boolean.FALSE);
        crToH.setPath("host");
        crToH.setName("crToH");

        TraversalSpec dcToHf = new TraversalSpec();
        dcToHf.setType(VimNames.TYPE_DATACENTER);
        dcToHf.setSkip(Boolean.FALSE);
        dcToHf.setPath("hostFolder");
        dcToHf.setName("dcToHf");
        dcToHf.getSelectSet().add(getSelectionSpec("VisitFolders"));

        TraversalSpec vAppToRp = new TraversalSpec();
        vAppToRp.setType(VimNames.TYPE_VAPP);
        vAppToRp.setName("vAppToRp");
        vAppToRp.setPath("resourcePool");
        vAppToRp.getSelectSet().add(getSelectionSpec("rpToRp"));

        TraversalSpec dcToVmf = new TraversalSpec();
        dcToVmf.setType(VimNames.TYPE_DATACENTER);
        dcToVmf.setSkip(Boolean.FALSE);
        dcToVmf.setPath("vmFolder");
        dcToVmf.setName("dcToVmf");
        dcToVmf.getSelectSet().add(getSelectionSpec("VisitFolders"));

        // For Folder -> Folder recursion
        TraversalSpec visitFolders = new TraversalSpec();
        visitFolders.setType(VimNames.TYPE_FOLDER);
        visitFolders.setPath("childEntity");
        visitFolders.setSkip(Boolean.FALSE);
        visitFolders.setName("VisitFolders");

        List<SelectionSpec> sspecarrvf = new ArrayList<>();
        sspecarrvf.add(getSelectionSpec("crToRp"));
        sspecarrvf.add(getSelectionSpec("crToH"));
        sspecarrvf.add(getSelectionSpec("dcToVmf"));
        sspecarrvf.add(getSelectionSpec("dcToHf"));
        sspecarrvf.add(getSelectionSpec("vAppToRp"));
        sspecarrvf.add(getSelectionSpec("vAppToVM"));
        sspecarrvf.add(getSelectionSpec("dcToDs"));
        sspecarrvf.add(getSelectionSpec("hToVm"));
        sspecarrvf.add(getSelectionSpec("rpToVm"));
        sspecarrvf.add(getSelectionSpec("VisitFolders"));

        visitFolders.getSelectSet().addAll(sspecarrvf);

        List<SelectionSpec> resultspec = new ArrayList<>();
        resultspec.add(visitFolders);
        resultspec.add(crToRp);
        resultspec.add(crToH);
        resultspec.add(dcToVmf);
        resultspec.add(dcToHf);
        resultspec.add(vAppToRp);
        resultspec.add(vAppToVM);
        resultspec.add(dcToDs);
        resultspec.add(hToVm);
        resultspec.add(rpToVm);
        resultspec.add(rpToRp);

        return resultspec;
    }

    public PropertyFilterSpec createFullFilterSpec() {
        ObjectSpec ospec = new ObjectSpec();
        ospec.setObj(this.finder.getDatacenter().object);
        ospec.setSkip(false);

        ospec.getSelectSet().addAll(buildFullTraversal());

        PropertySpec vmSpec = new PropertySpec();
        vmSpec.setType(VimNames.TYPE_VM);
        vmSpec.getPathSet().addAll(Arrays.asList(
                VimPath.vm_config_name,
                VimPath.vm_config_instanceUuid,
                VimPath.vm_config_hardware_device,
                VimPath.vm_summary_config_numCpu,
                VimPath.vm_config_extraConfig,
                VimPath.vm_runtime_powerState,
                VimPath.vm_runtime_maxCpuUsage,
                VimPath.vm_config_template,
                VimPath.vm_runtime_maxMemoryUsage
        ));

        PropertySpec hostSpec = new PropertySpec();
        hostSpec.setType(VimNames.TYPE_HOST);
        hostSpec.getPathSet().addAll(Arrays.asList(
                VimPath.host_summary_hardware_memorySize,
                VimPath.host_summary_hardware_cpuMhz,
                VimPath.host_summary_hardware_numCpuCores,
                VimPath.host_summary_hardware_uuid,
                "name"
        ));

        PropertySpec rpSpec = new PropertySpec();
        rpSpec.setType(VimNames.TYPE_RESOURCE_POOL);
        rpSpec.getPathSet().addAll(Arrays.asList(
                VimPath.rp_summary_config_memoryAllocation_limit,
                VimPath.rp_summary_config_memoryAllocation_reservation,
                "name"
        ));

        PropertyFilterSpec filterSpec = new PropertyFilterSpec();
        filterSpec.getObjectSet().add(ospec);
        filterSpec.getPropSet().add(hostSpec);
        filterSpec.getPropSet().add(vmSpec);
        filterSpec.getPropSet().add(rpSpec);

        return filterSpec;
    }

    public Iterable<List<ObjectContent>> retrieveObjects(
            PropertyFilterSpec spec) throws RuntimeFaultFaultMsg {
        ManagedObjectReference pc = createPropertyCollector();

        return () -> new ObjectContentIterator(pc, spec);
    }

    private void destroyCollectorQuietly(ManagedObjectReference pc) {
        try {
            getVimPort().destroyCollector(pc);
        } catch (RuntimeFaultFaultMsg ignore) {

        }
    }

    public Iterable<UpdateSet> pollForUpdates(PropertyFilterSpec spec)
            throws InvalidPropertyFaultMsg, RuntimeFaultFaultMsg {
        ManagedObjectReference pc = createPropertyCollectorWithFilter(spec);

        return () -> new ObjectUpdateIterator(pc);
    }

    /**
     * closes underlying connection. All objects associated with this client will become
     * invalid.
     */
    public void close() {
        this.connection.close();
    }

    private class ObjectContentIterator implements Iterator<List<ObjectContent>> {
        private final RetrieveOptions opts;
        private final ManagedObjectReference pc;
        private final PropertyFilterSpec spec;

        private RetrieveResult result;

        ObjectContentIterator(ManagedObjectReference pc, PropertyFilterSpec spec) {
            this.pc = pc;
            this.spec = spec;

            this.opts = new RetrieveOptions();
            this.opts.setMaxObjects(DEFAULT_FETCH_PAGE_SIZE);
        }

        @Override
        public boolean hasNext() {
            if (this.result == null) {
                // has to check, may still return an empty first page
                return true;
            }

            return this.result.getToken() != null;
        }

        @Override
        public List<ObjectContent> next() {
            if (this.result == null) {
                try {
                    this.result = getVimPort()
                            .retrievePropertiesEx(this.pc, Collections.singletonList(this.spec), this.opts);
                } catch (RuntimeException e) {
                    destroyCollectorQuietly(this.pc);
                    throw e;
                } catch (Exception e) {
                    destroyCollectorQuietly(this.pc);
                    throw new RuntimeException(e);
                }

                return this.result.getObjects();
            }

            try {
                this.result = getVimPort().continueRetrievePropertiesEx(this.pc, this.result.getToken());
            } catch (RuntimeException e) {
                destroyCollectorQuietly(this.pc);
                throw e;
            } catch (Exception e) {
                destroyCollectorQuietly(this.pc);
                throw new RuntimeException(e);
            }

            return this.result.getObjects();
        }
    }

    private class ObjectUpdateIterator implements Iterator<UpdateSet> {
        private final ManagedObjectReference pc;

        private final WaitOptions opts;

        private String since;

        ObjectUpdateIterator(ManagedObjectReference pc) {
            this.pc = pc;

            // don't fetch too much data or block for too long
            this.opts = new WaitOptions();
            this.opts.setMaxWaitSeconds(10);
            this.opts.setMaxObjectUpdates(DEFAULT_FETCH_PAGE_SIZE);
        }

        @Override
        public boolean hasNext() {
            // updates are never exhausted, one must break the loop in other way
            return true;
        }

        @Override
        public UpdateSet next() {
            try {
                UpdateSet result = getVimPort().waitForUpdatesEx(this.pc, this.since, this.opts);
                this.since = result.getVersion();
                return result;
            } catch (Exception e) {
                destroyCollectorQuietly(this.pc);
                throw new RuntimeException(e);
            }
        }
    }
}
