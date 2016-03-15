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

import java.util.EnumSet;
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

import com.vmware.photon.controller.model.adapterapi.NetworkInstanceRequest;
import com.vmware.photon.controller.model.helpers.BaseModelTest;
import com.vmware.photon.controller.model.resources.NetworkService;
import com.vmware.photon.controller.model.resources.NetworkService.NetworkState;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.UriUtils;

/**
 * This class implements tests for the {@link ProvisionNetworkTaskService}
 * class.
 */
@RunWith(ProvisionNetworkTaskServiceTest.class)
@SuiteClasses({ ProvisionNetworkTaskServiceTest.ConstructorTest.class,
        ProvisionNetworkTaskServiceTest.HandleStartTest.class,
        ProvisionFirewallTaskServiceTest.HandlePatchTest.class })
public class ProvisionNetworkTaskServiceTest extends Suite {

    public ProvisionNetworkTaskServiceTest(Class<?> klass, RunnerBuilder builder)
            throws InitializationError {
        super(klass, builder);
    }

    private static ProvisionNetworkTaskService.ProvisionNetworkTaskState buildValidStartState(
            BaseModelTest test,
            NetworkInstanceRequest.InstanceRequestType requestType,
            boolean success) throws Throwable {

        NetworkState nState = new NetworkState();
        nState.authCredentialsLink = "authCredentialsLink";
        nState.name = "firewall-name";
        nState.regionID = "regionId";
        nState.resourcePoolLink = "http://resourcePoolLink";
        nState.subnetCIDR = "152.151.150.222/22";
        if (success) {
            nState.instanceAdapterReference = UriUtils.buildUri(test.getHost(),
                    MockAdapter.MockNetworkInstanceSuccessAdapter.SELF_LINK);
        } else {
            nState.instanceAdapterReference = UriUtils.buildUri(test.getHost(),
                    MockAdapter.MockNetworkInstanceFailureAdapter.SELF_LINK);
        }
        nState.id = UUID.randomUUID().toString();

        NetworkState returnState = test.postServiceSynchronously(
                NetworkService.FACTORY_LINK, nState, NetworkState.class);
        ProvisionNetworkTaskService.ProvisionNetworkTaskState startState = new ProvisionNetworkTaskService.ProvisionNetworkTaskState();

        startState.requestType = requestType;
        startState.networkDescriptionLink = returnState.documentSelfLink;
        startState.isMockRequest = true;
        return startState;
    }

    private static ProvisionNetworkTaskService.ProvisionNetworkTaskState postAndWaitForService(
            BaseModelTest test,
            ProvisionNetworkTaskService.ProvisionNetworkTaskState startState)
            throws Throwable {
        ProvisionNetworkTaskService.ProvisionNetworkTaskState returnState = test
                .postServiceSynchronously(
                        ProvisionNetworkTaskFactoryService.SELF_LINK,
                        startState,
                        ProvisionNetworkTaskService.ProvisionNetworkTaskState.class);

        ProvisionNetworkTaskService.ProvisionNetworkTaskState completeState = test
                .waitForServiceState(
                        ProvisionNetworkTaskService.ProvisionNetworkTaskState.class,
                        returnState.documentSelfLink,
                        state -> TaskState.TaskStage.FINISHED.ordinal() <= state.taskInfo.stage
                                .ordinal());

        return completeState;
    }

    private static void startFactoryServices(BaseModelTest test) throws Throwable {
        TaskServices.startFactories(test);
        MockAdapter.startFactories(test);
    }

    /**
     * This class implements tests for the constructor.
     */
    public static class ConstructorTest {

        private ProvisionNetworkTaskService provisionNetworkTaskService;

        @Before
        public void setUpTest() {
            this.provisionNetworkTaskService = new ProvisionNetworkTaskService();
        }

        @Test
        public void testServiceOptions() {

            EnumSet<Service.ServiceOption> expected = EnumSet.of(
                    Service.ServiceOption.CONCURRENT_GET_HANDLING,
                    Service.ServiceOption.PERSISTENCE,
                    Service.ServiceOption.REPLICATION,
                    Service.ServiceOption.OWNER_SELECTION);

            assertThat(this.provisionNetworkTaskService.getOptions(),
                    is(expected));
            assertThat(this.provisionNetworkTaskService.getProcessingStage(),
                    is(Service.ProcessingStage.CREATED));
        }
    }

