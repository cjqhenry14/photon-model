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

import static com.vmware.photon.controller.model.adapters.awsadapter.AWSUtils.awaitTermination;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.ExecutorService;
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
import com.vmware.photon.controller.model.adapters.awsadapter.util.AWSStatsNormalizer;
import com.vmware.photon.controller.model.adapters.util.AdapterUtils;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription.ComputeType;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeStateWithDescription;

import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.OperationContext;
import com.vmware.xenon.common.ServiceStats.ServiceStat;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.AuthCredentialsService;
import com.vmware.xenon.services.common.AuthCredentialsService.AuthCredentialsServiceState;

/**
 * Service to gather stats on AWS.
 */
public class AWSStatsService extends StatelessService {

    public static final String SELF_LINK = AWSUriPaths.AWS_STATS_ADAPTER;

    public static final String[] METRIC_NAMES = { "CPUUtilization", "DiskReadBytes",
            "DiskWriteBytes", "NetworkIn", "NetworkOut", "CPUCreditUsage",
            "CPUCreditBalance", "DiskReadOps", "DiskWriteOps", "NetworkPacketsIn",
            "NetworkPacketsOut", "StatusCheckFailed", "StatusCheckFailed_Instance",
            "StatusCheckFailed_System" };

    public static final String[] AGGREGATE_METRIC_NAMES_ACROSS_INSTANCES = { "CPUUtilization",
            "DiskReadBytes", "DiskReadOps", "DiskWriteBytes", "DiskWriteOps", "NetworkIn",
            "NetworkOut" };

    private static final String[] STATISTICS = { "Average", "SampleCount" };
    private static final String NAMESPACE = "AWS/EC2";
    private static final String DIMENSION_INSTANCE_ID = "InstanceId";
    private static final int METRIC_COLLECTION_WINDOW_IN_MINUTES = 10;
    private static final int METRIC_COLLECTION_PERIOD_IN_SECONDS = 60;

    // Cost
    private static final String BILLING_NAMESPACE = "AWS/Billing";
    public static final String COST_METRIC = "EstimatedCharges";
    public static final String BURN_RATE = "BurnRatePerHour";
    private static final String DIMENSION_CURRENCY = "Currency";
    private static final String DIMENSION_CURRENCY_VALUE = "USD";
    private static final int COST_COLLECTION_WINDOW_IN_HOURS = 10;
    // AWS stores all billing data in us-east-1 zone.
    private static final String COST_ZONE_ID = "us-east-1";

    private class AWSStatsDataHolder {
        public ComputeStateWithDescription computeDesc;
        public ComputeStateWithDescription parentDesc;
        public AuthCredentialsService.AuthCredentialsServiceState parentAuth;
        public ComputeStatsRequest statsRequest;
        public ComputeStats statsResponse;
        public AtomicInteger numResponses = new AtomicInteger(0);
        public AmazonCloudWatchAsyncClient statsClient;
        public AmazonCloudWatchAsyncClient billingClient;
        public boolean isComputeHost;

        public AWSStatsDataHolder() {
            statsResponse = new ComputeStats();
            // create a thread safe map to hold stats values for resource
            statsResponse.statValues = new ConcurrentSkipListMap<>();
        }
    }

    private ExecutorService executorService;

    @Override
    public void handleStart(Operation startPost) {
        executorService = getHost().allocateExecutor(this);

        super.handleStart(startPost);
    }

    @Override
    public void handleStop(Operation delete) {
        executorService.shutdown();
        awaitTermination(this, executorService);
        super.handleStop(delete);
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
            statsData.isComputeHost = isComputeHost(statsData.computeDesc.description);

            // if we have a compute host then we directly get the auth.
            if (statsData.isComputeHost) {
                getParentAuth(statsData);
            } else {
                getParentVMDescription(statsData);
            }
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
        String authLink;
        if (statsData.isComputeHost) {
            authLink = statsData.computeDesc.description.authCredentialsLink;
        } else {
            authLink = statsData.parentDesc.description.authCredentialsLink;
        }
        AdapterUtils.getServiceState(this, authLink, onSuccess, getFailureConsumer(statsData));
    }

    private Consumer<Throwable> getFailureConsumer(AWSStatsDataHolder statsData) {
        return ((t) -> {
            AdapterUtils.sendFailurePatchToProvisioningTask(this, getHost(),
                    statsData.statsRequest.parentTaskLink, t);
        });
    }

    private void getStats(AWSStatsDataHolder statsData) {
        if (statsData.isComputeHost) {
            // Get host level stats for billing and ec2.
            getBillingStats(statsData);
            return;
        }
        getEC2Stats(statsData, METRIC_NAMES, false);
    }

