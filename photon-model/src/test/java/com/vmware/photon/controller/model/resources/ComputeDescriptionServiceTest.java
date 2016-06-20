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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

import java.net.URI;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
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
 * This class implements tests for the {@link ComputeDescriptionService} class.
 */
@RunWith(ComputeDescriptionServiceTest.class)
@SuiteClasses({ ComputeDescriptionServiceTest.ConstructorTest.class,
        ComputeDescriptionServiceTest.HandleStartTest.class,
        ComputeDescriptionServiceTest.HandlePatchTest.class,
        ComputeDescriptionServiceTest.QueryTest.class })
public class ComputeDescriptionServiceTest extends Suite {
    private static final String TEST_DESC_PROPERTY_NAME = "testDescProperty";

    public ComputeDescriptionServiceTest(Class<?> klass, RunnerBuilder builder)
            throws InitializationError {
        super(klass, builder);
    }

    public static ComputeDescriptionService.ComputeDescription buildValidStartState()
            throws Throwable {
        ComputeDescriptionService.ComputeDescription cd = new ComputeDescriptionService.ComputeDescription();

        cd.bootAdapterReference = new URI("http://bootAdapterReference");
        cd.powerAdapterReference = new URI("http://powerAdapterReference");
        cd.instanceAdapterReference = new URI("http://instanceAdapterReference");
        cd.healthAdapterReference = new URI("http://healthAdapterReference");
        cd.enumerationAdapterReference = new URI(
                "http://enumerationAdapterReference");

        cd.dataCenterId = null;
        cd.networkId = null;
        cd.dataStoreId = null;

        ArrayList<String> children = new ArrayList<>();
        children.add(ComputeDescriptionService.ComputeDescription.ComputeType.VM_HOST
                .toString());

        cd.supportedChildren = children;
        cd.environmentName = ComputeDescriptionService.ComputeDescription.ENVIRONMENT_NAME_ON_PREMISE;
        cd.costPerMinute = 1;
        cd.cpuMhzPerCore = 1000;
        cd.cpuCount = 2;
        cd.gpuCount = 1;
        cd.currencyUnit = "USD";
        cd.totalMemoryBytes = Integer.MAX_VALUE;
        cd.id = UUID.randomUUID().toString();
        cd.name = "friendly-name";
        cd.regionId = "provider-specific-regions";
        cd.zoneId = "provider-specific-zone";
        return cd;
    }

    public static ComputeDescriptionService.ComputeDescription createComputeDescription(
            BaseModelTest test) throws Throwable {
        ComputeDescriptionService.ComputeDescription cd = ComputeDescriptionServiceTest
                .buildValidStartState();
        // disable periodic maintenance for tests by default.
        cd.healthAdapterReference = null;
        return test.postServiceSynchronously(
                ComputeDescriptionService.FACTORY_LINK, cd,
                ComputeDescriptionService.ComputeDescription.class);
    }

    /**
     * This class implements tests for the constructor.
     */
    public static class ConstructorTest {

        private ComputeDescriptionService computeDescriptionService;

        @Before
        public void setUpTest() {
            this.computeDescriptionService = new ComputeDescriptionService();
        }

        @Test
        public void testServiceOptions() {

            EnumSet<Service.ServiceOption> expected = EnumSet.of(
                    Service.ServiceOption.CONCURRENT_GET_HANDLING,
                    Service.ServiceOption.OWNER_SELECTION,
                    Service.ServiceOption.PERSISTENCE,
                    Service.ServiceOption.REPLICATION,
                    Service.ServiceOption.IDEMPOTENT_POST);

            assertThat(this.computeDescriptionService.getOptions(),
                    is(expected));
        }
    }

    /**
     * This class implements tests for the handleStart method.
     */
    public static class HandleStartTest extends BaseModelTest {

