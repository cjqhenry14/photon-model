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

import static org.junit.Assert.assertEquals;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import org.junit.runners.Suite.SuiteClasses;
import org.junit.runners.model.InitializationError;
import org.junit.runners.model.RunnerBuilder;

import com.vmware.photon.controller.model.adapterapi.ComputeInstanceRequest;
import com.vmware.photon.controller.model.helpers.BaseModelTest;

import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.services.common.AuthCredentialsService;

/**
 * This class implements tests for the {@link AWSInstanceService} class.
 */
@RunWith(AWSInstanceServiceTest.class)
@SuiteClasses({ AWSInstanceServiceTest.HandleRequestTest.class})
public class AWSInstanceServiceTest extends Suite{

    public AWSInstanceServiceTest(Class<?> klass, RunnerBuilder builder) throws InitializationError {
        super(klass, builder);
    }

    /**
     * This class implements tests for the handleRequest method.
     */
    public static class HandleRequestTest extends BaseModelTest {

        private String authCredentialsLink;

        private ServiceDocument createAuthCredentialsDocument(String privateKey,
                String privateKeyId) throws Throwable {
            AuthCredentialsService.AuthCredentialsServiceState creds = new AuthCredentialsService.AuthCredentialsServiceState();
            creds.privateKey = privateKey;
            creds.privateKeyId = privateKeyId;
            return postServiceSynchronously(
                    AuthCredentialsService.FACTORY_LINK, creds,
                    AuthCredentialsService.AuthCredentialsServiceState.class);
        }

        @Override
        protected void startRequiredServices() throws Throwable {
            getHost().startService(new AWSInstanceService());
        }

        @Before
        public void setUpClass() throws Throwable {
            String privateKey = "privateKey";
            String privateKeyId = "privateKeyID";

            // create credentials
            ServiceDocument awsCredentials = createAuthCredentialsDocument(
                    privateKey, privateKeyId);
            this.authCredentialsLink = awsCredentials.documentSelfLink;
        }

        @Test
        public void testValidationSuccess() throws Throwable {
            ComputeInstanceRequest req = new ComputeInstanceRequest();
            req.requestType = ComputeInstanceRequest.InstanceRequestType.VALIDATE_CREDENTIALS;
            req.authCredentialsLink = this.authCredentialsLink;
            req.regionId = "us-east-1";
            req.isMockRequest = true;
            req.requestType = ComputeInstanceRequest.InstanceRequestType.CREATE;
            int statusCode = patchServiceSynchronously(
                    AWSInstanceService.SELF_LINK, req);
            assertEquals(statusCode, 200);
        }
    }
}
