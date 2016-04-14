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

package com.vmware.photon.controller.model.adapters.awsadapter;

import static com.vmware.photon.controller.model.adapters.awsadapter.TestUtils.getExecutor;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;

import com.amazonaws.handlers.AsyncHandler;
import com.amazonaws.services.ec2.AmazonEC2AsyncClient;
import com.amazonaws.services.ec2.model.DescribeInstancesRequest;
import com.amazonaws.services.ec2.model.DescribeInstancesResult;
import com.amazonaws.services.ec2.model.Instance;
import com.amazonaws.services.ec2.model.RunInstancesRequest;
import com.amazonaws.services.ec2.model.RunInstancesResult;
import com.amazonaws.services.ec2.model.TerminateInstancesRequest;
import com.amazonaws.services.ec2.model.TerminateInstancesResult;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.vmware.photon.controller.model.adapterapi.EnumerationAction;
import com.vmware.photon.controller.model.adapters.awsadapter.enumeration.AWSEnumerationService;
import com.vmware.photon.controller.model.resources.ComputeService;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.resources.ResourcePoolService.ResourcePoolState;
import com.vmware.photon.controller.model.tasks.ProvisionComputeTaskService;
import com.vmware.photon.controller.model.tasks.ProvisionComputeTaskService.ProvisionComputeTaskState;
import com.vmware.photon.controller.model.tasks.ProvisioningUtils;
import com.vmware.photon.controller.model.tasks.ResourceEnumerationTaskService;
import com.vmware.photon.controller.model.tasks.ResourceEnumerationTaskService.ResourceEnumerationTaskState;
import com.vmware.photon.controller.model.tasks.TestUtils;

import com.vmware.xenon.common.BasicReusableHostTestCase;
import com.vmware.xenon.common.CommandLineArgumentParser;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.common.test.VerificationHost;
import com.vmware.xenon.services.common.AuthCredentialsService.AuthCredentialsServiceState;

/**
 * Test to enumerate instances on AWS and tear it down. The test creates VM using the Provisioning task as well as
 * directly creating instances on AWS using the EC2 client.It then invokes the AWS enumeration adapter to enumerate
 * all the resources on the AWS edpoint and validates that all the updates to the local state are as expected.If the 'isMock'
 * flag is set to true the test runs the adapter in mock mode and does not actually create a VM.
 * Minimally the accessKey and secretKey for AWS must be specified must be provided in the SetupUtils class to run the test.
 *
 */
public class TestAWSEnumerationTask extends BasicReusableHostTestCase {
    public ComputeService.ComputeState vmState;
    public ResourcePoolState outPool;
    public ComputeService.ComputeState outComputeHost;
    public AuthCredentialsServiceState creds;
    public static final String EC2_IMAGEID = "ami-0d4cfd66";
    public static final String T2_NANO_INSTANCE_TYPE = "t2.nano";
    public static final String DEFAULT_SECURITY_GROUP_NAME = "cell-manager-security-group";
    public static List<String> instancesToCleanUp = new ArrayList<String>();
    public static List<String> instanceIds = new ArrayList<String>();
    public List<String> instanceIdsToDelete = new ArrayList<String>();
    public AmazonEC2AsyncClient client;
    public static List<Boolean> provisioningFlags;
    public static List<Boolean> deletionFlags = new ArrayList<Boolean>();
    public boolean isMock = true;
    public String accessKey = "accessKey";
    public String secretKey = "secretKey";

    @Before
    public void setUp() throws Exception {
        CommandLineArgumentParser.parseFromProperties(this);
        // create credentials
        creds = new AuthCredentialsServiceState();
        creds.privateKey = secretKey;
        creds.privateKeyId = accessKey;
        client = AWSUtils.getAsyncClient(creds, TestAWSSetupUtils.zoneId,
                isMock, getExecutor());
        List<String> serviceSelfLinks = new ArrayList<String>();
        try {
            ProvisioningUtils.startProvisioningServices(this.host);
            host.setTimeoutSeconds(600);
            host.startService(
                    Operation.createPost(UriUtils.buildUri(host,
                            AWSInstanceService.class)),
                    new AWSInstanceService());
            serviceSelfLinks.add(AWSInstanceService.SELF_LINK);

            host.startService(
                    Operation.createPost(UriUtils.buildUri(host,
                            AWSEnumerationService.class)),
                    new AWSEnumerationService());
            serviceSelfLinks.add(AWSEnumerationService.SELF_LINK);

            host.startService(
                    Operation.createPost(UriUtils.buildUri(host,
                            AWSStatsService.class)),
                    new AWSStatsService());
            serviceSelfLinks.add(AWSStatsService.SELF_LINK);

            ProvisioningUtils.waitForServiceStart(host, serviceSelfLinks.toArray(new String[] {}));
        } catch (Throwable e) {
            host.log("Error starting up services for the test %s", e.getMessage());
            throw new Exception(e);
        }
    }

