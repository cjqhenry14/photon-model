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

package com.vmware.photon.controller.model.tasks;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.UUID;

import com.vmware.photon.controller.model.helpers.BaseModelTest;
import com.vmware.photon.controller.model.resources.ComputeDescriptionFactoryService;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService;
import com.vmware.photon.controller.model.resources.ComputeDescriptionServiceTest;
import com.vmware.photon.controller.model.resources.ComputeFactoryService;
import com.vmware.photon.controller.model.resources.ComputeService;
import com.vmware.xenon.common.UriUtils;

/**
 * Utility class to create service documents for tests.
 */
public class ModelUtils {
    private static final String TEST_DESC_PROPERTY_NAME = "testDescProperty";
    private static final String TEST_DESC_PROPERTY_VALUE = UUID.randomUUID()
            .toString();

    public static ComputeDescriptionService.ComputeDescription createComputeDescription(
            BaseModelTest test, String instanceAdapterLink, String bootAdapterLink)
            throws Throwable {
        ComputeDescriptionService.ComputeDescription cd = ComputeDescriptionServiceTest
                .buildValidStartState();
        // disable periodic maintenance for tests by default.
        cd.healthAdapterReference = null;
        if (instanceAdapterLink != null) {
            cd.instanceAdapterReference = UriUtils.buildUri(test.getHost(),
                    instanceAdapterLink);
        }
        if (bootAdapterLink != null) {
            cd.bootAdapterReference = UriUtils.buildUri(test.getHost(), bootAdapterLink);
        }
        return test.postServiceSynchronously(
                ComputeDescriptionFactoryService.SELF_LINK, cd,
                ComputeDescriptionService.ComputeDescription.class);
    }

    public static ComputeService.ComputeStateWithDescription createCompute(
            BaseModelTest test, ComputeDescriptionService.ComputeDescription cd)
            throws Throwable {
        ComputeService.ComputeState cs = new ComputeService.ComputeStateWithDescription();
        cs.id = UUID.randomUUID().toString();
        cs.descriptionLink = cd.documentSelfLink;
        cs.resourcePoolLink = null;
        cs.address = "10.0.0.1";
        cs.primaryMAC = "01:23:45:67:89:ab";
        cs.powerState = ComputeService.PowerState.ON;
        cs.adapterManagementReference = URI
                .create("https://esxhost-01:443/sdk");
        cs.diskLinks = new ArrayList<>();
        cs.diskLinks.add("http://disk");
        cs.networkLinks = new ArrayList<>();
        cs.networkLinks.add("http://network");
        cs.customProperties = new HashMap<>();
        cs.customProperties.put(TEST_DESC_PROPERTY_NAME,
                TEST_DESC_PROPERTY_VALUE);
        cs.tenantLinks = new ArrayList<>();
        cs.tenantLinks.add("http://tenant");

        ComputeService.ComputeState returnState = test
                .postServiceSynchronously(ComputeFactoryService.SELF_LINK, cs,
                        ComputeService.ComputeState.class);

        return ComputeService.ComputeStateWithDescription.create(cd,
                returnState);
    }

    public static ComputeService.ComputeStateWithDescription createComputeWithDescription(
            BaseModelTest test, String instanceAdapterLink, String bootAdapterLink)
            throws Throwable {
        return ModelUtils.createCompute(test, ModelUtils
                .createComputeDescription(test, instanceAdapterLink,
                        bootAdapterLink));
    }

    public static ComputeService.ComputeStateWithDescription createComputeWithDescription(
            BaseModelTest test) throws Throwable {
        return createComputeWithDescription(test, null, null);
    }
}
