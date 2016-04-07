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

package com.vmware.photon.controller.model.adapters.vsphere;

import com.vmware.photon.controller.model.adapterapi.ComputeInstanceRequest;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.StatelessService;

/**
 */
public class VSphereAdapterInstanceService extends StatelessService {
    public static final String SELF_LINK = VSphereUriPaths.INSTANCE_SERVICE;

    /**
     * pool is looked-up once on service start
     */
    private transient VSphereIOThreadPool connectionPool;

    @Override
    public void handleStart(Operation startPost) {
        super.handleStart(startPost);

        connectionPool = VSphereIOThreadPoolAllocator.getPool(this);
    }

    @Override
    public void handlePatch(Operation op) {
        if (!op.hasBody()) {
            op.fail(new IllegalArgumentException("body is required"));
            return;
        }

        ComputeInstanceRequest request = op.getBody(ComputeInstanceRequest.class);
        switch (request.requestType) {
        case VALIDATE_CREDENTIALS:
            handleCredentialValidation(request, op);
            break;
        default:
            op.fail(new UnsupportedOperationException("Not implemented"));
        }
    }

    private void handleCredentialValidation(ComputeInstanceRequest request, Operation op) {
        if (request.isMockRequest) {
            op.complete();
            return;
        }

        connectionPool.submit(this, request.computeReference, request.authCredentialsLink, (c, e) -> {
            if (e != null) {
                op.fail(e);
            } else {
                // here a connection is established, credentials are OK
                op.complete();
            }
        });
    }
}
