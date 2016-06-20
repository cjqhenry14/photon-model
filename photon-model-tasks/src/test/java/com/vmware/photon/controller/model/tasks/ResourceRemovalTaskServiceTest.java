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
import static org.hamcrest.Matchers.notNullValue;

import java.util.EnumSet;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import org.junit.runners.Suite.SuiteClasses;
import org.junit.runners.model.InitializationError;
import org.junit.runners.model.RunnerBuilder;

import com.vmware.photon.controller.model.helpers.BaseModelTest;
import com.vmware.photon.controller.model.resources.ComputeService;

import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.QueryTask;
import com.vmware.xenon.services.common.ServiceUriPaths;

/**
 * This class implements tests for the {@link ResourceRemovalTaskService} class.
 */
@RunWith(ResourceRemovalTaskServiceTest.class)
@SuiteClasses({ ResourceRemovalTaskServiceTest.ConstructorTest.class,
        ResourceRemovalTaskServiceTest.HandleStartTest.class,
        ResourceRemovalTaskServiceTest.EndToEndTest.class })
public class ResourceRemovalTaskServiceTest extends Suite {

    public ResourceRemovalTaskServiceTest(Class<?> klass, RunnerBuilder builder)
            throws InitializationError {
        super(klass, builder);
    }

    private static ResourceRemovalTaskService.ResourceRemovalTaskState buildValidStartState() {
        ResourceRemovalTaskService.ResourceRemovalTaskState startState = new ResourceRemovalTaskService.ResourceRemovalTaskState();

        startState.resourceQuerySpec = new QueryTask.QuerySpecification();
        QueryTask.Query kindClause = new QueryTask.Query().setTermPropertyName(
                ServiceDocument.FIELD_NAME_KIND).setTermMatchValue(
                        Utils.buildKind(ComputeService.ComputeState.class));
        startState.resourceQuerySpec.query.addBooleanClause(kindClause);
        startState.isMockRequest = true;

        return startState;
    }

    private static void startFactoryServices(BaseModelTest test) throws Throwable {
        PhotonModelTaskServices.startServices(test.getHost());
        MockAdapter.startFactories(test);
    }

    /**
     * This class implements tests for the constructor.
     */
    public static class ConstructorTest {

        private ResourceRemovalTaskService resourceRemovalTaskService;

        @Before
        public void setUpTest() {
            this.resourceRemovalTaskService = new ResourceRemovalTaskService();
        }

        @Test
        public void testServiceOptions() {

            EnumSet<Service.ServiceOption> expected = EnumSet.of(
                    Service.ServiceOption.CONCURRENT_GET_HANDLING,
                    Service.ServiceOption.INSTRUMENTATION,
                    Service.ServiceOption.PERSISTENCE,
                    Service.ServiceOption.REPLICATION,
                    Service.ServiceOption.OWNER_SELECTION);

            assertThat(this.resourceRemovalTaskService.getOptions(),
                    is(expected));
        }
    }

    /**
     * This class implements tests for the handleStart method.
     */
    public static class HandleStartTest extends BaseModelTest {
        @Override
        protected void startRequiredServices() throws Throwable {
            ResourceRemovalTaskServiceTest.startFactoryServices(this);
            super.startRequiredServices();
        }

        @Test
        public void testMissingResourceQuerySpec() throws Throwable {
            ResourceRemovalTaskService.ResourceRemovalTaskState startState = buildValidStartState();
            startState.resourceQuerySpec = null;

            postServiceSynchronously(
                    ResourceRemovalTaskService.FACTORY_LINK, startState,
                    ResourceRemovalTaskService.ResourceRemovalTaskState.class,
                    IllegalArgumentException.class);
        }

        @Test
        public void testMissingTaskInfo() throws Throwable {
            ResourceRemovalTaskService.ResourceRemovalTaskState startState = buildValidStartState();
            startState.taskInfo = null;

            ResourceRemovalTaskService.ResourceRemovalTaskState returnState = postServiceSynchronously(
                    ResourceRemovalTaskService.FACTORY_LINK,
                    startState,
                    ResourceRemovalTaskService.ResourceRemovalTaskState.class);

            assertThat(returnState.taskInfo, notNullValue());
            assertThat(returnState.taskInfo.stage,
                    is(TaskState.TaskStage.CREATED));
        }

