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

import java.net.URI;
import java.util.List;

import com.vmware.photon.controller.model.UriPaths;
import com.vmware.xenon.common.FactoryService;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption;
import com.vmware.xenon.common.StatefulService;
import com.vmware.xenon.common.Utils;


/**
 * Describes the resource that is used by a compute type.
 */
public class ResourceDescriptionService extends StatefulService {

    public static final String FACTORY_LINK = UriPaths.RESOURCES
            + "/resource-descriptions";

    public static FactoryService createFactory() {
        return FactoryService.createIdempotent(ResourceDescriptionService.class);
    }

    /**
     * This class represents the document state associated with a
     * {@link ResourceDescriptionService} task.
     */
    public static class ResourceDescription extends ResourceState {

        /**
         * Type of compute to create. Used to find Computes which can create
         * this child.
         */
        @UsageOption(option = PropertyUsageOption.REQUIRED)
        public String computeType;

        /**
         * The compute description that defines the resource instances.
         */
        @UsageOption(option = PropertyUsageOption.REQUIRED)
        public String computeDescriptionLink;

        /**
         * The disk descriptions used as a templates to create a disk per
         * resource.
         */
        public List<String> diskDescriptionLinks;

        /**
         * The network descriptions used to associate network resources with
         * compute resources.
         */
        public List<String> networkDescriptionLinks;

        /**
         * The network bridge descriptions used to associate bridges to the
         * computes created here. after they are instantiated.
         */
        public String networkBridgeDescriptionLink;

        /**
         * The URI to the network bridge task service.
         */
        public URI networkBridgeTaskServiceReference;
    }

    public ResourceDescriptionService() {
        super(ResourceDescription.class);
        super.toggleOption(Service.ServiceOption.PERSISTENCE, true);
        super.toggleOption(Service.ServiceOption.REPLICATION, true);
        super.toggleOption(Service.ServiceOption.OWNER_SELECTION, true);
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
            ResourceDescription returnState = processInput(put);
            setState(put, returnState);
            put.complete();
        } catch (Throwable t) {
            put.fail(t);
        }
    }

    @Override
    public void handlePatch(Operation patch) {
        ResourceDescription currentState = getState(patch);
        ResourceDescription patchBody = getBody(patch);

        boolean hasStateChanged = ResourceUtils.mergeWithState(getStateDescription(),
                currentState, patchBody);
        ResourceUtils.complePatchOperation(patch, hasStateChanged);

    }

    private ResourceDescription processInput(Operation op) {
        if (!op.hasBody()) {
            throw (new IllegalArgumentException("body is required"));
        }
        ResourceDescription state = op.getBody(ResourceDescription.class);
        Utils.validateState(getStateDescription(), state);
        return state;
    }
}