    @After
    public void tearDown() throws InterruptedException {
        if (host == null) {
            return;
        }
        // try to delete the VMs
        if (vmState != null) {
            try {
                // Delete all vms from the endpoint
                TestAWSSetupUtils.deleteAllVMsOnThisEndpoint(host, isMock,
                        outComputeHost.documentSelfLink, instancesToCleanUp);
                ProvisioningUtils.queryComputeInstances(this.host, 1);

                if (client != null) {
                    client.shutdown();
                    client = null;
                }
            } catch (Throwable deleteEx) {
                // just log and move on
                host.log(Level.WARNING, "Exception deleting VMs - %s", deleteEx.getMessage());
            }
        }
    }

    // Runs the enumeration task on the AWS endpoint to list all the instances on the endpoint.
    @Test
    public void testEnumeration() throws Throwable {
        if (!isMock) {
            host.setTimeoutSeconds(600);
            // Provision a single VM . Check initial state.
            provisionSingleVMUsingInstanceService();
            ProvisioningUtils.queryComputeInstances(this.host, 2);
            ProvisioningUtils.queryComputeDescriptions(this.host, 2);

            // CREATION directly on AWS
            provisionAWSVMWithEC2Client(2, T2_NANO_INSTANCE_TYPE);

            // Xenon does not know about the new instances.
            ProvisioningUtils.queryComputeInstances(this.host, 2);

            enumerateResources();
            // 2 new resources should be discovered.
            ProvisioningUtils.queryComputeDescriptions(this.host, 3);
            ProvisioningUtils.queryComputeInstances(this.host, 4);

            // Provision an additional VM that has a compute description already present in the
            // system.
            provisionAWSVMWithEC2Client(1, TestAWSSetupUtils.instanceType_t2_micro);
            enumerateResources();
            // One additional compute state and no new compute descriptions should be created.
            ProvisioningUtils.queryComputeDescriptions(this.host, 3);
            ProvisioningUtils.queryComputeInstances(this.host, 5);
        } else {
            // Create basic state for kicking off enumeration
            createResourcePoolComputeHostAndVMState();
            // Just make a call to the enumeration service and make sure that the adapter patches
            // the parent with completion.
            enumerateResources();
        }
    }

    /**
     * Enumerates resources on the AWS endpoint.
     * @throws Throwable
     * @throws InterruptedException
     * @throws TimeoutException
     */
    private void enumerateResources() throws Throwable, InterruptedException, TimeoutException {
        // Perform resource enumeration on the AWS end point. Pass the references to the AWS compute
        // host.
        host.log("Performing resource enumeration");
        ResourceEnumerationTaskService.ResourceEnumerationTaskState enumTask = performResourceEnumeration(
                outPool.documentSelfLink, outComputeHost.descriptionLink,
                outComputeHost.documentSelfLink);
        // Wait for the enumeration task to be completed.
        this.host.waitFor("Error waiting for enumeration task", () -> {
            return checkEnumerationTaskCompletion(enumTask);
        });
    }

