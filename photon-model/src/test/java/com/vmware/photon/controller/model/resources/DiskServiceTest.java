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
 * This class implements tests for the {@link DiskService} class.
 */
@RunWith(DiskServiceTest.class)
@SuiteClasses({ DiskServiceTest.ConstructorTest.class,
        DiskServiceTest.HandleStartTest.class,
        DiskServiceTest.HandlePatchTest.class, DiskServiceTest.QueryTest.class })
public class DiskServiceTest extends Suite {

    public DiskServiceTest(Class<?> klass, RunnerBuilder builder)
            throws InitializationError {
        super(klass, builder);
    }

    private static DiskService.DiskState buildValidStartState()
            throws Throwable {
        DiskService.DiskState disk = new DiskService.DiskState();

        disk.id = UUID.randomUUID().toString();
        disk.type = DiskService.DiskType.HDD;
        disk.name = "friendly-name";
        disk.capacityMBytes = 100L;

        return disk;
    }

    /**
     * This class implements tests for the constructor.
     */
    public static class ConstructorTest {

        private DiskService diskService;

        @Before
        public void setUpTest() {
            this.diskService = new DiskService();
        }

        @Test
        public void testServiceOptions() {

            EnumSet<Service.ServiceOption> expected = EnumSet.of(
                    Service.ServiceOption.CONCURRENT_GET_HANDLING,
                    Service.ServiceOption.OWNER_SELECTION,
                    Service.ServiceOption.PERSISTENCE,
                    Service.ServiceOption.REPLICATION);

            assertThat(this.diskService.getOptions(), is(expected));
        }
    }

    /**
     * This class implements tests for the handleStart method.
     */
    public static class HandleStartTest extends BaseModelTest {

        @Test
        public void testValidStartState() throws Throwable {
            DiskService.DiskState startState = buildValidStartState();
            DiskService.DiskState returnState = postServiceSynchronously(
                    DiskService.FACTORY_LINK, startState,
                    DiskService.DiskState.class);

            assertNotNull(returnState);
            assertThat(returnState.id, is(startState.id));
            assertThat(returnState.name, is(startState.name));
            assertThat(returnState.type, is(startState.type));
            assertThat(returnState.capacityMBytes,
                    is(startState.capacityMBytes));
        }

        @Test
        public void testDuplicatePost() throws Throwable {
            DiskService.DiskState startState = buildValidStartState();
            DiskService.DiskState returnState = postServiceSynchronously(
                    DiskService.FACTORY_LINK, startState,
                    DiskService.DiskState.class);

            assertNotNull(returnState);
            assertThat(returnState.name, is(startState.name));
            startState.name = "new-name";
            returnState = postServiceSynchronously(
                    DiskService.FACTORY_LINK, startState,
                    DiskService.DiskState.class);
            assertThat(returnState.name, is(startState.name));

        }

        @Test
        public void testMissingId() throws Throwable {
            DiskService.DiskState startState = buildValidStartState();
            startState.id = null;

            DiskService.DiskState returnState = postServiceSynchronously(
                    DiskService.FACTORY_LINK, startState,
                    DiskService.DiskState.class);

            assertNotNull(returnState);
            assertNotNull(returnState.id);
        }

        @Test
        public void testMissingName() throws Throwable {
            DiskService.DiskState startState = buildValidStartState();
            startState.name = null;

            postServiceSynchronously(DiskService.FACTORY_LINK,
                    startState, DiskService.DiskState.class,
                    IllegalArgumentException.class);
        }

        @Test
        public void testMissingType() throws Throwable {
            DiskService.DiskState startState = buildValidStartState();
            startState.type = null;

            postServiceSynchronously(DiskService.FACTORY_LINK,
                    startState, DiskService.DiskState.class,
                    IllegalArgumentException.class);
        }

        public void testCapacityLessThanOneMB(Long capacityMBytes)
                throws Throwable {
            DiskService.DiskState startState = buildValidStartState();
            startState.capacityMBytes = 0L;
            startState.sourceImageReference = new URI(
                    "http://sourceImageReference");
            startState.customizationServiceReference = new URI(
                    "http://customizationServiceReference");

            DiskService.DiskState returnState = postServiceSynchronously(
                    DiskService.FACTORY_LINK, startState,
                    DiskService.DiskState.class);

            assertNotNull(returnState);
        }

        @Test
        public void testMissingTwoReferencesWhenCapacityLessThanOneMB()
                throws Throwable {
            DiskService.DiskState startState = buildValidStartState();
            startState.capacityMBytes = 0;
            startState.sourceImageReference = null;
            startState.customizationServiceReference = null;

            postServiceSynchronously(DiskService.FACTORY_LINK,
                    startState, DiskService.DiskState.class,
                    IllegalArgumentException.class);
        }