    /**
     * Gets EC2 statistics.
     *
     * @param statsData The context object for stats.
     * @param metricNames The metrics names to gather stats for.
     * @param isAggregateStats Indicates where we are interested in aggregate stats or not.
     */
    private void getEC2Stats(AWSStatsDataHolder statsData, String[] metricNames, boolean isAggregateStats) {
        getAWSAsyncStatsClient(statsData);
        long endTimeMicros = Utils.getNowMicrosUtc();

        for (String metricName : metricNames) {
            GetMetricStatisticsRequest metricRequest = new GetMetricStatisticsRequest();
            // get one minute averages for the last 10 minutes
            metricRequest.setEndTime(new Date(TimeUnit.MICROSECONDS.toMillis(endTimeMicros)));
            metricRequest.setStartTime(new Date(
                    TimeUnit.MICROSECONDS.toMillis(endTimeMicros) -
                            TimeUnit.MINUTES.toMillis(METRIC_COLLECTION_WINDOW_IN_MINUTES)));
            metricRequest.setPeriod(METRIC_COLLECTION_PERIOD_IN_SECONDS);
            metricRequest.setStatistics(Arrays.asList(STATISTICS));
            metricRequest.setNamespace(NAMESPACE);

            // Provide instance id dimension only if it is not aggregate stats.
            if (!isAggregateStats) {
                List<Dimension> dimensions = new ArrayList<>();
                Dimension dimension = new Dimension();
                dimension.setName(DIMENSION_INSTANCE_ID);
                String instanceId = statsData.computeDesc.id;
                dimension.setValue(instanceId);
                dimensions.add(dimension);
                metricRequest.setDimensions(dimensions);
            }

            metricRequest.setMetricName(metricName);

            logFine("Retrieving %s metric from AWS", metricName);
            AsyncHandler<GetMetricStatisticsRequest, GetMetricStatisticsResult> resultHandler = new AWSStatsHandler(
                    this, statsData, metricNames.length);
            statsData.statsClient.getMetricStatisticsAsync(metricRequest, resultHandler);
        }
    }

    private void getBillingStats(AWSStatsDataHolder statsData) {
        getAWSAsyncBillingClient(statsData);
        Dimension dimension = new Dimension();
        dimension.setName(DIMENSION_CURRENCY);
        dimension.setValue(DIMENSION_CURRENCY_VALUE);

        long endTimeMicros = Utils.getNowMicrosUtc();
        GetMetricStatisticsRequest request = new GetMetricStatisticsRequest();
        // AWS pushes billing metrics every 4 hours.
        // Get at least 2 metrics to calculate the recent value and the burn rate
        request.setEndTime(new Date(TimeUnit.MICROSECONDS.toMillis(endTimeMicros)));
        request.setStartTime(new Date(
                TimeUnit.MICROSECONDS.toMillis(endTimeMicros) -
                TimeUnit.HOURS.toMillis(COST_COLLECTION_WINDOW_IN_HOURS)));
        request.setPeriod(METRIC_COLLECTION_PERIOD_IN_SECONDS);
        request.setStatistics(Arrays.asList(STATISTICS));
        request.setNamespace(BILLING_NAMESPACE);
        request.setDimensions(Collections.singletonList(dimension));
        request.setMetricName(COST_METRIC);

        logFine("Retrieving %s metric from AWS", COST_METRIC);
        AsyncHandler<GetMetricStatisticsRequest, GetMetricStatisticsResult> resultHandler = new AWSBillingStatsHandler(
                this, statsData);
        statsData.billingClient.getMetricStatisticsAsync(request, resultHandler);
    }

    private void getAWSAsyncStatsClient(AWSStatsDataHolder statsData) {
        if (statsData.statsClient == null) {
            try {
                statsData.statsClient = AWSUtils.getStatsAsyncClient(statsData.parentAuth,
                        statsData.computeDesc.description.zoneId, executorService);
            } catch (Exception e) {
                logSevere(e);
            }
        }
    }

    private void getAWSAsyncBillingClient(AWSStatsDataHolder statsData) {
        if (statsData.billingClient == null) {
            try {
                statsData.billingClient = AWSUtils
                        .getStatsAsyncClient(statsData.parentAuth, COST_ZONE_ID, executorService);
            } catch (Exception e) {
                logSevere(e);
            }
        }
    }

