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

import java.net.URI;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertNotNull;

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
 * This class implements tests for the {@link ResourceDescriptionService} class.
 */
@RunWith(SnapshotServiceTest.class)
@SuiteClasses({ SnapshotServiceTest.ConstructorTest.class,
        SnapshotServiceTest.HandleStartTest.class,
        SnapshotServiceTest.HandlePatchTest.class,
        SnapshotServiceTest.QueryTest.class })
public class SnapshotServiceTest extends Suite {

    public SnapshotServiceTest(Class<?> klass, RunnerBuilder builder)
            throws InitializationError {
        super(klass, builder);
    }

    private static SnapshotService.SnapshotState buildValidStartState()
            throws Throwable {
        SnapshotService.SnapshotState st = new SnapshotService.SnapshotState();
        st.id = UUID.randomUUID().toString();
        st.name = "friendly-name";
        st.computeLink = "compute-link";
        st.description = "description";
        st.customProperties = new HashMap<>();
        st.customProperties.put("defaultKey", "defaultVal");

        return st;
    }

    /**
     * This class implements tests for the constructor.
     */
    public static class ConstructorTest {

        private SnapshotService snapshotService;

        @Before
        public void setUpTest() {
            this.snapshotService = new SnapshotService();
        }

        @Test
        public void testServiceOptions() {

            EnumSet<Service.ServiceOption> expected = EnumSet.of(
                    Service.ServiceOption.CONCURRENT_GET_HANDLING,
                    Service.ServiceOption.PERSISTENCE,
                    Service.ServiceOption.REPLICATION,
                    Service.ServiceOption.OWNER_SELECTION);

            assertThat(this.snapshotService.getOptions(), is(expected));
        }
    }

    /**
     * This class implements tests for the handleStart method.
     */
    public static class HandleStartTest extends BaseModelTest {
        @Test
        public void testValidStartState() throws Throwable {
            SnapshotService.SnapshotState startState = buildValidStartState();
            assertNotNull(host);

            SnapshotService.SnapshotState returnState = postServiceSynchronously(
                    SnapshotService.FACTORY_LINK,
                    startState, SnapshotService.SnapshotState.class);

            assertNotNull(returnState);
            assertThat(returnState.id, is(startState.id));
            assertThat(returnState.name, is(startState.name));
            assertThat(returnState.computeLink, is(startState.computeLink));
        }

        @Test
        public void testDuplicatePost() throws Throwable {
            SnapshotService.SnapshotState startState = buildValidStartState();
            assertNotNull(host);

            SnapshotService.SnapshotState returnState = postServiceSynchronously(
                    SnapshotService.FACTORY_LINK,
                    startState, SnapshotService.SnapshotState.class);

            assertNotNull(returnState);
            assertThat(returnState.name, is(startState.name));
            startState.name = "new name";
            returnState = postServiceSynchronously(SnapshotService.FACTORY_LINK,
                    startState, SnapshotService.SnapshotState.class);
            assertThat(returnState.name, is(startState.name));
        }

        @Test
        public void testMissingId() throws Throwable {

            SnapshotService.SnapshotState startState = buildValidStartState();
            startState.id = null;

            SnapshotService.SnapshotState returnState = postServiceSynchronously(
                    SnapshotService.FACTORY_LINK,
                    startState, SnapshotService.SnapshotState.class);

            assertNotNull(returnState);
            assertNotNull(returnState.id);
        }

        @Test
        public void testMissingName() throws Throwable {
            SnapshotService.SnapshotState startState = buildValidStartState();
            startState.name = null;

            postServiceSynchronously(SnapshotService.FACTORY_LINK,
                    startState, SnapshotService.SnapshotState.class,
                    IllegalArgumentException.class);
        }

        @Test
        public void testMissingComputeLink() throws Throwable {
            SnapshotService.SnapshotState startState = buildValidStartState();
            startState.computeLink = null;

            postServiceSynchronously(SnapshotService.FACTORY_LINK,
                    startState, SnapshotService.SnapshotState.class,
                    IllegalArgumentException.class);
        }
    }

