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

import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.vmware.photon.controller.model.adapters.vsphere.util.connection.Connection;
import com.vmware.photon.controller.model.adapters.vsphere.util.connection.GetMoRef;
import com.vmware.vim25.DynamicProperty;
import com.vmware.vim25.InvalidPropertyFaultMsg;
import com.vmware.vim25.ManagedObjectReference;
import com.vmware.vim25.ObjectContent;
import com.vmware.vim25.ObjectSpec;
import com.vmware.vim25.PropertyFilterSpec;
import com.vmware.vim25.PropertySpec;
import com.vmware.vim25.RuntimeFaultFaultMsg;
import com.vmware.vim25.SelectionSpec;
import com.vmware.vim25.TraversalSpec;

/**
 * Port of https://github.com/vmware/govmomi/blob/master/find/finder.go to Java.
 */
public class Finder extends Recurser {
    private final Element datacenter;

    private Element networkFolder;
    private Element datastoreFolder;
    private Element hostFolder;
    private Element vmFolder;

    /**
     * A finder in the context of a datacenter. The datacenter path must uniquely define a datacenter.
     */
    public Finder(Connection connection, String datacenterPath)
            throws FinderException, InvalidPropertyFaultMsg, RuntimeFaultFaultMsg {
        super(connection);
        this.datacenter = datacenter(datacenterPath);
    }

    public static List<String> toParts(String p) {
        p = Paths.get(p).normalize().toString();

        if (p.equals("/")) {
            return Collections.emptyList();
        }

        if (p.length() > 0) {
            // Prefix ./ if relative
            if (p.charAt(0) != '/' && p.charAt(0) != '.') {
                p = "./" + p;
            }
        }

        String[] ps = p.split("/");

        List<String> res = Arrays.asList(ps);

        if (ps.length == 1 && ps[0].equals("")) {
            return Collections.singletonList(".");
        }

        if (ps[0].equals("")) {
            // Start at asRoot
            return res.subList(1, res.size());
        } else {
            return res;
        }
    }

    private List<Element> find(Element pivot, boolean traverseLeafs, String[] path)
            throws FinderException, InvalidPropertyFaultMsg, RuntimeFaultFaultMsg {
        List<Element> out = new ArrayList<>();

        for (String arg : path) {
            List<Element> es = list(pivot, traverseLeafs, arg);
            out.addAll(es);
        }
        return out;
    }

    private List<Element> list(Element pivot, boolean traverseLeafs, String arg)
            throws FinderException, InvalidPropertyFaultMsg, RuntimeFaultFaultMsg {
        List<String> parts = toParts(arg);

        Element root = rootElement();

        if (parts.size() > 0) {
            switch (parts.get(0)) {
            case "..": // Not supported; many edge case, little value
                throw new FinderException("cannot traverse up a tree");
            case ".": // Relative to whatever
                root = fullPath(pivot.object);
                parts = parts.subList(1, parts.size());
                break;
            default:
            }
        }

        this.traverseLeafs = traverseLeafs;
        return this.recurse(root, parts.toArray(new String[] {}));
    }

    /**
     * Walks up the tree until a root object is found.
     */
    public Element fullPath(ManagedObjectReference obj)
            throws InvalidPropertyFaultMsg, RuntimeFaultFaultMsg, FinderException {
        List<ObjectContent> set = ancestrySet(obj);

        Map<MoRef, Element> childToParent = new HashMap<>();

        for (ObjectContent cont : set) {
            MoRef key = new MoRef(cont.getObj());
            ManagedObjectReference parentRef = getParent(cont);
            Element parent;
            if (parentRef != null) {
                parent = Element.make(parentRef, getName(cont));
            } else {
                // use self as parent
                parent = Element.make(cont.getObj(), getName(cont));
            }
            childToParent.put(key, parent);
        }

        // build the path in reverse by walking the ancestry list
        MoRef next = new MoRef(obj);
        List<String> path = new ArrayList<>();
        while (true) {
            Element parent = childToParent.get(next);
            path.add(parent.path);

            if (next.equals(new MoRef(parent.object))) {
                // parent == self means we've arrived at the root of the tree
                break;
            }
            next = new MoRef(parent.object);
        }

        // get the real path
        Collections.reverse(path);

        // build a full path as string
        StringBuilder sb = new StringBuilder();
        for (String s : path) {
            sb.append('/');
            sb.append(s);
        }

        return Element.make(obj, sb.toString());
    }