        @Test
        public void testMissingStatus() throws Throwable {
            DiskService.DiskState startState = buildValidStartState();
            startState.status = null;

            DiskService.DiskState returnState = postServiceSynchronously(
                    DiskService.FACTORY_LINK, startState,
                    DiskService.DiskState.class);

            assertNotNull(returnState);
            assertThat(returnState.status, is(DiskService.DiskStatus.DETACHED));
        }

        public void testMissingPathInFileEntry(String path) throws Throwable {
            DiskService.DiskState startState = buildValidStartState();
            startState.bootConfig = new DiskService.DiskState.BootConfig();
            startState.bootConfig.files = new DiskService.DiskState.BootConfig.FileEntry[1];
            startState.bootConfig.files[0] = new DiskService.DiskState.BootConfig.FileEntry();
            startState.bootConfig.files[0].path = null;

            postServiceSynchronously(DiskService.FACTORY_LINK,
                    startState, DiskService.DiskState.class,
                    IllegalArgumentException.class);
            startState.bootConfig.files[0].path = "";

            postServiceSynchronously(DiskService.FACTORY_LINK,
                    startState, DiskService.DiskState.class,
                    IllegalArgumentException.class);

        }
    }

    /**
     * This class implements tests for the handlePatch method.
     */
    public static class HandlePatchTest extends BaseModelTest {

        @Test
        public void testPatchZoneId() throws Throwable {
            DiskService.DiskState startState = buildValidStartState();
            startState.zoneId = "startZoneId";

            DiskService.DiskState returnState = postServiceSynchronously(
                    DiskService.FACTORY_LINK, startState,
                    DiskService.DiskState.class);

            DiskService.DiskState patchState = new DiskService.DiskState();
            patchState.zoneId = null;

            patchServiceSynchronously(returnState.documentSelfLink,
                    patchState);
            returnState = getServiceSynchronously(
                    returnState.documentSelfLink, DiskService.DiskState.class);
            assertThat(returnState.zoneId, is("startZoneId"));
            patchState.zoneId = "startZoneId";

            patchServiceSynchronously(returnState.documentSelfLink,
                    patchState);
            returnState = getServiceSynchronously(
                    returnState.documentSelfLink, DiskService.DiskState.class);
            assertThat(returnState.zoneId, is("startZoneId"));
            patchState.zoneId = "patchZoneId";

            patchServiceSynchronously(returnState.documentSelfLink,
                    patchState);
            returnState = getServiceSynchronously(
                    returnState.documentSelfLink, DiskService.DiskState.class);
            assertThat(returnState.zoneId, is("patchZoneId"));

        }

        @Test
        public void testPatchName() throws Throwable {
            DiskService.DiskState startState = buildValidStartState();
            startState.name = "startName";

            DiskService.DiskState returnState = postServiceSynchronously(
                    DiskService.FACTORY_LINK, startState,
                    DiskService.DiskState.class);

            DiskService.DiskState patchState = new DiskService.DiskState();
            patchState.name = null;

            patchServiceSynchronously(returnState.documentSelfLink,
                    patchState);
            returnState = getServiceSynchronously(
                    returnState.documentSelfLink, DiskService.DiskState.class);
            assertThat(returnState.name, is("startName"));
            patchState.name = "startName";

            patchServiceSynchronously(returnState.documentSelfLink,
                    patchState);
            returnState = getServiceSynchronously(
                    returnState.documentSelfLink, DiskService.DiskState.class);
            assertThat(returnState.name, is("startName"));
            patchState.name = "patchName";

            patchServiceSynchronously(returnState.documentSelfLink,
                    patchState);
            returnState = getServiceSynchronously(
                    returnState.documentSelfLink, DiskService.DiskState.class);
            assertThat(returnState.name, is("patchName"));

        }

        @Test
        public void testPatchStatus() throws Throwable {
            DiskService.DiskState startState = buildValidStartState();
            startState.status = DiskService.DiskStatus.DETACHED;

            DiskService.DiskState returnState = postServiceSynchronously(
                    DiskService.FACTORY_LINK, startState,
                    DiskService.DiskState.class);

            DiskService.DiskState patchState = new DiskService.DiskState();
            patchState.status = null;

            patchServiceSynchronously(returnState.documentSelfLink,
                    patchState);
            returnState = getServiceSynchronously(
                    returnState.documentSelfLink, DiskService.DiskState.class);
            assertThat(returnState.status, is(DiskService.DiskStatus.DETACHED));
            patchState.status = DiskService.DiskStatus.DETACHED;

            patchServiceSynchronously(returnState.documentSelfLink,
                    patchState);
            returnState = getServiceSynchronously(
                    returnState.documentSelfLink, DiskService.DiskState.class);
            assertThat(returnState.status, is(DiskService.DiskStatus.DETACHED));
            patchState.status = DiskService.DiskStatus.ATTACHED;

            patchServiceSynchronously(returnState.documentSelfLink,
                    patchState);
            returnState = getServiceSynchronously(
                    returnState.documentSelfLink, DiskService.DiskState.class);
            assertThat(returnState.status, is(DiskService.DiskStatus.ATTACHED));
        }

