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
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption;
import com.vmware.xenon.common.StatefulService;
import com.vmware.xenon.common.Utils;

public class StorageDescriptionService extends StatefulService {

    public static final String FACTORY_LINK = UriPaths.RESOURCES + "/storage-descriptions";

    public StorageDescriptionService() {
        super(StorageDescription.class);
        super.toggleOption(ServiceOption.PERSISTENCE, true);
        super.toggleOption(ServiceOption.REPLICATION, true);
        super.toggleOption(ServiceOption.OWNER_SELECTION, true);
        super.toggleOption(ServiceOption.IDEMPOTENT_POST, true);
    }

    /**
     * This class represents the document state associated with a
     * {@link com.vmware.photon.controller.model.resources.StorageDescriptionService} task.
     */
    public static class StorageDescription extends ResourceState {
        /**
         * Identifier of the Storage service Instance
         */
        @UsageOption(option = PropertyUsageOption.ID)
        @UsageOption(option = PropertyUsageOption.REQUIRED)
        public String id;

        /**
         * Region or the Location the storage belongs to.
         */
        @UsageOption(option = PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL)
        public String regionId;

        /**
         * Name of the Storage description.
         */
        @UsageOption(option = PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL)
        @UsageOption(option = PropertyUsageOption.REQUIRED)
        public String name;

        /**
         * Type of Storage.
         */
        @UsageOption(option = PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL)
        public String type;

        /**
         * Self-link to the AuthCredentialsService used to access this compute
         * host.
         */
        @UsageOption(option = PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL)
        public String authCredentialsLink;

        /**
         * The pool which this resource is a part of.
         */
        @UsageOption(option = PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL)
        public String resourcePoolLink;
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
            StorageDescription returnState = processInput(put);
            setState(put, returnState);
            put.complete();
        } catch (Throwable t) {
            put.fail(t);
        }
    }

    private StorageDescription processInput(Operation op) {
        if (!op.hasBody()) {
            throw (new IllegalArgumentException("body is required"));
        }
        StorageDescription state = op.getBody(StorageDescription.class);
        Utils.validateState(getStateDescription(), state);
        return state;
    }

    @Override
    public void handlePatch(Operation patch) {
        StorageDescription currentState = getState(patch);
        StorageDescription patchBody = getBody(patch);

        boolean hasStateChanged = ResourceUtils.mergeWithState(getStateDescription(), currentState, patchBody);
        ResourceUtils.complePatchOperation(patch, hasStateChanged);
    }
}
