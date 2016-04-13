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

package com.vmware.photon.controller.model.adapters.vsphere.util.finders;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.vmware.photon.controller.model.adapters.vsphere.util.connection.BaseHelper;
import com.vmware.photon.controller.model.adapters.vsphere.util.connection.Connection;
import com.vmware.vim25.DynamicProperty;
import com.vmware.vim25.InvalidPropertyFaultMsg;
import com.vmware.vim25.ManagedObjectReference;
import com.vmware.vim25.ObjectContent;
import com.vmware.vim25.ObjectSpec;
import com.vmware.vim25.PropertyFilterSpec;
import com.vmware.vim25.PropertySpec;
import com.vmware.vim25.RuntimeFaultFaultMsg;
import com.vmware.vim25.TraversalSpec;

/**
 * Based on https://github.com/vmware/govmomi/blob/master/list/lister.go
 */
public class Lister extends BaseHelper {

    private final ManagedObjectReference start;
    private final String prefix;

    public Lister(Connection connection, ManagedObjectReference start, String prefix) {
        super(connection);
        this.start = start;

        this.prefix = prefix;
    }

    public static boolean isTraversable(ManagedObjectReference ref) {
        switch (ref.getType()) {
        case "Folder":
        case "Datacenter":
        case "ComputeResource":
        case "ClusterComputeResource":
            // Treat ComputeResource and ClusterComputeResource as one and the same.
            // It doesn't matter from the perspective of the lister.
            return true;
        default:
            return false;
        }

    }

    public List<Element> list()
            throws FinderException, InvalidPropertyFaultMsg, RuntimeFaultFaultMsg {
        switch (start.getType()) {
        case "Folder":
            return listFolder();
        case "Datacenter":
            return listDatacenter();
        case "ComputeResource":
        case "ClusterComputeResource":
            // Treat ComputeResource and ClusterComputeResource as one and the same.
            // It doesn't matter from the perspective of the lister.
            return listComputeResource();
        case "ResourcePool":
            return listResourcePool();
        default:
            throw new FinderException("Unlistable type: " + start.getType());
        }
    }

    private List<Element> listResourcePool()
            throws FinderException, InvalidPropertyFaultMsg, RuntimeFaultFaultMsg {
        ObjectSpec ospec = new ObjectSpec();
        ospec.setObj(this.start);
        ospec.setSkip(true);

        for (String f : new String[] { "resourcePool" }) {
            TraversalSpec tspec = new TraversalSpec();
            tspec.setPath(f);
            tspec.setSkip(false);
            tspec.setType("ResourcePool");

            ospec.getSelectSet().add(tspec);
        }

        List<PropertySpec> pspecs = new ArrayList<>();
        for (String t : new String[] { "ResourcePool" }) {
            PropertySpec pspec = new PropertySpec();
            pspec.setType(t);
            pspec.getPathSet().add("name");

            pspecs.add(pspec);
        }

        PropertyFilterSpec spec = new PropertyFilterSpec();
        spec.getObjectSet().add(ospec);
        spec.getPropSet().addAll(pspecs);

        return callPropertyCollectorAndConvert(spec);
    }

    private List<Element> listComputeResource()
            throws FinderException, InvalidPropertyFaultMsg, RuntimeFaultFaultMsg {
        ObjectSpec ospec = new ObjectSpec();
        ospec.setSkip(true);
        ospec.setObj(this.start);

        for (String f : new String[] { "host", "resourcePool" }) {
            TraversalSpec tspec = new TraversalSpec();
            tspec.setPath(f);
            tspec.setType("ComputeResource");
            tspec.setSkip(false);

            ospec.getSelectSet().add(tspec);
        }

        PropertyFilterSpec filter = new PropertyFilterSpec();
        filter.getObjectSet().add(ospec);

        for (String t : new String[] { "HostSystem", "ResourcePool" }) {
            PropertySpec pspec = new PropertySpec();
            pspec.setType(t);
            pspec.getPathSet().add("name");

            filter.getPropSet().add(pspec);
        }

        return callPropertyCollectorAndConvert(filter);
    }

