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

import com.vmware.photon.controller.model.adapterapi.EnumerationAction;
import com.vmware.photon.controller.model.helpers.BaseModelTest;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService;
import com.vmware.photon.controller.model.resources.ComputeDescriptionServiceTest;
import com.vmware.photon.controller.model.resources.ComputeService;

import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.UriUtils;

/**
 * This class implements tests for the {@link ProvisionComputeTaskService}
 * class.
 */
@RunWith(ResourceEnumerationTaskServiceTest.class)
@SuiteClasses({ ResourceEnumerationTaskServiceTest.ConstructorTest.class,
        ResourceEnumerationTaskServiceTest.HandleStartTest.class,
        ResourceEnumerationTaskServiceTest.EndToEndTest.class })
public class ResourceEnumerationTaskServiceTest extends Suite {
    private static final String TEST_DESC_PROPERTY_NAME = "testDescProperty";
    private static final String TEST_DESC_PROPERTY_VALUE = UUID.randomUUID()
            .toString();

    public ResourceEnumerationTaskServiceTest(Class<?> klass,
            RunnerBuilder builder) throws InitializationError {
        super(klass, builder);
    }

    private static ResourceEnumerationTaskService.ResourceEnumerationTaskState buildValidStartState(
            ComputeService.ComputeStateWithDescription computeStateWithDescription)
            throws Throwable {
        ResourceEnumerationTaskService.ResourceEnumerationTaskState state = new ResourceEnumerationTaskService.ResourceEnumerationTaskState();
        state.adapterManagementReference = new URI(
                "http://adapterManagementReference");
        state.resourcePoolLink = "http://resourcePoolLink";
        state.enumerationAction = EnumerationAction.RERESH;
        if (computeStateWithDescription != null) {
            state.computeDescriptionLink = computeStateWithDescription.descriptionLink;
            state.parentComputeLink = computeStateWithDescription.documentSelfLink;
        }
        return state;
    }

    private static ComputeDescriptionService.ComputeDescription createComputeDescription(
            BaseModelTest test, String enumerationAdapterReference) throws Throwable {
        ComputeDescriptionService.ComputeDescription cd = ComputeDescriptionServiceTest
                .buildValidStartState();
        cd.healthAdapterReference = null;
        cd.enumerationAdapterReference = UriUtils.buildUri(test.getHost(),
                enumerationAdapterReference);

        return test.postServiceSynchronously(
                ComputeDescriptionService.FACTORY_LINK, cd,
                ComputeDescriptionService.ComputeDescription.class);
    }

    private static ComputeService.ComputeStateWithDescription createCompute(
            BaseModelTest test, ComputeDescriptionService.ComputeDescription cd)
            throws Throwable {
        ComputeService.ComputeState cs = new ComputeService.ComputeStateWithDescription();
        cs.id = UUID.randomUUID().toString();
        cs.descriptionLink = cd.documentSelfLink;
        cs.resourcePoolLink = null;
        cs.address = "10.0.0.1";
        cs.primaryMAC = "01:23:45:67:89:ab";
        cs.powerState = ComputeService.PowerState.ON;
        cs.adapterManagementReference = URI
                .create("https://esxhost-01:443/sdk");
        cs.diskLinks = new ArrayList<>();
        cs.diskLinks.add("http://disk");
        cs.networkLinks = new ArrayList<>();
        cs.networkLinks.add("http://network");
        cs.customProperties = new HashMap<>();
        cs.customProperties.put(TEST_DESC_PROPERTY_NAME,
                TEST_DESC_PROPERTY_VALUE);
        cs.tenantLinks = new ArrayList<>();
        cs.tenantLinks.add("http://tenant");

        ComputeService.ComputeState returnState = test
                .postServiceSynchronously(ComputeService.FACTORY_LINK, cs,
                        ComputeService.ComputeState.class);

        return ComputeService.ComputeStateWithDescription.create(cd,
                returnState);
    }

    private static void startFactoryServices(BaseModelTest test) throws Throwable {
        TaskServices.startFactories(test);
        MockAdapter.startFactories(test);
    }

    /**
     * This class implements tests for the constructor.
     */
    public static class ConstructorTest {

        private ResourceEnumerationTaskService resourceEnumerationTaskService;

        @Before
        public void setUpTest() {
            this.resourceEnumerationTaskService = new ResourceEnumerationTaskService();
        }

        @Test
        public void testServiceOptions() {

            EnumSet<Service.ServiceOption> expected = EnumSet.of(
                    Service.ServiceOption.CONCURRENT_GET_HANDLING,
                    Service.ServiceOption.INSTRUMENTATION,
                    Service.ServiceOption.OWNER_SELECTION,
                    Service.ServiceOption.PERSISTENCE,
                    Service.ServiceOption.REPLICATION);

            assertThat(this.resourceEnumerationTaskService.getOptions(),
                    is(expected));
        }
    }

