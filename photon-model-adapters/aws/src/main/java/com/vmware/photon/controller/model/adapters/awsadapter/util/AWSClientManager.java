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

package com.vmware.photon.controller.model.adapters.awsadapter.util;

import static com.vmware.photon.controller.model.adapters.awsadapter.AWSConstants.CLIENT_CACHE_INITIAL_SIZE;
import static com.vmware.photon.controller.model.adapters.awsadapter.AWSConstants.CLIENT_CACHE_MAX_SIZE;
import static com.vmware.photon.controller.model.adapters.awsadapter.AWSUtils.TILDA;
import static com.vmware.photon.controller.model.adapters.awsadapter.AWSUtils.cleanupCloudWatchClientResources;
import static com.vmware.photon.controller.model.adapters.awsadapter.AWSUtils.cleanupEC2ClientResources;

import java.net.URI;
import java.util.concurrent.ExecutorService;

import com.amazonaws.services.cloudwatch.AmazonCloudWatchAsyncClient;
import com.amazonaws.services.ec2.AmazonEC2AsyncClient;

import com.vmware.photon.controller.model.adapters.awsadapter.AWSUtils;
import com.vmware.photon.controller.model.adapters.util.AdapterUtils;
import com.vmware.photon.controller.model.adapters.util.LRUCache;

import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.services.common.AuthCredentialsService.AuthCredentialsServiceState;

/**
 * Holds the cache for managing the AWS Clients used to make calls to AWS from the photon model adapters.
 */
public class AWSClientManager {

    // Flag for determining if the client manager needs to manage the EC2 client cache or the
    // CloudWatch cache. If the stats mode is set, the cloud watch client cache is maintained in
    // this class.
    boolean statsFlag;
    LRUCache<String, AmazonEC2AsyncClient> ec2ClientCache;
    LRUCache<String, AmazonCloudWatchAsyncClient> cloudWatchClientCache;

    public AWSClientManager() {
        this(false);
    }

    public AWSClientManager(boolean statsFlag) {
        this.statsFlag = statsFlag;
        if (statsFlag) {
            cloudWatchClientCache = new LRUCache<String, AmazonCloudWatchAsyncClient>(
                    CLIENT_CACHE_INITIAL_SIZE, CLIENT_CACHE_MAX_SIZE);
            return;
        }
        ec2ClientCache = new LRUCache<String, AmazonEC2AsyncClient>(CLIENT_CACHE_INITIAL_SIZE,
                CLIENT_CACHE_MAX_SIZE);
    }

    /**
     * Accesses the client cache to get the EC2 client for the given auth credentials and regionId. If a client
     * is not found to exist, creates a new one and adds an entry in the cache for it.
     *
     * @param credentials The auth credentials to be used for the client creation
     * @param regionId The region of the AWS client
     * @param service The stateless service making the request and for which the executor pool needs to be allocated.
     * @param parentTaskLink The parentTaskLink where the error (if any) needs to be reported.
     * @param isMock Indicates if this a mock request
     * @return The AWSClient
     */
    public AmazonEC2AsyncClient getOrCreateEC2Client(AuthCredentialsServiceState credentials,
            String regionId, StatelessService service, URI parentTaskLink, boolean isMock,
            boolean isEnumeration) {
        if (statsFlag) {
            throw new UnsupportedOperationException(
                    "Cannot get AWS EC2 Client in Stats mode.");
        }
        String cacheKey = credentials.documentSelfLink + TILDA + regionId;
        AmazonEC2AsyncClient amazonEC2Client = ec2ClientCache.get(cacheKey);
        if (amazonEC2Client == null) {
            try {
                amazonEC2Client = AWSUtils.getAsyncClient(
                        credentials, regionId,
                        isMock, service.getHost().allocateExecutor(service));
                ec2ClientCache.put(cacheKey, amazonEC2Client);
            } catch (Throwable e) {
                service.logSevere(e);
                if (isEnumeration) {
                    AdapterUtils.sendFailurePatchToEnumerationTask(service,
                                parentTaskLink, e);
                } else {
                    AdapterUtils.sendFailurePatchToProvisioningTask(service,
                                parentTaskLink, e);
                }
            }
        }
        return amazonEC2Client;
    }

    /**
     * Get or create a CloudWatch Client instance that will be used to get stats from AWS.
     * @param credentials The auth credentials to be used for the client creation
     * @param regionId The region of the AWS client
     * @param executorService The executorService used to run the requests.
     * @param service The stateless service for which the operation is being performed.
     * @param parentTaskLink The parentTaskLink where the error (if any) needs to be reported.
     * @param isMock Indicates if this a mock request
     * @return
     */
    public AmazonCloudWatchAsyncClient getOrCreateCloudWatchClient(
            AuthCredentialsServiceState credentials,
            String regionId, ExecutorService executorService, StatelessService service,
            URI parentTaskLink, boolean isMock) {
        if (!statsFlag) {
            throw new UnsupportedOperationException(
                    "Cannot get AWS CloudWatch without Stats mode.");
        }
        String cacheKey = credentials.documentSelfLink + TILDA + regionId;
        AmazonCloudWatchAsyncClient amazonCloudWatchClient = cloudWatchClientCache
                .get(cacheKey);
        if (amazonCloudWatchClient == null) {
            try {
                amazonCloudWatchClient = AWSUtils.getStatsAsyncClient(credentials,
                        regionId, executorService, isMock);
                cloudWatchClientCache.put(cacheKey, amazonCloudWatchClient);
            } catch (Throwable e) {
                service.logSevere(e);
                AdapterUtils.sendFailurePatchToProvisioningTask(service,
                        parentTaskLink, e);
            }
        }
        return amazonCloudWatchClient;
    }

    /**
     * Clears out the client cache and all the resources associated with each of the AWS clients.
     */
    public void cleanUp() {
        if (statsFlag) {
            for (AmazonCloudWatchAsyncClient client : cloudWatchClientCache.values()) {
                cleanupCloudWatchClientResources(client);
                return;
            }
        }
        for (AmazonEC2AsyncClient client : ec2ClientCache.values()) {
            cleanupEC2ClientResources(client);
        }
    }
}
