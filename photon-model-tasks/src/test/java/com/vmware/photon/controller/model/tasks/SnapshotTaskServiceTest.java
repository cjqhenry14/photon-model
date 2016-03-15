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
import java.util.EnumSet;
import java.util.HashMap;
import java.util.UUID;

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
import com.vmware.photon.controller.model.resources.SnapshotService;

import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.UriUtils;

/**
 * This class implements tests for the {@link SnapshotTaskService} class.
 */
@RunWith(SnapshotTaskServiceTest.class)
@SuiteClasses({ SnapshotTaskServiceTest.ConstructorTest.class,
        SnapshotTaskServiceTest.HandleStartTest.class,
        SnapshotTaskServiceTest.EndToEndTest.class })
public class SnapshotTaskServiceTest extends Suite {
    public SnapshotTaskServiceTest(Class<?> klass, RunnerBuilder builder)
            throws InitializationError {
        super(klass, builder);
    }

    private static SnapshotTaskService.SnapshotTaskState buildValidStartState(
            SnapshotService.SnapshotState st) throws Throwable {
        SnapshotTaskService.SnapshotTaskState state = new SnapshotTaskService.SnapshotTaskState();
        state.snapshotAdapterReference = new URI(
                "http://snapshotAdapterReference");

        state.isMockRequest = true;
        state.snapshotLink = st.documentSelfLink;

        return state;
    }

    private static SnapshotService.SnapshotState createSnapshotService(
            BaseModelTest test) throws Throwable {
        SnapshotService.SnapshotState st = new SnapshotService.SnapshotState();
        st.id = UUID.randomUUID().toString();
        st.name = "friendly-name";
        st.computeLink = "compute-link";
        st.description = "description";
        st.customProperties = new HashMap<>();
        st.customProperties.put("defaultKey", "defaultVal");

        SnapshotService.SnapshotState serviceState = test
                .postServiceSynchronously(SnapshotService.FACTORY_LINK, st,
                        SnapshotService.SnapshotState.class);

        return serviceState;
    }

    private static void startFactoryServices(BaseModelTest test) throws Throwable {
        TaskServices.startFactories(test);
        MockAdapter.startFactories(test);
    }

    /**
     * This class implements tests for the constructor.
     */
    public static class ConstructorTest {

        private SnapshotTaskService snapshotTaskService;

        @Before
        public void setUpTest() {
            this.snapshotTaskService = new SnapshotTaskService();
        }

        @Test
        public void testServiceOptions() {

            EnumSet<Service.ServiceOption> expected = EnumSet.of(
                    Service.ServiceOption.CONCURRENT_GET_HANDLING,
                    Service.ServiceOption.INSTRUMENTATION,
                    Service.ServiceOption.OWNER_SELECTION,
                    Service.ServiceOption.PERSISTENCE,
                    Service.ServiceOption.REPLICATION);

            assertThat(this.snapshotTaskService.getOptions(), is(expected));
        }
    }

    /**
     * This class implements tests for the handleStart method.
     */
    public static class HandleStartTest extends BaseModelTest {
        @Override
        protected void startRequiredServices() throws Throwable {
            SnapshotTaskServiceTest.startFactoryServices(this);
            super.startRequiredServices();
        }

        @Test
        public void testMissingSnapshotLink() throws Throwable {
            SnapshotService.SnapshotState serviceState = new SnapshotService.SnapshotState();
            serviceState.computeLink = null;

            SnapshotTaskService.SnapshotTaskState taskState = buildValidStartState(serviceState);

            this.postServiceSynchronously(
                    SnapshotTaskFactoryService.SELF_LINK, taskState,
                    SnapshotTaskService.SnapshotTaskState.class,
                    IllegalArgumentException.class);
        }

        @Test
        public void testMissingSnapshotAdapterReference() throws Throwable {
            SnapshotService.SnapshotState serviceState = createSnapshotService(this);

            SnapshotTaskService.SnapshotTaskState taskState = buildValidStartState(serviceState);
            taskState.snapshotAdapterReference = null;

            this.postServiceSynchronously(
                    SnapshotTaskFactoryService.SELF_LINK, taskState,
                    SnapshotTaskService.SnapshotTaskState.class,
                    IllegalArgumentException.class);
        }
    }

    /**
     * This class implements EndToEnd tests for {@link SnapshotTaskService}.
     */

    public static class EndToEndTest extends BaseModelTest {
        @Override
        protected void startRequiredServices() throws Throwable {
            SnapshotTaskServiceTest.startFactoryServices(this);
            super.startRequiredServices();
        }

        @Test
        public void testSuccess() throws Throwable {
            SnapshotService.SnapshotState serviceState = createSnapshotService(this);

            SnapshotTaskService.SnapshotTaskState startState = buildValidStartState(serviceState);
            startState.snapshotAdapterReference = UriUtils.buildUri(this.host,
                    MockAdapter.MockSnapshotSuccessAdapter.SELF_LINK);

            SnapshotTaskService.SnapshotTaskState returnState = this
                    .postServiceSynchronously(
                            SnapshotTaskFactoryService.SELF_LINK, startState,
                            SnapshotTaskService.SnapshotTaskState.class);

            returnState = this
                    .waitForServiceState(
                            SnapshotTaskService.SnapshotTaskState.class,
                            returnState.documentSelfLink,
                            state -> state.taskInfo.stage == TaskState.TaskStage.FINISHED);

            assertThat(returnState.taskInfo.stage,
                    is(TaskState.TaskStage.FINISHED));
        }

        @Test
        public void testFailure() throws Throwable {
            SnapshotService.SnapshotState serviceState = createSnapshotService(this);

            SnapshotTaskService.SnapshotTaskState startState = buildValidStartState(serviceState);
            startState.snapshotAdapterReference = UriUtils.buildUri(this.host,
                    MockAdapter.MockSnapshotFailureAdapter.SELF_LINK);

            SnapshotTaskService.SnapshotTaskState returnState = this
                    .postServiceSynchronously(
                            SnapshotTaskFactoryService.SELF_LINK, startState,
                            SnapshotTaskService.SnapshotTaskState.class);

            returnState = this
                    .waitForServiceState(
                            SnapshotTaskService.SnapshotTaskState.class,
                            returnState.documentSelfLink,
                            state -> state.taskInfo.stage == TaskState.TaskStage.FAILED);

            assertThat(returnState.taskInfo.stage,
                    is(TaskState.TaskStage.FAILED));
        }
    }
}