        @Test
        public void testPatchCapacityMBytes() throws Throwable {
            DiskService.DiskState startState = buildValidStartState();
            startState.capacityMBytes = 100L;

            DiskService.DiskState returnState = postServiceSynchronously(
                    DiskService.FACTORY_LINK, startState,
                    DiskService.DiskState.class);

            DiskService.DiskState patchState = new DiskService.DiskState();
            patchState.capacityMBytes = 0;

            patchServiceSynchronously(returnState.documentSelfLink,
                    patchState);
            returnState = getServiceSynchronously(
                    returnState.documentSelfLink, DiskService.DiskState.class);
            assertThat(returnState.capacityMBytes, is(100L));
            patchState.capacityMBytes = 100L;

            patchServiceSynchronously(returnState.documentSelfLink,
                    patchState);
            returnState = getServiceSynchronously(
                    returnState.documentSelfLink, DiskService.DiskState.class);
            assertThat(returnState.capacityMBytes, is(100L));
            patchState.capacityMBytes = 200L;

            patchServiceSynchronously(returnState.documentSelfLink,
                    patchState);
            returnState = getServiceSynchronously(
                    returnState.documentSelfLink, DiskService.DiskState.class);
            assertThat(returnState.capacityMBytes, is(200L));
        }

        @Test
        public void testPatchOtherFields() throws Throwable {
            DiskService.DiskState startState = buildValidStartState();
            startState.dataCenterId = "data-center-id1";
            startState.resourcePoolLink = "resource-pool-link1";
            startState.authCredentialsLink = "auth-credentials-link1";
            startState.customProperties = new HashMap<>();
            startState.customProperties.put("cp1-key", "cp1-value");
            startState.tenantLinks = new ArrayList<>();
            startState.tenantLinks.add("tenant-link1");
            startState.bootOrder = 1;
            startState.bootArguments = new String[] { "boot-argument1" };
            startState.currencyUnit = "currency-unit1";

            DiskService.DiskState returnState = postServiceSynchronously(
                    DiskService.FACTORY_LINK, startState,
                    DiskService.DiskState.class);

            DiskService.DiskState patchState = new DiskService.DiskState();
            patchState.dataCenterId = "data-center-id2";
            patchState.resourcePoolLink = "resource-pool-link2";
            patchState.authCredentialsLink = "auth-credentials-link2";
            patchState.customProperties = new HashMap<>();
            patchState.customProperties.put("cp2-key", "cp2-value");
            patchState.tenantLinks = new ArrayList<>();
            patchState.tenantLinks.add("tenant-link2");
            patchState.bootOrder = 2;
            patchState.bootArguments = new String[] { "boot-argument2" };
            patchState.currencyUnit = "currency-unit2";

            patchServiceSynchronously(returnState.documentSelfLink,
                    patchState);
            returnState = getServiceSynchronously(
                    returnState.documentSelfLink, DiskService.DiskState.class);
            assertThat(returnState.dataCenterId, is(startState.dataCenterId));
            assertThat(returnState.resourcePoolLink,
                    is(startState.resourcePoolLink));
            assertThat(returnState.authCredentialsLink,
                    is(startState.authCredentialsLink));
            assertThat(returnState.customProperties,
                    is(startState.customProperties));
            assertThat(returnState.tenantLinks, is(startState.tenantLinks));
            assertThat(returnState.bootOrder, is(startState.bootOrder));
            assertThat(returnState.bootArguments, is(startState.bootArguments));
            assertThat(returnState.currencyUnit, is(startState.currencyUnit));

        }
    }

    /**
     * This class implements tests for query.
     */
    public static class QueryTest extends BaseModelTest {
        @Test
        public void testTenantLinksQuery() throws Throwable {
            DiskService.DiskState disk = buildValidStartState();

            URI tenantUri = UriUtils.buildFactoryUri(host, TenantService.class);
            disk.tenantLinks = new ArrayList<>();
            disk.tenantLinks.add(UriUtils.buildUriPath(tenantUri.getPath(),
                    "tenantA"));

            DiskService.DiskState startState = postServiceSynchronously(
                    DiskService.FACTORY_LINK, disk,
                    DiskService.DiskState.class);

            String kind = Utils.buildKind(DiskService.DiskState.class);
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
    }
}
