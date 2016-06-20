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

import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Map.Entry;

import com.vmware.photon.controller.model.UriPaths;
import com.vmware.photon.controller.model.adapterapi.ComputeStatsRequest;
import com.vmware.photon.controller.model.adapterapi.ComputeStatsResponse.ComputeStats;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeStateWithDescription;
import com.vmware.photon.controller.model.tasks.TaskUtils;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.OperationJoin;
import com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption;
import com.vmware.xenon.common.ServiceStats;
import com.vmware.xenon.common.ServiceStats.ServiceStat;
import com.vmware.xenon.common.ServiceStats.TimeSeriesStats;
import com.vmware.xenon.common.ServiceStats.TimeSeriesStats.AggregationType;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.TaskState.TaskStage;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.TaskService;
import com.vmware.xenon.services.common.TaskService.TaskServiceState;

/**
 * Task service to kick off stats collection at a resource level.
 * The stats adapter associated with this resource can return stats
 * data for a set of resources
 *
 */
public class SingleResourceStatsCollectionTaskService extends TaskService<SingleResourceStatsCollectionTaskService.SingleResourceStatsCollectionTaskState> {

    public static final String FACTORY_LINK = UriPaths.MONITORING
            + "/stats-collection-resource-tasks";

    // keep data for an hour, at 1 minute intervals
    private static int numBucketsHourly = 60;
    private static int bucketSizeMinutes = 1000 * 60;

    // keep data for an day, at one hour intervals
    private static int numBucketsDaily = 24;
    private static int bucketSizeHours = bucketSizeMinutes * 60;

    private static String HOUR_SUFFIX = "(Hourly)";
    private static String MIN_SUFFIX = "(Minutes)";

    public enum SingleResourceTaskCollectionStage {
        GET_DESCRIPTIONS, UPDATE_STATS
    }

    public static class SingleResourceStatsCollectionTaskState extends TaskServiceState {
        /**
         * compute resource link
         */
        public String computeLink;

        /**
         * Task state
         */
        public SingleResourceTaskCollectionStage taskStage;

        /**
         * Body to patch back upon task completion
         */
        public Object parentPatchBody;

        /**
         * Task to patch back to
         */
        public String parentLink;

        /**
         * List of stats; this is maintained as part of the
         * state as the adapter patches this back and we
         * want the patch handler to deserialize it generically.
         * Given that the task is non persistent, the cost
         * is minimal
         */
        @UsageOption(option = PropertyUsageOption.SERVICE_USE)
        public List<ComputeStats> statsList;
    }

    public SingleResourceStatsCollectionTaskService() {
        super(SingleResourceStatsCollectionTaskState.class);
    }

    @Override
    public void handleStart(Operation start) {
        try {
            if (!start.hasBody()) {
                start.fail(new IllegalArgumentException("body is required"));
                return;
            }
            SingleResourceStatsCollectionTaskState state = start
                    .getBody(SingleResourceStatsCollectionTaskState.class);

            validateState(state);
            start.complete();
            state.taskInfo = TaskUtils.createTaskState(TaskStage.STARTED);
            state.taskStage = SingleResourceTaskCollectionStage.GET_DESCRIPTIONS;
            TaskUtils.sendPatch(this, state);
        } catch (Throwable e) {
            start.fail(e);
        }
    }

    @Override
    public void handlePatch(Operation patch) {
        SingleResourceStatsCollectionTaskState currentState = getState(patch);
        SingleResourceStatsCollectionTaskState patchState = patch
                .getBody(SingleResourceStatsCollectionTaskState.class);
        updateState(currentState, patchState);
        patch.setBody(currentState);
        patch.complete();

        switch (currentState.taskInfo.stage) {
        case CREATED:
            break;
        case STARTED:
            handleStagePatch(patch, currentState);
            break;
        case FINISHED:
        case FAILED:
        case CANCELLED:
            // this is a one shot task, self delete
            sendRequest(Operation
                    .createPatch(UriUtils.buildUri(getHost(), currentState.parentLink))
                    .setBody(currentState.parentPatchBody)
                    .setCompletion(
                            (patchOp, patchEx) -> {
                                if (patchEx != null) {
                                    logWarning("Patching parent task failed %s",
                                            Utils.toString(patchEx));
                                }
                                sendRequest(Operation
                                        .createDelete(getUri()));
                            }));
            break;
        default:
            break;
        }
    }

    private void validateState(SingleResourceStatsCollectionTaskState state) {
        if (state.computeLink == null) {
            throw new IllegalStateException("computeLink should not be null");
        }
        if (state.parentLink == null) {
            throw new IllegalStateException("parentLink should not be null");
        }
        if (state.parentPatchBody == null) {
            throw new IllegalStateException("parentPatchBody should not be null");
        }
    }