    private ManagedObjectReference getParent(ObjectContent cont) {
        for (DynamicProperty dp : cont.getPropSet()) {
            if (dp.getName().equals("parent")) {
                return (ManagedObjectReference) dp.getVal();
            }
        }

        // root object
        return null;
    }

    private String getName(ObjectContent cont) throws FinderException {
        for (DynamicProperty dp : cont.getPropSet()) {
            if (dp.getName().equals("name")) {
                return (String) dp.getVal();
            }
        }

        // probably bad spec
        throw new FinderException("Name property not found/fetched for " + cont.getObj().getType());
    }

    private List<ObjectContent> ancestrySet(ManagedObjectReference ref)
            throws InvalidPropertyFaultMsg, RuntimeFaultFaultMsg {
        ObjectSpec ospec = new ObjectSpec();
        ospec.setObj(ref);
        ospec.setSkip(false);

        TraversalSpec tspec = new TraversalSpec();
        tspec.setSkip(false);
        tspec.setPath("parent");
        tspec.setType("ManagedEntity");
        tspec.setName("traverseParent");

        SelectionSpec selSpec = new SelectionSpec();
        selSpec.setName("traverseParent");

        tspec.getSelectSet().add(selSpec);

        ospec.getSelectSet().add(tspec);

        PropertySpec pspec = new PropertySpec();
        pspec.setType("ManagedEntity");
        pspec.getPathSet().add("name");
        pspec.getPathSet().add("parent");

        PropertyFilterSpec filter = new PropertyFilterSpec();
        filter.getObjectSet().add(ospec);
        filter.getPropSet().add(pspec);

        return connection.getVimPort()
                .retrieveProperties(connection.getServiceContent().getPropertyCollector(),
                        Collections.singletonList(filter));

    }

    public List<Element> managedObjectList(String... path)
            throws FinderException, InvalidPropertyFaultMsg, RuntimeFaultFaultMsg {
        Element root;
        if (this.datacenter != null) {
            root = this.datacenter;
        } else {
            root = rootElement();
        }

        if (path.length == 0) {
            path = new String[] { "." };
        }

        return find(root, true, path);
    }

    public List<Element> datacenterList(String... path)
            throws InvalidPropertyFaultMsg, FinderException, RuntimeFaultFaultMsg {
        List<Element> found = find(rootElement(), false, path);

        return acceptOnly(found, "Datacenter");
    }

    private List<Element> acceptOnly(List<Element> found, String... types) {
        return found.stream()
                .filter(e -> {
                    for (String t : types) {
                        if (e.object.getType().equals(t)) {
                            return true;
                        }
                    }
                    return false;
                })
                .collect(Collectors.toList());
    }

    public Element datacenter(String... path)
            throws RuntimeFaultFaultMsg, InvalidPropertyFaultMsg, FinderException {
        List<Element> dcs = datacenterList(path);
        return uniqueResultOrFail(dcs);
    }

    public Element defaultDatacenter()
            throws FinderException, InvalidPropertyFaultMsg, RuntimeFaultFaultMsg {
        List<Element> found = datacenterList("*");
        return uniqueResultOrFail(found);
    }

    public List<Element> datastoreList(String... path)
            throws InvalidPropertyFaultMsg, FinderException, RuntimeFaultFaultMsg {
        List<Element> found = find(datastoreFolder(), false, path);
        return acceptOnly(found, "Datastore");
    }

    public Element datastore(String path) throws
            RuntimeFaultFaultMsg,
            InvalidPropertyFaultMsg,
            FinderException {
        List<Element> found = datastoreList(path);
        return uniqueResultOrFail(found);
    }

    public List<Element> resourcePoolList(String... path) throws
            InvalidPropertyFaultMsg,
            FinderException,
            RuntimeFaultFaultMsg {
        List<Element> found = find(hostFolder(), true, path);

        return acceptOnly(found, "ResourcePool");
    }

    public Element resourcePool(String path) throws
            RuntimeFaultFaultMsg,
            InvalidPropertyFaultMsg,
            FinderException {
        List<Element> found = resourcePoolList(path);

        return uniqueResultOrFail(found);
    }

