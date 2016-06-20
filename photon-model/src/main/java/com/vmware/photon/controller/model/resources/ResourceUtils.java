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

package com.vmware.photon.controller.model.resources;

import java.util.Map;

import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocumentDescription;
import com.vmware.xenon.common.Utils;

public class ResourceUtils {

    /**
     * Update status code and complete the patch operation
     * @param patch the PATCH operation
     * @param hasStateChanged true if the patch has updated the service state, false otherwise
     */
    public static void complePatchOperation(Operation patch, boolean hasStateChanged) {
        if (!hasStateChanged) {
            patch.setStatusCode(Operation.STATUS_CODE_NOT_MODIFIED);
        }
        patch.complete();
    }

    /**
     * Update the state of the service based on the input patch
     *
     * @param description service document description
     * @param source currentState of the service
     * @param patch patch state
     * @return
     */
    public static boolean mergeWithState(ServiceDocumentDescription description,
            ResourceState source, ResourceState patch) {
        boolean isChanged = Utils.mergeWithState(description, source, patch);
        if (patch.customProperties != null
                && !patch.customProperties.isEmpty()) {
            if (source.customProperties == null
                    || source.customProperties.isEmpty()) {
                source.customProperties = patch.customProperties;
            } else {
                for (Map.Entry<String, String> e : patch.customProperties
                        .entrySet()) {
                    source.customProperties.put(e.getKey(), e.getValue());
                }
            }
            isChanged = true;
        }

        if (patch.tenantLinks != null
                && !patch.tenantLinks.isEmpty()) {
            if (source.tenantLinks == null
                    || source.tenantLinks.isEmpty()) {
                source.tenantLinks = patch.tenantLinks;
                isChanged = true;
            } else {
                for (String e : patch.tenantLinks) {
                    if (!source.tenantLinks.contains(e)) {
                        source.tenantLinks.add(e);
                        isChanged = true;
                    }
                }
            }
        }

        if (patch.groupLinks != null
                && !patch.groupLinks.isEmpty()) {
            if (source.groupLinks == null
                    || source.groupLinks.isEmpty()) {
                source.groupLinks = patch.groupLinks;
                isChanged = true;
            } else {
                if (source.groupLinks.addAll(patch.groupLinks)) {
                    isChanged = true;
                }
            }
        }
        return isChanged;
    }
}
