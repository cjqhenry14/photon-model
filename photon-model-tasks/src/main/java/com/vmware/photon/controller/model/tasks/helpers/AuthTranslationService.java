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

import java.net.URI;
import java.util.List;

import com.vmware.photon.controller.model.resources.ComputeDescriptionService;

import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.services.common.AuthCredentialsService;
import com.vmware.xenon.services.common.AuthCredentialsService.AuthCredentialsServiceState;

/**
 * AuthTranslationService.
 *
 * Used to create a xenon AuthCredentialsServiceState from a Oauth secrets body.
 */
public class AuthTranslationService extends StatelessService {
    public static final String SELF_LINK =  "/auth-trans";

    public static class AuthSecrets {
        public String environmentName;
        public String privateKey;
        public String privateKeyId;
        /**
         * Client ID.
         */
        public String client_id;

        /**
         * Client email.
         */
        public String client_email;

        /**
         * Service Account private key
         */
        public String private_key;

        /**
         * Service Account private key id
         */
        public String private_key_id;

        /**
         * Redirect URIs.
         */
        public List<String> redirect_uris;

        /**
         * Authorization server URI.
         */
        public String auth_uri;

        /**
         * Token server URI.
         */
        public URI token_uri;

        /**
         * Type of account
         */
        public String type;

        public List<String> tenantLinks;
    }

    @Override
    public void handleRequest(Operation op) {
        switch (op.getAction()) {
        case POST:
            validateOperation(op);
            Operation post = Operation
                    .createPost(this, AuthCredentialsService.FACTORY_LINK)
                    .setBody(translateCredentials(op))
                    .setCompletion(
                            (o, ex) -> {
                                if (ex != null) {
                                    op.fail(ex);
                                    return;
                                }
                                AuthCredentialsService.AuthCredentialsServiceState authBody = o
                                        .getBody(AuthCredentialsService
                                                .AuthCredentialsServiceState.class);

                                // the POST operation will return the resultant
                                // AuthCredentialsServiceState.
                                op.setBody(authBody).complete();
                            });
            sendRequest(post);
            break;
        default:
            super.handleRequest(op);
        }
    }

    private void validateOperation(Operation op) throws IllegalArgumentException {
        if (!op.hasBody()) {
            throw new IllegalArgumentException("must have body");
        }
    }

    private AuthCredentialsServiceState translateCredentials(Operation op) {

        AuthSecrets secrets = op.getBody(AuthSecrets.class);
        if (secrets.environmentName == null) {
            secrets.environmentName = "Unknown";
        }
        AuthCredentialsServiceState authState = new AuthCredentialsServiceState();
        authState.tenantLinks = secrets.tenantLinks;
        switch (secrets.environmentName) {
        case ComputeDescriptionService.ComputeDescription.ENVIRONMENT_NAME_AWS:
            authState.privateKey = secrets.privateKey;
            authState.privateKeyId = secrets.privateKeyId;
            break;
        default:
            authState.userLink = secrets.client_id;
            authState.userEmail = secrets.client_email;
            authState.privateKey = secrets.private_key;
            authState.privateKeyId = secrets.private_key_id;
            authState.tokenReference = secrets.token_uri;
            authState.type = secrets.type;
        }

        return authState;
    }
}
