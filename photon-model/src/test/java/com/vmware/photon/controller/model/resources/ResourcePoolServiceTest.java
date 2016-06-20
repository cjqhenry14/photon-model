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

import java.net.URI;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
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
import com.vmware.xenon.common.ServiceDocumentDescription;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.QueryTask;
import com.vmware.xenon.services.common.TenantService;

/**
 * This class implements tests for the {@link ResourcePoolService} class.
 */
@RunWith(ResourcePoolServiceTest.class)
@SuiteClasses({ ResourcePoolServiceTest.ConstructorTest.class,
        ResourcePoolServiceTest.HandleStartTest.class,
        ResourcePoolServiceTest.HandlePatchTest.class,
        ResourcePoolServiceTest.QueryTest.class })
public class ResourcePoolServiceTest extends Suite {

    public ResourcePoolServiceTest(Class<?> klass, RunnerBuilder builder)
            throws InitializationError {
        super(klass, builder);
    }

    private static ResourcePoolService.ResourcePoolState buildValidStartState()
            throws Throwable {
        ResourcePoolService.ResourcePoolState rp = new ResourcePoolService.ResourcePoolState();
        rp.id = UUID.randomUUID().toString();
        rp.currencyUnit = "US dollar";
        rp.maxCpuCostPerMinute = 10.0;
        rp.maxCpuCount = 16;
        rp.maxDiskCapacityBytes = 2 ^ 40L;
        rp.maxDiskCostPerMinute = 10.0;
        rp.maxGpuCount = 16;
        rp.maxMemoryBytes = 2 ^ 36L;
        rp.minCpuCount = 2;
        rp.minDiskCapacityBytes = 2 ^ 40L;
        rp.minGpuCount = 0;
        rp.minMemoryBytes = 2 ^ 34L;
        rp.name = "esx medium resource pool";
        rp.projectName = "GCE-project-123";
        return rp;
    }

    /**
     * This class implements tests for the constructor.
     */
    public static class ConstructorTest {

        private ResourcePoolService resourcePoolService;

        @Before
        public void setUpTest() {
            this.resourcePoolService = new ResourcePoolService();
        }

        @Test
        public void testServiceOptions() {

            EnumSet<Service.ServiceOption> expected = EnumSet.of(
                    Service.ServiceOption.CONCURRENT_GET_HANDLING,
                    Service.ServiceOption.OWNER_SELECTION,
                    Service.ServiceOption.PERSISTENCE,
                    Service.ServiceOption.REPLICATION,
                    Service.ServiceOption.HTML_USER_INTERFACE,
                    Service.ServiceOption.IDEMPOTENT_POST);

            assertThat(this.resourcePoolService.getOptions(), is(expected));
        }
    }

    /**
     * This class implements tests for the handleStart method.
     */
    public static class HandleStartTest extends BaseModelTest {
        @Test
        public void testValidStartState() throws Throwable {
            ResourcePoolService.ResourcePoolState startState = buildValidStartState();
            ResourcePoolService.ResourcePoolState returnState = postServiceSynchronously(
                    ResourcePoolService.FACTORY_LINK, startState,
                    ResourcePoolService.ResourcePoolState.class);

            assertNotNull(returnState);
            assertThat(returnState.id, is(startState.id));
            assertThat(returnState.name, is(startState.name));
            assertThat(returnState.projectName, is(startState.projectName));
            assertThat(returnState.currencyUnit, is(startState.currencyUnit));
            assertThat(returnState.maxCpuCount, is(startState.maxCpuCount));
            assertThat(returnState.maxGpuCount, is(startState.maxGpuCount));
            assertThat(returnState.maxMemoryBytes,
                    is(startState.maxMemoryBytes));
            assertThat(returnState.minMemoryBytes,
                    is(startState.minMemoryBytes));
            assertThat(returnState.maxCpuCostPerMinute,
                    is(startState.maxCpuCostPerMinute));
            assertThat(returnState.maxDiskCapacityBytes,
                    is(startState.maxDiskCapacityBytes));
        }

