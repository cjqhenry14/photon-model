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

import com.vmware.photon.controller.model.adapterapi.FirewallInstanceRequest;
import com.vmware.photon.controller.model.helpers.BaseModelTest;
import com.vmware.photon.controller.model.resources.FirewallFactoryService;
import com.vmware.photon.controller.model.resources.FirewallService;
import com.vmware.photon.controller.model.resources.FirewallService.FirewallState;

import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.UriUtils;

/**
 * This class implements tests for the {@link ProvisionFirewallTaskService}
 * class.
 */
@RunWith(ProvisionFirewallTaskServiceTest.class)
@SuiteClasses({ ProvisionFirewallTaskServiceTest.ConstructorTest.class,
        ProvisionFirewallTaskServiceTest.HandleStartTest.class,
        ProvisionFirewallTaskServiceTest.HandlePatchTest.class })
public class ProvisionFirewallTaskServiceTest extends Suite {

    public ProvisionFirewallTaskServiceTest(Class<?> klass,
            RunnerBuilder builder) throws InitializationError {
        super(klass, builder);
    }

    private static ProvisionFirewallTaskService.ProvisionFirewallTaskState buildValidStartState(
            BaseModelTest test,
            FirewallInstanceRequest.InstanceRequestType requestType,
            boolean success) throws Throwable {
        ProvisionFirewallTaskService.ProvisionFirewallTaskState startState = new ProvisionFirewallTaskService.ProvisionFirewallTaskState();

        FirewallState fState = new FirewallState();
        fState.networkDescriptionLink = "http://networkDescriptionLink";
        fState.authCredentialsLink = "authCredentialsLink";
        fState.name = "firewall-name";
        fState.regionID = "regionId";
        fState.resourcePoolLink = "http://resourcePoolLink";
        if (success) {
            fState.instanceAdapterReference = UriUtils.buildUri(test.getHost(),
                    MockAdapter.MockFirewallInstanceSuccessAdapter.SELF_LINK);
        } else {
            fState.instanceAdapterReference = UriUtils.buildUri(test.getHost(),
                    MockAdapter.MockFirewallInstanceFailureAdapter.SELF_LINK);
        }
        fState.id = UUID.randomUUID().toString();
        ArrayList<FirewallService.FirewallState.Allow> rules = new ArrayList<>();
        FirewallService.FirewallState.Allow ssh = new FirewallService.FirewallState.Allow();
        ssh.name = "ssh";
        ssh.protocol = "tcp";
        ssh.ipRange = "0.0.0.0/0";
        ssh.ports = new ArrayList<>();
        ssh.ports.add("22");
        rules.add(ssh);
        fState.ingress = rules;
        fState.egress = rules;
        FirewallState returnState = test.postServiceSynchronously(
                FirewallFactoryService.SELF_LINK, fState, FirewallState.class);
        startState.requestType = requestType;
        startState.firewallDescriptionLink = returnState.documentSelfLink;

        startState.isMockRequest = true;

        return startState;
    }

