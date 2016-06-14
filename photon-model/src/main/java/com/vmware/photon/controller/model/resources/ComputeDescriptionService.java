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
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

import com.esotericsoftware.kryo.serializers.VersionFieldSerializer.Since;

import com.vmware.photon.controller.model.UriPaths;

import com.vmware.xenon.common.FactoryService;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceDocumentDescription;
import com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption;
import com.vmware.xenon.common.StatefulService;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;

/**
 * Describes a compute resource. The same description service instance can be
 * re-used across many compute resources acting as a shared template.
 */
public class ComputeDescriptionService extends StatefulService {
    public static final String FACTORY_LINK = UriPaths.RESOURCES
            + "/compute-descriptions";

    public static FactoryService createFactory() {
        return FactoryService.createIdempotent(ComputeDescriptionService.class);
    }

    /**
     * This class represents the document state associated with a
     * {@link ComputeDescriptionService} task.
     */
    public static class     ComputeDescription extends ResourceState {

        public static final String CUSTOM_PROPERTY_KEY_TEMPLATE = "Template";
        public static final String FIELD_NAME_RESOURCE_POOL_ID = "resourcePoolId";
        public static final String FIELD_NAME_SUPPORTED_CHILDREN = "supportedChildren";
        public static final String FIELD_NAME_ZONE_ID = "zoneId";
        public static final String FIELD_NAME_ID = "id";
        public static final String FIELD_NAME_CUSTOM_PROPERTIES = "customProperties";
        public static final String FIELD_NAME_ENVIRONMENT_NAME = "environmentName";
        public static final String FIELD_NAME_NAME = "name";
        public static final String ENVIRONMENT_NAME_ON_PREMISE = "On premise";
        public static final String ENVIRONMENT_NAME_VCLOUD_AIR = "VMware vCloud Air";
        public static final String ENVIRONMENT_NAME_GCE = "Google Compute Engine";
        public static final String ENVIRONMENT_NAME_AWS = "Amazon Web Services";
        public static final String ENVIRONMENT_NAME_AZURE = "Microsoft Azure";

        /**
         * Types of Compute hosts.
         */
        public static enum ComputeType {
            VM_HOST, VM_GUEST, DOCKER_CONTAINER, PHYSICAL, OS_ON_PHYSICAL
        }

        /**
         * Identifier of this description service instance.
         */
        @UsageOption(option = PropertyUsageOption.UNIQUE_IDENTIFIER)
        @UsageOption(option = PropertyUsageOption.REQUIRED)
        public String id;

        /**
         * Name of this description service instance.
         */
        public String name;

        /**
         * Region identifier of this description service instance.
         */
        public String regionId;

        /**
         * Identifier of the zone associated with this compute host.
         */
        public String zoneId;

        /**
         * The type of the compute instance, as understood by the provider. E.g. the type of
         * instance determines your instance’s CPU capacity, memory, and storage.
         */
        public String instanceType;

        /**
         * Environment/ Platform name this compute host is provisioned on.
         */
        public String environmentName;

        /**
         * Identifier of the data center associated with this compute host.
         */
        public String dataCenterId;

        /**
         * Identifier of the data store associated with this compute host.
         */
        public String dataStoreId;

        /**
         * Identifier of the network associated with this compute host.
         */
        public String networkId;

        /**
         * Self-link to the AuthCredentialsService used to access this compute
         * host.
         */
        public String authCredentialsLink;

        /**
         * List of compute types this host supports actuating.
         */
        public List<String> supportedChildren;

        /**
         * Number of CPU Cores in this host.
         */
        public long cpuCount;

        /**
         * Clock Speed (in Mhz) per CPU Core.
         */
        public long cpuMhzPerCore;

        /**
         * Number of GPU Cores in this host.
         */
        public long gpuCount;

        /**
         * Total amount of memory (in bytes) available on this host.
         */
        public long totalMemoryBytes;

        /**
         * Pricing associated with this host (measured per minute).
         */
        public double costPerMinute;

        /**
         * Currency unit used for pricing.
         */
        public String currencyUnit;

        /**
         * URI reference to the adapter used to create an instance of this host.
         */
        public URI instanceAdapterReference;

        /**
         * URI reference to the adapter used to power-on this host.
         */
        public URI powerAdapterReference;

        /**
         * URI reference to the adapter used to boot this host.
         */
        public URI bootAdapterReference;

        /**
         * URI reference to the adapter used to get the health status of this
         * host.
         */
        public URI healthAdapterReference;

        /**
         * URI reference to the adapter used to get the stats info of this
         * host.
         */
        @Since(032)
        public URI statsAdapterReference;

        /**
         * URI reference to the adapter used to enumerate instances of this
         * host.
         */
        public URI enumerationAdapterReference;
    }

