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

package com.vmware.photon.controller.model.tasks;

import java.util.List;

import com.vmware.photon.controller.model.UriPaths;

import com.vmware.xenon.common.FactoryService;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.services.common.TaskService;
import com.vmware.xenon.services.common.TaskService.TaskServiceState;

/**
 * Task service to invoke other tasks on a schedule. The interval
 * to invoke the tasks is controlled by the maintenance interval
 * set for the service.
 */
public class ScheduledTaskService extends TaskService<ScheduledTaskService.ScheduledTaskState> {
    public static final String FACTORY_LINK = UriPaths.PROVISIONING + "/scheduled-tasks";

    public static FactoryService createFactory() {
        return FactoryService.createIdempotent(ScheduledTaskService.class);
    }

    public static class ScheduledTaskState extends TaskServiceState {
        /**
         * Link to the service factory
         */
        public String factoryLink;

        /**
         * JSON payload to be used for creating the service instance
         */
        public String initialStateJson;

        /**
         * Interval for task execution
         */
        public Long intervalMicros;

        /**
         * A list of tenant links which can access this task.
         */
        public List<String> tenantLinks;
    }

    public ScheduledTaskService() {
        super(ScheduledTaskState.class);
        super.toggleOption(ServiceOption.PERSISTENCE, true);
        super.toggleOption(ServiceOption.REPLICATION, true);
        super.toggleOption(ServiceOption.OWNER_SELECTION, true);
        super.toggleOption(ServiceOption.INSTRUMENTATION, true);
        super.toggleOption(ServiceOption.PERIODIC_MAINTENANCE, true);
    }

    @Override
    public void handleStart(Operation start) {
        try {
            if (!start.hasBody()) {
                start.fail(new IllegalArgumentException("body is required"));
                return;
            }
            ScheduledTaskState state = getBody(start);
            if (state.factoryLink == null) {
                throw new IllegalArgumentException("factoryLink cannot be null");
            }
            if (state.initialStateJson == null) {
                throw new IllegalArgumentException("initialStateJson cannot be null");
            }
            if (state.intervalMicros != null) {
                this.setMaintenanceIntervalMicros(state.intervalMicros);
            }
            start.complete();
        } catch (Throwable e) {
            start.fail(e);
        }
    }

    @Override
    public void handlePatch(Operation patch) {
        patch.complete();
        return;
    }

    @Override
    public void handlePeriodicMaintenance(Operation maintenanceOp) {
        sendRequest(Operation.createGet(getUri())
                .setCompletion((getOp, getEx) -> {
                    if (getEx != null) {
                        maintenanceOp.fail(getEx);
                        return;
                    }
                    ScheduledTaskState state =
                            getOp.getBody(ScheduledTaskState.class);
                    sendRequest(Operation
                            .createPost(this, state.factoryLink)
                            .setBody(state.initialStateJson)
                            .addPragmaDirective(Operation.PRAGMA_DIRECTIVE_FORCE_INDEX_UPDATE)
                            .setCompletion(
                                    (o, e) -> {
                                        // if a task instance is already running, just log the fact
                                        if (o.getStatusCode() == Operation.STATUS_CODE_CONFLICT) {
                                            logInfo("service instance already ruunning.");
                                            maintenanceOp.complete();
                                            return;
                                        } else if (e != null) {
                                            maintenanceOp.fail(e);
                                            return;
                                        }
                                        maintenanceOp.complete();
                                        // patch self to update the version; this tells us
                                        // the number of invocations
                                        ScheduledTaskState patchState = new ScheduledTaskState();
                                        sendRequest(Operation.createPatch(getUri())
                                                .setBody(patchState));
                                    }));
                }));
    }
}
