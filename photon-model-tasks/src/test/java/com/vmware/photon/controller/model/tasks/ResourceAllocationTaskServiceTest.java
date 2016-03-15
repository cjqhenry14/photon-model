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
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import org.junit.runners.Suite.SuiteClasses;
import org.junit.runners.model.InitializationError;
import org.junit.runners.model.RunnerBuilder;

import com.vmware.photon.controller.model.helpers.BaseModelTest;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription.ComputeType;
import com.vmware.photon.controller.model.resources.ComputeService;
import com.vmware.photon.controller.model.resources.DiskService;
import com.vmware.photon.controller.model.resources.NetworkInterfaceService;
import com.vmware.photon.controller.model.resources.ResourceDescriptionService;
import com.vmware.photon.controller.model.tasks.ResourceAllocationTaskService.ResourceAllocationTaskState;

import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.UriUtils;

/**
 * This class implements tests for the {@link ResourceAllocationTaskService}
 * class.
 */
@RunWith(ResourceAllocationTaskServiceTest.class)
@SuiteClasses({
        ResourceAllocationTaskServiceTest.ConstructorTest.class,
        ResourceAllocationTaskServiceTest.HandleStartTest.class,
        ResourceAllocationTaskServiceTest.QueryAvailableComputeResourcesTest.class,
        ResourceAllocationTaskServiceTest.ComputeResourceProvisioningTest.class })
public class ResourceAllocationTaskServiceTest extends Suite {

    private static final String ZONE_ID = "provider-specific-zone";

    public ResourceAllocationTaskServiceTest(Class<?> klass,
            RunnerBuilder builder) throws InitializationError {
        super(klass, builder);
    }

    private static ComputeService.ComputeState createParentCompute(
            BaseModelTest test, String resourcePool, String zoneId) throws Throwable {
        // Create parent ComputeDescription
        ComputeDescriptionService.ComputeDescription cd = new ComputeDescriptionService.ComputeDescription();
        cd.bootAdapterReference = new URI("http://bootAdapterReference");
        cd.powerAdapterReference = new URI("http://powerAdapterReference");
        cd.instanceAdapterReference = new URI("http://instanceAdapterReference");
        cd.healthAdapterReference = null;
        cd.enumerationAdapterReference = new URI(
                "http://enumerationAdapterReference");
        cd.supportedChildren = new ArrayList<>();
        cd.supportedChildren.add(ComputeType.VM_GUEST.toString());
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
        cd.zoneId = zoneId;
        ComputeDescriptionService.ComputeDescription cd1 = test
                .postServiceSynchronously(
                        ComputeDescriptionService.FACTORY_LINK, cd,
                        ComputeDescriptionService.ComputeDescription.class);

        // Create parent Compute
        ComputeService.ComputeState cs = new ComputeService.ComputeState();
        cs.id = UUID.randomUUID().toString();
        cs.descriptionLink = cd1.documentSelfLink;
        cs.resourcePoolLink = resourcePool;
        cs.adapterManagementReference = URI
                .create("https://esxhost-01:443/sdk");
        ComputeService.ComputeState cs1 = test.postServiceSynchronously(
                ComputeService.FACTORY_LINK, cs,
                ComputeService.ComputeState.class);

        return cs1;
    }

    private static ComputeService.ComputeState createParentCompute(
            BaseModelTest test, String resourcePool) throws Throwable {
        return createParentCompute(test, resourcePool, ZONE_ID);
    }

    private static ComputeDescriptionService.ComputeDescription createComputeDescription(
            BaseModelTest test, String instanceAdapterLink, String bootAdapterLink)
            throws Throwable {
        // Create ComputeDescription
        ComputeDescriptionService.ComputeDescription cd = new ComputeDescriptionService.ComputeDescription();
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
        // disable periodic maintenance for tests by default.
        cd.healthAdapterReference = null;
        if (instanceAdapterLink != null) {
            cd.instanceAdapterReference = UriUtils.buildUri(test.getHost(),
                    instanceAdapterLink);
            cd.powerAdapterReference = URI.create("http://powerAdapter");
        }
        if (bootAdapterLink != null) {
            cd.bootAdapterReference = UriUtils.buildUri(test.getHost(), bootAdapterLink);
        }
        return test.postServiceSynchronously(
                ComputeDescriptionService.FACTORY_LINK, cd,
                ComputeDescriptionService.ComputeDescription.class);
    }

