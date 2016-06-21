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

import static com.vmware.photon.controller.model.adapters.awsadapter.AWSConstants.AWS_INVALID_INSTANCE_ID_ERROR_CODE;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.handlers.AsyncHandler;
import com.amazonaws.services.ec2.AmazonEC2AsyncClient;
import com.amazonaws.services.ec2.model.DescribeInstancesRequest;
import com.amazonaws.services.ec2.model.DescribeInstancesResult;
import com.amazonaws.services.ec2.model.Instance;

import com.vmware.photon.controller.model.adapterapi.ComputeInstanceRequest;
import com.vmware.photon.controller.model.adapters.util.AdapterUtils;

import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.common.Utils;

/**
 * Class to check if an instance is in the desired state if the vm is in the
 * desired state invoke the consumer, else reschedule to check in 5 seconds.
 *
 */
public class AWSTaskStatusChecker {

    private String instanceId;
    private AmazonEC2AsyncClient amazonEC2Client;
    private String desiredState;
    private Consumer<Instance> consumer;
    private ComputeInstanceRequest computeRequest;
    private StatelessService service;
    private long expirationTimeMicros;

    private AWSTaskStatusChecker(AmazonEC2AsyncClient amazonEC2Client,
            String instanceId, String desiredState,
            Consumer<Instance> consumer, ComputeInstanceRequest computeRequest,
            StatelessService service, long expirationTimeMicros) {
        this.instanceId = instanceId;
        this.amazonEC2Client = amazonEC2Client;
        this.consumer = consumer;
        this.desiredState = desiredState;
        this.computeRequest = computeRequest;
        this.service = service;
        this.expirationTimeMicros = expirationTimeMicros;
    }

    public static AWSTaskStatusChecker create(
            AmazonEC2AsyncClient amazonEC2Client, String instanceId,
            String desiredState, Consumer<Instance> consumer,
            ComputeInstanceRequest computeRequest, StatelessService service,
            long expirationTimeMicros) {
        return new AWSTaskStatusChecker(amazonEC2Client, instanceId,
                desiredState, consumer, computeRequest, service, expirationTimeMicros);
    }

    public void start() {
        if (this.expirationTimeMicros > 0 && Utils.getNowMicrosUtc() > this.expirationTimeMicros) {
            String msg = String
                    .format("Compute with instance id %s did not reach desired %s state in the required time interval.",
                            this.instanceId, this.desiredState);
            this.service.logSevere(msg);
            Throwable t = new RuntimeException(msg);
            AdapterUtils.sendFailurePatchToProvisioningTask(this.service,
                    this.computeRequest.provisioningTaskReference, t);
            return;
        }
        DescribeInstancesRequest descRequest = new DescribeInstancesRequest();
        List<String> instanceIdList = new ArrayList<String>();
        instanceIdList.add(this.instanceId);
        descRequest.setInstanceIds(instanceIdList);
        AsyncHandler<DescribeInstancesRequest, DescribeInstancesResult> describeHandler = new AsyncHandler<DescribeInstancesRequest, DescribeInstancesResult>() {

            @Override
            public void onError(Exception exception) {
                // Sometimes AWS takes time to acknowledge the presence of newly provisioned
                // instances. Not failing the request immediately in case AWS cannot find the
                // particular instanceId.
                if (exception instanceof AmazonServiceException
                        && ((AmazonServiceException) exception).getErrorCode()
                                .equalsIgnoreCase(AWS_INVALID_INSTANCE_ID_ERROR_CODE)) {
                    AWSTaskStatusChecker.this.service.logWarning(
                            "Could not retrieve status for instance %s. Retrying... Exception on AWS is %s",
                            AWSTaskStatusChecker.this.instanceId, exception);
                    AWSTaskStatusChecker.create(AWSTaskStatusChecker.this.amazonEC2Client,
                            AWSTaskStatusChecker.this.instanceId, AWSTaskStatusChecker.this.desiredState, AWSTaskStatusChecker.this.consumer,
                            AWSTaskStatusChecker.this.computeRequest, AWSTaskStatusChecker.this.service, AWSTaskStatusChecker.this.expirationTimeMicros).start();
                    return;
                }
                AdapterUtils.sendFailurePatchToProvisioningTask(AWSTaskStatusChecker.this.service,
                        AWSTaskStatusChecker.this.computeRequest.provisioningTaskReference, exception);
                return;
            }

            @Override
            public void onSuccess(DescribeInstancesRequest request,
                    DescribeInstancesResult result) {
                Instance instance = result.getReservations().get(0)
                        .getInstances().get(0);
                if (!instance.getState().getName().equals(AWSTaskStatusChecker.this.desiredState)) {
                    // if the task is not in the running state, schedule thread
                    // to run again in 5 seconds
                    AWSTaskStatusChecker.this.service.getHost().schedule(
                            () -> {
                                AWSTaskStatusChecker.create(AWSTaskStatusChecker.this.amazonEC2Client,
                                        AWSTaskStatusChecker.this.instanceId, AWSTaskStatusChecker.this.desiredState, AWSTaskStatusChecker.this.consumer,
                                        AWSTaskStatusChecker.this.computeRequest, AWSTaskStatusChecker.this.service, AWSTaskStatusChecker.this.expirationTimeMicros).start();
                            }, 5, TimeUnit.SECONDS);
                    return;
                }
                AWSTaskStatusChecker.this.consumer.accept(instance);
                return;
            }
        };
        this.amazonEC2Client.describeInstancesAsync(descRequest, describeHandler);
    }
}
