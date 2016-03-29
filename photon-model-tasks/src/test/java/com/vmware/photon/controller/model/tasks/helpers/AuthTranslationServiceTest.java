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

package com.vmware.photon.controller.model.tasks.helpers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import org.junit.runners.Suite.SuiteClasses;
import org.junit.runners.model.InitializationError;
import org.junit.runners.model.RunnerBuilder;

import com.vmware.photon.controller.model.helpers.BaseModelTest;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService;

import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.services.common.AuthCredentialsService;
import com.vmware.xenon.services.common.AuthCredentialsService.AuthCredentialsServiceState;

@RunWith(AuthTranslationServiceTest.class)
@SuiteClasses({ AuthTranslationServiceTest.EndToEndTest.class})
public class AuthTranslationServiceTest extends Suite {
    public AuthTranslationServiceTest(Class<?> klass, RunnerBuilder builder)
            throws InitializationError {
        super(klass, builder);
    }

    private static void startFactoryServices(BaseModelTest test) throws Throwable {
        test.getHost().startService(
                Operation.createPost(UriUtils.buildUri(test.getHost(),
                        AuthTranslationService.class)),
                new AuthTranslationService());
        test.testStart(1);
        test.getHost().registerForServiceAvailability(test.getCompletion(), AuthTranslationService.SELF_LINK);
        test.testWait();
    }

    /**
     * This class implements end-to-end tests.
     */
    public static class EndToEndTest extends BaseModelTest {
        @Override
        protected void startRequiredServices() throws Throwable {
            AuthTranslationServiceTest.startFactoryServices(this);
            super.startRequiredServices();
        }

        @Test
        public void testGCEAuthTranslationService() throws Throwable {
            AuthTranslationService.AuthSecrets oauthSecrets = new AuthTranslationService.AuthSecrets();
            final String[] authSecretsLink = { null };

            oauthSecrets.client_id = "foo";
            oauthSecrets.client_email = "foo@bar.com";
            oauthSecrets.private_key = "not_a_secret";
            oauthSecrets.private_key_id = "0";
            oauthSecrets.type = "service_account";
            oauthSecrets.environmentName = ComputeDescriptionService.ComputeDescription.ENVIRONMENT_NAME_GCE;

            this.host.testStart(1);
            Operation op = Operation
                    .createPost(
                            UriUtils.buildUri(this.host,
                                    AuthTranslationService.SELF_LINK))
                    .setBody(oauthSecrets)
                    .setCompletion(
                            (o, e) -> {
                                if (e != null) {
                                    this.host.failIteration(e);
                                    return;
                                }
                                AuthCredentialsServiceState secrets = o
                                        .getBody(AuthCredentialsServiceState.class);

                                authSecretsLink[0] = secrets.documentSelfLink;
                                this.host.completeIteration();
                            });
            this.host.send(op);
            this.host.testWait();

            assertNotNull(authSecretsLink[0]);

            AuthCredentialsService.AuthCredentialsServiceState outSecrets = this.host.getServiceState(
                    null,
                    AuthCredentialsService.AuthCredentialsServiceState.class,
                    UriUtils.buildUri(this.host, authSecretsLink[0]));

            assertEquals(outSecrets.userLink, oauthSecrets.client_id);
            assertEquals(outSecrets.userEmail, oauthSecrets.client_email);
            assertEquals(outSecrets.privateKey, oauthSecrets.private_key);
            assertEquals(outSecrets.privateKeyId, oauthSecrets.private_key_id);
            assertEquals(outSecrets.type, oauthSecrets.type);
        }

        @Test
        public void testAWSAuthTranslationService() throws Throwable {
            AuthTranslationService.AuthSecrets oauthSecrets = new AuthTranslationService.AuthSecrets();
            final String[] authSecretsLink = { null };

            oauthSecrets.privateKey = "foo";
            oauthSecrets.privateKeyId = "foo-id";
            oauthSecrets.environmentName = ComputeDescriptionService.ComputeDescription.ENVIRONMENT_NAME_AWS;

            this.host.testStart(1);
            Operation op = Operation
                    .createPost(
                            UriUtils.buildUri(this.host,
                                    AuthTranslationService.SELF_LINK))
                    .setBody(oauthSecrets)
                    .setCompletion(
                            (o, e) -> {
                                if (e != null) {
                                    this.host.failIteration(e);
                                    return;
                                }
                                AuthCredentialsServiceState secrets = o
                                        .getBody(AuthCredentialsServiceState.class);

                                authSecretsLink[0] = secrets.documentSelfLink;
                                this.host.completeIteration();
                            });
            this.host.send(op);
            this.host.testWait();

            assertNotNull(authSecretsLink[0]);

            AuthCredentialsService.AuthCredentialsServiceState outSecrets = this.host.getServiceState(
                    null,
                    AuthCredentialsService.AuthCredentialsServiceState.class,
                    UriUtils.buildUri(this.host, authSecretsLink[0]));

            assertEquals(outSecrets.privateKey, oauthSecrets.privateKey);
            assertEquals(outSecrets.privateKeyId, oauthSecrets.privateKeyId);
        }
    }
}