    public Element defaultResourcePool() throws
            RuntimeFaultFaultMsg,
            InvalidPropertyFaultMsg,
            FinderException {
        List<Element> found = resourcePoolList("*/Resources");
        return uniqueResultOrFail(found);
    }

    private Element uniqueResultOrFail(List<Element> found) throws FinderException {
        if (found.isEmpty()) {
            throw new FinderException("No elements matching the pattern found");
        }

        if (found.size() > 1) {
            List<String> idList = found.stream()
                    .map(o -> o.object.getValue())
                    .collect(Collectors.toList());
            throw new FinderException("More than one elements found: " + idList);
        }

        return found.get(0);
    }

    public Element defaultDatastore() throws
            RuntimeFaultFaultMsg,
            InvalidPropertyFaultMsg,
            FinderException {
        List<Element> found = datastoreList("*");

        return uniqueResultOrFail(found);
    }

    public List<Element> networkList(String... path) throws
            InvalidPropertyFaultMsg,
            FinderException,
            RuntimeFaultFaultMsg {
        List<Element> found = find(networkFolder(), false, path);
        return acceptOnly(found, "Network", "DistributedVirtualPortgroup");
    }

    public Element network(String path) throws
            RuntimeFaultFaultMsg,
            InvalidPropertyFaultMsg,
            FinderException {
        List<Element> found = networkList(path);
        return uniqueResultOrFail(found);
    }

    public Element defaultNetwork() throws
            RuntimeFaultFaultMsg,
            InvalidPropertyFaultMsg,
            FinderException {
        List<Element> found = networkList("*");
        return uniqueResultOrFail(found);
    }

    private Element networkFolder() throws
            InvalidPropertyFaultMsg,
            RuntimeFaultFaultMsg,
            FinderException {
        if (this.networkFolder == null) {
            loadFolders();
        }
        return networkFolder;
    }

    private Element hostFolder() throws
            InvalidPropertyFaultMsg,
            RuntimeFaultFaultMsg,
            FinderException {
        if (this.hostFolder == null) {
            loadFolders();
        }
        return hostFolder;
    }

    private Element datastoreFolder() throws
            InvalidPropertyFaultMsg,
            RuntimeFaultFaultMsg,
            FinderException {
        if (this.datastoreFolder == null) {
            loadFolders();
        }
        return datastoreFolder;
    }

    private Element vmFolder() throws
            InvalidPropertyFaultMsg,
            RuntimeFaultFaultMsg,
            FinderException {
        if (this.vmFolder == null) {
            loadFolders();
        }
        return vmFolder;
    }

    /**
     * Lazily load the datacenter folders
     * @throws InvalidPropertyFaultMsg
     * @throws RuntimeFaultFaultMsg
     * @throws FinderException
     */
    private void loadFolders() throws
            InvalidPropertyFaultMsg,
            RuntimeFaultFaultMsg,
            FinderException {
        GetMoRef get = new GetMoRef(this.connection);
        Map<String, Object> folders = get.entityProps(this.datacenter.object,
                new String[] { "vmFolder", "hostFolder", "datastoreFolder", "networkFolder" });

        this.vmFolder = fullPath((ManagedObjectReference) folders.get("vmFolder"));
        this.hostFolder = fullPath((ManagedObjectReference) folders.get("hostFolder"));
        this.datastoreFolder = fullPath((ManagedObjectReference) folders.get("datastoreFolder"));
        this.networkFolder = fullPath((ManagedObjectReference) folders.get("networkFolder"));
    }

    public Element rootElement() {
        return Element.asRoot(this.connection.getServiceContent().getRootFolder());
    }

    /**
     * A ManagedObjectReference wrapper that implements equals and hashCode
     */
    private static class MoRef {
        private final String type;
        private final String value;

        public MoRef(ManagedObjectReference ref) {
            this.type = ref.getType();
            this.value = ref.getValue();
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            MoRef moRef = (MoRef) o;

            return type.equals(moRef.type) && value.equals(moRef.value);
        }

        @Override
        public int hashCode() {
            int result = type.hashCode();
            result = 31 * result + value.hashCode();
            return result;
        }
    }
}
