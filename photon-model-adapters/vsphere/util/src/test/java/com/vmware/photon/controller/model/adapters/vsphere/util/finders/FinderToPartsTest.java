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

import static org.junit.Assert.assertEquals;

import java.util.Arrays;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

/**
 * Based on https://github.com/vmware/govmomi/blob/master/list/path_test.go
 */
@RunWith(Parameterized.class)
public class FinderToPartsTest {

    private final String path;
    private final String[] parts;

    public FinderToPartsTest(String path, String[] parts) {
        this.path = path;
        this.parts = parts;
    }

    @Parameters
    public static Iterable<Object[]> data() {
        return Arrays.asList(new Object[][] {
                { "/", new String[] {} },
                { "/foo", new String[] { "foo" } },
                { "/foo/..", new String[] {} },
                { "/./foo", new String[] { "foo" } },
                { "/foo/bar", new String[] { "foo", "bar" } },
                { "/foo/bar/..", new String[] { "foo" } },
                { "", new String[] { "." } },
                { ".", new String[] { "." } },
                { "foo", new String[] { ".", "foo" } },
                { "foo/..", new String[] { "." } },
                { "./foo", new String[] { ".", "foo" } },
                { "./foo", new String[] { ".", "foo" } },
                { "../foo", new String[] { "..", "foo" } },
                { "foo/bar/..", new String[] { ".", "foo" } },
        });
    }

    @Test
    public void toParts() {
        assertEquals(Arrays.asList(parts), Finder.toParts(path));
    }
}
