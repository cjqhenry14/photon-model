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

import com.vmware.photon.controller.model.UriPaths;

import com.vmware.xenon.common.FactoryService;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption;
import com.vmware.xenon.common.StatefulService;

/**
 * Describes a resource group instance. A resource group is a grouping
 * of photon model resources that have the same groupLink field
 */
public class ResourceGroupService extends StatefulService {

    public static final String FACTORY_LINK = UriPaths.RESOURCES + "/groups";

    public static FactoryService createFactory() {
        return FactoryService.createIdempotent(ResourceGroupService.class);
    }

    /**
     * This class represents the document state associated with a
     * {@link com.vmware.photon.controller.model.resources.ResourceGroupService}.
     */
    public static class ResourceGroupState extends ResourceState {
        /**
         * name of the resource group instance
         */
        @UsageOption(option = PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL)
        public String name;
    }

    public ResourceGroupService() {
        super(ResourceGroupState.class);
        super.toggleOption(ServiceOption.PERSISTENCE, true);
        super.toggleOption(ServiceOption.REPLICATION, true);
        super.toggleOption(ServiceOption.OWNER_SELECTION, true);
    }

    @Override
    public void handleStart(Operation start) {
        try {
            processInput(start);
            start.complete();
        } catch (Throwable t) {
            start.fail(t);
        }
    }

    @Override
    public void handlePut(Operation put) {
        try {
            ResourceGroupState returnState = processInput(put);
            setState(put, returnState);
            put.complete();
        } catch (Throwable t) {
            put.fail(t);
        }
    }

    private ResourceGroupState processInput(Operation op) {
        if (!op.hasBody()) {
            throw (new IllegalArgumentException("body is required"));
        }
        ResourceGroupState state = op.getBody(ResourceGroupState.class);
        validateState(state);
        return state;
    }

    private void validateState(ResourceGroupState state) {
        if (state.name == null) {
            throw new IllegalArgumentException("name is required");
        }

    }

    @Override
    public void handlePatch(Operation patch) {
        ResourceGroupState currentState = getState(patch);
        ResourceGroupState patchBody = getBody(patch);

        boolean hasStateChanged = ResourceUtils.mergeWithState(getStateDescription(),
                currentState, patchBody);
        ResourceUtils.complePatchOperation(patch, hasStateChanged);

    }

    @Override
    public ServiceDocument getDocumentTemplate() {
        ServiceDocument td = super.getDocumentTemplate();
        ResourceGroupState template = (ResourceGroupState) td;
        ResourceUtils.updateIndexingOptions(td.documentDescription);
        template.name = "resource-group-1";
        return template;
    }
}
