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

import java.net.URL;
import java.util.List;
import java.util.Map;

import com.vmware.vim25.ManagedObjectReference;
import com.vmware.vim25.ServiceContent;
import com.vmware.vim25.UserSession;
import com.vmware.vim25.VimPortType;
import com.vmware.vim25.VimService;

/**
 * This simple object shows how to set up a vCenter connection. It is intended as a utility class for use
 * by Samples that will need to connect before they can do anything useful. This is a light weight POJO
 * that should be very easy to make portable.
 *
 */
public interface Connection {
    String getUrl();

    void setUrl(String url);

    String getHost();

    Integer getPort();

    String getUsername();

    void setUsername(String username);

    String getPassword();

    void setPassword(String password);

    VimService getVimService();

    VimPortType getVimPort();

    ServiceContent getServiceContent();

    UserSession getUserSession();

    String getServiceInstanceName();

    Map<String, List<String>> getHeaders();

    ManagedObjectReference getServiceInstanceReference();

    Connection connect();

    boolean isConnected();

    Connection disconnect();

    URL getURL();
}