    /**
     * This class implements tests for the handlePatch method.
     */
    public static class HandlePatchTest extends BaseModelTest {
        @Test
        public void testPatchSnapshotName() throws Throwable {
            SnapshotService.SnapshotState startState = createSnapshotService();

            SnapshotService.SnapshotState patchState = new SnapshotService.SnapshotState();
            patchState.name = UUID.randomUUID().toString();
            patchServiceSynchronously(startState.documentSelfLink,
                    patchState);

            SnapshotService.SnapshotState newState = getServiceSynchronously(
                    startState.documentSelfLink,
                    SnapshotService.SnapshotState.class);
            assertThat(newState.name, is(patchState.name));
        }

        @Test
        public void testPatchSnapshotDescription() throws Throwable {
            SnapshotService.SnapshotState startState = createSnapshotService();

            SnapshotService.SnapshotState patchState = new SnapshotService.SnapshotState();
            patchState.description = "test-description";
            patchServiceSynchronously(startState.documentSelfLink,
                    patchState);

            SnapshotService.SnapshotState newState = getServiceSynchronously(
                    startState.documentSelfLink,
                    SnapshotService.SnapshotState.class);

            assertThat(newState.description, is(patchState.description));
        }

        @Test
        public void testPatchSnapshotComputeLink() throws Throwable {
            SnapshotService.SnapshotState startState = createSnapshotService();

            SnapshotService.SnapshotState patchState = new SnapshotService.SnapshotState();
            patchState.computeLink = "test-compute-link";
            patchServiceSynchronously(startState.documentSelfLink,
                    patchState);

            SnapshotService.SnapshotState newState = getServiceSynchronously(
                    startState.documentSelfLink,
                    SnapshotService.SnapshotState.class);

            assertThat(newState.computeLink, is(patchState.computeLink));
        }

        @Test
        public void testPatchSnapshotCustomProperties() throws Throwable {
            SnapshotService.SnapshotState startState = createSnapshotService();

            SnapshotService.SnapshotState patchState = new SnapshotService.SnapshotState();
            patchState.customProperties = new HashMap<>();
            patchState.customProperties.put("key1", "val1");
            patchState.customProperties.put("key2", "val2");
            patchState.customProperties.put("key3", "val3");

            patchServiceSynchronously(startState.documentSelfLink,
                    patchState);

            SnapshotService.SnapshotState newState = getServiceSynchronously(
                    startState.documentSelfLink,
                    SnapshotService.SnapshotState.class);

            for (Map.Entry<String, String> entry : patchState.customProperties
                    .entrySet()) {
                String key = entry.getKey();
                String value = entry.getValue();
                String newStateProperty = newState.customProperties.get(key);
                assertNotNull(newStateProperty);
                assert (newStateProperty.equals(value));
            }

        }

        private SnapshotService.SnapshotState createSnapshotService()
                throws Throwable {
            SnapshotService.SnapshotState startState = buildValidStartState();
            return postServiceSynchronously(
                    SnapshotService.FACTORY_LINK, startState,
                    SnapshotService.SnapshotState.class);
        }
    }

    /**
     * This class implements tests for query.
     */
    public static class QueryTest extends BaseModelTest {
        @Test
        public void testTenantLinksQuery() throws Throwable {
            SnapshotService.SnapshotState st = buildValidStartState();

            URI tenantUri = UriUtils.buildFactoryUri(host, TenantService.class);
            st.tenantLinks = new ArrayList<>();
            st.tenantLinks.add(UriUtils.buildUriPath(tenantUri.getPath(),
                    "tenantA"));

            SnapshotService.SnapshotState startState = postServiceSynchronously(
                    SnapshotService.FACTORY_LINK,
                    st, SnapshotService.SnapshotState.class);

            String kind = Utils.buildKind(SnapshotService.SnapshotState.class);
            String propertyName = QueryTask.QuerySpecification
                    .buildCollectionItemName(ServiceDocumentDescription.FIELD_NAME_TENANT_LINKS);

            QueryTask q = createDirectQueryTask(kind, propertyName,
                    st.tenantLinks.get(0));
            q = querySynchronously(q);
            assertNotNull(q.results.documentLinks);
            assertThat(q.results.documentCount, is(1L));
            assertThat(q.results.documentLinks.get(0),
                    is(startState.documentSelfLink));
        }
    }
}
