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

package com.vmware.photon.controller.model.adapters.vsphere.util.connection;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import javax.xml.ws.BindingProvider;
import javax.xml.ws.handler.MessageContext;

import com.vmware.vim25.InvalidLocaleFaultMsg;
import com.vmware.vim25.InvalidLoginFaultMsg;
import com.vmware.vim25.ManagedObjectReference;
import com.vmware.vim25.RuntimeFaultFaultMsg;
import com.vmware.vim25.ServiceContent;
import com.vmware.vim25.UserSession;
import com.vmware.vim25.VimPortType;
import com.vmware.vim25.VimService;

/**
 * This simple object shows how to set up a vSphere connection as it was done in vSphere 4.x and is provided
 * as a reference for anyone working with older vSphere servers that do not support modern SSO features.
 * It is intended as a utility class for use by Samples that will need to connect before they can do anything useful.
 * This is a light weight POJO that should be very easy to reuse later.
 * <p>
 * Samples that need a connection open before they can do anything useful extend ConnectedVimServiceBase so that the
 * code in those samples can focus on demonstrating the feature at hand. The logic of most samples will not be
 * changed by the use of the BasicConnection or the SsoConnection.
 * </p>
 *
 */
public class BasicConnection implements Connection {
    public static final String SERVICE_INSTANCE = "ServiceInstance";
    private static final String REQUEST_TIMEOUT = "com.sun.xml.internal.ws.request.timeout";
    private VimService vimService;
    private VimPortType vimPort;
    private ServiceContent serviceContent;
    private UserSession userSession;
    private ManagedObjectReference svcInstRef;

    private boolean ignoreSslErrors;

    private URI uri;
    private String username;
    private String password = ""; // default password is empty since on rare occasion passwords are not set
    private Map<String, List<String>> headers;
    private long requestTimeoutMillis = -1;

    public void setURI(URI uri) {
        this.uri = uri;
    }

    @Override
    public String getUsername() {
        return this.username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    @Override
    public String getPassword() {
        return this.password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    @Override
    public VimService getVimService() {
        return this.vimService;
    }

    @Override
    public VimPortType getVimPort() {
        return this.vimPort;
    }

    @Override
    public ServiceContent getServiceContent() {
        return this.serviceContent;
    }

    @Override
    public UserSession getUserSession() {
        return this.userSession;
    }

    @Override
    public String getServiceInstanceName() {
        return SERVICE_INSTANCE;
    }

    @Override
    public Map<String, List<String>> getHeaders() {
        return this.headers;
    }

    @Override
    public ManagedObjectReference getServiceInstanceReference() {
        if (this.svcInstRef == null) {
            ManagedObjectReference ref = new ManagedObjectReference();
            ref.setType(this.getServiceInstanceName());
            ref.setValue(this.getServiceInstanceName());
            this.svcInstRef = ref;
        }
        return this.svcInstRef;
    }

    public void connect() {
        try {
            _connect();
        } catch (Exception e) {
            Throwable cause = (e.getCause() != null) ? e.getCause() : e;
            throw new BasicConnectionException(
                    "failed to connect: " + e.getMessage() + " : " + cause.getMessage(), cause);
        }
    }

    @SuppressWarnings("unchecked")
    private void _connect()
            throws RuntimeFaultFaultMsg, InvalidLocaleFaultMsg, InvalidLoginFaultMsg {
        this.vimService = new VimService();
        this.vimPort = this.vimService.getVimPort();
        BindingProvider bindingProvider = getBindingsProvider();
        Map<String, Object> requestContext = bindingProvider.getRequestContext();

        requestContext.put(BindingProvider.ENDPOINT_ADDRESS_PROPERTY, this.uri.toString());
        requestContext.put(BindingProvider.SESSION_MAINTAIN_PROPERTY, true);

        updateRequestTimeout();

        if (this.ignoreSslErrors) {
            IgnoreSslErrors.ignoreErrors(bindingProvider);
        }

        this.serviceContent = this.vimPort
                .retrieveServiceContent(this.getServiceInstanceReference());

        this.userSession = this.vimPort.login(
                this.serviceContent.getSessionManager(),
                this.username,
                this.password,
                null);

        this.headers = (Map<String, List<String>>) bindingProvider
                .getResponseContext().get(MessageContext.HTTP_RESPONSE_HEADERS);
    }

    private void updateRequestTimeout() {
        if (this.requestTimeoutMillis > 0 && getBindingsProvider() != null) {
            getBindingsProvider().getRequestContext()
                    .put(REQUEST_TIMEOUT, (int) this.requestTimeoutMillis);
        }
    }

    private BindingProvider getBindingsProvider() {
        return (BindingProvider) this.vimPort;
    }

    @Override
    public void close() {
        if (this.userSession == null) {
            return;
        }

        try {
            this.vimPort.logout(this.serviceContent.getSessionManager());
        } catch (Exception e) {
            Throwable cause = e.getCause();
            throw new BasicConnectionException(
                    "failed to close properly: " + e.getMessage() + " : " + cause
                            .getMessage(), cause);
        } finally {
            // A connection is very memory intensive, I'm helping the garbage collector here
            this.userSession = null;
            this.serviceContent = null;
            this.vimPort = null;
            this.vimService = null;
        }
    }

    @Override
    public URI getURI() {
        return this.uri;
    }

    public boolean isIgnoreSslErrors() {
        return this.ignoreSslErrors;
    }

    public void setIgnoreSslErrors(boolean ignoreSslErrors) {
        this.ignoreSslErrors = ignoreSslErrors;
    }

    @Override
    public void setRequestTimeout(long time, TimeUnit unit) {
        this.requestTimeoutMillis = TimeUnit.MILLISECONDS.convert(time, unit);

        updateRequestTimeout();
    }

    @Override
    public long getRequestTimeout(TimeUnit unit) {
        return this.requestTimeoutMillis;
    }

    public static class BasicConnectionException extends ConnectionException {
        private static final long serialVersionUID = 1L;

        public BasicConnectionException(String s, Throwable t) {
            super(s, t);
        }
    }

    @Override
    public Connection createUnmanagedCopy() {
        BasicConnection res = new BasicConnection();
        res.setURI(this.getURI());
        res.setPassword(this.getPassword());
        res.setIgnoreSslErrors(this.ignoreSslErrors);
        res.setUsername(this.getUsername());
        res.setRequestTimeout(this.getRequestTimeout(TimeUnit.MILLISECONDS), TimeUnit.MILLISECONDS);
        res.connect();
        return res;
    }
}
