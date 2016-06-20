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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.UUID;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import org.junit.runners.Suite.SuiteClasses;
import org.junit.runners.model.InitializationError;
import org.junit.runners.model.RunnerBuilder;

import com.vmware.photon.controller.model.helpers.BaseModelTest;

import com.vmware.xenon.common.Service;


/**
 * This class implements tests for the {@link ResourceGroupService} class.
 */
@RunWith(ResourceGroupServiceTest.class)
@SuiteClasses({ ResourceGroupServiceTest.ConstructorTest.class,
        ResourceGroupServiceTest.HandleStartTest.class,
        ResourceGroupServiceTest.HandlePatchTest.class})
public class ResourceGroupServiceTest extends Suite {

    public ResourceGroupServiceTest(Class<?> klass, RunnerBuilder builder)
            throws InitializationError {
        super(klass, builder);
    }

    private static ResourceGroupService.ResourceGroupState buildValidStartState()
            throws Throwable {
        ResourceGroupService.ResourceGroupState rg = new ResourceGroupService.ResourceGroupState();
        rg.name = "my resource group";
        rg.customProperties = new HashMap<String, String>();
        rg.customProperties.put("key1", "value1");
        return rg;
    }

    /**
     * This class implements tests for the constructor.
     */
    public static class ConstructorTest {

        private ResourceGroupService resourceGroupService;

        @Before
        public void setUpTest() {
            this.resourceGroupService = new ResourceGroupService();
        }

        @Test
        public void testServiceOptions() {

            EnumSet<Service.ServiceOption> expected = EnumSet.of(
                    Service.ServiceOption.CONCURRENT_GET_HANDLING,
                    Service.ServiceOption.OWNER_SELECTION,
                    Service.ServiceOption.PERSISTENCE,
                    Service.ServiceOption.REPLICATION,
                    Service.ServiceOption.IDEMPOTENT_POST);

            assertThat(this.resourceGroupService.getOptions(), is(expected));
        }
    }

    /**
     * This class implements tests for the handleStart method.
     */
    public static class HandleStartTest extends BaseModelTest {
        @Test
        public void testValidStartState() throws Throwable {
            ResourceGroupService.ResourceGroupState startState = buildValidStartState();
            ResourceGroupService.ResourceGroupState returnState = postServiceSynchronously(
                    ResourceGroupService.FACTORY_LINK, startState,
                    ResourceGroupService.ResourceGroupState.class);

            assertThat(returnState.name, is(startState.name));
            assertEquals(returnState.customProperties, returnState.customProperties);
        }

        @Test
        public void testDuplicatePost() throws Throwable {
            ResourceGroupService.ResourceGroupState startState = buildValidStartState();
            ResourceGroupService.ResourceGroupState returnState = postServiceSynchronously(
                    ResourceGroupService.FACTORY_LINK, startState,
                    ResourceGroupService.ResourceGroupState.class);

            assertNotNull(returnState);
            assertThat(returnState.name, is(startState.name));
            startState.name = "new name";
            returnState = postServiceSynchronously(
                    ResourceGroupService.FACTORY_LINK, startState,
                    ResourceGroupService.ResourceGroupState.class);

            assertNotNull(returnState);
            assertThat(returnState.name, is(startState.name));
            ResourceGroupService.ResourceGroupState newState = getServiceSynchronously(
                    returnState.documentSelfLink,
                    ResourceGroupService.ResourceGroupState.class);
            assertThat(newState.name, is(startState.name));
        }

        @Test
        public void testMissingName() throws Throwable {
            ResourceGroupService.ResourceGroupState startState = buildValidStartState();
            startState.name = null;
            postServiceSynchronously(ResourceGroupService.FACTORY_LINK,
                    startState, ResourceGroupService.ResourceGroupState.class,
                    IllegalArgumentException.class);
        }
    }

    /**
     * This class implements tests for the handlePatch method.
     */
    public static class HandlePatchTest extends BaseModelTest {
        @Test
        public void testPatchResourceGroupName() throws Throwable {
            ResourceGroupService.ResourceGroupState startState = postServiceSynchronously(
                    ResourceGroupService.FACTORY_LINK, buildValidStartState(),
                    ResourceGroupService.ResourceGroupState.class);

            ResourceGroupService.ResourceGroupState patchState = new ResourceGroupService.ResourceGroupState();
            patchState.name = UUID.randomUUID().toString();
            patchState.customProperties = new HashMap<String, String>();
            patchState.customProperties.put("key2", "value2");
            patchState.tenantLinks = new ArrayList<String>();
            patchState.tenantLinks.add("tenant1");
            patchServiceSynchronously(startState.documentSelfLink,
                    patchState);

            ResourceGroupService.ResourceGroupState newState = getServiceSynchronously(
                    startState.documentSelfLink,
                    ResourceGroupService.ResourceGroupState.class);
            assertThat(newState.name, is(patchState.name));
            assertEquals(newState.tenantLinks, patchState.tenantLinks);
            assertEquals(newState.customProperties.size(), 2);
        }
    }
}
