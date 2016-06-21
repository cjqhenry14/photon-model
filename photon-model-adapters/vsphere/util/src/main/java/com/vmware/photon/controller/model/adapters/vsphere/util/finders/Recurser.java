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
import java.util.List;

import org.apache.oro.text.GlobCompiler;
import org.apache.oro.text.regex.MalformedPatternException;
import org.apache.oro.text.regex.Pattern;
import org.apache.oro.text.regex.Perl5Matcher;

import com.vmware.photon.controller.model.adapters.vsphere.util.connection.BaseHelper;
import com.vmware.photon.controller.model.adapters.vsphere.util.connection.Connection;
import com.vmware.vim25.InvalidPropertyFaultMsg;
import com.vmware.vim25.RuntimeFaultFaultMsg;

/**
 * Based on https://github.com/vmware/govmomi/blob/master/list/recurser.go
 */
public class Recurser extends BaseHelper {
    private final GlobCompiler globCompiler;

    private final Perl5Matcher matcher;

    /**
     * TraverseLeafs configures the Recurser to traverse traversable leaf nodes.
     * This is typically set to true when used from the ls command, where listing
     * a folder means listing its contents. This is typically set to false for
     * commands that take managed entities that are not folders as input.
     */
    protected boolean traverseLeafs;

    public Recurser(Connection connection) {
        super(connection);

        this.globCompiler = new GlobCompiler();
        this.matcher = new Perl5Matcher();
    }

    public List<Element> recurse(Element root, String... parts)
            throws InvalidPropertyFaultMsg, FinderException, RuntimeFaultFaultMsg {
        if (parts == null || parts.length == 0) {
            if (!Lister.isTraversable(root.object) || !this.traverseLeafs) {
                return Collections.singletonList(root);
            }
        }

        Lister lister = new Lister(this.connection, root.object, root.path);

        List<Element> listed = lister.list();

        // This folder is a leaf as far as the glob goes.
        if (parts.length == 0) {
            return listed;
        }

        String pattern = parts[0];
        parts = Arrays.copyOfRange(parts, 1, parts.length);

        List<Element> out = new ArrayList<>();
        for (Element e : listed) {
            boolean matched = matches(pattern, e.path);

            if (!matched) {
                continue;
            }

            List<Element> nres = this.recurse(e, parts);
            out.addAll(nres);
        }

        return out;
    }

    private boolean matches(String pattern, String path) throws FinderException {
        Pattern compiled;

        try {
            compiled = this.globCompiler.compile(pattern);
        } catch (MalformedPatternException e) {
            throw new FinderException("Bad glob pattern: " + pattern, e);
        }

        path = basename(path);

        return this.matcher.matches(path, compiled);
    }

    private String basename(String path) {
        return Paths.get(path).getFileName().toString();
    }

    public boolean isTraverseLeafs() {
        return this.traverseLeafs;
    }

    public void setTraverseLeafs(boolean traverseLeafs) {
        this.traverseLeafs = traverseLeafs;
    }
}
