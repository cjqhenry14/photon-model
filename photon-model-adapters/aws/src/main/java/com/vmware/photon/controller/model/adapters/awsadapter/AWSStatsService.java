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

package com.vmware.photon.controller.model.adapters.awsadapter;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import com.amazonaws.handlers.AsyncHandler;
import com.amazonaws.services.cloudwatch.AmazonCloudWatchAsyncClient;
import com.amazonaws.services.cloudwatch.model.Datapoint;
import com.amazonaws.services.cloudwatch.model.Dimension;
import com.amazonaws.services.cloudwatch.model.GetMetricStatisticsRequest;
import com.amazonaws.services.cloudwatch.model.GetMetricStatisticsResult;

import com.vmware.photon.controller.model.adapterapi.ComputeStatsRequest;
import com.vmware.photon.controller.model.adapterapi.ComputeStatsResponse;
import com.vmware.photon.controller.model.adapterapi.ComputeStatsResponse.ComputeStats;
import com.vmware.photon.controller.model.adapters.util.AdapterUtils;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeStateWithDescription;

import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.AuthCredentialsService;
import com.vmware.xenon.services.common.AuthCredentialsService.AuthCredentialsServiceState;

public class AWSStatsService extends StatelessService {

    public static final String SELF_LINK = AWSUriPaths.AWS_STATS_SERVICE;

    private static final String[] METRIC_NAMES = { "CPUUtilization", "DiskReadBytes",
            "DiskWriteBytes", "NetworkIn", "NetworkOut" };
    private static final String[] STATISTICS = { "Average" };
    private static final String NAMESPACE = "AWS/EC2";
    private static final String DIMENSION_INSTANCE_ID = "InstanceId";

    private class AWSStatsDataHolder {
        public ComputeStateWithDescription computeDesc;
        public ComputeStateWithDescription parentDesc;
        public AuthCredentialsService.AuthCredentialsServiceState parentAuth;
        public ComputeStatsRequest statsRequest;
        public ComputeStats statsResponse;
        public AtomicInteger numResponses = new AtomicInteger(0);
        public AmazonCloudWatchAsyncClient statsClient;

        public AWSStatsDataHolder() {
            statsResponse = new ComputeStats();
            // create a thread safe map to hold stats values for resource
            statsResponse.statValues = new ConcurrentSkipListMap<String, Double>();
        }
    }

    @Override
    public void handleRequest(Operation op) {
        if (!op.hasBody()) {
            op.fail(new IllegalArgumentException("body is required"));
            return;
        }
        op.complete();
        switch (op.getAction()) {
        case PATCH:
            ComputeStatsRequest statsRequest = op.getBody(ComputeStatsRequest.class);

            if (statsRequest.isMockRequest) {
                // patch status to parent task
                AdapterUtils.sendPatchToProvisioningTask(this,
                        UriUtils.buildUri(getHost(), statsRequest.parentTaskLink));
                return;
            }

            AWSStatsDataHolder statsData = new AWSStatsDataHolder();
            statsData.statsRequest = statsRequest;
            getVMDescription(statsData);
            break;
        default:
            super.handleRequest(op);
        }
    }

    private void getVMDescription(AWSStatsDataHolder statsData) {
        Consumer<Operation> onSuccess = (op) -> {
            statsData.computeDesc = op.getBody(ComputeStateWithDescription.class);
            getParentVMDescription(statsData);
        };
        URI computeUri = UriUtils.extendUriWithQuery(
                UriUtils.buildUri(getHost(), statsData.statsRequest.computeLink),
                UriUtils.URI_PARAM_ODATA_EXPAND,
                Boolean.TRUE.toString());
        AdapterUtils.getServiceState(this, computeUri, onSuccess, getFailureConsumer(statsData));
    }

    private void getParentVMDescription(AWSStatsDataHolder statsData) {
        Consumer<Operation> onSuccess = (op) -> {
            statsData.parentDesc = op.getBody(ComputeStateWithDescription.class);
            getParentAuth(statsData);
        };
        URI computeUri = UriUtils.extendUriWithQuery(
                UriUtils.buildUri(getHost(), statsData.computeDesc.parentLink),
                UriUtils.URI_PARAM_ODATA_EXPAND,
                Boolean.TRUE.toString());
        AdapterUtils.getServiceState(this, computeUri, onSuccess, getFailureConsumer(statsData));
    }