    private static List<String> createDiskDescription(BaseModelTest test)
            throws Throwable {
        DiskService.DiskState d = new DiskService.DiskState();
        d.id = UUID.randomUUID().toString();
        d.type = DiskService.DiskType.HDD;
        d.name = "friendly-name";
        d.capacityMBytes = 100L;
        DiskService.DiskState d1 = test.postServiceSynchronously(
                DiskService.FACTORY_LINK, d, DiskService.DiskState.class);
        List<String> links = new ArrayList<>();
        links.add(d1.documentSelfLink);
        return links;
    }

    private static List<String> createNetworkDescription(BaseModelTest test)
            throws Throwable {
        NetworkInterfaceService.NetworkInterfaceState n = new NetworkInterfaceService.NetworkInterfaceState();
        n.id = UUID.randomUUID().toString();
        n.networkDescriptionLink = "http://network-description";
        NetworkInterfaceService.NetworkInterfaceState n1 = test
                .postServiceSynchronously(
                        NetworkInterfaceService.FACTORY_LINK, n,
                        NetworkInterfaceService.NetworkInterfaceState.class);
        List<String> links = new ArrayList<>();
        links.add(n1.documentSelfLink);

        return links;
    }

    private static ResourceDescriptionService.ResourceDescription createResourceDescription(
            BaseModelTest test, ComputeDescriptionService.ComputeDescription cd,
            List<String> diskDescriptionLinks,
            List<String> networkDescriptionLinks) throws Throwable {
        ResourceDescriptionService.ResourceDescription rd = new ResourceDescriptionService.ResourceDescription();
        rd.computeType = ComputeType.VM_GUEST.toString();
        rd.computeDescriptionLink = cd.documentSelfLink;
        rd.diskDescriptionLinks = diskDescriptionLinks;
        rd.networkDescriptionLinks = networkDescriptionLinks;

        return test.postServiceSynchronously(
                ResourceDescriptionService.FACTORY_LINK, rd,
                ResourceDescriptionService.ResourceDescription.class);
    }

    private static ResourceAllocationTaskState createAllocationRequestWithResourceDescription(
            String resourcePool,
            ComputeDescriptionService.ComputeDescription cd,
            ResourceDescriptionService.ResourceDescription rd) {
        ResourceAllocationTaskState state = new ResourceAllocationTaskState();
        state.taskSubStage = ResourceAllocationTaskService.SubStage.QUERYING_AVAILABLE_COMPUTE_RESOURCES;
        state.resourceCount = 2;
        state.resourcePoolLink = resourcePool;
        state.resourceDescriptionLink = rd.documentSelfLink;

        return state;
    }

    private static ResourceAllocationTaskState createAllocationRequest(
            String resourcePool, String computeDescriptionLink,
            List<String> diskDescriptionLinks,
            List<String> networkDescriptionLinks) {
        ResourceAllocationTaskState state = new ResourceAllocationTaskState();
        state.taskSubStage = ResourceAllocationTaskService.SubStage.QUERYING_AVAILABLE_COMPUTE_RESOURCES;
        state.resourceCount = 2;
        state.resourcePoolLink = resourcePool;
        state.computeDescriptionLink = computeDescriptionLink;
        state.computeType = ComputeType.VM_GUEST.toString();
        state.customProperties = new HashMap<>();
        state.customProperties.put("testProp", "testValue");

        // For most tests, we override resourceDescription.
        state.resourceDescriptionLink = null;

        state.diskDescriptionLinks = diskDescriptionLinks;
        state.networkDescriptionLinks = networkDescriptionLinks;

        return state;
    }