    /**
     * This class implements tests for the handleStart method.
     */
    public static class HandleStartTest extends BaseModelTest {

        private ComputeService.ComputeStateWithDescription computeHost;

        @Override
        protected void startRequiredServices() throws Throwable {
            ResourceEnumerationTaskServiceTest.startFactoryServices(this);
            super.startRequiredServices();

            ComputeDescriptionService.ComputeDescription cd = createComputeDescription(
                    this, "http://enumerationAdapter");
            this.computeHost = createCompute(this, cd);
        }


        @Test
        public void testMissingComputeDescription() throws Throwable {
            ResourceEnumerationTaskService.ResourceEnumerationTaskState state = buildValidStartState(null);
            this.postServiceSynchronously(
                            ResourceEnumerationTaskService.FACTORY_LINK,
                            state,
                            ResourceEnumerationTaskService.ResourceEnumerationTaskState.class,
                            IllegalArgumentException.class);
        }

        @Test
        public void testMissingAdapterManagementReference() throws Throwable {
            ResourceEnumerationTaskService.ResourceEnumerationTaskState state = buildValidStartState(this.computeHost);
            state.adapterManagementReference = null;

            this.postServiceSynchronously(
                            ResourceEnumerationTaskService.FACTORY_LINK,
                            state,
                            ResourceEnumerationTaskService.ResourceEnumerationTaskState.class,
                            IllegalArgumentException.class);
        }

        @Test
        public void testMissingResourcePoolLink() throws Throwable {
            ResourceEnumerationTaskService.ResourceEnumerationTaskState state = buildValidStartState(this.computeHost);
            state.resourcePoolLink = null;

            this.postServiceSynchronously(
                            ResourceEnumerationTaskService.FACTORY_LINK,
                            state,
                            ResourceEnumerationTaskService.ResourceEnumerationTaskState.class,
                            IllegalArgumentException.class);
        }
    }

    /**
     * This class implements EndToEnd tests for the
     * ResourceEnumerationTaskService.
     */
    public static class EndToEndTest extends BaseModelTest {

        @Override
        protected void startRequiredServices() throws Throwable {
            ResourceEnumerationTaskServiceTest.startFactoryServices(this);
            super.startRequiredServices();
        }

        @Test
        public void testSuccess() throws Throwable {
            ComputeDescriptionService.ComputeDescription cd = createComputeDescription(
                    this,
                    MockAdapter.MockSuccessEnumerationAdapter.SELF_LINK);
            ComputeService.ComputeStateWithDescription computeHost = createCompute(
                    this, cd);

            ResourceEnumerationTaskService.ResourceEnumerationTaskState startState = this
                    .postServiceSynchronously(
                            ResourceEnumerationTaskService.FACTORY_LINK,
                            buildValidStartState(computeHost),
                            ResourceEnumerationTaskService.ResourceEnumerationTaskState.class);

            ResourceEnumerationTaskService.ResourceEnumerationTaskState newState = this
                    .waitForServiceState(
                            ResourceEnumerationTaskService.ResourceEnumerationTaskState.class,
                            startState.documentSelfLink,
                            state -> TaskState.TaskStage.FINISHED.ordinal() <= state.taskInfo.stage
                                    .ordinal());

            assertThat(newState.taskInfo.stage,
                    is(TaskState.TaskStage.FINISHED));
        }

        @Test
        public void testFailure() throws Throwable {
            ComputeDescriptionService.ComputeDescription cd = createComputeDescription(
                    this,
                    MockAdapter.MockFailureEnumerationAdapter.SELF_LINK);
            ComputeService.ComputeStateWithDescription computeHost = createCompute(
                    this, cd);

            ResourceEnumerationTaskService.ResourceEnumerationTaskState startState = this
                    .postServiceSynchronously(
                            ResourceEnumerationTaskService.FACTORY_LINK,
                            buildValidStartState(computeHost),
                            ResourceEnumerationTaskService.ResourceEnumerationTaskState.class);

            ResourceEnumerationTaskService.ResourceEnumerationTaskState newState = this
                    .waitForServiceState(
                            ResourceEnumerationTaskService.ResourceEnumerationTaskState.class,
                            startState.documentSelfLink,
                            state -> TaskState.TaskStage.FINISHED.ordinal() <= state.taskInfo.stage
                                    .ordinal());

            assertThat(newState.taskInfo.stage, is(TaskState.TaskStage.FAILED));
        }
    }
}