        @Test
        public void testValidStartState() throws Throwable {
            ComputeDescriptionService.ComputeDescription startState = buildValidStartState();
            ComputeDescriptionService.ComputeDescription returnState = postServiceSynchronously(
                    ComputeDescriptionService.FACTORY_LINK,
                            startState,
                            ComputeDescriptionService.ComputeDescription.class);

            assertNotNull(returnState);
            assertThat(returnState.id, is(startState.id));
            assertThat(returnState.name, is(startState.name));
            assertThat(returnState.regionId, is(startState.regionId));
            assertThat(returnState.environmentName,
                    is(startState.environmentName));
        }

        @Test
        public void testDuplicatePost() throws Throwable {
            ComputeDescriptionService.ComputeDescription startState = buildValidStartState();
            ComputeDescriptionService.ComputeDescription returnState = postServiceSynchronously(
                    ComputeDescriptionService.FACTORY_LINK,
                            startState,
                            ComputeDescriptionService.ComputeDescription.class);

            assertNotNull(returnState);
            assertThat(returnState.name, is(startState.name));
            startState.name = "new-name";
            returnState = postServiceSynchronously(
                    ComputeDescriptionService.FACTORY_LINK,
                            startState,
                            ComputeDescriptionService.ComputeDescription.class);
            assertThat(returnState.name, is(startState.name));
        }

        @Test
        public void testMissingId() throws Throwable {
            ComputeDescriptionService.ComputeDescription startState = buildValidStartState();
            startState.id = null;

            ComputeDescriptionService.ComputeDescription returnState = postServiceSynchronously(
                    ComputeDescriptionService.FACTORY_LINK,
                            startState,
                            ComputeDescriptionService.ComputeDescription.class);

            assertNotNull(returnState);
            assertNotNull(returnState.id);
        }

        @Test
        public void testMissingBootAdapterReference() throws Throwable {
            ComputeDescriptionService.ComputeDescription startState = buildValidStartState();
            startState.bootAdapterReference = null;

            postServiceSynchronously(
                    ComputeDescriptionService.FACTORY_LINK, startState,
                    ComputeDescriptionService.ComputeDescription.class,
                    IllegalArgumentException.class);
        }

        @Test
        public void testMissingPowerAdapterReference() throws Throwable {
            ComputeDescriptionService.ComputeDescription startState = buildValidStartState();
            startState.powerAdapterReference = null;

            postServiceSynchronously(
                    ComputeDescriptionService.FACTORY_LINK, startState,
                    ComputeDescriptionService.ComputeDescription.class,
                    IllegalArgumentException.class);
        }

        @Test
        public void testMissingInstanceAdapterReference() throws Throwable {
            ComputeDescriptionService.ComputeDescription startState = buildValidStartState();
            startState.instanceAdapterReference = null;

            postServiceSynchronously(
                    ComputeDescriptionService.FACTORY_LINK, startState,
                    ComputeDescriptionService.ComputeDescription.class,
                    IllegalArgumentException.class);
        }
    }

    /**
     * This class implements tests for the handlePatch method.
     */
    public static class HandlePatchTest extends BaseModelTest {
        @Test
        public void testPatch() throws Throwable {
            ComputeDescriptionService.ComputeDescription startState = buildValidStartState();

            ComputeDescriptionService.ComputeDescription returnState = postServiceSynchronously(
                    ComputeDescriptionService.FACTORY_LINK,
                            startState, ComputeDescriptionService.ComputeDescription.class);

            ComputeDescriptionService.ComputeDescription patchState = new ComputeDescriptionService.ComputeDescription();
            patchState.tenantLinks = new ArrayList<String>();
            patchState.tenantLinks.add("tenant1");
            patchState.groupLinks = new HashSet<String>();
            patchState.groupLinks.add("group1");
            patchServiceSynchronously(returnState.documentSelfLink,
                    patchState);

            returnState = getServiceSynchronously(
                    returnState.documentSelfLink,
                    ComputeDescriptionService.ComputeDescription.class);

            assertEquals(returnState.tenantLinks, patchState.tenantLinks);
            assertEquals(returnState.groupLinks, patchState.groupLinks);

        }
    }
    /**
     * This class implements tests for query.
     */
    public static class QueryTest extends BaseModelTest {

