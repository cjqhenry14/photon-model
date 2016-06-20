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
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

import org.apache.commons.validator.routines.InetAddressValidator;

import com.vmware.photon.controller.model.UriPaths;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription.ComputeType;
import com.vmware.xenon.common.FactoryService;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption;
import com.vmware.xenon.common.StatefulService;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;

/**
 * Represents a compute resource.
 */
public class ComputeService extends StatefulService {

    public static final String FACTORY_LINK = UriPaths.RESOURCES + "/compute";

    public static FactoryService createFactory() {
        return FactoryService.createIdempotent(ComputeService.class);
    }

    /**
     * Power State.
     */
    public static enum PowerState {
        ON, OFF, UNKNOWN, SUSPEND
    }

    /**
     * Power Transition.
     */
    public enum PowerTransition {
        SOFT, HARD
    }

    /**
     * Boot Device.
     */
    public enum BootDevice {
        CDROM, DISK, NETWORK
    }

    /**
     * Compute State document.
     */
    public static class ComputeState extends ResourceState {
        public static final String FIELD_NAME_ID = "id";
        public static final String FIELD_NAME_DESCRIPTION_LINK = "descriptionLink";
        public static final String FIELD_NAME_RESOURCE_POOL_LINK = "resourcePoolLink";
        public static final String FIELD_NAME_CUSTOM_PROPERTIES = "customProperties";
        public static final String FIELD_NAME_PARENT_LINK = "parentLink";
        public static final String CUSTOM_PROPERTY_NAME_RUNTIME_INFO = "runtimeInfo";

        /**
         * Identifier of this compute instance.
         */
        @UsageOption(option = PropertyUsageOption.ID)
        @UsageOption(option = PropertyUsageOption.REQUIRED)
        @UsageOption(option = PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL)
        public String id;

        /**
         * URI reference to corresponding ComputeDescription.
         */
        @UsageOption(option = PropertyUsageOption.REQUIRED)
        @UsageOption(option = PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL)
        public String descriptionLink;

        /**
         * URI reference to corresponding resource pool.
         */
        @UsageOption(option = PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL)
        public String resourcePoolLink;

        /**
         * Ip address of this compute instance.
         */
        public String address;

        /**
         * MAC address of this compute instance.
         */
        @UsageOption(option = PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL)
        public String primaryMAC;

        /**
         * Power state of this compute instance.
         */
        public PowerState powerState = PowerState.UNKNOWN;

        /**
         * URI reference to parent compute instance.
         */
        public String parentLink;

        /**
         * URI reference to resource pool management site.
         */
        @UsageOption(option = PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL)
        public URI adapterManagementReference;

        /**
         * Disks associated with this compute instance.
         */
        public List<String> diskLinks;

        /**
         * Network interfaces associated with this compute instance.
         */
        public List<String> networkLinks;
    }

    /**
     * State with in-line, expanded description.
     */
    public static class ComputeStateWithDescription extends ComputeState {
        /**
         * Compute description associated with this compute instance.
         */
        public ComputeDescription description;

        public static URI buildUri(URI computeHostUri) {
            return UriUtils.extendUriWithQuery(computeHostUri,
                    UriUtils.URI_PARAM_ODATA_EXPAND,
                    ComputeState.FIELD_NAME_DESCRIPTION_LINK);
        }

        public static ComputeStateWithDescription create(
                ComputeDescription desc, ComputeState currentState) {
            ComputeStateWithDescription chsWithDesc = new ComputeStateWithDescription();
            currentState.copyTo(chsWithDesc);

            chsWithDesc.address = currentState.address;
            chsWithDesc.diskLinks = currentState.diskLinks;
            chsWithDesc.id = currentState.id;
            chsWithDesc.parentLink = currentState.parentLink;
            chsWithDesc.powerState = currentState.powerState;
            chsWithDesc.primaryMAC = currentState.primaryMAC;
            chsWithDesc.resourcePoolLink = currentState.resourcePoolLink;
            chsWithDesc.adapterManagementReference = currentState.adapterManagementReference;
            chsWithDesc.customProperties = currentState.customProperties;
            chsWithDesc.networkLinks = currentState.networkLinks;
            chsWithDesc.tenantLinks = currentState.tenantLinks;

            chsWithDesc.description = desc;
            chsWithDesc.descriptionLink = desc.documentSelfLink;

            return chsWithDesc;
        }
    }

    public ComputeService() {
        super(ComputeState.class);
        super.toggleOption(ServiceOption.PERSISTENCE, true);
        super.toggleOption(ServiceOption.REPLICATION, true);
        super.toggleOption(ServiceOption.OWNER_SELECTION, true);
    }

