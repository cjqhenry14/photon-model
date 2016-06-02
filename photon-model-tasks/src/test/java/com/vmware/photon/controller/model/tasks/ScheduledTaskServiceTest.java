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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertEquals;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import org.junit.runners.Suite.SuiteClasses;
import org.junit.runners.model.InitializationError;
import org.junit.runners.model.RunnerBuilder;

import com.vmware.photon.controller.model.helpers.BaseModelTest;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService;
import com.vmware.photon.controller.model.resources.ComputeService;
import com.vmware.photon.controller.model.resources.ResourcePoolService;
import com.vmware.photon.controller.model.resources.ResourcePoolService.ResourcePoolState;
import com.vmware.photon.controller.model.tasks.ResourceEnumerationTaskService;
import com.vmware.photon.controller.model.tasks.ResourceEnumerationTaskService.ResourceEnumerationTaskState;
import com.vmware.photon.controller.model.tasks.ScheduledTaskService;
import com.vmware.photon.controller.model.tasks.ScheduledTaskService.ScheduledTaskState;
import com.vmware.photon.controller.model.tasks.TestUtils;

import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;

@RunWith(SnapshotTaskServiceTest.class)
@SuiteClasses({ ScheduledTaskServiceTest.ConstructorTest.class,
        ScheduledTaskServiceTest.HandleStartTest.class,
        ScheduledTaskServiceTest.EndToEndTest.class })
public class ScheduledTaskServiceTest extends Suite {
    public ScheduledTaskServiceTest(Class<?> klass, RunnerBuilder builder)
            throws InitializationError {
        super(klass, builder);
    }

    private static ScheduledTaskService.ScheduledTaskState buildValidStartState() throws Throwable {
        ScheduledTaskService.ScheduledTaskState state = new ScheduledTaskService.ScheduledTaskState();
        state.factoryLink = "factoryLink";
        state.initialStateJson = "some string";
        state.customProperties = new HashMap<String, String>();
        state.customProperties.put("testKey", "testValue");
        state.intervalMicros = 100L;
        return state;
    }
    /**
     * This class implements tests for the constructor.
     */
    public static class ConstructorTest {

        private ScheduledTaskService scheduledTaskService;

        @Before
        public void setUpTest() {
            this.scheduledTaskService = new ScheduledTaskService();
        }

        @Test
        public void testServiceOptions() {

            EnumSet<Service.ServiceOption> expected = EnumSet.of(
                    Service.ServiceOption.CONCURRENT_GET_HANDLING,
                    Service.ServiceOption.INSTRUMENTATION,
                    Service.ServiceOption.OWNER_SELECTION,
                    Service.ServiceOption.PERIODIC_MAINTENANCE,
                    Service.ServiceOption.PERSISTENCE,
                    Service.ServiceOption.REPLICATION);

            assertThat(this.scheduledTaskService.getOptions(), is(expected));
        }
    }

    /**
     * This class implements tests for the handleStart method.
     */
    public static class HandleStartTest extends BaseModelTest {
        @Override
        protected void startRequiredServices() throws Throwable {
            TaskServices.startFactories(this);
            super.startRequiredServices();
        }

        @Test
        public void testMissingFactoryLink() throws Throwable {
            ScheduledTaskService.ScheduledTaskState taskState = buildValidStartState();
            taskState.factoryLink = null;
            this.postServiceSynchronously(
                    ScheduledTaskService.FACTORY_LINK, taskState,
                    ScheduledTaskService.ScheduledTaskState.class,
                    IllegalArgumentException.class);
        }

        @Test
        public void testMissingServicePayload() throws Throwable {
            ScheduledTaskService.ScheduledTaskState taskState = buildValidStartState();
            taskState.initialStateJson = null;
            this.postServiceSynchronously(
                    ScheduledTaskService.FACTORY_LINK, taskState,
                    ScheduledTaskService.ScheduledTaskState.class,
                    IllegalArgumentException.class);
        }

        @Test
        public void testDefaultState() throws Throwable {
            ScheduledTaskService.ScheduledTaskState taskState = buildValidStartState();
            ResourcePoolState inPool = new ResourcePoolState();
            inPool.name = UUID.randomUUID().toString();
            inPool.id = inPool.name;
            inPool.minCpuCount = 1;
            inPool.minMemoryBytes = 1024;
            taskState.factoryLink = ResourcePoolService.FACTORY_LINK;
            taskState.initialStateJson = Utils.toJson(inPool);

            ScheduledTaskService.ScheduledTaskState returnState = this.postServiceSynchronously(
                    ScheduledTaskService.FACTORY_LINK, taskState,
                    ScheduledTaskService.ScheduledTaskState.class);
            assertEquals(taskState.customProperties, returnState.customProperties);
            assertEquals(taskState.initialStateJson, returnState.initialStateJson);
            assertEquals(taskState.factoryLink, returnState.factoryLink);
            assertEquals(taskState.intervalMicros, returnState.intervalMicros);
        }
    }

