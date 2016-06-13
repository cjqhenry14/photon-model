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

package com.vmware.photon.controller.model.resources;

import java.util.List;
import java.util.Map;
import java.util.Set;

import com.vmware.xenon.common.ServiceDocument;

/**
 * Base PODO for all photon model resource services
 */
public class ResourceState extends ServiceDocument {

    public static final String FIELD_NAME_GROUP_LINKS = "groupLinks";

    /**
     * Custom property bag that can be used to store disk specific
     * properties.
     */
    public Map<String, String> customProperties;

    /**
     * A list of tenant links can access this disk resource.
     */
    public List<String> tenantLinks;

    /**
     * Set of groups the resource belongs to
     */
    public Set<String> groupLinks;

}