    @Override
    public void handleGet(Operation get) {
        ComputeState currentState = getState(get);
        boolean doExpand = get.getUri().getQuery() != null &&
                UriUtils.hasODataExpandParamValue(get.getUri());

        if (!doExpand) {
            get.setBody(currentState).complete();
            return;
        }

        // retrieve the description and include in an augmented version of our
        // state.
        Operation getDesc = Operation
                .createGet(this, currentState.descriptionLink)
                .setCompletion(
                        (o, e) -> {
                            if (e != null) {
                                get.fail(e);
                                return;
                            }
                            ComputeDescription desc = o
                                    .getBody(ComputeDescription.class);
                            ComputeStateWithDescription chsWithDesc = ComputeStateWithDescription
                                    .create(desc, currentState);
                            get.setBody(chsWithDesc).complete();
                        });
        sendRequest(getDesc);
    }

    @Override
    public void handleStart(Operation start) {
        try {
            validateStartOrPut(start);
            start.complete();
        } catch (Throwable t) {
            start.fail(t);
        }
    }

    @Override
    public void handlePut(Operation put) {
        try {
            ComputeState returnState = validateStartOrPut(put);
            setState(put, returnState);
            put.complete();
        } catch (Throwable t) {
            put.fail(t);
        }
    }

    private ComputeState validateStartOrPut(Operation op) {
        if (!op.hasBody()) {
            throw (new IllegalArgumentException("body is required"));
        }
        ComputeState state = op.getBody(ComputeState.class);
        Utils.validateState(getStateDescription(), state);
        return state;
    }

    public static void validateSupportedChildren(ComputeState state,
            ComputeDescription description) {

        if (description.supportedChildren == null) {
            return;
        }

        Iterator<String> childIterator = description.supportedChildren
                .iterator();
        while (childIterator.hasNext()) {
            ComputeType type = ComputeType.valueOf(childIterator.next());
            switch (type) {
            case VM_HOST:
            case PHYSICAL:
                if (state.adapterManagementReference == null) {
                    throw new IllegalArgumentException(
                            "adapterManagementReference is required");
                }
                break;
            case DOCKER_CONTAINER:
                break;
            case OS_ON_PHYSICAL:
                break;
            case VM_GUEST:
                break;
            default:
                break;
            }
        }
    }

    @Override
    public void handlePatch(Operation patch) {
        ComputeState currentState = getState(patch);
        ComputeState patchBody = getBody(patch);

        boolean hasStateChanged = ResourceUtils.mergeWithState(getStateDescription(), currentState, patchBody);

        if (patchBody.address != null
                && !patchBody.address.equals(currentState.address)) {
            InetAddressValidator.getInstance().isValidInet4Address(
                    patchBody.address);
            currentState.address = patchBody.address;
            hasStateChanged = true;
        }

        if (patchBody.powerState != null
                && patchBody.powerState != PowerState.UNKNOWN
                && patchBody.powerState != currentState.powerState) {
            currentState.powerState = patchBody.powerState;
            hasStateChanged = true;
        }

        if (patchBody.diskLinks != null) {
            if (currentState.diskLinks == null) {
                currentState.diskLinks = patchBody.diskLinks;
                hasStateChanged = true;
            } else {
                for (String link : patchBody.diskLinks) {
                    if (!currentState.diskLinks.contains(link)) {
                        currentState.diskLinks.add(link);
                        hasStateChanged = true;
                    }
                }
            }
        }

        if (patchBody.networkLinks != null) {
            if (currentState.networkLinks == null) {
                currentState.networkLinks = patchBody.networkLinks;
                hasStateChanged = true;
            } else {
                for (String link : patchBody.networkLinks) {
                    if (!currentState.networkLinks.contains(link)) {
                        currentState.networkLinks.add(link);
                        hasStateChanged = true;
                    }
                }
            }
        }

        ResourceUtils.complePatchOperation(patch, hasStateChanged);
    }

    @Override
    public ServiceDocument getDocumentTemplate() {
        ServiceDocument td = super.getDocumentTemplate();

        ComputeState template = (ComputeState) td;

        template.id = UUID.randomUUID().toString();
        template.primaryMAC = "01:23:45:67:89:ab";
        template.descriptionLink = UriUtils.buildUriPath(
                ComputeDescriptionService.FACTORY_LINK,
                "on-prem-one-cpu-vm-guest");
        template.resourcePoolLink = null;
        template.adapterManagementReference = URI
                .create("https://esxhost-01:443/sdk");

        return template;
    }
}