    private static void startFactoryServices(BaseModelTest test) throws Throwable {
        TaskServices.startFactories(test);
        MockAdapter.startFactories(test);
    }

    /**
     * This class implements tests for the constructor.
     */
    public static class ConstructorTest {

        private ResourceAllocationTaskService provisionComputeTaskService;

        @Before
        public void setUpTest() {
            this.provisionComputeTaskService = new ResourceAllocationTaskService();
        }

        @Test
        public void testServiceOptions() {

            EnumSet<Service.ServiceOption> expected = EnumSet.of(
                    Service.ServiceOption.CONCURRENT_GET_HANDLING,
                    Service.ServiceOption.PERSISTENCE,
                    Service.ServiceOption.REPLICATION,
                    Service.ServiceOption.OWNER_SELECTION,
                    Service.ServiceOption.INSTRUMENTATION);

            assertThat(this.provisionComputeTaskService.getOptions(),
                    is(expected));
        }
    }

    /**
     * This class implements tests for the handleStart method.
     */
    public static class HandleStartTest extends BaseModelTest {

        @Override
        protected void startRequiredServices() throws Throwable {
            ResourceAllocationTaskServiceTest.startFactoryServices(this);
            super.startRequiredServices();
        }

        @Test
        public void testSuccess() throws Throwable {
            String resourcePool = UUID.randomUUID().toString();

            createParentCompute(this, resourcePool);

            ComputeDescriptionService.ComputeDescription cd = createComputeDescription(
                    this, MockAdapter.MockSuccessInstanceAdapter.SELF_LINK,
                    MockAdapter.MockSuccessBootAdapter.SELF_LINK);

            ResourceAllocationTaskState startState = createAllocationRequest(
                    resourcePool, cd.documentSelfLink,
                    createDiskDescription(this), createNetworkDescription(this));

            ResourceAllocationTaskState returnState = this
                    .postServiceSynchronously(
                            ResourceAllocationTaskFactoryService.SELF_LINK,
                            startState, ResourceAllocationTaskState.class);

            ResourceAllocationTaskState completeState = this
                    .waitForServiceState(
                            ResourceAllocationTaskState.class,
                            returnState.documentSelfLink,
                            state -> TaskState.TaskStage.FINISHED.ordinal() <= state.taskInfo.stage
                                    .ordinal());

            assertThat(completeState.taskInfo.stage,
                    is(TaskState.TaskStage.FINISHED));
        }

        /**
         * Call task with a template resource description
         * (ResourceAllocationTaskState.resourceDescriptionLink).
         */
        @Test
        public void testSuccessWithResourceDescription() throws Throwable {
            String resourcePool = UUID.randomUUID().toString();

            createParentCompute(this, resourcePool);

            ComputeDescriptionService.ComputeDescription cd = createComputeDescription(
                    this, MockAdapter.MockSuccessInstanceAdapter.SELF_LINK,
                    MockAdapter.MockSuccessBootAdapter.SELF_LINK);

            ResourceDescriptionService.ResourceDescription rd = createResourceDescription(
                    this, cd, createDiskDescription(this),
                    createNetworkDescription(this));

            ResourceAllocationTaskState startState = createAllocationRequestWithResourceDescription(
                    resourcePool, cd, rd);

            ResourceAllocationTaskState returnState = this
                    .postServiceSynchronously(
                            ResourceAllocationTaskFactoryService.SELF_LINK,
                            startState, ResourceAllocationTaskState.class);

            ResourceAllocationTaskState completeState = this
                    .waitForServiceState(
                            ResourceAllocationTaskState.class,
                            returnState.documentSelfLink,
                            state -> TaskState.TaskStage.FINISHED.ordinal() <= state.taskInfo.stage
                                    .ordinal());

            assertThat(completeState.taskInfo.stage,
                    is(TaskState.TaskStage.FINISHED));
        }

