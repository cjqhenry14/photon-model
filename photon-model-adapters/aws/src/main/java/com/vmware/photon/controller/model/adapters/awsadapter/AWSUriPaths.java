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
    public static final String AWS_INSTANCE_ADAPTER = PROVISIONING_AWS
            + "/instance-adapter";
    public static final String AWS_NETWORK_ADAPTER = PROVISIONING_AWS
            + "/network-adapter";
    public static final String AWS_FIREWALL_ADAPTER = PROVISIONING_AWS
            + "/firewall-adapter";
    public static final String AWS_STATS_ADAPTER = PROVISIONING_AWS
            + "/stats-adapter";
    public static final String AWS_ENUMERATION_ADAPTER = PROVISIONING_AWS
            + "/enumeration-adapter";
    public static final String AWS_ENUMERATION_CREATION_ADAPTER = PROVISIONING_AWS
            + "/enumeration-creation-adapter";
    public static final String AWS_ENUMERATION_DELETION_ADAPTER = PROVISIONING_AWS
            + "/enumeration-deletion-adapter";
    public static final String AWS_COMPUTE_DESCRIPTION_CREATION_ADAPTER = PROVISIONING_AWS
            + "/compute-description-creation-adapter";
    public static final String AWS_COMPUTE_STATE_CREATION_ADAPTER = PROVISIONING_AWS
            + "/compute-state-creation-adapter";
}