    @Override
    public void updateState(SingleResourceStatsCollectionTaskState currentState,
            SingleResourceStatsCollectionTaskState patchState) {
        if (patchState.taskInfo != null) {
            currentState.taskInfo = patchState.taskInfo;
        }
        if (patchState.taskStage != null) {
            currentState.taskStage = patchState.taskStage;
        }
        if (patchState.statsList != null) {
            currentState.statsList = patchState.statsList;
        }
    }

    private void handleStagePatch(Operation op, SingleResourceStatsCollectionTaskState currentState) {
        switch (currentState.taskStage) {
        case GET_DESCRIPTIONS:
            getDescriptions(op, currentState);
            break;
        case UPDATE_STATS:
            updateStats(op, currentState);
            break;
        default:
            break;
        }
    }

    private void getDescriptions(Operation op, SingleResourceStatsCollectionTaskState currentState) {
        URI computeDescUri = UriUtils.buildExpandLinksQueryUri(UriUtils.buildUri(getHost(),
                currentState.computeLink));
        sendRequest(Operation
                .createGet(computeDescUri)
                .setCompletion(
                        (getOp, getEx) -> {
                            ComputeStateWithDescription computeStateWithDesc = getOp
                                    .getBody(ComputeStateWithDescription.class);
                            ComputeStatsRequest statsRequest = new ComputeStatsRequest();
                            URI patchUri = null;
                            Object patchBody = null;
                            if (computeStateWithDesc.description != null &&
                                    computeStateWithDesc.description.statsAdapterReference != null) {
                                statsRequest.nextStage = SingleResourceTaskCollectionStage.UPDATE_STATS;
                                statsRequest.computeLink = computeStateWithDesc.documentSelfLink;
                                statsRequest.parentTaskLink = getUri().getPath();
                                patchUri = computeStateWithDesc.description.statsAdapterReference;
                                patchBody = statsRequest;
                            } else {
                                // no adapter associated with this resource, just patch completion
                                SingleResourceStatsCollectionTaskState nextStageState = new SingleResourceStatsCollectionTaskState();
                                nextStageState.taskInfo = new TaskState();
                                nextStageState.taskInfo.stage = TaskStage.FINISHED;
                                patchUri = getUri();
                                patchBody = nextStageState;
                            }
                            sendRequest(Operation.createPatch(patchUri)
                                    .setBody(patchBody)
                                    .setCompletion((patchOp, patchEx) -> {
                                        if (patchEx != null) {
                                            TaskUtils.sendFailurePatch(this, new SingleResourceStatsCollectionTaskState(), patchEx);
                                        }
                                    }));
                        }));
    }

    private void updateStats(Operation op, SingleResourceStatsCollectionTaskState currentState) {
        Collection<Operation> operations = new ArrayList<>();
        for (ComputeStats stats : currentState.statsList) {
            URI statsUri = UriUtils.buildStatsUri(getHost(), stats.computeLink);
            // TODO: https://jira-hzn.eng.vmware.com/browse/VSYM-330
            for (Entry<String, ServiceStat> entry : stats.statValues.entrySet()) {
                ServiceStats.ServiceStat minuteStats = new ServiceStats.ServiceStat();
                minuteStats.name = new StringBuffer(entry.getKey()).append(MIN_SUFFIX).toString();
                minuteStats.latestValue = entry.getValue().latestValue;
                minuteStats.unit = entry.getValue().unit;
                minuteStats.timeSeriesStats = new TimeSeriesStats(numBucketsHourly,
                        bucketSizeMinutes, EnumSet.allOf(AggregationType.class));
                ServiceStats.ServiceStat hourStats = new ServiceStats.ServiceStat();
                hourStats.name = new StringBuffer(entry.getKey()).append(HOUR_SUFFIX).toString();
                hourStats.latestValue = entry.getValue().latestValue;
                hourStats.unit = entry.getValue().unit;
                hourStats.timeSeriesStats = new TimeSeriesStats(numBucketsDaily,
                        bucketSizeHours, EnumSet.allOf(AggregationType.class));
                operations.add(Operation.createPost(statsUri)
                        .setBody(hourStats));
                operations.add(Operation.createPost(statsUri)
                        .setBody(minuteStats));
            }
        }
        OperationJoin operationJoin = OperationJoin
                .create(operations)
                .setCompletion(
                        (ops, exc) -> {
                            if (exc != null) {
                                TaskUtils.sendFailurePatch(this, new SingleResourceStatsCollectionTaskState(), exc.values());
                                return;
                            }
                            SingleResourceStatsCollectionTaskState nextStatePatch = new SingleResourceStatsCollectionTaskState();
                            nextStatePatch.taskInfo = TaskUtils.createTaskState(TaskStage.FINISHED);
                            TaskUtils.sendPatch(this, nextStatePatch);
                        });
        operationJoin.sendWith(this);
    }
}
