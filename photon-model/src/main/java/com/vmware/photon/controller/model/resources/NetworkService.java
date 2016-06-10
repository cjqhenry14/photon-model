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
import java.util.UUID;

import org.apache.commons.net.util.SubnetUtils;

import com.vmware.photon.controller.model.UriPaths;

import com.vmware.xenon.common.FactoryService;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption;
import com.vmware.xenon.common.StatefulService;

/**
 * Represents a network resource.
 */
public class NetworkService extends StatefulService {

    public static final String FACTORY_LINK = UriPaths.RESOURCES + "/networks";

    public static FactoryService createFactory() {
        return FactoryService.createIdempotent(NetworkService.class);
    }

    /**
     * Network State document.
     */
    public static class NetworkState extends ResourceState {
        public String id;

        /**
         * Name of the network instance
         */
        @UsageOption(option = PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL)
        public String name;

        /**
         * Subnet CIDR
         */
        @UsageOption(option = PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL)
        public String subnetCIDR;

        /**
         * Region identifier of this description service instance.
         */
        @UsageOption(option = PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL)
        public String regionID;

        /**
         * Link to secrets. Required
         */
        @UsageOption(option = PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL)
        public String authCredentialsLink;

        /**
         * The pool which this resource is a part of. Required
         */
        @UsageOption(option = PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL)
        public String resourcePoolLink;

        /**
         * The network adapter to use to create the network. Required
         */
        @UsageOption(option = PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL)
        public URI instanceAdapterReference;
    }

    public NetworkService() {
        super(NetworkState.class);
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
            NetworkState returnState = processInput(put);
            setState(put, returnState);
            put.complete();
        } catch (Throwable t) {
            put.fail(t);
        }
    }

    private NetworkState processInput(Operation op) {
        if (!op.hasBody()) {
            throw (new IllegalArgumentException("body is required"));
        }
        NetworkState state = op.getBody(NetworkState.class);
        validateState(state);
        return state;
    }

    public static void validateState(NetworkState state) {
        if (state.id == null) {
            state.id = UUID.randomUUID().toString();
        }

        if (state.subnetCIDR == null) {
            throw new IllegalArgumentException(
                    "subnet in CIDR notation is required");
        }
        if (state.regionID == null || state.regionID.isEmpty()) {
            throw new IllegalArgumentException("regionID required");
        }

        if (state.authCredentialsLink == null
                || state.authCredentialsLink.isEmpty()) {
            throw new IllegalArgumentException("authCredentialsLink required");
        }

        if (state.resourcePoolLink == null || state.resourcePoolLink.isEmpty()) {
            throw new IllegalArgumentException("resourcePoolLink required");
        }

        if (state.instanceAdapterReference == null) {
            throw new IllegalArgumentException("networkServiceAdapter required");
        }
        // do we have a subnet in CIDR notation
        // creating new SubnetUtils to validate
        new SubnetUtils(state.subnetCIDR);
    }

    @Override
    public void handlePatch(Operation patch) {
        NetworkState currentState = getState(patch);
        NetworkState patchBody = getBody(patch);

        boolean hasStateChanged = ResourceUtils.mergeWithState(getStateDescription(),
                currentState, patchBody);
        ResourceUtils.complePatchOperation(patch, hasStateChanged);
    }

    @Override
    public ServiceDocument getDocumentTemplate() {
        ServiceDocument td = super.getDocumentTemplate();
        NetworkState template = (NetworkState) td;
        ResourceUtils.updateIndexingOptions(td.documentDescription);

        template.id = UUID.randomUUID().toString();
        template.subnetCIDR = "10.1.0.0/16";
        template.name = "cell-network";

        return template;
    }
}