    private void getParentAuth(AWSStatsDataHolder statsData) {
        Consumer<Operation> onSuccess = (op) -> {
            statsData.parentAuth = op.getBody(AuthCredentialsServiceState.class);
            getStats(statsData);
        };
        URI parentAuthUri = UriUtils.buildUri(getHost(),
                statsData.parentDesc.description.authCredentialsLink);
        AdapterUtils.getServiceState(this, parentAuthUri, onSuccess, getFailureConsumer(statsData));
    }

    private Consumer<Throwable> getFailureConsumer(AWSStatsDataHolder statsData) {
        return ((t) -> {
            AdapterUtils.sendFailurePatchToProvisioningTask(this,
                    UriUtils.buildUri(getHost(), statsData.statsRequest.parentTaskLink), t);
        });
    }

    private void getStats(AWSStatsDataHolder statsData) {
        getAWSAsyncStatsClient(statsData);
        long endTimeMicros = Utils.getNowMicrosUtc();

        for (String metricName : METRIC_NAMES) {
            GetMetricStatisticsRequest metricRequest = new GetMetricStatisticsRequest();
            // get one minute averages for the last 5 minutes
            metricRequest.setEndTime(new Date(TimeUnit.MICROSECONDS.toMillis(endTimeMicros)));
            metricRequest.setStartTime(new Date(
                    TimeUnit.MICROSECONDS.toMillis(endTimeMicros) - TimeUnit.MINUTES.toMillis(5)));
            metricRequest.setPeriod(60);
            metricRequest.setStatistics(Arrays.asList(STATISTICS));
            metricRequest.setNamespace(NAMESPACE);
            List<Dimension> dimensions = new ArrayList<Dimension>();
            Dimension dimension = new Dimension();
            dimension.setName(DIMENSION_INSTANCE_ID);
            String instanceId = statsData.computeDesc.customProperties
                    .get(AWSConstants.AWS_INSTANCE_ID);
            dimension.setValue(instanceId);
            dimensions.add(dimension);
            metricRequest.setDimensions(dimensions);
            metricRequest.setMetricName(metricName);
            AsyncHandler<GetMetricStatisticsRequest, GetMetricStatisticsResult> resultHandler = new AWSStatsHandler(
                    this, statsData);
            statsData.statsClient.getMetricStatisticsAsync(metricRequest, resultHandler);
        }
    }

    private void getAWSAsyncStatsClient(AWSStatsDataHolder statsData) {
        if (statsData.statsClient == null) {
            try {
                statsData.statsClient = AWSUtils.getStatsAsyncClient(statsData.parentAuth,
                        statsData.computeDesc.description.zoneId, getHost().allocateExecutor(this));
            } catch (Exception e) {
                logSevere(e);
            }
        }
    }

    private class AWSStatsHandler implements
            AsyncHandler<GetMetricStatisticsRequest, GetMetricStatisticsResult> {

        private AWSStatsDataHolder statsData;
        private StatelessService service;

        public AWSStatsHandler(StatelessService service, AWSStatsDataHolder statsData) {
            this.statsData = statsData;
            this.service = service;

        }

        @Override
        public void onError(Exception exception) {
            AdapterUtils.sendFailurePatchToProvisioningTask(service,
                    UriUtils.buildUri(service.getHost(), statsData.statsRequest.parentTaskLink),
                    exception);
        }

        @Override
        public void onSuccess(GetMetricStatisticsRequest request,
                GetMetricStatisticsResult result) {
            List<Datapoint> dpList = result.getDatapoints();
            Double sum = new Double(0);
            if (dpList != null && dpList.size() != 0) {
                for (Datapoint dp : dpList) {
                    sum += dp.getAverage();
                }
                statsData.statsResponse.statValues.put(result.getLabel(), sum / dpList.size());
            }

            if (statsData.numResponses.incrementAndGet() == METRIC_NAMES.length) {
                ComputeStatsResponse respBody = new ComputeStatsResponse();
                statsData.statsResponse.computeLink = statsData.computeDesc.documentSelfLink;
                respBody.taskStage = statsData.statsRequest.nextStage;
                respBody.statsList = new ArrayList<ComputeStats>();
                respBody.statsList.add(statsData.statsResponse);
                service.sendRequest(Operation
                        .createPatch(
                                UriUtils.buildUri(service.getHost(),
                                        statsData.statsRequest.parentTaskLink))
                        .setBody(respBody));
            }
        }

    }
}