        @Test
        public void testDuplicatePost() throws Throwable {
            ResourcePoolService.ResourcePoolState startState = buildValidStartState();
            ResourcePoolService.ResourcePoolState returnState = postServiceSynchronously(
                    ResourcePoolService.FACTORY_LINK, startState,
                    ResourcePoolService.ResourcePoolState.class);

            assertNotNull(returnState);
            assertThat(returnState.projectName, is(startState.projectName));
            startState.projectName = "new projectName";
            returnState = postServiceSynchronously(
                    ResourcePoolService.FACTORY_LINK, startState,
                    ResourcePoolService.ResourcePoolState.class);

            assertNotNull(returnState);
            assertThat(returnState.projectName, is(startState.projectName));
            ResourcePoolService.ResourcePoolState newState = getServiceSynchronously(
                    returnState.documentSelfLink,
                    ResourcePoolService.ResourcePoolState.class);
            assertThat(newState.projectName, is(startState.projectName));
        }

        @Test
        public void testMissingId() throws Throwable {
            ResourcePoolService.ResourcePoolState startState = buildValidStartState();
            startState.id = null;

            ResourcePoolService.ResourcePoolState returnState = postServiceSynchronously(
                    ResourcePoolService.FACTORY_LINK, startState,
                    ResourcePoolService.ResourcePoolState.class);

            assertNotNull(returnState);
            assertNotNull(returnState.id);
        }
    }

    /**
     * This class implements tests for the handlePatch method.
     */
    public static class HandlePatchTest extends BaseModelTest {
        @Test
        public void testPatchResourcePoolName() throws Throwable {
            ResourcePoolService.ResourcePoolState startState = createResourcePoolService();

            ResourcePoolService.ResourcePoolState patchState = new ResourcePoolService.ResourcePoolState();
            patchState.name = UUID.randomUUID().toString();
            patchState.tenantLinks = new ArrayList<String>();
            patchState.tenantLinks.add("tenant1");
            patchState.groupLinks = new HashSet<String>();
            patchState.groupLinks.add("group1");
            patchServiceSynchronously(startState.documentSelfLink,
                    patchState);

            ResourcePoolService.ResourcePoolState newState = getServiceSynchronously(
                    startState.documentSelfLink,
                    ResourcePoolService.ResourcePoolState.class);
            assertThat(newState.name, is(patchState.name));
            assertEquals(newState.tenantLinks, patchState.tenantLinks);
            assertEquals(newState.groupLinks, patchState.groupLinks);
        }

        @Test
        public void testPatchResourcePoolProjectName() throws Throwable {
            ResourcePoolService.ResourcePoolState startState = createResourcePoolService();

            ResourcePoolService.ResourcePoolState patchState = new ResourcePoolService.ResourcePoolState();
            patchState.projectName = UUID.randomUUID().toString();
            patchServiceSynchronously(startState.documentSelfLink,
                    patchState);

            ResourcePoolService.ResourcePoolState newState = getServiceSynchronously(
                    startState.documentSelfLink,
                    ResourcePoolService.ResourcePoolState.class);
            assertThat(newState.projectName, is(patchState.projectName));
        }

        @Test
        public void testPatchResourcePoolDiskCost() throws Throwable {
            ResourcePoolService.ResourcePoolState startState = createResourcePoolService();

            ResourcePoolService.ResourcePoolState patchState = new ResourcePoolService.ResourcePoolState();
            patchState.maxDiskCostPerMinute = 12345.6789;
            patchServiceSynchronously(startState.documentSelfLink, patchState);

            ResourcePoolService.ResourcePoolState newState = getServiceSynchronously(
                    startState.documentSelfLink,
                    ResourcePoolService.ResourcePoolState.class);
            assertThat(newState.maxDiskCostPerMinute,
                    is(patchState.maxDiskCostPerMinute));
        }

