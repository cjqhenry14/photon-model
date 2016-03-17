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
import java.util.Map;
import java.util.UUID;

import com.vmware.photon.controller.model.UriPaths;
import com.vmware.xenon.common.FactoryService;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceDocumentDescription;
import com.vmware.xenon.common.StatefulService;
import com.vmware.xenon.common.UriUtils;

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
    public static class SnapshotState extends ServiceDocument {
        public static final String FIELD_NAME_DESCRIPTION_LINK = "descriptionLink";

        /**
         * Identifier of this snapshot.
         */
        public String id;

        /**
         * Name of this snapshot.
         */
        public String name;

        /**
         * Description of this snapshot.
         */
        public String description;

        /**
         * Compute link for this snapshot.
         */
        public String computeLink;

        /**
         * Custom properties passes in for the resources to be provisioned.
         */
        public Map<String, String> customProperties;

        /**
         * A list of tenant links which can access this compute resource.
         */
        public List<String> tenantLinks;

        public static URI buildUri(URI snapshotUri) {
            return UriUtils.extendUriWithQuery(snapshotUri,
                    UriUtils.URI_PARAM_ODATA_EXPAND,
                    SnapshotState.FIELD_NAME_DESCRIPTION_LINK);
        }
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
        validateState(state);
        return state;
    }

    public static void validateState(SnapshotState state) {
        if (state.id == null) {
            state.id = UUID.randomUUID().toString();
        }

        if (state.name == null || state.name.isEmpty()) {
            throw new IllegalArgumentException("name is required");
        }

        if (state.computeLink == null || state.computeLink.isEmpty()) {
            throw new IllegalArgumentException("computeLink is required");
        }
    }

    @Override
    public void handlePatch(Operation patch) {
        SnapshotState currentState = getState(patch);
        SnapshotState patchBody = patch.getBody(SnapshotState.class);

        boolean isChanged = false;

        if (patchBody.name != null && !patchBody.name.equals(currentState.name)) {
            currentState.name = patchBody.name;
            isChanged = true;
        }

        if (patchBody.description != null
                && !patchBody.description.equals(currentState.description)) {
            currentState.description = patchBody.description;
            isChanged = true;
        }

        if (patchBody.computeLink != null
                && !patchBody.computeLink.equals(currentState.computeLink)) {
            currentState.computeLink = patchBody.computeLink;
            isChanged = true;
        }

        if (patchBody.customProperties != null
                && !patchBody.customProperties.isEmpty()) {
            if (currentState.customProperties == null
                    || currentState.customProperties.isEmpty()) {
                currentState.customProperties = patchBody.customProperties;
            } else {
                for (Map.Entry<String, String> e : patchBody.customProperties
                        .entrySet()) {
                    currentState.customProperties.put(e.getKey(), e.getValue());
                }
            }

            isChanged = true;
        }

        if (!isChanged) {
            patch.setStatusCode(Operation.STATUS_CODE_NOT_MODIFIED);
        }

        patch.complete();
    }

    @Override
    public ServiceDocument getDocumentTemplate() {
        ServiceDocument td = super.getDocumentTemplate();
        SnapshotState template = (SnapshotState) td;

        ServiceDocumentDescription.expandTenantLinks(td.documentDescription);

        template.id = UUID.randomUUID().toString();
        template.name = "snapshot01";
        template.description = "";
        return template;
    }
}
