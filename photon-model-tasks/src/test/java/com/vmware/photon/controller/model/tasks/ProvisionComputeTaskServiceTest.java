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

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import org.junit.runners.Suite.SuiteClasses;
import org.junit.runners.model.InitializationError;
import org.junit.runners.model.RunnerBuilder;

import com.vmware.photon.controller.model.ModelServices;
import com.vmware.photon.controller.model.TaskServices;
import com.vmware.photon.controller.model.helpers.BaseModelTest;
import com.vmware.photon.controller.model.resources.ComputeDescriptionFactoryService;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription.ComputeType;
import com.vmware.photon.controller.model.resources.ComputeDescriptionServiceTest;
import com.vmware.photon.controller.model.resources.ComputeService;

import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.TaskState;

/**
 * This class implements tests for the {@link ProvisionComputeTaskService}
 * class.
 */
@RunWith(ProvisionComputeTaskServiceTest.class)
@SuiteClasses({ ProvisionComputeTaskServiceTest.ConstructorTest.class,
        ProvisionComputeTaskServiceTest.HandleStartTest.class,
        ProvisionComputeTaskServiceTest.HandlePatchTest.class })
public class ProvisionComputeTaskServiceTest extends Suite {

    public ProvisionComputeTaskServiceTest(Class<?> klass, RunnerBuilder builder)
            throws InitializationError {
        super(klass, builder);
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private static Class<? extends Service>[] getFactoryServices() {
        List<Class<? extends Service>> services = new ArrayList<>();
        Collections.addAll(services, ModelServices.getFactories());
        Collections.addAll(services, TaskServices.getFactories());
        Collections.addAll(services, MockAdapter.getFactories());
        return services.toArray((Class<? extends Service>[]) new Class[services
                .size()]);
    }

    /**
     * This class implements tests for the constructor.
     */
    public static class ConstructorTest {

        private ProvisionComputeTaskService provisionComputeTaskService;

        @Before
        public void setUpTest() {
            this.provisionComputeTaskService = new ProvisionComputeTaskService();
        }

        @Test
        public void testServiceOptions() {

            EnumSet<Service.ServiceOption> expected = EnumSet
                    .of(Service.ServiceOption.INSTRUMENTATION);

            assertThat(this.provisionComputeTaskService.getOptions(),
                    is(expected));
        }
    }

    /**
     * This class implements tests for the handleStart method.
     */
    public static class HandleStartTest extends BaseModelTest {
        @Override
        protected Class<? extends Service>[] getFactoryServices() {
            return ProvisionComputeTaskServiceTest.getFactoryServices();
        }

        private ProvisionComputeTaskService provisionComputeTaskService;

        @Before
        public void setUpTest() throws Throwable {
            super.setUpClass();
            this.provisionComputeTaskService = new ProvisionComputeTaskService();
        }

        @Test
        public void testValidateComputeHost() throws Throwable {
            ComputeService.ComputeStateWithDescription cs = ModelUtils
                    .createComputeWithDescription(host);

            ProvisionComputeTaskService.ProvisionComputeTaskState startState = new ProvisionComputeTaskService.ProvisionComputeTaskState();
            startState.computeLink = cs.documentSelfLink;
            startState.taskSubStage = ProvisionComputeTaskService.ProvisionComputeTaskState.SubStage.VALIDATE_COMPUTE_HOST;
            startState.isMockRequest = true;

            ProvisionComputeTaskService.ProvisionComputeTaskState returnState = host
                    .postServiceSynchronously(
                            ProvisionComputeTaskFactoryService.SELF_LINK,
                            startState,
                            ProvisionComputeTaskService.ProvisionComputeTaskState.class);

            ProvisionComputeTaskService.ProvisionComputeTaskState completeState = host
                    .waitForServiceState(
                            ProvisionComputeTaskService.ProvisionComputeTaskState.class,
                            returnState.documentSelfLink,
                            state -> TaskState.TaskStage.FINISHED.ordinal() <= state.taskInfo.stage
                                    .ordinal());

            assertThat(completeState.taskInfo.stage,
                    is(TaskState.TaskStage.FINISHED));
        }

        @Test
        public void testMissingComputeLink() throws Throwable {
            ComputeService.ComputeStateWithDescription cs = ModelUtils
                    .createComputeWithDescription(host);

            ProvisionComputeTaskService.ProvisionComputeTaskState startState = new ProvisionComputeTaskService.ProvisionComputeTaskState();
            startState.computeLink = null;

            host.postServiceSynchronously(
                    ProvisionComputeTaskFactoryService.SELF_LINK,
                    startState,
                    ProvisionComputeTaskService.ProvisionComputeTaskState.class,
                    IllegalArgumentException.class);
        }

        @Test
        public void testMissingSubStage() throws Throwable {
            ComputeService.ComputeStateWithDescription cs = ModelUtils
                    .createComputeWithDescription(host);

            ProvisionComputeTaskService.ProvisionComputeTaskState startState = new ProvisionComputeTaskService.ProvisionComputeTaskState();
            startState.taskSubStage = null;

            host.postServiceSynchronously(
                    ProvisionComputeTaskFactoryService.SELF_LINK,
                    startState,
                    ProvisionComputeTaskService.ProvisionComputeTaskState.class,
                    IllegalArgumentException.class);
        }

        @Test
        public void testMissingInstanceAdapterReference() throws Throwable {
            ComputeDescriptionService.ComputeDescription cd = ComputeDescriptionServiceTest
                    .buildValidStartState();
            cd.healthAdapterReference = null;
            cd.supportedChildren = new ArrayList<>();
            cd.supportedChildren.add(ComputeType.DOCKER_CONTAINER.toString());
            cd.instanceAdapterReference = null;
            ComputeDescriptionService.ComputeDescription cd1 = host
                    .postServiceSynchronously(
                            ComputeDescriptionFactoryService.SELF_LINK, cd,
                            ComputeDescriptionService.ComputeDescription.class);

            ComputeService.ComputeStateWithDescription cs = ModelUtils
                    .createCompute(host, cd1);

            ProvisionComputeTaskService.ProvisionComputeTaskState startState = new ProvisionComputeTaskService.ProvisionComputeTaskState();
            startState.computeLink = cs.documentSelfLink;
            startState.taskSubStage = ProvisionComputeTaskService.ProvisionComputeTaskState.SubStage.CREATING_HOST;

            ProvisionComputeTaskService.ProvisionComputeTaskState returnState = host
                    .postServiceSynchronously(
                            ProvisionComputeTaskFactoryService.SELF_LINK,
                            startState,
                            ProvisionComputeTaskService.ProvisionComputeTaskState.class);

            ProvisionComputeTaskService.ProvisionComputeTaskState completeState = host
                    .waitForServiceState(
                            ProvisionComputeTaskService.ProvisionComputeTaskState.class,
                            returnState.documentSelfLink,
                            state -> TaskState.TaskStage.FINISHED.ordinal() <= state.taskInfo.stage
                                    .ordinal());

            assertThat(completeState.taskInfo.stage,
                    is(TaskState.TaskStage.FAILED));
            assertThat(completeState.taskInfo.failure.message,
                    is("computeHost does not have create service specified"));
        }
    }

    /**
     * This class implements tests for the handlePatch method.
     */
    public static class HandlePatchTest extends BaseModelTest {
        @Override
        protected Class<? extends Service>[] getFactoryServices() {
            return ProvisionComputeTaskServiceTest.getFactoryServices();
        }

        @Before
        public void setUpTest() throws Throwable {
            super.setUpClass();
        }

        @Test
        public void testCreateAndBootHostSuccess() throws Throwable {
            ComputeService.ComputeStateWithDescription cs = ModelUtils
                    .createComputeWithDescription(host,
                            MockAdapter.MockSuccessInstanceAdapter.SELF_LINK,
                            MockAdapter.MockSuccessBootAdapter.SELF_LINK);

            ProvisionComputeTaskService.ProvisionComputeTaskState startState = new ProvisionComputeTaskService.ProvisionComputeTaskState();
            startState.computeLink = cs.documentSelfLink;
            startState.taskSubStage = ProvisionComputeTaskService.ProvisionComputeTaskState.SubStage.CREATING_HOST;
            startState.isMockRequest = true;

            ProvisionComputeTaskService.ProvisionComputeTaskState returnState = host
                    .postServiceSynchronously(
                            ProvisionComputeTaskFactoryService.SELF_LINK,
                            startState,
                            ProvisionComputeTaskService.ProvisionComputeTaskState.class);

            ProvisionComputeTaskService.ProvisionComputeTaskState completeState = host
                    .waitForServiceState(
                            ProvisionComputeTaskService.ProvisionComputeTaskState.class,
                            returnState.documentSelfLink,
                            state -> TaskState.TaskStage.FINISHED.ordinal() <= state.taskInfo.stage
                                    .ordinal());

            assertThat(completeState.taskInfo.stage,
                    is(TaskState.TaskStage.FINISHED));
        }

        @Test
        public void testCreateHostFailure() throws Throwable {
            ComputeService.ComputeStateWithDescription cs = ModelUtils
                    .createComputeWithDescription(host,
                            MockAdapter.MockFailureInstanceAdapter.SELF_LINK,
                            null);

            ProvisionComputeTaskService.ProvisionComputeTaskState startState = new ProvisionComputeTaskService.ProvisionComputeTaskState();
            startState.computeLink = cs.documentSelfLink;
            startState.taskSubStage = ProvisionComputeTaskService.ProvisionComputeTaskState.SubStage.CREATING_HOST;
            startState.isMockRequest = true;

            ProvisionComputeTaskService.ProvisionComputeTaskState returnState = host
                    .postServiceSynchronously(
                            ProvisionComputeTaskFactoryService.SELF_LINK,
                            startState,
                            ProvisionComputeTaskService.ProvisionComputeTaskState.class);

            ProvisionComputeTaskService.ProvisionComputeTaskState completeState = host
                    .waitForServiceState(
                            ProvisionComputeTaskService.ProvisionComputeTaskState.class,
                            returnState.documentSelfLink,
                            state -> TaskState.TaskStage.FINISHED.ordinal() <= state.taskInfo.stage
                                    .ordinal());

            assertThat(completeState.taskInfo.stage,
                    is(TaskState.TaskStage.FAILED));
        }

        @Test
        public void testBootHostFailure() throws Throwable {
            ComputeService.ComputeStateWithDescription cs = ModelUtils
                    .createComputeWithDescription(host,
                            MockAdapter.MockSuccessInstanceAdapter.SELF_LINK,
                            MockAdapter.MockFailureBootAdapter.SELF_LINK);

            ProvisionComputeTaskService.ProvisionComputeTaskState startState = new ProvisionComputeTaskService.ProvisionComputeTaskState();
            startState.computeLink = cs.documentSelfLink;
            startState.taskSubStage = ProvisionComputeTaskService.ProvisionComputeTaskState.SubStage.CREATING_HOST;
            startState.isMockRequest = true;

            ProvisionComputeTaskService.ProvisionComputeTaskState returnState = host
                    .postServiceSynchronously(
                            ProvisionComputeTaskFactoryService.SELF_LINK,
                            startState,
                            ProvisionComputeTaskService.ProvisionComputeTaskState.class);

            ProvisionComputeTaskService.ProvisionComputeTaskState completeState = host
                    .waitForServiceState(
                            ProvisionComputeTaskService.ProvisionComputeTaskState.class,
                            returnState.documentSelfLink,
                            state -> TaskState.TaskStage.FINISHED.ordinal() <= state.taskInfo.stage
                                    .ordinal());

            assertThat(completeState.taskInfo.stage,
                    is(TaskState.TaskStage.FAILED));
        }
    }
}
