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

import org.apache.commons.validator.routines.InetAddressValidator;

import com.vmware.photon.controller.model.UriPaths;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption;
import com.vmware.xenon.common.StatefulService;
import com.vmware.xenon.common.Utils;

/**
 * Represents a network interface.
 */
public class NetworkInterfaceService extends StatefulService {

    public static final String FACTORY_LINK = UriPaths.RESOURCES_NETWORKS
            + "/interfaces";

    /**
     * Represents the state of a network interface.
     */
    public static class NetworkInterfaceState extends ResourceState {
        /**
         * The name or id of the interface on the compute.
         */
        @UsageOption(option = PropertyUsageOption.ID)
        @UsageOption(option = PropertyUsageOption.REQUIRED)
        public String id;

        /**
         * The description for the interface. If this is an interface which uses
         * DHCP to resolve its address, then this is a link to the subnet
         * document.
         */
        public String networkDescriptionLink;

        /**
         * The IP information of the interface. Optional.
         */
        @UsageOption(option = PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL)
        public String leaseLink;

        /**
         * The static IP of the interface. Optional. If networkDescriptionLink /
         * leaseLink are defined, this cannot be and vice versa.
         */
        public String address;

        /**
         * The bridge this interface will be instantiated on. Optional.
         */
        public String networkBridgeLink;
    }

    public NetworkInterfaceService() {
        super(NetworkInterfaceState.class);
        super.toggleOption(ServiceOption.PERSISTENCE, true);
        super.toggleOption(ServiceOption.REPLICATION, true);
        super.toggleOption(ServiceOption.OWNER_SELECTION, true);
        super.toggleOption(ServiceOption.IDEMPOTENT_POST, true);
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
            NetworkInterfaceState returnState = processInput(put);
            setState(put, returnState);
            put.complete();
        } catch (Throwable t) {
            put.fail(t);
        }
    }

    private NetworkInterfaceState processInput(Operation op) {
        if (!op.hasBody()) {
            throw (new IllegalArgumentException("body is required"));
        }
        NetworkInterfaceState state = op.getBody(NetworkInterfaceState.class);
        validateState(state);
        return state;
    }

    @Override
    public void handlePatch(Operation patch) {
        NetworkInterfaceState currentState = getState(patch);
        NetworkInterfaceState patchBody = getBody(patch);

        boolean hasStateChanged = ResourceUtils.mergeWithState(getStateDescription(),
                currentState, patchBody);
        ResourceUtils.complePatchOperation(patch, hasStateChanged);

    }

    private void validateState(NetworkInterfaceState state) {
        Utils.validateState(getStateDescription(), state);

        if (state.address != null) {
            if (state.networkDescriptionLink != null) {
                throw new IllegalArgumentException(
                        "both networkDescriptionLink and IP cannot be set");
            }
            if (!InetAddressValidator.getInstance().isValidInet4Address(
                    state.address)) {
                throw new IllegalArgumentException("IP address is invalid");
            }

        } else if (state.networkDescriptionLink == null) {
            throw new IllegalArgumentException(
                    "either IP or networkDescriptionLink must be set");
        }
    }
}
