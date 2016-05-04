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

package com.vmware.photon.controller.model.adapters.azureadapter.async;

import com.vmware.photon.controller.model.adapters.azureadapter.stats.models.AzureMetricRequest;

/**
 * Callback interface for notification on AzureMetric requests executed with the
 * asynchronous clients.
 */
public interface AsyncHandler<REQUEST extends AzureMetricRequest, RESULT> {

    /**
     * Invoked after an asynchronous request
     * @param exception
     */
    public void onError(Exception exception);

    /**
     * Invoked after an asynchronous request has completed successfully. Callers
     * have access to the original request object and the returned response
     * object.
     *
     * @param request
     *            The initial request created by the caller
     * @param result
     *            The successful result of the executed operation.
     */
    public void onSuccess(REQUEST request, RESULT result);

}