        @Test
        public void testMissingComputeDescription() throws Throwable {
            String resourcePool = UUID.randomUUID().toString();
            ResourceAllocationTaskState startState = createAllocationRequest(
                    resourcePool, null, null, null);

            this.postServiceSynchronously(
                    ResourceAllocationTaskFactoryService.SELF_LINK, startState,
                    ResourceAllocationTaskState.class,
                    IllegalArgumentException.class);
        }

        @Test
        public void testInvalidResourceCount() throws Throwable {
            String resourcePool = UUID.randomUUID().toString();
            ResourceAllocationTaskState startState = createAllocationRequest(
                    resourcePool, "http://computeDescription", null, null);
            startState.resourceCount = -1;

            this.postServiceSynchronously(
                    ResourceAllocationTaskFactoryService.SELF_LINK, startState,
                    ResourceAllocationTaskState.class,
                    IllegalArgumentException.class);
        }

        @Test
        public void testInvalidErrorThreshold() throws Throwable {
            String resourcePool = UUID.randomUUID().toString();
            ResourceAllocationTaskState startState = createAllocationRequest(
                    resourcePool, "http://computeDescription", null, null);
            startState.errorThreshold = -1;

            this.postServiceSynchronously(
                    ResourceAllocationTaskFactoryService.SELF_LINK, startState,
                    ResourceAllocationTaskState.class,
                    IllegalArgumentException.class);
        }
    }

