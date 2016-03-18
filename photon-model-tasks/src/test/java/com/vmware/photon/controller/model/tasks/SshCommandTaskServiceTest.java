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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import org.junit.runners.Suite.SuiteClasses;
import org.junit.runners.model.InitializationError;
import org.junit.runners.model.RunnerBuilder;

import com.vmware.photon.controller.model.helpers.BaseModelTest;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.services.common.AuthCredentialsService;
import com.vmware.xenon.services.common.AuthCredentialsService.AuthCredentialsServiceState;

/**
 * This class implements tests for the {@link SshCommandTaskService} class.
 */
@RunWith(SshCommandTaskServiceTest.class)
@SuiteClasses({ SshCommandTaskServiceTest.ConstructorTest.class,
        SshCommandTaskServiceTest.HandleStartTest.class,
        SshCommandTaskServiceTest.EndToEndTest.class,
        SshCommandTaskServiceTest.ManualTest.class })
public class SshCommandTaskServiceTest extends Suite {

    public SshCommandTaskServiceTest(Class<?> klass, RunnerBuilder builder)
            throws InitializationError {
        super(klass, builder);
    }

    private static void startFactoryServices(BaseModelTest test) throws Throwable {
        TaskServices.startFactories(test);
        MockAdapter.startFactories(test);
    }

    private static String createAuth(BaseModelTest test, String username,
            String privateKey) throws Throwable {
        AuthCredentialsServiceState startState = new AuthCredentialsServiceState();
        startState.userEmail = username;
        startState.privateKey = privateKey;
        AuthCredentialsServiceState returnState = test
                .postServiceSynchronously(
                        AuthCredentialsService.FACTORY_LINK, startState,
                        AuthCredentialsServiceState.class);

        return returnState.documentSelfLink;
    }

    /**
     * This class implements tests for the constructor.
     */
    public static class ConstructorTest {

        private SshCommandTaskService provisionComputeTaskService;

        @Before
        public void setUpTest() {
            this.provisionComputeTaskService = new SshCommandTaskService(null);
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
        protected void startRequiredServices() throws Throwable {
            SshCommandTaskServiceTest.startFactoryServices(this);
            super.startRequiredServices();
        }

        @Test
        public void testNoHost() throws Throwable {
            SshCommandTaskService.SshCommandTaskState startState = new SshCommandTaskService.SshCommandTaskState();
            startState.isMockRequest = true;
            startState.host = null;
            startState.authCredentialLink = "authLink";
            startState.commands = new ArrayList<>();
            startState.commands.add("ls");

            this.postServiceSynchronously(
                    SshCommandTaskService.FACTORY_LINK, startState,
                    SshCommandTaskService.SshCommandTaskState.class,
                    IllegalArgumentException.class);
        }

        @Test
        public void testNoAuthLink() throws Throwable {
            SshCommandTaskService.SshCommandTaskState startState = new SshCommandTaskService.SshCommandTaskState();
            startState.isMockRequest = true;
            startState.host = "localhost";
            startState.authCredentialLink = null;
            startState.commands = new ArrayList<>();
            startState.commands.add("ls");

            this.postServiceSynchronously(
                    SshCommandTaskService.FACTORY_LINK, startState,
                    SshCommandTaskService.SshCommandTaskState.class,
                    IllegalArgumentException.class);
        }

        @Test
        public void testNoCommands() throws Throwable {
            SshCommandTaskService.SshCommandTaskState startState = new SshCommandTaskService.SshCommandTaskState();
            startState.isMockRequest = true;
            startState.host = "localhost";
            startState.authCredentialLink = "authLink";
            startState.commands = null;

            this.postServiceSynchronously(
                    SshCommandTaskService.FACTORY_LINK, startState,
                    SshCommandTaskService.SshCommandTaskState.class,
                    IllegalArgumentException.class);
        }

        @Test
        public void testBadStage() throws Throwable {
            SshCommandTaskService.SshCommandTaskState startState = new SshCommandTaskService.SshCommandTaskState();
            startState.isMockRequest = true;
            startState.host = "localhost";
            startState.authCredentialLink = "authLink";
            startState.commands = new ArrayList<>();
            startState.commands.add("ls");
            startState.taskInfo = new TaskState();
            startState.taskInfo.stage = TaskState.TaskStage.STARTED;

            this.postServiceSynchronously(
                    SshCommandTaskService.FACTORY_LINK, startState,
                    SshCommandTaskService.SshCommandTaskState.class,
                    IllegalStateException.class);
        }
    }

    /**
     * This class implements end-to-end tests.
     */
    public static class EndToEndTest extends BaseModelTest {
        @Override
        protected void startRequiredServices() throws Throwable {
            SshCommandTaskServiceTest.startFactoryServices(this);
            super.startRequiredServices();
        }

