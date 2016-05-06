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

package com.vmware.photon.controller.model.adapters.util.enums;


/**
 * Common stages for enumeration adapters.
 */
public enum EnumerationStages {
    /**
     * Stage to lookup compute host for enumeration.
     */
    HOSTDESC,

    /**
     * Stage to lookup auth information for compute host.
     */
    PARENTAUTH,

    /**
     * Stage to get client for enumeration.
     */
    CLIENT,

    /**
     * Stage to start enumeration of resources.
     */
    ENUMERATE,

    /**
     * Stage to indicate that enumeration is finished.
     */
    FINISHED,

    /**
     * Stage to indicate error in enumeration.
     */
    ERROR
}