    /**
     * Billing specific async handler.
     */
    private class AWSBillingStatsHandler implements
            AsyncHandler<GetMetricStatisticsRequest, GetMetricStatisticsResult> {

        private AWSStatsDataHolder statsData;
        private StatelessService service;
        private OperationContext opContext;

        public AWSBillingStatsHandler(StatelessService service, AWSStatsDataHolder statsData) {
            this.statsData = statsData;
            this.service = service;
            this.opContext = OperationContext.getOperationContext();
        }

        @Override
        public void onError(Exception exception) {
            OperationContext.restoreOperationContext(opContext);
            AdapterUtils.sendFailurePatchToProvisioningTask(service, service.getHost(),
                    statsData.statsRequest.parentTaskLink, exception);
        }

        @Override
        public void onSuccess(GetMetricStatisticsRequest request,
                GetMetricStatisticsResult result) {
            OperationContext.restoreOperationContext(opContext);
            List<Datapoint> dpList = result.getDatapoints();
            // Sort the data points in increasing order of timestamp
            Collections.sort(dpList, new Comparator<Datapoint>() {
                @Override
                public int compare(Datapoint o1, Datapoint o2) {
                    return o1.getTimestamp().compareTo(o2.getTimestamp());
                }
            });
            Double latestAverage = 0D;
            Date currentDate = null;
            Long timeDifference = 0L;
            Double burnRate = 0D;
            if (dpList != null && dpList.size() != 0) {
                for (Datapoint dp : dpList) {
                    if (currentDate == null) {
                        currentDate = dp.getTimestamp();
                    } else {
                        timeDifference = getDateDifference(currentDate, dp.getTimestamp(),
                                TimeUnit.HOURS);
                        currentDate = dp.getTimestamp();
                    }
                    burnRate = (timeDifference == 0 ? 0 : ((dp.getAverage() - latestAverage) / timeDifference));
                    latestAverage = dp.getAverage();
                }
                ServiceStat stat = new ServiceStat();
                stat.latestValue = latestAverage;
                stat.unit = AWSStatsNormalizer.getNormalizedUnitValue(DIMENSION_CURRENCY_VALUE);
                statsData.statsResponse.statValues.put(result.getLabel(), stat);
                ServiceStat burnRateStat = new ServiceStat();
                burnRateStat.latestValue = burnRate;
                burnRateStat.unit = AWSStatsNormalizer.getNormalizedUnitValue(DIMENSION_CURRENCY_VALUE);
                statsData.statsResponse.statValues.put(BURN_RATE, burnRateStat);
            }

            getEC2Stats(statsData, AGGREGATE_METRIC_NAMES_ACROSS_INSTANCES, true);
        }

        private long getDateDifference(Date oldDate, Date newDate, TimeUnit timeUnit) {
            long differenceInMillies = newDate.getTime() - oldDate.getTime();
            return timeUnit.convert(differenceInMillies, TimeUnit.MILLISECONDS);
        }
    }

    private class AWSStatsHandler implements
            AsyncHandler<GetMetricStatisticsRequest, GetMetricStatisticsResult> {

        private final int numOfMetrics;
        private AWSStatsDataHolder statsData;
        private StatelessService service;
        private OperationContext opContext;

        public AWSStatsHandler(StatelessService service, AWSStatsDataHolder statsData,
                int numOfMetrics) {
            this.statsData = statsData;
            this.service = service;
            this.numOfMetrics = numOfMetrics;
            this.opContext = OperationContext.getOperationContext();
        }

        @Override
        public void onError(Exception exception) {
            OperationContext.restoreOperationContext(opContext);
            AdapterUtils.sendFailurePatchToProvisioningTask(service, service.getHost(),
                    statsData.statsRequest.parentTaskLink, exception);
        }

        @Override
        public void onSuccess(GetMetricStatisticsRequest request,
                GetMetricStatisticsResult result) {
            OperationContext.restoreOperationContext(opContext);
            List<Datapoint> dpList = result.getDatapoints();
            Double averageSum = 0d;
            Double sampleCount = 0d;
            String unit = null;
            if (dpList != null && dpList.size() != 0) {
                for (Datapoint dp : dpList) {
                    averageSum += dp.getAverage();
                    sampleCount += dp.getSampleCount();
                    unit = dp.getUnit();
                }
                ServiceStat stat = new ServiceStat();
                stat.latestValue = averageSum / sampleCount;
                stat.unit = AWSStatsNormalizer.getNormalizedUnitValue(unit);
                statsData.statsResponse.statValues.put(result.getLabel(), stat);
            }

            if (statsData.numResponses.incrementAndGet() == numOfMetrics) {
                ComputeStatsResponse respBody = new ComputeStatsResponse();
                statsData.statsResponse.computeLink = statsData.computeDesc.documentSelfLink;
                respBody.taskStage = statsData.statsRequest.nextStage;
                respBody.statsList = new ArrayList<>();
                respBody.statsList.add(statsData.statsResponse);
                service.sendRequest(Operation.createPatch(
                        UriUtils.buildUri(service.getHost(), statsData.statsRequest.parentTaskLink))
                        .setBody(respBody));
            }
        }
    }

    /**
     * Returns if the given compute description is a compute host or not.
     */
    private boolean isComputeHost(ComputeDescription computeDescription) {
        List<String> supportedChildren = computeDescription.supportedChildren;
        return supportedChildren != null && supportedChildren.contains(ComputeType.VM_GUEST.name());
    }
}