    /**
     * Checks is the Enumeration task state is FINISHED and throws an error if the
     * enumeration task has failed.
     * @param enumTask
     * @return boolean indicating the completion of the enumeration task.
     * @throws Throwable
     */
    private boolean checkEnumerationTaskCompletion(
            ResourceEnumerationTaskService.ResourceEnumerationTaskState enumTask) throws Throwable {
        URI[] enumerationUris = { UriUtils.buildUri(this.host, enumTask.documentSelfLink) };
        Map<URI, ResourceEnumerationTaskService.ResourceEnumerationTaskState> enumTasks = this.host
                .getServiceState(null,
                        ResourceEnumerationTaskService.ResourceEnumerationTaskState.class,
                        enumerationUris);
        boolean endLoop = true;
        for (Entry<URI, ResourceEnumerationTaskService.ResourceEnumerationTaskState> e : enumTasks
                .entrySet()) {
            ResourceEnumerationTaskService.ResourceEnumerationTaskState currentState = e
                    .getValue();
            if (currentState.taskInfo.stage == TaskState.TaskStage.FAILED) {
                throw new IllegalStateException(
                        "Task failed:" + Utils.toJsonHtml(currentState));
            }
            if (currentState.taskInfo.stage != TaskState.TaskStage.FINISHED) {
                // this will record the state for all the tasks. Only exit when all the enumeration
                // tasks have finished.
                endLoop = endLoop & false;
            }
        }
        return endLoop;
    }

    /**
     * Provisions a single VM on AWS using the AWS instance service.
     * @throws Throwable
     */
    private void provisionSingleVMUsingInstanceService() throws Throwable {
        createResourcePoolComputeHostAndVMState();
        provisionMachine();
    }

    /**
     * Creates the state associated with the resource pool, compute host and the VM to be created.
     * @throws Throwable
     */
    private void createResourcePoolComputeHostAndVMState() throws Throwable {
        // Create a resource pool where the VM will be housed
        outPool = TestAWSSetupUtils.createAWSResourcePool(this.host);

        // create a compute host for the AWS EC2 VM
        outComputeHost = TestAWSSetupUtils.createAWSComputeHost(this.host,
                outPool.documentSelfLink, accessKey, secretKey);

        // create a AWS VM compute resource
        vmState = TestAWSSetupUtils.createAWSVMResource(this.host, outComputeHost.documentSelfLink,
                outPool.documentSelfLink, this.getClass());
    }

    /**
     * Provisions a machine for which the state was created.
     * @param provisionTask
     * @throws Throwable
     * @throws InterruptedException
     * @throws TimeoutException
     */
    private void provisionMachine()
            throws Throwable, InterruptedException, TimeoutException {
        // kick off a provision task to do the actual VM creation
        ComputeState computeStateToCleanup = null;
        ProvisionComputeTaskState provisionTask = new ProvisionComputeTaskService.ProvisionComputeTaskState();

        provisionTask.computeLink = vmState.documentSelfLink;
        provisionTask.isMockRequest = isMock;
        provisionTask.taskSubStage = ProvisionComputeTaskState.SubStage.CREATING_HOST;
        ProvisionComputeTaskService.ProvisionComputeTaskState outTask = TestUtils.doPost(this.host,
                provisionTask,
                ProvisionComputeTaskState.class,
                UriUtils.buildUri(this.host,
                        ProvisionComputeTaskService.FACTORY_LINK));

        List<URI> uris = new ArrayList<URI>();
        uris.add(UriUtils.buildUri(this.host, outTask.documentSelfLink));
        ProvisioningUtils.waitForTaskCompletion(this.host, uris, ProvisionComputeTaskState.class);
        host.log("Sucessflly provisioned a machine %s ", vmState.id);
        // check that the VM has been created
        ProvisioningUtils.queryComputeInstances(this.host, 2);
        // Get instance Id of the provisioned machine and save that for cleanup
        URI[] computeURIs = { UriUtils.buildUri(this.host, vmState.documentSelfLink) };
        Map<URI, ComputeState> computeStateMap = host.getServiceState(null,
                ComputeState.class, computeURIs);
        if (computeStateMap != null) {
            computeStateToCleanup = computeStateMap.get(computeURIs[0]);
            if (computeStateToCleanup != null && computeStateToCleanup.customProperties != null) {
                instancesToCleanUp
                        .add(computeStateToCleanup.customProperties
                                .get(AWSConstants.AWS_INSTANCE_ID));
            }
        }

    }