    /**
     * This class implements tests for QUERYING_AVAILABLE_COMPUTE_RESOURCES
     * substage.
     */
    public static class QueryAvailableComputeResourcesTest extends
            BaseModelTest {
        @Override
        protected void startRequiredServices() throws Throwable {
            ResourceAllocationTaskServiceTest.startFactoryServices(this);
            super.startRequiredServices();
        }

        /**
         * Do not create parent compute, and resource allocation request will
         * fail.
         */
        @Test
        public void testNoResourcesFound() throws Throwable {
            String resourcePool = UUID.randomUUID().toString();

            ComputeDescriptionService.ComputeDescription cd = createComputeDescription(
                    this, MockAdapter.MockSuccessInstanceAdapter.SELF_LINK,
                    MockAdapter.MockSuccessBootAdapter.SELF_LINK);

            ResourceAllocationTaskState startState = createAllocationRequest(
                    resourcePool, cd.documentSelfLink, null, null);

            try {
                // Lower timeout
                host.setOperationTimeOutMicros(TimeUnit.SECONDS.toMicros(2));

                ResourceAllocationTaskState returnState = this
                        .postServiceSynchronously(
                                ResourceAllocationTaskFactoryService.SELF_LINK,
                                startState, ResourceAllocationTaskState.class);

                ResourceAllocationTaskState completeState = this
                        .waitForServiceState(
                                ResourceAllocationTaskState.class,
                                returnState.documentSelfLink,
                                state -> TaskState.TaskStage.FINISHED.ordinal() <= state.taskInfo.stage
                                        .ordinal());

                assertThat(completeState.taskInfo.stage,
                        is(TaskState.TaskStage.FAILED));
                assertThat(completeState.taskInfo.failure.message,
                        is("No compute resources available with poolId:"
                                + resourcePool));
            } finally {
                host.setOperationTimeOutMicros(ServiceHost.ServiceHostState.DEFAULT_OPERATION_TIMEOUT_MICROS);
            }
        }

        /**
         * Create parent compute shortly after the ResourceAllocationTask is
         * called, the task should be able to retry and complete successfully.
         */
        @Test
        public void testParentResourceBecomeAvailable() throws Throwable {
            String resourcePool = UUID.randomUUID().toString();

            ComputeDescriptionService.ComputeDescription cd = createComputeDescription(
                    this, MockAdapter.MockSuccessInstanceAdapter.SELF_LINK,
                    MockAdapter.MockSuccessBootAdapter.SELF_LINK);

            ResourceAllocationTaskState startState = createAllocationRequest(
                    resourcePool, cd.documentSelfLink, null, null);
            ResourceAllocationTaskState returnState = this
                    .postServiceSynchronously(
                            ResourceAllocationTaskFactoryService.SELF_LINK,
                            startState, ResourceAllocationTaskState.class);

            Thread.sleep(1500);
            createParentCompute(this, resourcePool);

            ResourceAllocationTaskState completeState = this
                    .waitForServiceState(
                            ResourceAllocationTaskState.class,
                            returnState.documentSelfLink,
                            state -> TaskState.TaskStage.FINISHED.ordinal() <= state.taskInfo.stage
                                    .ordinal());

            assertThat(completeState.taskInfo.stage,
                    is(TaskState.TaskStage.FINISHED));
        }

        @Test
        public void testWrongResourcePool() throws Throwable {
            String resourcePool1 = UUID.randomUUID().toString();
            String resourcePool2 = UUID.randomUUID().toString();

            createParentCompute(this, resourcePool1, ZONE_ID);

            ComputeDescriptionService.ComputeDescription cd = createComputeDescription(
                    this, MockAdapter.MockSuccessInstanceAdapter.SELF_LINK,
                    MockAdapter.MockSuccessBootAdapter.SELF_LINK);

            ResourceAllocationTaskState startState = createAllocationRequest(
                    resourcePool2, cd.documentSelfLink, null, null);

            try {
                // Lower timeout to 5 seconds
                host.setOperationTimeOutMicros(TimeUnit.SECONDS.toMicros(2));

                ResourceAllocationTaskState returnState = this
                        .postServiceSynchronously(
                                ResourceAllocationTaskFactoryService.SELF_LINK,
                                startState, ResourceAllocationTaskState.class);

                ResourceAllocationTaskState completeState = this
                        .waitForServiceState(
                                ResourceAllocationTaskState.class,
                                returnState.documentSelfLink,
                                state -> TaskState.TaskStage.FINISHED.ordinal() <= state.taskInfo.stage
                                        .ordinal());

                assertThat(completeState.taskInfo.stage,
                        is(TaskState.TaskStage.FAILED));
                assertThat(completeState.taskInfo.failure.message,
                        is("No compute resources available with poolId:"
                                + resourcePool2));
            } finally {
                host.setOperationTimeOutMicros(ServiceHost.ServiceHostState.DEFAULT_OPERATION_TIMEOUT_MICROS);
            }
        }

        @Test
        public void testWrongZone() throws Throwable {
            String resourcePool = UUID.randomUUID().toString();

            createParentCompute(this, resourcePool, "other-zone");

            ComputeDescriptionService.ComputeDescription cd = createComputeDescription(
                    this, MockAdapter.MockSuccessInstanceAdapter.SELF_LINK,
                    MockAdapter.MockSuccessBootAdapter.SELF_LINK);

            ResourceAllocationTaskState startState = createAllocationRequest(
                    resourcePool, cd.documentSelfLink, null, null);

            try {
                // Lower timeout to 5 seconds
                host.setOperationTimeOutMicros(TimeUnit.SECONDS.toMicros(2));

                ResourceAllocationTaskState returnState = this
                        .postServiceSynchronously(
                                ResourceAllocationTaskFactoryService.SELF_LINK,
                                startState, ResourceAllocationTaskState.class);

                ResourceAllocationTaskState completeState = this
                        .waitForServiceState(
                                ResourceAllocationTaskState.class,
                                returnState.documentSelfLink,
                                state -> TaskState.TaskStage.FINISHED.ordinal() <= state.taskInfo.stage
                                        .ordinal());

                assertThat(completeState.taskInfo.stage,
                        is(TaskState.TaskStage.FAILED));
                assertThat(completeState.taskInfo.failure.message,
                        is("No compute resources available with poolId:"
                                + resourcePool));
            } finally {
                host.setOperationTimeOutMicros(ServiceHost.ServiceHostState.DEFAULT_OPERATION_TIMEOUT_MICROS);
            }
        }
    }