    public ComputeDescriptionService() {
        super(ComputeDescription.class);
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
            ComputeDescription returnState = processInput(put);
            setState(put, returnState);
            put.complete();
        } catch (Throwable t) {
            put.fail(t);
        }
    }

    private ComputeDescription processInput(Operation op) {
        if (!op.hasBody()) {
            throw (new IllegalArgumentException("body is required"));
        }
        ComputeDescription state = op.getBody(ComputeDescription.class);
        validateState(state);
        return state;
    }

    public void validateState(ComputeDescription state) {
        Utils.validateState(getStateDescription(), state);

        if (state.environmentName == null) {
            state.environmentName = ComputeDescription.ENVIRONMENT_NAME_ON_PREMISE;
        }

        validateBootAdapterReference(state);
        validateInstanceAdapterReference(state);

        if (state.cpuCount == 0) {
            state.cpuCount = 1;
        }

        if (state.totalMemoryBytes == 0) {
            state.totalMemoryBytes = (long) Math.pow(2, 30); // 1GB
        }
    }

    private static void validateBootAdapterReference(ComputeDescription state) {
        if (state.supportedChildren == null) {
            return;
        }

        Iterator<String> childIterator = state.supportedChildren.iterator();
        while (childIterator.hasNext()) {
            ComputeDescription.ComputeType type = ComputeDescription.ComputeType
                    .valueOf(childIterator.next());
            switch (type) {
            case PHYSICAL:
            case VM_HOST:
                if (state.bootAdapterReference == null) {
                    throw new IllegalArgumentException(
                            "bootAdapterReference is required");
                }
                if (state.powerAdapterReference == null) {
                    throw new IllegalArgumentException(
                            "powerAdapterReference is required");
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

    private static void validateInstanceAdapterReference(
            ComputeDescription state) {
        if (state.supportedChildren == null) {
            return;
        }
        Iterator<String> childIterator = state.supportedChildren.iterator();
        while (childIterator.hasNext()) {
            ComputeDescription.ComputeType type = ComputeDescription.ComputeType
                    .valueOf(childIterator.next());
            switch (type) {
            case VM_HOST:
                if (state.instanceAdapterReference == null) {
                    throw new IllegalArgumentException(
                            "instanceAdapterReference is required");
                }
                break;
            case DOCKER_CONTAINER:
                break;
            case OS_ON_PHYSICAL:
                break;
            case PHYSICAL:
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
        ComputeDescription currentState = getState(patch);
        ComputeDescription patchBody = getBody(patch);

        boolean hasStateChanged = ResourceUtils.mergeWithState(getStateDescription(), currentState, patchBody);
        ResourceUtils.complePatchOperation(patch, hasStateChanged);
    }

    @Override
    public ServiceDocument getDocumentTemplate() {
        ServiceDocument td = super.getDocumentTemplate();
        ComputeDescription template = (ComputeDescription) td;

        template.bootAdapterReference = UriUtils.buildUri(this.getHost(),
                "/bootAdapterReference");
        template.powerAdapterReference = UriUtils.buildUri(this.getHost(),
                "/powerAdapterReference");
        template.instanceAdapterReference = UriUtils.buildUri(this.getHost(),
                "/instanceAdapterReference");
        template.healthAdapterReference = UriUtils.buildUri(this.getHost(),
                "/healthAdapterReference");
        template.statsAdapterReference = UriUtils.buildUri(this.getHost(),
                "/statsAdapterReference");
        template.enumerationAdapterReference = UriUtils.buildUri(
                this.getHost(), "/enumerationAdapterReference");

        template.dataCenterId = null;
        template.networkId = null;
        template.dataStoreId = null;

        ArrayList<String> children = new ArrayList<>();
        for (ComputeDescription.ComputeType type : ComputeDescription.ComputeType
                .values()) {
            children.add(type.name());
        }
        template.supportedChildren = children;

        // expand the supportedChildren field in order for it to be indexed
        // separately.
        ServiceDocumentDescription.PropertyDescription pdSupportedChildren = template.documentDescription.propertyDescriptions
                .get(ComputeDescription.FIELD_NAME_SUPPORTED_CHILDREN);
        pdSupportedChildren.indexingOptions = EnumSet
                .of(ServiceDocumentDescription.PropertyIndexingOption.EXPAND);

        ServiceDocumentDescription.PropertyDescription pdCustomProperties = template.documentDescription.propertyDescriptions
                .get(ComputeDescription.FIELD_NAME_CUSTOM_PROPERTIES);
        pdCustomProperties.indexingOptions = EnumSet
                .of(ServiceDocumentDescription.PropertyIndexingOption.EXPAND);

        ResourceUtils.updateIndexingOptions(template.documentDescription);

        template.environmentName = ComputeDescription.ENVIRONMENT_NAME_ON_PREMISE;
        template.cpuCount = 2;
        template.gpuCount = 1;
        template.totalMemoryBytes = Integer.MAX_VALUE;
        template.id = UUID.randomUUID().toString();
        template.name = "friendly-name";
        template.regionId = "provider-specific-regions";
        template.zoneId = "provider-specific-zone";
        template.instanceType = "provider-specific-instance-type";
        return template;
    }
}