    private List<Element> listDatacenter()
            throws InvalidPropertyFaultMsg, RuntimeFaultFaultMsg, FinderException {
        ObjectSpec ospec = new ObjectSpec();
        ospec.setObj(this.start);
        ospec.setSkip(true);

        // Include every datastore folder in the select set
        String[] fields = {
                "vmFolder",
                "hostFolder",
                "datastoreFolder",
                "networkFolder" };

        for (String f : fields) {
            TraversalSpec tspec = new TraversalSpec();
            tspec.setPath(f);
            tspec.setSkip(false);
            tspec.setType("Datacenter");

            ospec.getSelectSet().add(tspec);
        }

        PropertySpec pspec = new PropertySpec();
        pspec.setType("Folder");
        pspec.getPathSet().add("name");

        PropertyFilterSpec spec = new PropertyFilterSpec();
        spec.getObjectSet().add(ospec);
        spec.getPropSet().add(pspec);

        return callPropertyCollectorAndConvert(spec);
    }

    private List<Element> callPropertyCollectorAndConvert(PropertyFilterSpec spec)
            throws InvalidPropertyFaultMsg, RuntimeFaultFaultMsg, FinderException {
        List<ObjectContent> objectContents = retrieveProperties(spec);
        return covertObjectContentToElements(objectContents);
    }

    private List<Element> listFolder()
            throws InvalidPropertyFaultMsg, RuntimeFaultFaultMsg, FinderException {
        PropertyFilterSpec spec = new PropertyFilterSpec();

        ObjectSpec objSpec = new ObjectSpec();
        objSpec.setObj(start);

        TraversalSpec selectionSpec = new TraversalSpec();
        selectionSpec.setPath("childEntity");
        selectionSpec.setType("Folder");
        selectionSpec.setSkip(false);

        objSpec.getSelectSet().add(selectionSpec);

        spec.getObjectSet().add(objSpec);

        // Retrieve all objects that we can deal with
        String[] childTypes = {
                "Folder",
                "Datacenter",
                "VirtualMachine",
                "Network",
                "ComputeResource",
                "ClusterComputeResource",
                "Datastore"};

        for (String t : childTypes) {
            PropertySpec pspec = new PropertySpec();
            pspec.setType(t);

            pspec.getPathSet().add("name");

            // Additional basic properties.
            if (t.equals("ComputeResource") || t.equals("ClusterComputeResource")) {
                // The ComputeResource and ClusterComputeResource are dereferenced in
                // the ResourcePoolFlag. Make sure they always have their resourcePool
                // field populated.
                pspec.getPathSet().add("resourcePool");
            }

            spec.getPropSet().add(pspec);
        }

        return callPropertyCollectorAndConvert(spec);
    }

    private List<ObjectContent> retrieveProperties(PropertyFilterSpec spec)
            throws InvalidPropertyFaultMsg, RuntimeFaultFaultMsg {
        ManagedObjectReference pc = this.connection.getServiceContent().getPropertyCollector();

        return this.connection.getVimPort().retrieveProperties(pc, Arrays.asList(spec));
    }

    private List<Element> covertObjectContentToElements(List<ObjectContent> objectContents)
            throws FinderException {
        List<Element> res = new ArrayList<>();

        for (ObjectContent oc : objectContents) {
            res.add(Element.make(oc.getObj(), getName(oc), this.prefix));
        }

        return res;
    }

    private String getName(ObjectContent oc) throws FinderException {
        for (DynamicProperty dp : oc.getPropSet()) {
            if (dp.getName().equals("name")) {
                return (String) dp.getVal();
            }
        }

        throw new FinderException("No name fetched for " + oc.getObj());
    }
}
