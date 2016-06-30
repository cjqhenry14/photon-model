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

package com.vmware.photon.controller.model.monitoring;

import com.vmware.photon.controller.model.UriPaths;

import com.vmware.xenon.common.FactoryService;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption;
import com.vmware.xenon.common.StatefulService;
import com.vmware.xenon.common.Utils;

public class ResourceMetricService extends StatefulService {

    public static final String FACTORY_LINK = UriPaths.MONITORING + "/metrics";

    public static FactoryService createFactory() {
        return FactoryService.createIdempotent(ResourceMetricService.class);
    }

    public ResourceMetricService() {
        super(ResourceMetric.class);
        super.toggleOption(ServiceOption.PERSISTENCE, true);
        super.toggleOption(ServiceOption.ON_DEMAND_LOAD, true);
    }

    public static class ResourceMetric extends ServiceDocument {
        public static final int VERSION_RETENTION_LIMIT = Integer.getInteger(
                UriPaths.PROPERTY_PREFIX + ResourceMetric.class.getSimpleName()
                + ".VERSION_RETENTION_LIMIT", 50000);
        public static final String FIELD_NAME_VALUE = "value";
        public static final String FIELD_NAME_TIMESTAMP = "timestampMicrosUtc";

        @Documentation(description = "The average value returned by the cloud provider")
        @UsageOption(option = PropertyUsageOption.REQUIRED)
        public Double value;

        @Documentation(description = "The timestamp returned by the cloud provider")
        @UsageOption(option = PropertyUsageOption.REQUIRED)
        public Long timestampMicrosUtc;
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
            ResourceMetric returnState = processInput(put);
            setState(put, returnState);
            put.complete();
        } catch (Throwable t) {
            put.fail(t);
        }
    }

    private ResourceMetric processInput(Operation op) {
        if (!op.hasBody()) {
            throw (new IllegalArgumentException("body is required"));
        }
        ResourceMetric state = op.getBody(ResourceMetric.class);
        Utils.validateState(getStateDescription(), state);
        return state;
    }

    @Override
    public ServiceDocument getDocumentTemplate() {
        ServiceDocument serviceDocument = super.getDocumentTemplate();
        serviceDocument.documentDescription.versionRetentionLimit = ResourceMetric.VERSION_RETENTION_LIMIT;
        return serviceDocument;
    }
}
