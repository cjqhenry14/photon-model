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

package com.vmware.photon.controller.model.adapters.vsphere.util.connection.helpers;

import com.vmware.photon.controller.model.adapters.vsphere.util.connection.Connection;

/**
 * Some samples make use of API only found on vCenter. Other samples
 * make use of API that only make sense when used with a Host. This
 * utility helps with determining if the proper connection has been made.
 */
public class ApiValidator extends BaseHelper {
    public static final String VCENTER_API_TYPE = "VirtualCenter";
    public static final String HOST_API_TYPE = "HostAgent";

    public ApiValidator(final Connection connection) {
        super(connection);
    }

    public String getApiType() {
        return this.connection.getServiceContent().getAbout().getApiType();
    }

    public boolean assertVCenter() {
        return isOfType(getApiType(), VCENTER_API_TYPE);
    }

    public boolean assertHost() {
        return isOfType(getApiType(), HOST_API_TYPE);
    }

    private boolean isOfType(final String apiType, final String requiredApiType) {
        return requiredApiType.equals(apiType);
    }

    public static boolean assertVCenter(final Connection connection) {
        return new ApiValidator(connection).assertVCenter();
    }

    public static boolean assertHost(final Connection connection) {
        return new ApiValidator(connection).assertHost();
    }
}
