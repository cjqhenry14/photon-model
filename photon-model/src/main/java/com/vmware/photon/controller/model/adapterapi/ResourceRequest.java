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

package com.vmware.photon.controller.model.adapterapi;

import java.net.URI;

import com.vmware.xenon.common.UriUtils;

/**
 * Base class for all Request types to the Adapters.
 */
public abstract class ResourceRequest {

    /**
     * The URI of resource instance in whose context this request is initiated
     */
    public URI resourceReference;

    /**
     * URI reference to calling task.
     */
    public URI taskReference;

    /**
     * Value indicating whether the service should treat this as a mock request and complete the
     * work flow without involving the underlying compute host infrastructure.
     */
    public boolean isMockRequest;

    /**
     * A method build URI to a given resource by it's relative link.
     *
     * @param resourceLink
     *            the link to a given resource.
     * @return returns the full URI the resource under passed resourceLink parameter.
     */
    public URI buildUri(String resourceLink) {
        return UriUtils.buildUri(this.resourceReference, resourceLink);
    }
}
