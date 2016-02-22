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

import com.vmware.photon.controller.model.UriPaths;

/**
 * URI definitions for AWS adapters.
 */
public class AWSUriPaths {

    public static final String PROVISIONING_AWS = UriPaths.PROVISIONING
            + "/aws";
    public static final String AWS_INSTANCE_SERVICE = PROVISIONING_AWS
            + "/instance-service";
    public static final String AWS_NETWORK_SERVICE = PROVISIONING_AWS
            + "/network-service";
    public static final String AWS_FIREWALL_SERVICE = PROVISIONING_AWS
            + "/firewall-service";
}
