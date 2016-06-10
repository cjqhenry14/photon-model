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
import static org.junit.Assert.assertNotNull;

import java.net.URI;
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
import com.vmware.xenon.common.ServiceDocumentDescription;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.QueryTask;
import com.vmware.xenon.services.common.TenantService;

/**
 * This class implements tests for the {@link StorageDescriptionService} class.
 */
@RunWith(StorageDescriptionServiceTest.class)
@SuiteClasses({ StorageDescriptionServiceTest.ConstructorTest.class,
        StorageDescriptionServiceTest.HandleStartTest.class,
        StorageDescriptionServiceTest.HandlePatchTest.class,
        StorageDescriptionServiceTest.QueryTest.class })
public class StorageDescriptionServiceTest extends Suite {

    public StorageDescriptionServiceTest(Class<?> klass, RunnerBuilder builder)
            throws InitializationError {
        super(klass, builder);
    }

    private static StorageDescriptionService.StorageDescription buildValidStartState() {
        StorageDescriptionService.StorageDescription storageState = new StorageDescriptionService.StorageDescription();
        storageState.id = UUID.randomUUID().toString();
        storageState.name = "storageName";
        storageState.tenantLinks = new ArrayList<>();
        storageState.tenantLinks.add("tenant-linkA");
        storageState.regionId = "regionId";
        storageState.authCredentialsLink = "http://authCredentialsLink";
        storageState.resourcePoolLink = "http://resourcePoolLink";

        return storageState;
    }

    /**
     * This class implements tests for the constructor.
     */
    public static class ConstructorTest {
        private StorageDescriptionService storageDescriptionService = new StorageDescriptionService();

        @Before
        public void setupTest() {
            this.storageDescriptionService = new StorageDescriptionService();
        }

        @Test
        public void testServiceOptions() {
            EnumSet<Service.ServiceOption> expected = EnumSet.of(
                    Service.ServiceOption.CONCURRENT_GET_HANDLING,
                    Service.ServiceOption.PERSISTENCE,
                    Service.ServiceOption.REPLICATION,
                    Service.ServiceOption.OWNER_SELECTION);
            assertThat(this.storageDescriptionService.getOptions(), is(expected));
        }
    }

    /**
     * This class implements tests for the handleStart method.
     */
    public static class HandleStartTest extends BaseModelTest {
        @Test
        public void testValidStartState() throws Throwable {
            StorageDescriptionService.StorageDescription startState = buildValidStartState();
            StorageDescriptionService.StorageDescription returnState = postServiceSynchronously(
                    StorageDescriptionService.FACTORY_LINK,
                            startState, StorageDescriptionService.StorageDescription.class);

            assertNotNull(returnState);
            assertThat(returnState.id, is(startState.id));
            assertThat(returnState.name, is(startState.name));
            assertThat(returnState.tenantLinks.get(0),
                    is(startState.tenantLinks.get(0)));
            assertThat(returnState.regionId, is(startState.regionId));
            assertThat(returnState.authCredentialsLink,
                    is(startState.authCredentialsLink));
            assertThat(returnState.resourcePoolLink,
                    is(startState.resourcePoolLink));
        }

        @Test
        public void testDuplicatePost() throws Throwable {
            StorageDescriptionService.StorageDescription startState = buildValidStartState();
            StorageDescriptionService.StorageDescription returnState = postServiceSynchronously(
                    StorageDescriptionService.FACTORY_LINK,
                            startState, StorageDescriptionService.StorageDescription.class);

            assertNotNull(returnState);
            assertThat(returnState.name, is(startState.name));
            startState.name = "new-name";
            returnState = postServiceSynchronously(StorageDescriptionService.FACTORY_LINK,
                            startState, StorageDescriptionService.StorageDescription.class);
            assertThat(returnState.name, is(startState.name));
        }

        @Test
        public void testMissingBody() throws Throwable {
            postServiceSynchronously(
                    StorageDescriptionService.FACTORY_LINK,
                    null,
                    StorageDescriptionService.StorageDescription.class,
                    IllegalArgumentException.class);
        }

        @Test
        public void testMissingId() throws Throwable {
            StorageDescriptionService.StorageDescription startState = buildValidStartState();
            startState.id = null;

            StorageDescriptionService.StorageDescription returnState = postServiceSynchronously(
                    StorageDescriptionService.FACTORY_LINK, startState,
                    StorageDescriptionService.StorageDescription.class);

            assertNotNull(returnState);
            assertNotNull(returnState.id);
        }