        @Test
        public void testMissingTaskSubStage() throws Throwable {
            ResourceRemovalTaskService.ResourceRemovalTaskState startState = buildValidStartState();
            startState.taskSubStage = null;

            ResourceRemovalTaskService.ResourceRemovalTaskState returnState = postServiceSynchronously(
                    ResourceRemovalTaskService.FACTORY_LINK,
                    startState,
                    ResourceRemovalTaskService.ResourceRemovalTaskState.class);

            assertThat(returnState.taskSubStage, notNullValue());
            assertThat(
                    returnState.taskSubStage,
                    is(ResourceRemovalTaskService.SubStage.WAITING_FOR_QUERY_COMPLETION));
        }
    }

    /**
     * This class implements EndToEnd tests for
     * {@link ResourceRemovalTaskService}.
     */
    public static class EndToEndTest extends BaseModelTest {
        @Override
        protected void startRequiredServices() throws Throwable {
            ResourceRemovalTaskServiceTest.startFactoryServices(this);
            super.startRequiredServices();
        }

        @Test
        public void testQueryResourceReturnZeroDocument() throws Throwable {
            ResourceRemovalTaskService.ResourceRemovalTaskState startState = buildValidStartState();

            ResourceRemovalTaskService.ResourceRemovalTaskState returnState = this
                    .postServiceSynchronously(
                            ResourceRemovalTaskService.FACTORY_LINK,
                            startState,
                            ResourceRemovalTaskService.ResourceRemovalTaskState.class);

            returnState = this
                    .waitForServiceState(
                            ResourceRemovalTaskService.ResourceRemovalTaskState.class,
                            returnState.documentSelfLink,
                            state -> state.taskInfo.stage == TaskState.TaskStage.FINISHED);

            assertThat(returnState.taskSubStage,
                    is(ResourceRemovalTaskService.SubStage.FINISHED));
        }

        @Test
        public void testResourceRemovalSuccess() throws Throwable {
            ResourceRemovalTaskService.ResourceRemovalTaskState startState = buildValidStartState();
            ComputeService.ComputeStateWithDescription cs = ModelUtils
                    .createComputeWithDescription(this,
                            MockAdapter.MockSuccessInstanceAdapter.SELF_LINK,
                            null);

            ResourceRemovalTaskService.ResourceRemovalTaskState returnState = this
                    .postServiceSynchronously(
                            ResourceRemovalTaskService.FACTORY_LINK,
                            startState,
                            ResourceRemovalTaskService.ResourceRemovalTaskState.class);

            returnState = this
                    .waitForServiceState(
                            ResourceRemovalTaskService.ResourceRemovalTaskState.class,
                            returnState.documentSelfLink,
                            state -> state.taskInfo.stage == TaskState.TaskStage.FINISHED);

            assertThat(returnState.taskSubStage,
                    is(ResourceRemovalTaskService.SubStage.FINISHED));

            // Clean up the compute and description documents
            this.deleteServiceSynchronously(cs.documentSelfLink);
            this.deleteServiceSynchronously(cs.descriptionLink);

            // Stop factory service.
            this.deleteServiceSynchronously(ResourceRemovalTaskService.FACTORY_LINK);

            // stop the removal task
            this.stopServiceSynchronously(returnState.documentSelfLink);

            // restart and check service restart successfully.
            this.getHost().startFactory(new ResourceRemovalTaskService());

            // TODO work around until we move to Xenon 0.7.1 or later release. Factories started after
            // initial synch, will not have children restarted
            this.host.scheduleNodeGroupChangeMaintenance(ServiceUriPaths.DEFAULT_NODE_SELECTOR);
            ResourceRemovalTaskService.ResourceRemovalTaskState stateAfterRestart = this
                    .getServiceSynchronously(
                            returnState.documentSelfLink,
                            ResourceRemovalTaskService.ResourceRemovalTaskState.class);
            assertThat(stateAfterRestart, notNullValue());
        }

        @Test
        public void testResourceRemovalFailure() throws Throwable {
            ResourceRemovalTaskService.ResourceRemovalTaskState startState = buildValidStartState();
            ComputeService.ComputeStateWithDescription cs = ModelUtils
                    .createComputeWithDescription(this,
                            MockAdapter.MockFailureInstanceAdapter.SELF_LINK,
                            null);

            ResourceRemovalTaskService.ResourceRemovalTaskState returnState = this
                    .postServiceSynchronously(
                            ResourceRemovalTaskService.FACTORY_LINK,
                            startState,
                            ResourceRemovalTaskService.ResourceRemovalTaskState.class);

            this.waitForServiceState(
                    ResourceRemovalTaskService.ResourceRemovalTaskState.class,
                    returnState.documentSelfLink,
                    state -> state.taskInfo.stage == TaskState.TaskStage.FAILED);

            // Clean up the compute and description documents
            this.deleteServiceSynchronously(cs.documentSelfLink);
            this.deleteServiceSynchronously(cs.descriptionLink);
        }
    }
}