    /**
     * Method to perform compute resource enumeration on the AWS endpoint.
     * @param resourcePoolLink The link to the AWS resource pool.
     * @param computeDescriptionLink  The link to the compute description for the AWS host.
     * @param parentComputeLink The compute state associated with the AWS host.
     * @return
     * @throws Throwable
     * @throws InterruptedException
     * @throws TimeoutException
     */
    private ResourceEnumerationTaskService.ResourceEnumerationTaskState performResourceEnumeration(
            String resourcePoolLink, String computeDescriptionLink, String parentComputeLink)
            throws Throwable, InterruptedException, TimeoutException {
        // Kick of a Resource Enumeration task to enumerate the instances on the AWS endpoint
        ResourceEnumerationTaskState enumerationTaskState = new ResourceEnumerationTaskService.ResourceEnumerationTaskState();

        enumerationTaskState.computeDescriptionLink = computeDescriptionLink;
        enumerationTaskState.parentComputeLink = parentComputeLink;
        enumerationTaskState.enumerationAction = EnumerationAction.REFRESH;
        enumerationTaskState.adapterManagementReference = UriUtils
                .buildUri(AWSEnumerationService.SELF_LINK);
        enumerationTaskState.resourcePoolLink = resourcePoolLink;
        enumerationTaskState.isMockRequest = isMock;

        ResourceEnumerationTaskService.ResourceEnumerationTaskState enumTask = TestUtils.doPost(
                this.host,
                enumerationTaskState,
                ResourceEnumerationTaskState.class,
                UriUtils.buildUri(this.host,
                        ResourceEnumerationTaskService.FACTORY_LINK));
        return enumTask;

    }

    /**
     * Method to directly provision instances on the AWS endpoint without the knowledge of the local system. This is to test
     * that the discovery of items not provisioned by Xenon happens correctly.
     * @throws Throwable
     */
    private void provisionAWSVMWithEC2Client(int numberOfInstance, String instanceType)
            throws Throwable {
        provisioningFlags = new ArrayList<Boolean>(numberOfInstance);
        for (int i = 0; i < numberOfInstance; i++) {
            provisioningFlags.add(i, Boolean.FALSE);
        }
        RunInstancesRequest runInstancesRequest = new RunInstancesRequest()
                .withImageId(EC2_IMAGEID).withInstanceType(instanceType)
                .withMinCount(numberOfInstance).withMaxCount(numberOfInstance)
                .withMonitoring(true)
                .withSecurityGroupIds(DEFAULT_SECURITY_GROUP_NAME);

        // handler invoked once the EC2 runInstancesAsync commands completes
        AsyncHandler<RunInstancesRequest, RunInstancesResult> creationHandler = new AWSRunInstancesAsyncHandler(
                host);
        client.runInstancesAsync(runInstancesRequest, creationHandler);

        // Wait for the machine provisioning to be completed.Looping through as sometimes AWs does
        // not spin off instances immediately.
        this.host.waitFor("Error waiting for EC2 client provisioning in test ", () -> {
            return computeInstancesStartedState();
        });
    }

    private static class AWSRunInstancesAsyncHandler implements
            AsyncHandler<RunInstancesRequest, RunInstancesResult> {

        VerificationHost host;

        AWSRunInstancesAsyncHandler(VerificationHost host) {
            this.host = host;
        }

        @Override
        public void onError(Exception exception) {
            host.log("Error creating instance{s} on AWS endpoint %s", exception);
        }

        @Override
        public void onSuccess(RunInstancesRequest request, RunInstancesResult result) {
            int numberOfInstances = result.getReservation().getInstances().size();
            instanceIds = new ArrayList<String>(numberOfInstances);
            for (Instance i : result.getReservation().getInstances()) {
                instanceIds.add(i.getInstanceId());
                host.log("Successfully created instances on AWS endpoint %s", i.getInstanceId());
            }
            instancesToCleanUp.addAll(instanceIds);
        }
    }

    /**
     * Deletes instances on the AWS endpoint for the set of instance Ids that are passed in.
     * @param instanceIdsToDelete
     */
    private void deleteVMsUsingEC2Client(List<String> instanceIdsToDelete) throws Throwable {
        TerminateInstancesRequest termRequest = new TerminateInstancesRequest(
                instanceIdsToDelete);
        AsyncHandler<TerminateInstancesRequest, TerminateInstancesResult> terminateHandler = new AWSTerminateHandler(
                this.host);
        client.terminateInstancesAsync(termRequest,
                terminateHandler);
        for (int i = 0; i < instanceIdsToDelete.size(); i++) {
            deletionFlags.add(i, Boolean.FALSE);
        }
        this.host.waitFor("Error waiting for EC2 client delete instances in test ", () -> {
            return computeInstancesStopState();
        });

    }

