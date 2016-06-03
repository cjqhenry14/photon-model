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

package com.vmware.photon.controller.model;

/**
 * Common infrastructure provider properties for compute resources manipulated by adapters.
 */
public class ComputeProperties {

    /**
     * The display name of the compute resource.
     */
    public static final String CUSTOM_DISPLAY_NAME = "displayName";

    /**
     * The resource group name to use to group the resources. E.g. on vSpehere this can be the
     * folder name, on Azure this is the resourceGroupName, on AWS this value can be used to tag the
     * resources.
     */
    public static final String RESOURCE_GROUP_NAME = "resourceGroupName";
}