    /**
     * This class implements tests for the
     * {@link ProvisionNetworkTaskService#handleStart} method.
     */
    public static class HandleStartTest extends BaseModelTest {
        @Override
        protected void startRequiredServices() throws Throwable {
            ProvisionNetworkTaskServiceTest.startFactoryServices(this);
            super.startRequiredServices();
        }

        @Test
        public void testValidateNetworkService() throws Throwable {
            ProvisionNetworkTaskService.ProvisionNetworkTaskState startState = buildValidStartState(
                    this, NetworkInstanceRequest.InstanceRequestType.CREATE,
                    true);
            ProvisionNetworkTaskService.ProvisionNetworkTaskState completeState = postAndWaitForService(
                    this, startState);
            assertThat(completeState.taskInfo.stage,
                    is(TaskState.TaskStage.FINISHED));
        }

        @Test
        public void testMissingValue() throws Throwable {
            ProvisionNetworkTaskService.ProvisionNetworkTaskState invalidRequestType = buildValidStartState(
                    this,
                    NetworkInstanceRequest.InstanceRequestType.CREATE, true);
            ProvisionNetworkTaskService.ProvisionNetworkTaskState invalidNetworkDescriptionLink = buildValidStartState(
                    this,
                    NetworkInstanceRequest.InstanceRequestType.CREATE, true);

            invalidRequestType.requestType = null;
            invalidNetworkDescriptionLink.networkDescriptionLink = null;

            postServiceSynchronously(
                            ProvisionNetworkTaskFactoryService.SELF_LINK,
                            invalidRequestType,
                            ProvisionNetworkTaskService.ProvisionNetworkTaskState.class,
                            IllegalArgumentException.class);
            postServiceSynchronously(
                            ProvisionNetworkTaskFactoryService.SELF_LINK,
                            invalidNetworkDescriptionLink,
                            ProvisionNetworkTaskService.ProvisionNetworkTaskState.class,
                            IllegalArgumentException.class);

        }
    }

    /**
     * This class implements tests for the
     * {@link ProvisionNetworkTaskService#handlePatch} method.
     */
    public static class HandlePatchTest extends BaseModelTest {

        @Override
        protected void startRequiredServices() throws Throwable {
            ProvisionNetworkTaskServiceTest.startFactoryServices(this);
            super.startRequiredServices();
        }

        @Test
        public void testCreateNetworkSuccess() throws Throwable {

            ProvisionNetworkTaskService.ProvisionNetworkTaskState startState = buildValidStartState(
                    this, NetworkInstanceRequest.InstanceRequestType.CREATE,
                    true);

            ProvisionNetworkTaskService.ProvisionNetworkTaskState completeState = postAndWaitForService(
                    this, startState);

            assertThat(completeState.taskInfo.stage,
                    is(TaskState.TaskStage.FINISHED));
        }

        @Test
        public void testDeleteNetworkSuccess() throws Throwable {
            ProvisionNetworkTaskService.ProvisionNetworkTaskState startState = buildValidStartState(
                    this, NetworkInstanceRequest.InstanceRequestType.DELETE,
                    true);

            ProvisionNetworkTaskService.ProvisionNetworkTaskState completeState = postAndWaitForService(
                    this, startState);

            assertThat(completeState.taskInfo.stage,
                    is(TaskState.TaskStage.FINISHED));
        }

        @Test
        public void testCreateNetworkServiceAdapterFailure() throws Throwable {
            ProvisionNetworkTaskService.ProvisionNetworkTaskState startState = buildValidStartState(
                    this, NetworkInstanceRequest.InstanceRequestType.CREATE,
                    false);

            ProvisionNetworkTaskService.ProvisionNetworkTaskState completeState = postAndWaitForService(
                    this, startState);

            assertThat(completeState.taskInfo.stage,
                    is(TaskState.TaskStage.FAILED));
        }

    }
}