    /**
     * This class implements tests for PROVISIONING_VM_GUESTS /
     * PROVISIONING_CONTAINERS substage.
     */
    public static class ComputeResourceProvisioningTest extends BaseModelTest {
        @Override
        protected void startRequiredServices() throws Throwable {
            ResourceAllocationTaskServiceTest.startFactoryServices(this);
            super.startRequiredServices();
        }

        @Test
        public void testNoDisk() throws Throwable {
            String resourcePool = UUID.randomUUID().toString();

            createParentCompute(this, resourcePool);

            ComputeDescriptionService.ComputeDescription cd = createComputeDescription(
                    this, MockAdapter.MockSuccessInstanceAdapter.SELF_LINK,
                    MockAdapter.MockSuccessBootAdapter.SELF_LINK);

            ResourceAllocationTaskState startState = createAllocationRequest(
                    resourcePool, cd.documentSelfLink, null,
                    createNetworkDescription(this));

            ResourceAllocationTaskState returnState = this
                    .postServiceSynchronously(
                            ResourceAllocationTaskFactoryService.SELF_LINK,
                            startState, ResourceAllocationTaskState.class);

            ResourceAllocationTaskState completeState = this
                    .waitForServiceState(
                            ResourceAllocationTaskState.class,
                            returnState.documentSelfLink,
                            state -> TaskState.TaskStage.FINISHED.ordinal() <= state.taskInfo.stage
                                    .ordinal());

            assertThat(completeState.taskInfo.stage,
                    is(TaskState.TaskStage.FINISHED));
        }

        @Test
        public void testNoNetwork() throws Throwable {
            String resourcePool = UUID.randomUUID().toString();

            createParentCompute(this, resourcePool);

            ComputeDescriptionService.ComputeDescription cd = createComputeDescription(
                    this, MockAdapter.MockSuccessInstanceAdapter.SELF_LINK,
                    MockAdapter.MockSuccessBootAdapter.SELF_LINK);

            ResourceAllocationTaskState startState = createAllocationRequest(
                    resourcePool, cd.documentSelfLink,
                    createDiskDescription(this), null);

            ResourceAllocationTaskState returnState = this
                    .postServiceSynchronously(
                            ResourceAllocationTaskFactoryService.SELF_LINK,
                            startState, ResourceAllocationTaskState.class);

            ResourceAllocationTaskState completeState = this
                    .waitForServiceState(
                            ResourceAllocationTaskState.class,
                            returnState.documentSelfLink,
                            state -> TaskState.TaskStage.FINISHED.ordinal() <= state.taskInfo.stage
                                    .ordinal());

            assertThat(completeState.taskInfo.stage,
                    is(TaskState.TaskStage.FINISHED));
        }

        @Test
        public void testNoDiskOrNetwork() throws Throwable {
            String resourcePool = UUID.randomUUID().toString();

            createParentCompute(this, resourcePool);

            ComputeDescriptionService.ComputeDescription cd = createComputeDescription(
                    this, MockAdapter.MockSuccessInstanceAdapter.SELF_LINK,
                    MockAdapter.MockSuccessBootAdapter.SELF_LINK);

            ResourceAllocationTaskState startState = createAllocationRequest(
                    resourcePool, cd.documentSelfLink, null, null);

            ResourceAllocationTaskState returnState = this
                    .postServiceSynchronously(
                            ResourceAllocationTaskFactoryService.SELF_LINK,
                            startState, ResourceAllocationTaskState.class);

            ResourceAllocationTaskState completeState = this
                    .waitForServiceState(
                            ResourceAllocationTaskState.class,
                            returnState.documentSelfLink,
                            state -> TaskState.TaskStage.FINISHED.ordinal() <= state.taskInfo.stage
                                    .ordinal());

            assertThat(completeState.taskInfo.stage,
                    is(TaskState.TaskStage.FINISHED));
        }