        @Test
        public void testTenantLinksQuery() throws Throwable {
            ComputeDescriptionService.ComputeDescription disk = buildValidStartState();

            URI tenantUri = UriUtils.buildFactoryUri(host, TenantService.class);
            disk.tenantLinks = new ArrayList<>();
            disk.tenantLinks.add(UriUtils.buildUriPath(tenantUri.getPath(),
                    "tenantA"));

            ComputeDescriptionService.ComputeDescription startState = postServiceSynchronously(
                    ComputeDescriptionService.FACTORY_LINK, disk,
                            ComputeDescriptionService.ComputeDescription.class);

            String kind = Utils
                    .buildKind(ComputeDescriptionService.ComputeDescription.class);
            String propertyName = QueryTask.QuerySpecification
                    .buildCollectionItemName(ServiceDocumentDescription.FIELD_NAME_TENANT_LINKS);

            QueryTask q = createDirectQueryTask(kind, propertyName,
                    disk.tenantLinks.get(0));
            q = querySynchronously(q);
            assertNotNull(q.results.documentLinks);
            assertThat(q.results.documentCount, is(1L));
            assertThat(q.results.documentLinks.get(0),
                    is(startState.documentSelfLink));
        }

        @Test
        public void testCustomPropertiesQuery() throws Throwable {
            String newCustomPropertyValue = UUID.randomUUID().toString();

            ComputeDescriptionService.ComputeDescription cd = buildValidStartState();
            cd.customProperties = new HashMap<>();
            cd.customProperties.put(TEST_DESC_PROPERTY_NAME,
                    newCustomPropertyValue);

            postServiceSynchronously(
                    ComputeDescriptionService.FACTORY_LINK, cd,
                    ComputeDescriptionService.ComputeDescription.class);

            String kind = Utils
                    .buildKind(ComputeDescriptionService.ComputeDescription.class);
            String propertyName = QueryTask.QuerySpecification
                    .buildCompositeFieldName(
                            ComputeService.ComputeState.FIELD_NAME_CUSTOM_PROPERTIES,
                            TEST_DESC_PROPERTY_NAME);

            // Query computes with newCustomPropClause and expect 1 instance
            QueryTask q = createDirectQueryTask(kind, propertyName,
                    newCustomPropertyValue);
            queryComputes(q, 1);
        }

        @Test
        public void testSupportedChildrenQuery() throws Throwable {
            ComputeDescriptionService.ComputeDescription cd = buildValidStartState();
            cd.supportedChildren
                    .add(ComputeDescriptionService.ComputeDescription.ComputeType.DOCKER_CONTAINER
                            .toString());
            postServiceSynchronously(
                    ComputeDescriptionService.FACTORY_LINK, cd,
                    ComputeDescriptionService.ComputeDescription.class);

            String kind = Utils
                    .buildKind(ComputeDescriptionService.ComputeDescription.class);
            String propertyName = QueryTask.QuerySpecification
                    .buildCollectionItemName(ComputeDescriptionService.ComputeDescription.FIELD_NAME_SUPPORTED_CHILDREN);

            // Query computes with newCustomPropClause and expect 1 instance
            QueryTask q = createDirectQueryTask(
                            kind,
                            propertyName,
                            ComputeDescriptionService.ComputeDescription.ComputeType.DOCKER_CONTAINER
                                    .toString());
            queryComputes(q, 1);
        }

        private void queryComputes(QueryTask q, int expectedCount)
                throws Throwable {
            QueryTask queryTask = querySynchronously(q);
            assertNotNull(queryTask.results.documentLinks);
            assertFalse(queryTask.results.documentLinks.isEmpty());
            assertThat(queryTask.results.documentLinks.size(),
                    is(expectedCount));
        }
    }
}