        @Test
        public void testPatchResourcePoolCpuCost() throws Throwable {
            ResourcePoolService.ResourcePoolState startState = createResourcePoolService();

            ResourcePoolService.ResourcePoolState patchState = new ResourcePoolService.ResourcePoolState();
            patchState.maxCpuCostPerMinute = 12345.6789;
            patchServiceSynchronously(startState.documentSelfLink,
                    patchState);

            ResourcePoolService.ResourcePoolState newState = getServiceSynchronously(
                    startState.documentSelfLink,
                    ResourcePoolService.ResourcePoolState.class);
            assertThat(newState.maxCpuCostPerMinute,
                    is(patchState.maxCpuCostPerMinute));
        }

        @Test
        public void testPatchResourcePoolCpuCount() throws Throwable {
            ResourcePoolService.ResourcePoolState startState = createResourcePoolService();

            ResourcePoolService.ResourcePoolState patchState = new ResourcePoolService.ResourcePoolState();
            patchState.maxCpuCount = 500L;
            patchServiceSynchronously(startState.documentSelfLink,
                    patchState);

            ResourcePoolService.ResourcePoolState newState = getServiceSynchronously(
                    startState.documentSelfLink,
                    ResourcePoolService.ResourcePoolState.class);
            assertThat(newState.maxCpuCount, is(startState.maxCpuCount));
        }

        private ResourcePoolService.ResourcePoolState createResourcePoolService()
                throws Throwable {
            ResourcePoolService.ResourcePoolState startState = buildValidStartState();
            return postServiceSynchronously(
                    ResourcePoolService.FACTORY_LINK, startState,
                    ResourcePoolService.ResourcePoolState.class);
        }
    }

    /**
     * This class implements tests for query.
     */
    public static class QueryTest extends BaseModelTest {
        @Test
        public void testTenantLinksQuery() throws Throwable {
            ResourcePoolService.ResourcePoolState rp = buildValidStartState();

            URI tenantUri = UriUtils.buildFactoryUri(host, TenantService.class);
            rp.tenantLinks = new ArrayList<>();
            rp.tenantLinks.add(UriUtils.buildUriPath(tenantUri.getPath(),
                    "tenantA"));

            ResourcePoolService.ResourcePoolState startState = postServiceSynchronously(
                    ResourcePoolService.FACTORY_LINK, rp,
                    ResourcePoolService.ResourcePoolState.class);

            String kind = Utils
                    .buildKind(ResourcePoolService.ResourcePoolState.class);
            String propertyName = QueryTask.QuerySpecification
                    .buildCollectionItemName(ServiceDocumentDescription.FIELD_NAME_TENANT_LINKS);

            QueryTask q = createDirectQueryTask(kind, propertyName,
                    rp.tenantLinks.get(0));
            q = querySynchronously(q);
            assertNotNull(q.results.documentLinks);
            assertThat(q.results.documentCount, is(1L));
            assertThat(q.results.documentLinks.get(0),
                    is(startState.documentSelfLink));
        }

        @Test
        public void testResourcePoolQuery() throws Throwable {
            // Create a resourcePool
            ResourcePoolService.ResourcePoolState rp = buildValidStartState();
            ResourcePoolService.ResourcePoolState startState = postServiceSynchronously(
                    ResourcePoolService.FACTORY_LINK, rp,
                    ResourcePoolService.ResourcePoolState.class);

            // Create a ComputeService in the same resource Pool
            ComputeDescriptionService.ComputeDescription cd = ComputeDescriptionServiceTest
                    .createComputeDescription(this);
            ComputeService.ComputeState cs = ComputeServiceTest
                    .buildValidStartState(cd);
            cs.resourcePoolLink = startState.documentSelfLink;
            ComputeService.ComputeState csStartState = postServiceSynchronously(
                    ComputeService.FACTORY_LINK,
                    cs, ComputeService.ComputeState.class);

            QueryTask q = new QueryTask();
            q.querySpec = startState.querySpecification;
            q.taskInfo.isDirect = true;
            QueryTask qr = querySynchronously(q);

            assertNotNull(qr.results.documentLinks);
            assertThat(qr.results.documentCount, is(1L));
            assertThat(qr.results.documentLinks.get(0),
                    is(csStartState.documentSelfLink));
        }
    }
}
