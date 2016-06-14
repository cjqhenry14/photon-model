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

import java.util.UUID;

import com.vmware.photon.controller.model.UriPaths;

import com.vmware.xenon.common.FactoryService;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption;
import com.vmware.xenon.common.StatefulService;
import com.vmware.xenon.common.Utils;

/**
 * Represents a snapshot resource.
 */
public class SnapshotService extends StatefulService {
    public static final String FACTORY_LINK = UriPaths.RESOURCES + "/snapshots";

    public static FactoryService createFactory() {
        return FactoryService.createIdempotent(SnapshotService.class);
    }

    /**
     * This class represents the document state associated with a
     * {@link SnapshotService} task.
     */
    public static class SnapshotState extends ResourceState {
        /**
         * Identifier of this snapshot.
         */
        @UsageOption(option = PropertyUsageOption.UNIQUE_IDENTIFIER)
        @UsageOption(option = PropertyUsageOption.REQUIRED)
        public String id;

        /**
         * Name of this snapshot.
         */
        @UsageOption(option = PropertyUsageOption.REQUIRED)
        @UsageOption(option = PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL)
        public String name;

        /**
         * Description of this snapshot.
         */
        @UsageOption(option = PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL)
        public String description;

        /**
         * Compute link for this snapshot.
         */
        @UsageOption(option = PropertyUsageOption.REQUIRED)
        @UsageOption(option = PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL)
        public String computeLink;
    }

    public SnapshotService() {
        super(SnapshotState.class);
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
            SnapshotState returnState = processInput(put);
            setState(put, returnState);
            put.complete();
        } catch (Throwable t) {
            put.fail(t);
        }
    }

    private SnapshotState processInput(Operation op) {
        if (!op.hasBody()) {
            throw (new IllegalArgumentException("body is required"));
        }
        SnapshotState state = op.getBody(SnapshotState.class);
        Utils.validateState(getStateDescription(), state);
        return state;
    }

    @Override
    public void handlePatch(Operation patch) {
        SnapshotState currentState = getState(patch);
        SnapshotState patchBody = getBody(patch);

        boolean hasStateChanged = ResourceUtils.mergeWithState(getStateDescription(),
                currentState, patchBody);
        ResourceUtils.complePatchOperation(patch, hasStateChanged);

    }

    @Override
    public ServiceDocument getDocumentTemplate() {
        ServiceDocument td = super.getDocumentTemplate();
        SnapshotState template = (SnapshotState) td;
        ResourceUtils.updateIndexingOptions(td.documentDescription);

        template.id = UUID.randomUUID().toString();
        template.name = "snapshot01";
        template.description = "";
        return template;
    }
}