    private static ProvisionFirewallTaskService.ProvisionFirewallTaskState postAndWaitForService(
            BaseModelTest test,
            ProvisionFirewallTaskService.ProvisionFirewallTaskState startState)
            throws Throwable {
        ProvisionFirewallTaskService.ProvisionFirewallTaskState returnState = test
                .postServiceSynchronously(
                        ProvisionFirewallTaskFactoryService.SELF_LINK,
                        startState,
                        ProvisionFirewallTaskService.ProvisionFirewallTaskState.class);

        ProvisionFirewallTaskService.ProvisionFirewallTaskState completeState = test
                .waitForServiceState(
                        ProvisionFirewallTaskService.ProvisionFirewallTaskState.class,
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
        private ProvisionFirewallTaskService provisionFirewallTaskService;

        @Before
        public void setupTest() {
            this.provisionFirewallTaskService = new ProvisionFirewallTaskService();
        }

        @Test
        public void testServiceOptions() {
            EnumSet<Service.ServiceOption> expected = EnumSet.of(
                    Service.ServiceOption.CONCURRENT_GET_HANDLING,
                    Service.ServiceOption.PERSISTENCE,
                    Service.ServiceOption.REPLICATION,
                    Service.ServiceOption.OWNER_SELECTION);

            assertThat(this.provisionFirewallTaskService.getOptions(),
                    is(expected));
            assertThat(this.provisionFirewallTaskService.getProcessingStage(),
                    is(Service.ProcessingStage.CREATED));
        }
    }

    /**
     * This class implements tests for the
     * {@link ProvisionFirewallTaskService#handleStart} method.
     */
    public static class HandleStartTest extends BaseModelTest {
        @Override
        protected void startRequiredServices() throws Throwable {
            ProvisionFirewallTaskServiceTest.startFactoryServices(this);
            super.startRequiredServices();
        }

        @Test
        public void testValidateProvisionFirewallTaskService() throws Throwable {
            ProvisionFirewallTaskService.ProvisionFirewallTaskState startState = buildValidStartState(
                    this, FirewallInstanceRequest.InstanceRequestType.CREATE,
                    true);
            ProvisionFirewallTaskService.ProvisionFirewallTaskState completeState = postAndWaitForService(
                    this, startState);
            assertThat(completeState.taskInfo.stage,
                    is(TaskState.TaskStage.FINISHED));
        }

        @Test
        public void testMissingValue() throws Throwable {
            ProvisionFirewallTaskService.ProvisionFirewallTaskState invalidRequestType = buildValidStartState(
                    this,
                    FirewallInstanceRequest.InstanceRequestType.CREATE, true);
            ProvisionFirewallTaskService.ProvisionFirewallTaskState invalidFirewallDescriptionLink = buildValidStartState(
                    this,
                    FirewallInstanceRequest.InstanceRequestType.CREATE, true);

            invalidRequestType.requestType = null;
            invalidFirewallDescriptionLink.firewallDescriptionLink = null;

            this.postServiceSynchronously(
                            ProvisionFirewallTaskFactoryService.SELF_LINK,
                            invalidRequestType,
                            ProvisionFirewallTaskService.ProvisionFirewallTaskState.class,
                            IllegalArgumentException.class);
            this.postServiceSynchronously(
                            ProvisionFirewallTaskFactoryService.SELF_LINK,
                            invalidFirewallDescriptionLink,
                            ProvisionFirewallTaskService.ProvisionFirewallTaskState.class,
                            IllegalArgumentException.class);
        }
    }

    /**
     * This class implements tests for the
     * {@link ProvisionFirewallTaskService#handlePatch} method.
     */
    public static class HandlePatchTest extends BaseModelTest {
        @Override
        protected void startRequiredServices() throws Throwable {
            ProvisionFirewallTaskServiceTest.startFactoryServices(this);
            super.startRequiredServices();
        }

        @Test
        public void testCreateFirewallSuccess() throws Throwable {
            ProvisionFirewallTaskService.ProvisionFirewallTaskState startState = buildValidStartState(
                    this, FirewallInstanceRequest.InstanceRequestType.CREATE,
                    true);

            ProvisionFirewallTaskService.ProvisionFirewallTaskState completeState = postAndWaitForService(
                    this, startState);

            assertThat(completeState.taskInfo.stage,
                    is(TaskState.TaskStage.FINISHED));
        }

        @Test
        public void testDeleteFirewallSuccess() throws Throwable {
            ProvisionFirewallTaskService.ProvisionFirewallTaskState startState = buildValidStartState(
                    this, FirewallInstanceRequest.InstanceRequestType.DELETE,
                    true);

            ProvisionFirewallTaskService.ProvisionFirewallTaskState completeState = postAndWaitForService(
                    this, startState);

            assertThat(completeState.taskInfo.stage,
                    is(TaskState.TaskStage.FINISHED));
        }

        @Test
        public void testCreateFirewallServiceAdapterFailure() throws Throwable {
            ProvisionFirewallTaskService.ProvisionFirewallTaskState startState = buildValidStartState(
                    this, FirewallInstanceRequest.InstanceRequestType.CREATE,
                    false);

            ProvisionFirewallTaskService.ProvisionFirewallTaskState completeState = postAndWaitForService(
                    this, startState);
            assertThat(completeState.taskInfo.stage,
                    is(TaskState.TaskStage.FAILED));
        }
    }
}
