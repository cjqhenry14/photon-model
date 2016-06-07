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

package com.vmware.photon.controller.model.adapters.azure.instance;

import static org.junit.Assert.assertEquals;

import java.util.Collections;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import org.junit.runners.Suite.SuiteClasses;
import org.junit.runners.model.InitializationError;
import org.junit.runners.model.RunnerBuilder;

import com.vmware.photon.controller.model.adapterapi.ComputeInstanceRequest;
import com.vmware.photon.controller.model.adapters.azure.constants.AzureConstants;
import com.vmware.photon.controller.model.adapters.azure.instance.AzureInstanceService;
import com.vmware.photon.controller.model.helpers.BaseModelTest;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.services.common.AuthCredentialsService;
import com.vmware.xenon.services.common.AuthCredentialsService.AuthCredentialsServiceState;

/**
 * This class implements tests for the {@link AzureInstanceService} class.
 */
@RunWith(AzureInstanceServiceTest.class)
@SuiteClasses({ AzureInstanceServiceTest.HandleRequestTest.class })
public class AzureInstanceServiceTest extends Suite {

    public AzureInstanceServiceTest(Class<?> klass, RunnerBuilder builder)
            throws InitializationError {
        super(klass, builder);
    }

    /**
     * This class implements tests for the handleRequest method.
     */
    public static class HandleRequestTest extends BaseModelTest {

        private String authCredentialsLink;

        private ServiceDocument createAuthCredentialsDocument(String clientID, String clientKey,
                String subscriptionId, String tenantId) throws Throwable {
            AuthCredentialsServiceState creds = new AuthCredentialsServiceState();
            creds.privateKey = clientKey;
            creds.privateKeyId = clientID;
            creds.userLink = subscriptionId;
            creds.customProperties = Collections
                    .singletonMap(AzureConstants.AZURE_TENANT_ID, tenantId);
            return postServiceSynchronously(AuthCredentialsService.FACTORY_LINK, creds,
                    AuthCredentialsServiceState.class);
        }

        @Override
        protected void startRequiredServices() throws Throwable {
            getHost().startService(new AzureInstanceService());
        }

        @Before
        public void setUpClass() throws Throwable {
            String clientID = "clientID";
            String clientKey = "clientKey";
            String subscriptionId = "subscriptionId";
            String tenantId = "tenantId";

            // create credentials
            ServiceDocument creds = createAuthCredentialsDocument(
                    clientID, clientKey, subscriptionId, tenantId);
            this.authCredentialsLink = creds.documentSelfLink;
        }

        @Test
        public void testValidationSuccess() throws Throwable {
            ComputeInstanceRequest req = new ComputeInstanceRequest();
            req.requestType = ComputeInstanceRequest.InstanceRequestType.VALIDATE_CREDENTIALS;
            req.authCredentialsLink = this.authCredentialsLink;
            req.regionId = "westus";
            req.isMockRequest = true;
            int statusCode = patchServiceSynchronously(
                    AzureInstanceService.SELF_LINK, req);
            assertEquals(statusCode, Operation.STATUS_CODE_OK);
        }
    }
}
