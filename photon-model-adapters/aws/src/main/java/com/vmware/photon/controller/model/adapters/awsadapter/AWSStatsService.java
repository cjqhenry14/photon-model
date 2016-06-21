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
import java.util.Collections;
import java.util.Comparator;
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
import com.vmware.photon.controller.model.adapters.awsadapter.util.AWSClientManager;
import com.vmware.photon.controller.model.adapters.awsadapter.util.AWSClientManagerFactory;
import com.vmware.photon.controller.model.adapters.awsadapter.util.AWSStatsNormalizer;
import com.vmware.photon.controller.model.adapters.util.AdapterUtils;
import com.vmware.photon.controller.model.constants.PhotonModelConstants;
import com.vmware.photon.controller.model.monitoring.ResourceMetricService;
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
    private AWSClientManager clientManager;

    public AWSStatsService() {
        super.toggleOption(ServiceOption.INSTRUMENTATION, true);
        this.clientManager = AWSClientManagerFactory.getClientManager(true);
    }

    public static final String SELF_LINK = AWSUriPaths.AWS_STATS_ADAPTER;

    public static final String[] METRIC_NAMES = { AWSConstants.CPU_UTILIZATION,
            AWSConstants.DISK_READ_BYTES, AWSConstants.DISK_WRITE_BYTES,
            AWSConstants.NETWORK_IN, AWSConstants.NETWORK_OUT,
            AWSConstants.CPU_CREDIT_USAGE, AWSConstants.CPU_CREDIT_BALANCE,
            AWSConstants.DISK_READ_OPS, AWSConstants.DISK_WRITE_OPS,
            AWSConstants.NETWORK_PACKETS_IN, AWSConstants.NETWORK_PACKETS_OUT,
            AWSConstants.STATUS_CHECK_FAILED, AWSConstants.STATUS_CHECK_FAILED_INSTANCE,
            AWSConstants.STATUS_CHECK_FAILED_SYSTEM };

    public static final String[] AGGREGATE_METRIC_NAMES_ACROSS_INSTANCES = {
            AWSConstants.CPU_UTILIZATION, AWSConstants.DISK_READ_BYTES,
            AWSConstants.DISK_READ_OPS, AWSConstants.DISK_WRITE_BYTES,
            AWSConstants.DISK_WRITE_OPS, AWSConstants.NETWORK_IN,
            AWSConstants.NETWORK_OUT };

    private static final String[] STATISTICS = { "Average", "SampleCount" };
    private static final String NAMESPACE = "AWS/EC2";
    private static final String DIMENSION_INSTANCE_ID = "InstanceId";
    private static final int METRIC_COLLECTION_WINDOW_IN_MINUTES = 1;
    private static final int METRIC_COLLECTION_PERIOD_IN_SECONDS = 60;

    // Cost
    private static final String BILLING_NAMESPACE = "AWS/Billing";
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
        public URI persistStatsUri;
        public boolean isComputeHost;

        public AWSStatsDataHolder() {
            this.persistStatsUri = UriUtils.buildUri(getHost(), ResourceMetricService.FACTORY_LINK);
            this.statsResponse = new ComputeStats();
            // create a thread safe map to hold stats values for resource
            this.statsResponse.statValues = new ConcurrentSkipListMap<>();
        }
    }

    @Override
    public void handleStart(Operation startPost) {
        super.handleStart(startPost);
    }

    @Override
    public void handleStop(Operation delete) {
        AWSClientManagerFactory.returnClientManager(this.clientManager, true);
        super.handleStop(delete);
    }

    @Override
    public void handlePatch(Operation op) {
        if (!op.hasBody()) {
            op.fail(new IllegalArgumentException("body is required"));
            return;
        }
        op.complete();
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
    private void getEC2Stats(AWSStatsDataHolder statsData, String[] metricNames,
            boolean isAggregateStats) {
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
                    this, statsData, metricNames.length, isAggregateStats);
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
        request.setMetricName(AWSConstants.ESTIMATED_CHARGES);

        logFine("Retrieving %s metric from AWS", AWSConstants.ESTIMATED_CHARGES);
        AsyncHandler<GetMetricStatisticsRequest, GetMetricStatisticsResult> resultHandler = new AWSBillingStatsHandler(
                this, statsData);
        statsData.billingClient.getMetricStatisticsAsync(request, resultHandler);
    }

    private void getAWSAsyncStatsClient(AWSStatsDataHolder statsData) {
        URI parentURI = UriUtils.buildUri(this.getHost(), statsData.statsRequest.parentTaskLink);
        statsData.statsClient = this.clientManager.getOrCreateCloudWatchClient(statsData.parentAuth,
                statsData.computeDesc.description.zoneId, this, parentURI,
                statsData.statsRequest.isMockRequest);
    }

    private void getAWSAsyncBillingClient(AWSStatsDataHolder statsData) {
        URI parentURI = UriUtils.buildUri(this.getHost(), statsData.statsRequest.parentTaskLink);
        statsData.billingClient = this.clientManager.getOrCreateCloudWatchClient(
                statsData.parentAuth,
                COST_ZONE_ID, this, parentURI,
                statsData.statsRequest.isMockRequest);
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
            OperationContext.restoreOperationContext(this.opContext);
            AdapterUtils.sendFailurePatchToProvisioningTask(this.service, this.service.getHost(),
                    this.statsData.statsRequest.parentTaskLink, exception);
        }

        @Override
        public void onSuccess(GetMetricStatisticsRequest request,
                GetMetricStatisticsResult result) {
            OperationContext.restoreOperationContext(this.opContext);
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
                    burnRate = (timeDifference == 0 ? 0
                            : ((dp.getAverage() - latestAverage) / timeDifference));
                    latestAverage = dp.getAverage();
                    persistStat(this.service, dp,
                            AWSStatsNormalizer.getNormalizedStatKeyValue(result.getLabel()),
                            this.statsData);
                }
                ServiceStat stat = new ServiceStat();
                stat.latestValue = latestAverage;
                stat.unit = AWSStatsNormalizer.getNormalizedUnitValue(DIMENSION_CURRENCY_VALUE);
                this.statsData.statsResponse.statValues
                        .put(AWSStatsNormalizer.getNormalizedStatKeyValue(result.getLabel()), stat);
                ServiceStat burnRateStat = new ServiceStat();
                burnRateStat.latestValue = burnRate;
                burnRateStat.unit = AWSStatsNormalizer
                        .getNormalizedUnitValue(DIMENSION_CURRENCY_VALUE);
                this.statsData.statsResponse.statValues.put(
                        AWSStatsNormalizer.getNormalizedStatKeyValue(AWSConstants.BURN_RATE),
                        burnRateStat);
            }

            getEC2Stats(this.statsData, AGGREGATE_METRIC_NAMES_ACROSS_INSTANCES, true);
        }

        private long getDateDifference(Date oldDate, Date newDate, TimeUnit timeUnit) {
            long differenceInMillies = newDate.getTime() - oldDate.getTime();
            return timeUnit.convert(differenceInMillies, TimeUnit.MILLISECONDS);
        }
    }

    private class AWSStatsHandler implements
            AsyncHandler<GetMetricStatisticsRequest, GetMetricStatisticsResult> {

        private final int numOfMetrics;
        private final Boolean isAggregateStats;
        private AWSStatsDataHolder statsData;
        private StatelessService service;
        private OperationContext opContext;

        public AWSStatsHandler(StatelessService service, AWSStatsDataHolder statsData,
                int numOfMetrics, Boolean isAggregateStats) {
            this.statsData = statsData;
            this.service = service;
            this.numOfMetrics = numOfMetrics;
            this.isAggregateStats = isAggregateStats;
            this.opContext = OperationContext.getOperationContext();
        }

        @Override
        public void onError(Exception exception) {
            OperationContext.restoreOperationContext(this.opContext);
            AdapterUtils.sendFailurePatchToProvisioningTask(this.service, this.service.getHost(),
                    this.statsData.statsRequest.parentTaskLink, exception);
        }

        @Override
        public void onSuccess(GetMetricStatisticsRequest request,
                GetMetricStatisticsResult result) {
            OperationContext.restoreOperationContext(this.opContext);
            List<Datapoint> dpList = result.getDatapoints();
            Double averageSum = 0d;
            Double sampleCount = 0d;
            String unit = null;
            if (dpList != null && dpList.size() != 0) {
                for (Datapoint dp : dpList) {
                    averageSum += dp.getAverage();
                    sampleCount += dp.getSampleCount();
                    unit = dp.getUnit();
                    persistStat(this.service, dp,
                            AWSStatsNormalizer.getNormalizedStatKeyValue(result.getLabel()),
                            this.statsData);
                }
                ServiceStat stat = new ServiceStat();
                stat.latestValue = averageSum / sampleCount;
                stat.unit = AWSStatsNormalizer.getNormalizedUnitValue(unit);
                this.statsData.statsResponse.statValues
                        .put(AWSStatsNormalizer.getNormalizedStatKeyValue(result.getLabel()), stat);
            }

            if (this.statsData.numResponses.incrementAndGet() == this.numOfMetrics) {
                // Put the number of API requests as a stat
                ServiceStat apiCallCountStat = new ServiceStat();
                apiCallCountStat.latestValue = this.numOfMetrics;
                if (this.isAggregateStats) {
                    // Number of Aggregate metrics + 1 call for cost metric
                    apiCallCountStat.latestValue += 1;
                }
                apiCallCountStat.unit = PhotonModelConstants.UNIT_COUNT;
                this.statsData.statsResponse.statValues.put(PhotonModelConstants.API_CALL_COUNT,
                        apiCallCountStat);

                ComputeStatsResponse respBody = new ComputeStatsResponse();
                this.statsData.statsResponse.computeLink = this.statsData.computeDesc.documentSelfLink;
                respBody.taskStage = this.statsData.statsRequest.nextStage;
                respBody.statsList = new ArrayList<>();
                respBody.statsList.add(this.statsData.statsResponse);
                this.service.sendRequest(Operation.createPatch(
                        UriUtils.buildUri(this.service.getHost(), this.statsData.statsRequest.parentTaskLink))
                        .setBody(respBody));
            }
        }
    }

    private void persistStat(StatelessService service, Datapoint datapoint, String metricName,
            AWSStatsDataHolder statsData) {
        ResourceMetricService.ResourceMetric stat = new ResourceMetricService.ResourceMetric();
        // Set the documentSelfLink to <computeId>-<metricName>
        stat.documentSelfLink = UriUtils.getLastPathSegment(statsData.computeDesc.documentSelfLink)
                + "-" + metricName;
        stat.value = datapoint.getAverage();
        stat.timestampMicrosUtc = TimeUnit.MILLISECONDS
                .toMicros(datapoint.getTimestamp().getTime());
        service.getHost()
                .sendRequest(Operation
                        .createPost(statsData.persistStatsUri)
                        .setReferer(ResourceMetricService.FACTORY_LINK)
                        .setBodyNoCloning(stat));
    }

    /**
     * Returns if the given compute description is a compute host or not.
     */
    private boolean isComputeHost(ComputeDescription computeDescription) {
        List<String> supportedChildren = computeDescription.supportedChildren;
        return supportedChildren != null && supportedChildren.contains(ComputeType.VM_GUEST.name());
    }
}