    public static class EndToEndTest extends BaseModelTest {
        @Override
        protected void startRequiredServices() throws Throwable {
            TaskServices.startFactories(this);
            super.startRequiredServices();
        }

        @Test
        public void testEnumerationInvocation() throws Throwable {
            // create a resource pool, compute desc and compute instance
            ResourcePoolState inPool = new ResourcePoolState();
            inPool.name = UUID.randomUUID().toString();
            inPool.id = inPool.name;
            inPool.minCpuCount = 1;
            inPool.minMemoryBytes = 1024;

            ResourcePoolState returnPool = TestUtils.doPost(host, inPool, ResourcePoolState.class,
                    UriUtils.buildUri(host, ResourcePoolService.FACTORY_LINK));

            ComputeDescriptionService.ComputeDescription computeDescription = new ComputeDescriptionService.ComputeDescription();
            computeDescription.id = UUID.randomUUID().toString();
            computeDescription.documentSelfLink = computeDescription.id;
            computeDescription.name = "test-desc";
            computeDescription.enumerationAdapterReference = UriUtils.buildUri("http://foo.com");
            ComputeDescriptionService.ComputeDescription outDesc = TestUtils.doPost(host,
                    computeDescription,
                    ComputeDescriptionService.ComputeDescription.class,
                    UriUtils.buildUri(host, ComputeDescriptionService.FACTORY_LINK));

            ComputeService.ComputeState computeHost = new ComputeService.ComputeState();
            computeHost.id = UUID.randomUUID().toString();
            computeHost.descriptionLink = outDesc.documentSelfLink;
            computeHost.resourcePoolLink = returnPool.documentSelfLink;
            ComputeService.ComputeState returnComputeState = TestUtils.doPost(host, computeHost,
                    ComputeService.ComputeState.class,
                    UriUtils.buildUri(host, ComputeService.FACTORY_LINK));

            ResourceEnumerationTaskState enumTaskState = new ResourceEnumerationTaskState();
            enumTaskState.computeDescriptionLink = outDesc.documentSelfLink;
            enumTaskState.parentComputeLink = returnComputeState.documentSelfLink;
            enumTaskState.resourcePoolLink = returnPool.documentSelfLink;
            enumTaskState.adapterManagementReference = UriUtils.buildUri("http://foo.com");
            enumTaskState.documentSelfLink = UUID.randomUUID().toString();

            // create a scheduled task to run once every 10 minutes; verify that
            // it did run once on service start
            ScheduledTaskState scheduledTaskState = new ScheduledTaskState();
            scheduledTaskState.factoryLink = ResourceEnumerationTaskService.FACTORY_LINK;
            scheduledTaskState.initialStateJson = Utils.toJson(enumTaskState);
            scheduledTaskState.intervalMicros = TimeUnit.MINUTES.toMicros(10);
            scheduledTaskState.documentSelfLink = UUID.randomUUID().toString();
            TestUtils.doPost(host, scheduledTaskState,
                    ScheduledTaskState.class,
                    UriUtils.buildUri(host, ScheduledTaskService.FACTORY_LINK));
            this.host.waitFor(
                    "Timeout waiting for enum task execution",
                    () -> {
                        ResourceEnumerationTaskState outputState = this.getServiceSynchronously(UriUtils
                                .buildUriPath(
                                        ResourceEnumerationTaskService.FACTORY_LINK,
                                        enumTaskState.documentSelfLink),
                                        ResourceEnumerationTaskState.class);
                        if (outputState != null) {
                            return true;
                        }
                        return false;
                    });

            // verify that the periodic maintenance handler is invoked for
            // scheduled task
            enumTaskState.documentSelfLink = UUID.randomUUID().toString();
            ScheduledTaskState periodicTaskState = new ScheduledTaskState();
            periodicTaskState.factoryLink = ResourceEnumerationTaskService.FACTORY_LINK;
            periodicTaskState.initialStateJson = Utils.toJson(enumTaskState);
            periodicTaskState.intervalMicros = TimeUnit.MILLISECONDS.toMicros(250);
            periodicTaskState.documentSelfLink = UUID.randomUUID().toString();
            TestUtils.doPost(host, periodicTaskState,
                    ScheduledTaskState.class,
                    UriUtils.buildUri(host, ScheduledTaskService.FACTORY_LINK));
            int expectedInvocations = 5;
            this.host.waitFor(
                    "Timeout waiting for enum task execution",
                    () -> {
                        ScheduledTaskState outputState = this.getServiceSynchronously(UriUtils
                                .buildUriPath(
                                        ScheduledTaskService.FACTORY_LINK,
                                        periodicTaskState.documentSelfLink),
                                ScheduledTaskState.class);
                        if (outputState.documentVersion == expectedInvocations) {
                            return true;
                        }
                        return false;
                    });
        }
    }
}
