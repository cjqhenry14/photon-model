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

package com.vmware.photon.controller.model.tasks;

import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.test.VerificationHost;

/**
 * Test utility methods
 *
 */
public class TestUtils {

    public static final String ID_DELIMITER_CHAR = "-";

    /**
     * Generic doPost.
     *
     * @param host VerificationHost
     * @param inState Body to POST
     * @param type Body type to return
     * @param uri URI to post to
     * @param <T> type
     * @return State of service after POST
     * @throws Throwable
     */
    public static <T extends ServiceDocument, B extends ServiceDocument> B doPost(
            VerificationHost host, Class<B> type, URI uri) throws Throwable {
        final ServiceDocument[] doc = { null };
        host.testStart(1);
        Operation post = Operation
                .createPost(uri)
                .setCompletion(
                        (o, e) -> {
                            if (e != null) {
                                host.failIteration(e);
                                return;
                            }
                            doc[0] = o.getBody(ServiceDocument.class);
                            host.completeIteration();
                        });
        host.send(post);
        host.testWait();
        host.logThroughput();

        B outState = host.getServiceState(null,
                type,
                UriUtils.buildUri(uri.getHost(), uri.getPort(), doc[0].documentSelfLink, null));

        return outState;
    }

    /**
     * Generic doPost.
     *
     * @param host VerificationHost
     * @param inState Body to POST
     * @param type Body type to return
     * @param uri URI to post to
     * @param <T> type
     * @return State of service after POST
     * @throws Throwable
     */
    public static <T extends ServiceDocument, B extends ServiceDocument> B doPost(
            VerificationHost host, T inState, Class<B> type, URI uri) throws Throwable {
        final ServiceDocument[] doc = { null };
        host.testStart(1);
        Operation post = Operation
                .createPost(uri)
                .setBody(inState).setCompletion(
                        (o, e) -> {
                            if (e != null) {
                                host.failIteration(e);
                                return;
                            }
                            doc[0] = o.getBody(ServiceDocument.class);
                            host.completeIteration();
                        });
        host.send(post);
        host.testWait();
        host.logThroughput();

        B outState = host.getServiceState(null,
                type,
                UriUtils.buildUri(uri.getHost(), uri.getPort(), doc[0].documentSelfLink, null));

        return outState;
    }

    /**
     * Load a file
     * @param file
     * @return
     * @throws Throwable
     */
    public static String loadTestResource(@SuppressWarnings("rawtypes") Class clazz, String file) throws Throwable {
        URL url = clazz.getResource(file);
        Path resPath = Paths.get(url.toURI());
        return new String(Files.readAllBytes(resPath), "UTF8");
    }
}