        @Test
        public void testProvisionDiskFailure() throws Throwable {
            String resourcePool = UUID.randomUUID().toString();

            createParentCompute(this, resourcePool);

            ComputeDescriptionService.ComputeDescription cd = createComputeDescription(
                    this, MockAdapter.MockSuccessInstanceAdapter.SELF_LINK,
                    MockAdapter.MockSuccessBootAdapter.SELF_LINK);

            List<String> diskLinks = createDiskDescription(this);
            diskLinks.add("http://bad-disk-link");

            ResourceAllocationTaskState startState = createAllocationRequest(
                    resourcePool, cd.documentSelfLink, diskLinks,
                    createNetworkDescription(this));

            ResourceAllocationTaskState returnState = this
                    .postServiceSynchronously(
                            ResourceAllocationTaskFactoryService.SELF_LINK,
                            startState, ResourceAllocationTaskState.class);

            ResourceAllocationTaskState completeState = this
                    .waitForServiceState(
                            ResourceAllocationTaskState.class,
                            returnState.documentSelfLink,
                            state -> TaskState.TaskStage.FINISHED.ordinal() <= state.taskInfo.stage
                                    .ordinal());

            assertThat(completeState.taskInfo.stage,
                    is(TaskState.TaskStage.FAILED));
        }

        @Test
        public void testProvisionNetworkFailure() throws Throwable {
            String resourcePool = UUID.randomUUID().toString();

            createParentCompute(this, resourcePool);

            ComputeDescriptionService.ComputeDescription cd = createComputeDescription(
                    this,
                    MockAdapter.MockSuccessInstanceAdapter.SELF_LINK,
                    MockAdapter.MockSuccessBootAdapter.SELF_LINK);

            List<String> networkLinks = createNetworkDescription(this);
            networkLinks.add("http://bad-network-link");

            ResourceAllocationTaskState startState = createAllocationRequest(
                    resourcePool, cd.documentSelfLink,
                    createDiskDescription(this), networkLinks);

            ResourceAllocationTaskState returnState = this
                    .postServiceSynchronously(
                            ResourceAllocationTaskFactoryService.SELF_LINK,
                            startState, ResourceAllocationTaskState.class);

            ResourceAllocationTaskState completeState = this
                    .waitForServiceState(
                            ResourceAllocationTaskState.class,
                            returnState.documentSelfLink,
                            state -> TaskState.TaskStage.FINISHED.ordinal() <= state.taskInfo.stage
                                    .ordinal());

            assertThat(completeState.taskInfo.stage,
                    is(TaskState.TaskStage.FAILED));
        }

        @Test
        public void testProvisionCompulteFailure() throws Throwable {
            String resourcePool = UUID.randomUUID().toString();

            createParentCompute(this, resourcePool);

            ComputeDescriptionService.ComputeDescription cd = createComputeDescription(
                    this,
                    MockAdapter.MockFailureInstanceAdapter.SELF_LINK,
                    MockAdapter.MockFailureBootAdapter.SELF_LINK);

            ResourceAllocationTaskState startState = createAllocationRequest(
                    resourcePool, cd.documentSelfLink,
                    createDiskDescription(this),
                    createNetworkDescription(this));

            ResourceAllocationTaskState returnState = this
                    .postServiceSynchronously(
                            ResourceAllocationTaskFactoryService.SELF_LINK,
                            startState, ResourceAllocationTaskState.class);

            ResourceAllocationTaskState completeState = this
                    .waitForServiceState(
                            ResourceAllocationTaskState.class,
                            returnState.documentSelfLink,
                            state -> TaskState.TaskStage.FINISHED.ordinal() <= state.taskInfo.stage
                                    .ordinal());

            assertThat(completeState.taskInfo.stage,
                    is(TaskState.TaskStage.FAILED));
        }
    }

}