        @Test
        public void testMissingName() throws Throwable {
            StorageDescriptionService.StorageDescription startState = buildValidStartState();
            startState.name = null;

            postServiceSynchronously(StorageDescriptionService.FACTORY_LINK,
                    startState, StorageDescriptionService.StorageDescription.class,
                    IllegalArgumentException.class);
        }
    }

    /**
     * This class implements tests for the handlePatch method.
     */
    public static class HandlePatchTest extends BaseModelTest {
        @Test
        public void testPatch() throws Throwable {
            StorageDescriptionService.StorageDescription startState = buildValidStartState();

            StorageDescriptionService.StorageDescription returnState = postServiceSynchronously(
                    StorageDescriptionService.FACTORY_LINK,
                            startState, StorageDescriptionService.StorageDescription.class);

            StorageDescriptionService.StorageDescription patchState = new StorageDescriptionService.StorageDescription();
            patchState.name = "patchStorageName";
            patchState.customProperties = new HashMap<>();
            patchState.customProperties.put("patchKey", "patchValue");
            patchState.regionId = "patchRegionId";
            patchState.authCredentialsLink = "http://patchAuthCredentialsLink";
            patchState.resourcePoolLink = "http://patchResourcePoolLink";

            patchServiceSynchronously(returnState.documentSelfLink,
                    patchState);

            returnState = getServiceSynchronously(
                    returnState.documentSelfLink,
                    StorageDescriptionService.StorageDescription.class);

            assertThat(returnState.name,
                    is(patchState.name));
            assertThat(returnState.customProperties,
                    is(patchState.customProperties));
            assertThat(returnState.regionId,
                    is(patchState.regionId));
            assertThat(returnState.authCredentialsLink,
                    is(patchState.authCredentialsLink));
            assertThat(returnState.resourcePoolLink,
                    is(patchState.resourcePoolLink));
        }


        @Test
        public void testPatchRegionId() throws Throwable {
            StorageDescriptionService.StorageDescription startState = buildValidStartState();
            startState.regionId = "startRegionId";

            StorageDescriptionService.StorageDescription returnState = postServiceSynchronously(
                    StorageDescriptionService.FACTORY_LINK, startState,
                    StorageDescriptionService.StorageDescription.class);

            StorageDescriptionService.StorageDescription patchState = new StorageDescriptionService.StorageDescription();
            patchState.regionId = null;

            patchServiceSynchronously(returnState.documentSelfLink,
                    patchState);
            returnState = getServiceSynchronously(
                    returnState.documentSelfLink, StorageDescriptionService.StorageDescription.class);
            assertThat(returnState.regionId, is("startRegionId"));
            patchState.regionId = "startRegionId";

            patchServiceSynchronously(returnState.documentSelfLink,
                    patchState);
            returnState = getServiceSynchronously(
                    returnState.documentSelfLink, StorageDescriptionService.StorageDescription.class);
            assertThat(returnState.regionId, is("startRegionId"));
            patchState.regionId = "patchRegionId";

            patchServiceSynchronously(returnState.documentSelfLink,
                    patchState);
            returnState = getServiceSynchronously(
                    returnState.documentSelfLink, StorageDescriptionService.StorageDescription.class);
            assertThat(returnState.regionId, is("patchRegionId"));
        }

        @Test
        public void testPatchName() throws Throwable {
            StorageDescriptionService.StorageDescription startState = buildValidStartState();
            startState.name = "startName";

            StorageDescriptionService.StorageDescription returnState = postServiceSynchronously(
                    StorageDescriptionService.FACTORY_LINK, startState,
                    StorageDescriptionService.StorageDescription.class);

            StorageDescriptionService.StorageDescription patchState = new StorageDescriptionService.StorageDescription();
            patchState.name = null;

            patchServiceSynchronously(returnState.documentSelfLink,
                    patchState);
            returnState = getServiceSynchronously(
                    returnState.documentSelfLink, StorageDescriptionService.StorageDescription.class);
            assertThat(returnState.name, is("startName"));
            patchState.name = "startName";

            patchServiceSynchronously(returnState.documentSelfLink,
                    patchState);
            returnState = getServiceSynchronously(
                    returnState.documentSelfLink, StorageDescriptionService.StorageDescription.class);
            assertThat(returnState.name, is("startName"));
            patchState.name = "patchName";

            patchServiceSynchronously(returnState.documentSelfLink,
                    patchState);
            returnState = getServiceSynchronously(
                    returnState.documentSelfLink, StorageDescriptionService.StorageDescription.class);
            assertThat(returnState.name, is("patchName"));
        }

