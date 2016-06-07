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

package com.vmware.photon.controller.model.adapters.azure.utils;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import com.microsoft.azure.credentials.ApplicationTokenCredentials;
import com.microsoft.azure.credentials.AzureEnvironment;
import okhttp3.OkHttpClient;

import com.vmware.photon.controller.model.adapters.azure.constants.AzureConstants;

import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.services.common.AuthCredentialsService.AuthCredentialsServiceState;

/**
 * Utility methods.
 */
public class AzureUtils {
    private static final int EXECUTOR_SHUTDOWN_INTERVAL_MINUTES = 5;

    /**
     * Waits for termination of given executor service.
     */
    public static void awaitTermination(StatelessService service, ExecutorService executor) {
        try {
            if (!executor.awaitTermination(EXECUTOR_SHUTDOWN_INTERVAL_MINUTES, TimeUnit.MINUTES)) {
                service.logWarning(
                        "Executor service can't be shutdown for Azure. Trying to shutdown now...");
                executor.shutdownNow();
            }
            service.logFine("Executor service shutdown for Azure");
        } catch (InterruptedException e) {
            service.logSevere(e);
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            service.logSevere(e);
        }
    }

    /**
     * Clean up Azure SDK HTTP client.
     */
    public static void cleanUpHttpClient(StatelessService service, OkHttpClient httpClient) {
        if (httpClient == null) {
            return;
        }

        httpClient.connectionPool().evictAll();
        ExecutorService httpClientExecutor = httpClient.dispatcher().executorService();
        httpClientExecutor.shutdown();

        awaitTermination(service, httpClientExecutor);
    }

    /**
     * Configures authentication credential for Azure.
     */
    public static ApplicationTokenCredentials getAzureConfig(
            AuthCredentialsServiceState parentAuth) throws Exception {

        String clientId = parentAuth.privateKeyId;
        String clientKey = parentAuth.privateKey;
        String tenantId = parentAuth.customProperties.get(AzureConstants.AZURE_TENANT_ID);

        return new ApplicationTokenCredentials(clientId, tenantId, clientKey,
                AzureEnvironment.AZURE);
    }
}
