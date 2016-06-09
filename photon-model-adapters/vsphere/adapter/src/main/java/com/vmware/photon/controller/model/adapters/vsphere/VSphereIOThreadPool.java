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

import java.net.URI;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.vmware.photon.controller.model.adapters.vsphere.util.connection.BasicConnection;
import com.vmware.photon.controller.model.adapters.vsphere.util.connection.Connection;
import com.vmware.photon.controller.model.adapters.vsphere.util.connection.ConnectionException;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.services.common.AuthCredentialsService.AuthCredentialsServiceState;

/**
 * Manages a threadpool that executes request to vsphere instances. A threadpool is allocated per
 * ServiceHost, not per vSphere.
 */
public class VSphereIOThreadPool {
    private static final Logger logger = Logger.getLogger(VSphereIOThreadPool.class.getName());
    private final ExecutorService executorService;
    private final ServiceHost host;

    public VSphereIOThreadPool(ServiceHost host, ExecutorService executorService) {
        this.host = host;
        this.executorService = executorService;
    }

    public static VSphereIOThreadPool createDefault(ServiceHost host, int concurrency) {
        return new VSphereIOThreadPool(host, Executors.newFixedThreadPool(concurrency));
    }

    /**
     * This method will execute the provided callback in a managed threadpool and give it
     * a connection authenticated with the credentials found in the parentAuthLink.
     * @param  sender
     * @param adapterReference SDK url of vSpehere host, usually found in
     *      {@link com.vmware.photon.controller.model.resources.ComputeService.ComputeState#adapterManagementReference}.
     *      Probably looks like https://hostname:443/sdk
     * @param auth authorization object used to authenticate against vsphere host
     * @param callback non-null callback
     */
    public void submit(Service sender, URI adapterReference, AuthCredentialsServiceState auth,
            ConnectionCallback callback) {

        execute(adapterReference, auth, callback);
    }

    /**
     * @see {@link #execute(URI, AuthCredentialsServiceState, ConnectionCallback)}
     *
     * @param sender
     * @param adapterReference
     * @param authLink where to look for the credentials
     * @param callback
     */
    public void submit(Service sender, URI adapterReference, String authLink,
            ConnectionCallback callback) {
        URI authUri = UriUtils.buildUri(this.host, authLink);

        Operation op = Operation.createGet(authUri)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        ConnectionException failure = new ConnectionException(
                                "Cannot retrieve credentials from " + authLink, e);

                        callback.doInConnection(null, failure);
                        return;
                    }

                    AuthCredentialsServiceState auth = o.getBody(AuthCredentialsServiceState.class);
                    execute(adapterReference, auth, callback);
                });

        sender.sendRequest(op);
    }

    private void execute(URI adapterReference, AuthCredentialsServiceState auth,
            ConnectionCallback callback) {
        BasicConnection connection = new BasicConnection();

        // TODO control sslErrors policy externally
        connection.setIgnoreSslErrors(true);

        connection.setUsername(auth.privateKeyId);
        connection.setPassword(auth.privateKey);

        connection.setURI(adapterReference);

        // don't connect now, but as late as possible as the session can expire
        executeCallback(connection, callback);
    }

    private void executeCallback(BasicConnection connection, ConnectionCallback callback) {
        this.executorService.submit(() -> {
            try {
                // login and session creation
                connection.connect();
            } catch (ConnectionException e) {
                callback.doInConnection(null, e);
                return;
            }

            try {
                callback.doInConnection(connection, null);
            } finally {
                closeQuietly(connection);
            }
        });
    }

    private void closeQuietly(BasicConnection connection) {
        try {
            connection.close();
        } catch (Exception e) {
            logger.log(Level.WARNING, "Error closing connection to " + connection.getURI(), e);
        }
    }

    @FunctionalInterface
    public interface ConnectionCallback {
        /**
         *
         * @param connection connection to use for useful work
         * @param error only set if there was an error creating a connection
         */
        void doInConnection(Connection connection, ConnectionException error);
    }
}