    /**
     * Async handler for the deletions of instances from the AWS endpoint.
     *
     */
    private class AWSTerminateHandler implements
            AsyncHandler<TerminateInstancesRequest, TerminateInstancesResult> {

        VerificationHost host;

        AWSTerminateHandler(VerificationHost host) {
            this.host = host;
        }

        @Override
        public void onError(Exception exception) {
            host.log("Error deleting instance{s} from AWS %s", exception);
        }

        @Override
        public void onSuccess(TerminateInstancesRequest request,
                TerminateInstancesResult result) {
            host.log("Successfully deleted instances from the AWS endpoint %s",
                    result.getTerminatingInstances().toString());
        }
    }

    /**
     * Method that polls to see if the instances provisioned have turned ON. This is similar to the
     * Provisioning utils wait method.This is ensure deterministic behavior about instances discovery
     * even if the AWS APIs are taking longer to provision resources.
     * @return boolean if the required instances have been turned ON on AWS.
     */
    private boolean computeInstancesStartedState() {
        // If there are no instanceIds set then return false
        if (instanceIds.size() == 0) {
            return false;
        }
        // Calls the describe instances API to get the latest state of each machine being
        // provisioned.
        checkInstancesStarted();
        Boolean finalState = true;
        for (Boolean b : provisioningFlags) {
            finalState = finalState & b;
        }
        if (finalState) {
            instanceIds.clear();
        }
        return finalState;
    }

    /**
     * Method that polls to see if the instances provisioned have turned OFF on the AWS endpoint.
     */
    private boolean computeInstancesStopState() {
        checkInstancesDeleted();
        Boolean finalState = true;
        for (Boolean b : deletionFlags) {
            finalState = finalState & b;
        }
        return finalState;
    }

    /**
     * Checks if a newly provisioned instance has been turned ON
     * @return
     */
    private void checkInstancesStarted() {
        AWSEnumerationAsyncHandler enumerationHandler = new AWSEnumerationAsyncHandler(this.host,
                true);
        DescribeInstancesRequest request = new DescribeInstancesRequest()
                .withInstanceIds(instanceIds);
        client.describeInstancesAsync(request, enumerationHandler);
    }

    /**
     * Checks if a newly deleted instance has its status set to terminated.
     * @return
     */
    private void checkInstancesDeleted() {
        AWSEnumerationAsyncHandler enumerationHandler = new AWSEnumerationAsyncHandler(this.host,
                false);
        DescribeInstancesRequest request = new DescribeInstancesRequest()
                .withInstanceIds(instanceIdsToDelete);
        client.describeInstancesAsync(request, enumerationHandler);
    }

    /**
     * Handler to get the state of a provisioned machine and to ensure that it is powered ON
     *
     */
    private static class AWSEnumerationAsyncHandler implements
            AsyncHandler<DescribeInstancesRequest, DescribeInstancesResult> {

        private static final int AWS_TERMINATED_CODE = 48;
        private static final int AWS_STARTED_CODE = 16;
        VerificationHost host;
        // Flag to indicate whether you want to check if instance has started or stopped.
        boolean checkStart;

        AWSEnumerationAsyncHandler(VerificationHost host, boolean checkStart) {
            this.host = host;
            this.checkStart = checkStart;
        }

        @Override
        public void onError(Exception exception) {
            host.log("The exception encounterd is %s", exception);
        }

        @Override
        public void onSuccess(DescribeInstancesRequest request,
                DescribeInstancesResult result) {
            int counter = 0;
            if (checkStart) {
                for (Instance i : result.getReservations().get(0).getInstances()) {
                    if (i.getState().getCode() == AWS_STARTED_CODE) {
                        provisioningFlags.set(counter, Boolean.TRUE);
                        counter++;
                    }
                }
            } else { // checking that the instance has been turned OFF
                for (Instance i : result.getReservations().get(0).getInstances()) {
                    if (i.getState().getCode() == AWS_TERMINATED_CODE) {
                        host.log("Instance has stopped %d", counter);
                        deletionFlags.set(counter, Boolean.TRUE);
                        counter++;
                    }
                }
            }
        }
    }

}