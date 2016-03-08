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

package com.vmware.photon.controller.model.tasks.monitoring;

import java.util.concurrent.TimeUnit;

import com.vmware.photon.controller.model.UriPaths;

import com.vmware.xenon.common.FactoryService;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.StatefulService;
import com.vmware.xenon.common.UriUtils;

/**
 * Scheduler service that kicks off stats collection tasks periodically. These tasks are created
 * per resource pool instance and are long living tasks with no expiration
 *
 */
public class StatsCollectionTaskSchedulerService extends StatefulService {

    public static final String FACTORY_LINK = UriPaths.MONITORING + "/stats-collection-scheduler-tasks";

    public static final String STATS_MONITORING_INTERVAL = UriPaths.PROPERTY_PRFIX + "StatsCollectionTaskSchedulerService.monitoringInterval";
    private static final String MONITORING_INTERVAL = System.getProperty(STATS_MONITORING_INTERVAL);

    public static Service createFactory() {
        Service fs = FactoryService.create(StatsCollectionTaskSchedulerService.class,
                StatsCollectionTaskServiceSchedulerState.class);
        return fs;
    }

    /**
     * This class defines the document state associated with a single
     * StatsCollectionTaskSchedulerService instance.
     */
    public static class StatsCollectionTaskServiceSchedulerState extends ServiceDocument {
        /**
         * Reference URI to the resource pool.
         */
        public String resourcePoolLink;
    }

    public StatsCollectionTaskSchedulerService() {
        super(StatsCollectionTaskServiceSchedulerState.class);
        super.toggleOption(ServiceOption.PERSISTENCE, true);
        super.toggleOption(ServiceOption.REPLICATION, true);
        super.toggleOption(ServiceOption.OWNER_SELECTION, true);
        super.toggleOption(ServiceOption.PERIODIC_MAINTENANCE, true);
        long maintenanceInterval = (MONITORING_INTERVAL != null) ?
                TimeUnit.MILLISECONDS.toMicros(Long.valueOf(MONITORING_INTERVAL)) : TimeUnit.MINUTES.toMicros(1);
        super.setMaintenanceIntervalMicros(maintenanceInterval);
    }

    @Override
    public void handleStart(Operation startOp) {
        try {
            if (!startOp.hasBody()) {
                startOp.fail(new IllegalArgumentException("body is required"));
                return;
            }
            StatsCollectionTaskServiceSchedulerState state =
                    startOp.getBody(StatsCollectionTaskServiceSchedulerState.class);
            validateState(state);
            startOp.setBody(state).complete();
        } catch (Throwable t) {
            startOp.fail(t);
        }
    }

    private void validateState(StatsCollectionTaskServiceSchedulerState state) {
        if (state.resourcePoolLink == null) {
            throw new IllegalStateException("resourcePoolLink should not be null");
        }
    }

    @Override
    public void handleMaintenance(Operation maintenanceOp) {
        sendRequest(Operation.createGet(getUri())
                .setCompletion((getOp, getEx) -> {
                    if (getEx != null) {
                        maintenanceOp.fail(getEx);
                        return;
                    }
                    StatsCollectionTaskServiceSchedulerState state =
                            getOp.getBody(StatsCollectionTaskServiceSchedulerState.class);
                    StatsCollectionTaskService.StatsCollectionTaskState statServiceState =
                            new StatsCollectionTaskService.StatsCollectionTaskState();
                    statServiceState.resourcePoolLink = state.resourcePoolLink;
                    // create stats collection task with a well known link; if a task
                    // is already in flight, just complete maintenance
                    statServiceState.documentSelfLink =
                            UriUtils.getLastPathSegment(UriUtils.buildUri(state.documentSelfLink));
                    sendRequest(Operation
                            .createPost(this, StatsCollectionTaskService.FACTORY_LINK)
                            .setBody(statServiceState)
                            .setCompletion(
                                    (o, e) -> {
                                        if (o.getStatusCode() == Operation.STATUS_CODE_CONFLICT) {
                                            logInfo("Stats collector instance already ruunning.");
                                        } else if (e != null) {
                                            maintenanceOp.fail(e);
                                            return;
                                        }
                                        maintenanceOp.complete();
                                    }));
                }));
    }
}
