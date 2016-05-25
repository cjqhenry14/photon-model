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

package com.vmware.photon.controller.model.adapters.util;

import static org.junit.Assert.assertEquals;

import org.junit.Test;


/**
 * Unit test for {@link AdapterUriUtil}
 */
public class AdapterUriUtilTest {

    @Test
    public void testExpand() {
        final String uriTemplate = "/{a}/{b}/test/{c}/{d}/";

        String expandedUri = AdapterUriUtil.expandUriPathTemplate(uriTemplate, "1", "2", "3", "4");

        assertEquals("/1/2/test/3/4/", expandedUri);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testExpandWithMoreVariableValues() {
        final String uriTemplate = "/{a}/{b}/test/{c}/{d}/";

        AdapterUriUtil.expandUriPathTemplate(uriTemplate, "1", "2", "3", "4", "5");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testExpandWithLessVariableValues() {
        final String uriTemplate = "/{a}/{b}/test/{c}/{d}/";

        AdapterUriUtil.expandUriPathTemplate(uriTemplate, "1", "2");
    }


}