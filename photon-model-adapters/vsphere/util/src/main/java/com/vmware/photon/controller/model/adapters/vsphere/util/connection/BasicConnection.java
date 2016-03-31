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

import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;
import java.util.Map;

import javax.xml.datatype.XMLGregorianCalendar;
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
    private VimService vimService;
    private VimPortType vimPort;
    private ServiceContent serviceContent;
    private UserSession userSession;
    private ManagedObjectReference svcInstRef;

    private boolean ignoreSslErrors;

    private URL url;
    private String username;
    private String password = ""; // default password is empty since on rare occasion passwords are not set
    private Map<String, List<String>> headers;

    public String getUrl() {
        return this.url.toString();
    }

    public void setUrl(String url) {
        try {
            this.url = new URL(url);
        } catch (MalformedURLException e) {
            throw new IllegalArgumentException(e);
        }
    }

    public String getHost() {
        return this.url.getHost();
    }

    public Integer getPort() {
        return this.url.getPort();
    }

    public String getUsername() {
        return this.username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return this.password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public VimService getVimService() {
        return this.vimService;
    }

    public VimPortType getVimPort() {
        return this.vimPort;
    }

    public ServiceContent getServiceContent() {
        return this.serviceContent;
    }

    public UserSession getUserSession() {
        return this.userSession;
    }

    public String getServiceInstanceName() {
        return SERVICE_INSTANCE;
    }

    public Map<String, List<String>> getHeaders() {
        return this.headers;
    }

    public ManagedObjectReference getServiceInstanceReference() {
        if (this.svcInstRef == null) {
            ManagedObjectReference ref = new ManagedObjectReference();
            ref.setType(this.getServiceInstanceName());
            ref.setValue(this.getServiceInstanceName());
            this.svcInstRef = ref;
        }
        return this.svcInstRef;
    }

    public Connection connect() {
        if (!isConnected()) {
            try {
                _connect();
            } catch (Exception e) {
                Throwable cause = (e.getCause() != null) ? e.getCause() : e;
                throw new BasicConnectionException(
                        "failed to connect: " + e.getMessage() + " : " + cause.getMessage(),
                        cause);
            }
        }
        return this;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void _connect() throws
            RuntimeFaultFaultMsg, InvalidLocaleFaultMsg, InvalidLoginFaultMsg {
        this.vimService = new VimService();
        this.vimPort = this.vimService.getVimPort();
        BindingProvider bindingProvider = (BindingProvider) this.vimPort;
        Map<String, Object> requestContext = bindingProvider.getRequestContext();

        requestContext.put(BindingProvider.ENDPOINT_ADDRESS_PROPERTY, this.url.toString());
        requestContext.put(BindingProvider.SESSION_MAINTAIN_PROPERTY, true);

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

    public boolean isConnected() {
        if (this.userSession == null) {
            return false;
        }

        XMLGregorianCalendar startedCalendar = this.userSession.getLastActiveTime();
        long startTime = startedCalendar.toGregorianCalendar().getTimeInMillis();

        // 30 minutes in milliseconds = 30 minutes * 60 seconds * 1000 milliseconds
        return System.currentTimeMillis() < startTime + 30 * 60 * 1000;
    }

    public Connection disconnect() {
        if (this.isConnected()) {
            try {
                this.vimPort.logout(this.serviceContent.getSessionManager());
            } catch (Exception e) {
                Throwable cause = e.getCause();
                throw new BasicConnectionException(
                        "failed to disconnect properly: " + e.getMessage() + " : " + cause
                                .getMessage(),
                        cause
                );
            } finally {
                // A connection is very memory intensive, I'm helping the garbage collector here
                this.userSession = null;
                this.serviceContent = null;
                this.vimPort = null;
                this.vimService = null;
            }
        }
        return this;
    }

    @Override
    public URL getURL() {
        return this.url;
    }

    public boolean isIgnoreSslErrors() {
        return this.ignoreSslErrors;
    }

    public void setIgnoreSslErrors(boolean ignoreSslErrors) {
        this.ignoreSslErrors = ignoreSslErrors;
    }

    private class BasicConnectionException extends ConnectionException {
        private static final long serialVersionUID = 1L;

        public BasicConnectionException(String s, Throwable t) {
            super(s, t);
        }
    }
}