        @Test
        public void testSuccess() throws Throwable {
            String authLink = createAuth(this, "username", "privatekey");

            SshCommandTaskService.SshCommandTaskState startState = new SshCommandTaskService.SshCommandTaskState();
            startState.isMockRequest = true;
            startState.host = "localhost";
            startState.authCredentialLink = authLink;
            startState.commands = new ArrayList<>();
            startState.commands.add("ls");
            startState.commands.add("pwd");

            SshCommandTaskService.SshCommandTaskState returnState = postServiceSynchronously(
                    SshCommandTaskService.FACTORY_LINK, startState,
                    SshCommandTaskService.SshCommandTaskState.class);

            SshCommandTaskService.SshCommandTaskState completeState = waitForServiceState(
                    SshCommandTaskService.SshCommandTaskState.class,
                    returnState.documentSelfLink,
                    state -> TaskState.TaskStage.FINISHED.ordinal() <= state.taskInfo.stage
                            .ordinal());

            assertThat(completeState.taskInfo.stage,
                    is(TaskState.TaskStage.FINISHED));
            for (String cmd : startState.commands) {
                assertThat(completeState.commandResponse.get(cmd), is(cmd));
            }
        }

        @Test
        public void testBadAuthLink() throws Throwable {
            SshCommandTaskService.SshCommandTaskState startState = new SshCommandTaskService.SshCommandTaskState();
            startState.isMockRequest = true;
            startState.host = "localhost";
            startState.authCredentialLink = "http://localhost/badAuthLink";
            startState.commands = new ArrayList<>();
            startState.commands.add("ls");
            startState.commands.add("pwd");

            SshCommandTaskService.SshCommandTaskState returnState = postServiceSynchronously(
                    SshCommandTaskService.FACTORY_LINK, startState,
                    SshCommandTaskService.SshCommandTaskState.class);

            SshCommandTaskService.SshCommandTaskState completeState = waitForServiceState(
                    SshCommandTaskService.SshCommandTaskState.class,
                    returnState.documentSelfLink,
                    state -> TaskState.TaskStage.FINISHED.ordinal() <= state.taskInfo.stage
                            .ordinal());

            assertThat(completeState.taskInfo.stage,
                    is(TaskState.TaskStage.FAILED));
        }
    }

    /**
     * This class implements example tests against real ssh server.
     */
    @Ignore
    public static class ManualTest extends BaseModelTest {
        private final String hostname = "0.0.0.0";
        private final String username = "ec2-user";
        private final String privateKey = "-----BEGIN RSA PRIVATE KEY-----\n"
                + "\n-----END RSA PRIVATE KEY-----";

        @Override
        protected void startRequiredServices() throws Throwable {
            SshCommandTaskServiceTest.startFactoryServices(this);
            super.startRequiredServices();
        }

        /**
         * An example for testing against a real ssh server.
         */
        @Test
        public void testSuccess() throws Throwable {

            String authLink = createAuth(this, this.username,
                    this.privateKey);

            SshCommandTaskService.SshCommandTaskState startState = new SshCommandTaskService.SshCommandTaskState();
            startState.host = this.hostname;
            startState.authCredentialLink = authLink;
            startState.commands = new ArrayList<>();
            startState.commands.add("ls");
            startState.commands.add("pwd");

            SshCommandTaskService.SshCommandTaskState returnState = this
                    .postServiceSynchronously(
                            SshCommandTaskService.FACTORY_LINK, startState,
                            SshCommandTaskService.SshCommandTaskState.class);

            SshCommandTaskService.SshCommandTaskState completeState = this
                    .waitForServiceState(
                            SshCommandTaskService.SshCommandTaskState.class,
                            returnState.documentSelfLink,
                            state -> TaskState.TaskStage.FINISHED.ordinal() <= state.taskInfo.stage
                                    .ordinal());

            assertThat(completeState.taskInfo.stage,
                    is(TaskState.TaskStage.FINISHED));
        }

        @Test
        public void testFailedCommand() throws Throwable {

            String authLink = createAuth(this, this.username,
                    this.privateKey);

            SshCommandTaskService.SshCommandTaskState startState = new SshCommandTaskService.SshCommandTaskState();
            startState.host = this.hostname;
            startState.authCredentialLink = authLink;
            startState.commands = new ArrayList<>();
            startState.commands.add("test"); // this command fails (return
                                             // non-zero)
            startState.commands.add("pwd");

            SshCommandTaskService.SshCommandTaskState returnState = this
                    .postServiceSynchronously(
                            SshCommandTaskService.FACTORY_LINK, startState,
                            SshCommandTaskService.SshCommandTaskState.class);

            SshCommandTaskService.SshCommandTaskState completeState = this
                    .waitForServiceState(
                            SshCommandTaskService.SshCommandTaskState.class,
                            returnState.documentSelfLink,
                            state -> TaskState.TaskStage.FINISHED.ordinal() <= state.taskInfo.stage
                                    .ordinal());

            assertThat(completeState.taskInfo.stage,
                    is(TaskState.TaskStage.FAILED));
        }
    }
}
