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

import java.util.EnumSet;
import java.util.UUID;

import com.vmware.photon.controller.model.UriPaths;

import com.vmware.xenon.common.FactoryService;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption;
import com.vmware.xenon.common.StatefulService;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.QueryTask;

/**
 * Describes a resource pool. Resources are associated with a resource pool
 * through the resourcePoolLink which allows planning and allocation tasks to
 * track the desired versus realized resources.
 */
public class ResourcePoolService extends StatefulService {

    public static final String FACTORY_LINK = UriPaths.RESOURCES + "/pools";

    public static FactoryService createFactory() {
        return FactoryService.createIdempotent(ResourcePoolService.class);
    }

    /**
     * This class represents the document state associated with a
     * {@link ResourcePoolService} task.
     */
    public static class ResourcePoolState extends ResourceState {

        /**
         * Enumeration used to define properties of the resource pool.
         */
        public enum ResourcePoolProperty {
            ELASTIC, HYBRID
        }

        /**
         * Identifier of this resource pool.
         */
        public String id;

        /**
         * Name of this resource pool.
         */
        @UsageOption(option = PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL)
        public String name;

        /**
         * Project name of this resource pool.
         */
        @UsageOption(option = PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL)
        public String projectName;

        /**
         * Properties of this resource pool, if it is elastic, hybrid etc.
         */
        public EnumSet<ResourcePoolProperty> properties;

        /**
         * Minimum number of CPU Cores in this resource pool.
         */
        public long minCpuCount;

        /**
         * Minimum number of GPU Cores in this resource pool.
         */
        public long minGpuCount;

        /**
         * Minimum amount of memory (in bytes) in this resource pool.
         */
        public long minMemoryBytes;

        /**
         * Minimum disk capacity (in bytes) in this resource pool.
         */
        public long minDiskCapacityBytes;

        /**
         * Maximum number of CPU Cores in this resource pool.
         */
        public long maxCpuCount;

        /**
         * Maximum number of GPU Cores in this resource pool.
         */
        public long maxGpuCount;

        /**
         * Maximum amount of memory (in bytes) in this resource pool.
         */
        public long maxMemoryBytes;

        /**
         * Maximum disk capacity (in bytes) in this resource pool.
         */
        public long maxDiskCapacityBytes;

        /**
         * Maximum CPU Cost (per minute) in this resource pool.
         */
        @UsageOption(option = PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL)
        public Double maxCpuCostPerMinute;

        /**
         * Maximum Disk cost (per minute) in this resource pool.
         */
        @UsageOption(option = PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL)
        public Double maxDiskCostPerMinute;

        /**
         * Currency unit used for pricing.
         */
        public String currencyUnit;

        /**
         * Query description for the resource pool.
         */
        public QueryTask.QuerySpecification querySpecification;
    }

    public ResourcePoolService() {
        super(ResourcePoolState.class);
        super.toggleOption(ServiceOption.PERSISTENCE, true);
        super.toggleOption(ServiceOption.REPLICATION, true);
        super.toggleOption(ServiceOption.OWNER_SELECTION, true);
        super.toggleOption(ServiceOption.HTML_USER_INTERFACE, true);
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
            ResourcePoolState returnState = processInput(put);
            setState(put, returnState);
            put.complete();
        } catch (Throwable t) {
            put.fail(t);
        }
    }

    private ResourcePoolState processInput(Operation op) {
        if (!op.hasBody()) {
            throw (new IllegalArgumentException("body is required"));
        }
        ResourcePoolState state = op.getBody(ResourcePoolState.class);
        validateState(state);
        createResourceQuerySpec(state);
        return state;
    }

    @Override
    public void handlePatch(Operation patch) {
        ResourcePoolState currentState = getState(patch);
        ResourcePoolState patchBody = getBody(patch);

        boolean hasStateChanged = ResourceUtils.mergeWithState(getStateDescription(),
                currentState, patchBody);
        ResourceUtils.complePatchOperation(patch, hasStateChanged);
    }

    public static void validateState(ResourcePoolState state) {
        if (state.id == null) {
            state.id = UUID.randomUUID().toString();
        }
        if (state.properties == null) {
            state.properties = EnumSet
                    .noneOf(ResourcePoolState.ResourcePoolProperty.class);
        }
    }

    private void createResourceQuerySpec(ResourcePoolState initState) {

        QueryTask.QuerySpecification querySpec = new QueryTask.QuerySpecification();

        String kind = Utils.buildKind(ComputeService.ComputeState.class);
        QueryTask.Query kindClause = new QueryTask.Query().setTermPropertyName(
                ServiceDocument.FIELD_NAME_KIND).setTermMatchValue(kind);
        querySpec.query.addBooleanClause(kindClause);

        // we want compute resources only in the same resource pool as this task
        QueryTask.Query resourcePoolClause = new QueryTask.Query()
                .setTermPropertyName(
                        ComputeService.ComputeState.FIELD_NAME_RESOURCE_POOL_LINK)
                .setTermMatchValue(getSelfLink());
        querySpec.query.addBooleanClause(resourcePoolClause);

        initState.querySpecification = querySpec;
    }

    @Override
    public ServiceDocument getDocumentTemplate() {
        ServiceDocument td = super.getDocumentTemplate();
        ResourceUtils.updateIndexingOptions(td.documentDescription);
        return td;
    }
}