        @Test
        public void testPatchType() throws Throwable {
            StorageDescriptionService.StorageDescription startState = buildValidStartState();
            startState.type = "startType";

            StorageDescriptionService.StorageDescription returnState = postServiceSynchronously(
                    StorageDescriptionService.FACTORY_LINK, startState,
                    StorageDescriptionService.StorageDescription.class);

            StorageDescriptionService.StorageDescription patchState = new StorageDescriptionService.StorageDescription();
            patchState.type = null;

            patchServiceSynchronously(returnState.documentSelfLink,
                    patchState);
            returnState = getServiceSynchronously(
                    returnState.documentSelfLink, StorageDescriptionService.StorageDescription.class);
            assertThat(returnState.type, is("startType"));
            patchState.type = "startType";

            patchServiceSynchronously(returnState.documentSelfLink,
                    patchState);
            returnState = getServiceSynchronously(
                    returnState.documentSelfLink, StorageDescriptionService.StorageDescription.class);
            assertThat(returnState.type, is("startType"));
            patchState.type = "patchType";

            patchServiceSynchronously(returnState.documentSelfLink,
                    patchState);
            returnState = getServiceSynchronously(
                    returnState.documentSelfLink, StorageDescriptionService.StorageDescription.class);
            assertThat(returnState.type, is("patchType"));
        }

        @Test
        public void testPatchCustomProperties() throws Throwable {
            StorageDescriptionService.StorageDescription startState = buildValidStartState();
            startState.customProperties = new HashMap<>();
            startState.customProperties.put("cp1-key", "cp1-value");

            StorageDescriptionService.StorageDescription returnState = postServiceSynchronously(
                    StorageDescriptionService.FACTORY_LINK, startState,
                    StorageDescriptionService.StorageDescription.class);

            StorageDescriptionService.StorageDescription patchState = new StorageDescriptionService.StorageDescription();
            patchState.customProperties = new HashMap<>();
            patchState.customProperties.put("cp2-key", "cp2-value");

            patchServiceSynchronously(returnState.documentSelfLink,
                    patchState);

            returnState = getServiceSynchronously(
                    returnState.documentSelfLink, StorageDescriptionService.StorageDescription.class);
            HashMap<Object, Object> expectedCustomProperties = new HashMap<>();
            expectedCustomProperties.putAll(startState.customProperties);
            expectedCustomProperties.putAll(patchState.customProperties);

            assertThat(returnState.customProperties, is(expectedCustomProperties));
        }
    }

    /**
     * This class implements tests for query.
     */
    public static class QueryTest extends BaseModelTest {
        @Test
        public void testTenantLinksQuery() throws Throwable {
            StorageDescriptionService.StorageDescription storageState = buildValidStartState();
            URI tenantUri = UriUtils.buildFactoryUri(host, TenantService.class);
            storageState.tenantLinks = new ArrayList<>();
            storageState.tenantLinks.add(UriUtils.buildUriPath(
                    tenantUri.getPath(), "tenantA"));
            StorageDescriptionService.StorageDescription startState = postServiceSynchronously(
                    StorageDescriptionService.FACTORY_LINK,
                            storageState, StorageDescriptionService.StorageDescription.class);

            String kind = Utils.buildKind(StorageDescriptionService.StorageDescription.class);
            String propertyName = QueryTask.QuerySpecification
                    .buildCollectionItemName(ServiceDocumentDescription.FIELD_NAME_TENANT_LINKS);

            QueryTask q = createDirectQueryTask(kind, propertyName,
                    storageState.tenantLinks.get(0));
            q = querySynchronously(q);
            assertNotNull(q.results.documentLinks);
            assertThat(q.results.documentCount, is(1L));
            assertThat(q.results.documentLinks.get(0),
                    is(startState.documentSelfLink));
        }
    }
}
