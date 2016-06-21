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

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import static com.vmware.photon.controller.model.adapters.awsadapter.AWSConstants.AWS_GATEWAY_ID;
import static com.vmware.photon.controller.model.adapters.awsadapter.AWSConstants.AWS_SUBNET_ID;
import static com.vmware.photon.controller.model.adapters.awsadapter.AWSConstants.AWS_VPC_ID;
import static com.vmware.photon.controller.model.adapters.awsadapter.AWSConstants.AWS_VPC_ROUTE_TABLE_ID;
import static com.vmware.photon.controller.model.adapters.awsadapter.TestUtils.getExecutor;

import java.net.URI;
import java.util.Date;
import java.util.concurrent.TimeoutException;

import com.amazonaws.services.ec2.AmazonEC2AsyncClient;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.vmware.photon.controller.model.PhotonModelServices;
import com.vmware.photon.controller.model.adapterapi.NetworkInstanceRequest;
import com.vmware.photon.controller.model.resources.NetworkService.NetworkState;
import com.vmware.photon.controller.model.resources.ResourcePoolService.ResourcePoolState;
import com.vmware.photon.controller.model.tasks.PhotonModelTaskServices;
import com.vmware.photon.controller.model.tasks.ProvisionNetworkTaskService;
import com.vmware.photon.controller.model.tasks.ProvisionNetworkTaskService.ProvisionNetworkTaskState;

import com.vmware.xenon.common.CommandLineArgumentParser;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.common.test.VerificationHost;
import com.vmware.xenon.services.common.AuthCredentialsService.AuthCredentialsServiceState;

public class TestProvisionAWSNetwork {

    /*
    * This test requires the following 3 command line variables.
    * If they are not present the tests will be ignored.
    * Pass them into the test with the -Dxenon.variable=value syntax
    * i.e -Dxenon.subnet="10.1.0.0/16"
    *
    * privateKey & privateKeyId are credentials to an AWS VPC account
    * region is the ec2 region where the tests should be run (us-east-1)
    * subnet is the RFC-1918 subnet of the default VPC
    */
    public String privateKey;
    public String privateKeyId;
    public String region;
    public String subnet;

    private VerificationHost host;
    private URI provisionNetworkFactory;

    @Before
    public void setUp() throws Exception {
        CommandLineArgumentParser.parseFromProperties(this);

        // ignore if any of the required properties are missing
        org.junit.Assume.assumeTrue(TestUtils.isNull(this.privateKey, this.privateKeyId, this.region, this.subnet));

        this.host = VerificationHost.create(0);
        try {
            this.host.start();
            PhotonModelServices.startServices(this.host);
            PhotonModelTaskServices.startServices(this.host);
            // start the aws network service
            this.host.startService(
                    Operation.createPost(UriUtils.buildUri(this.host, AWSNetworkService.class)),
                    new AWSNetworkService());
            this.provisionNetworkFactory = UriUtils.buildUri(this.host,
                    ProvisionNetworkTaskService.FACTORY_LINK);
        } catch (Throwable e) {
            throw new Exception(e);
        }
    }

    @After
    public void tearDown() throws InterruptedException {
        if (this.host == null) {
            return;
        }
        this.host.tearDownInProcessPeers();
        this.host.toggleNegativeTestMode(false);
        this.host.tearDown();
    }

    @Test
    public void testProvisionAWSNetwork() throws Throwable {
        // first create network service
        Operation response = new Operation();
        // create credentials
        Operation credsResponse = new Operation();
        TestUtils.postCredentials(this.host,credsResponse,this.privateKey,this.privateKeyId);
        AuthCredentialsServiceState creds = credsResponse.getBody(AuthCredentialsServiceState.class);
        // create resource pool
        Operation poolResponse = new Operation();
        TestUtils.postResourcePool(this.host,poolResponse);
        ResourcePoolState pool = poolResponse.getBody(ResourcePoolState.class);

        NetworkState initialState = TestUtils.buildNetworkState(this.host);
        initialState.authCredentialsLink = creds.documentSelfLink;
        initialState.resourcePoolLink = pool.documentSelfLink;
        initialState.regionID = this.region;
        initialState.instanceAdapterReference = UriUtils.buildUri(ServiceHost.LOCAL_HOST,
                this.host.getPort(),
                AWSUriPaths.AWS_NETWORK_ADAPTER,
                null);

        TestUtils.postNetwork(this.host, initialState, response);
        NetworkState networkState = response.getBody(NetworkState.class);

        // set up network task state
        ProvisionNetworkTaskState task = new ProvisionNetworkTaskState();
        task.requestType = NetworkInstanceRequest.InstanceRequestType.CREATE;
        task.networkDescriptionLink = networkState.documentSelfLink;
        Operation provision = new Operation();
        provisionNetwork(task, provision);
        ProvisionNetworkTaskState ps = provision.getBody(ProvisionNetworkTaskState.class);
        waitForTaskCompletion(this.host, UriUtils.buildUri(this.host, ps.documentSelfLink));
        validateAWSArtifacts(networkState.documentSelfLink, creds);

        task.requestType = NetworkInstanceRequest.InstanceRequestType.DELETE;
        Operation remove = new Operation();
        provisionNetwork(task,remove);
        ProvisionNetworkTaskState removeTask = remove.getBody(ProvisionNetworkTaskState.class);
        waitForTaskCompletion(this.host, UriUtils.buildUri(this.host, removeTask.documentSelfLink));
        // verify properties have been set to no-value
        NetworkState removedNetwork = getNetworkState(networkState.documentSelfLink);
        assertTrue(removedNetwork.customProperties.get(AWS_VPC_ID)
                .equalsIgnoreCase(AWSUtils.NO_VALUE));
        assertTrue(removedNetwork.customProperties.get(AWS_GATEWAY_ID)
                .equalsIgnoreCase(AWSUtils.NO_VALUE));
        assertTrue(removedNetwork.customProperties.get(AWS_SUBNET_ID)
                .equalsIgnoreCase(AWSUtils.NO_VALUE));
        assertTrue(removedNetwork.customProperties.get(AWS_VPC_ROUTE_TABLE_ID)
                .equalsIgnoreCase(AWSUtils.NO_VALUE));
    }


    @Test
    public void testInvalidProvisionAWSNetwork() throws Throwable {
        // first create network service
        Operation response = new Operation();
        // create credentials
        Operation authResponse = new Operation();
        TestUtils.postCredentials(this.host,authResponse,this.privateKey,"invalid");
        AuthCredentialsServiceState creds = authResponse.getBody(AuthCredentialsServiceState.class);
        // create resource pool
        Operation poolResponse = new Operation();
        TestUtils.postResourcePool(this.host,poolResponse);
        ResourcePoolState pool = poolResponse.getBody(ResourcePoolState.class);

        NetworkState initialState = TestUtils.buildNetworkState(this.host);
        initialState.authCredentialsLink = creds.documentSelfLink;
        initialState.resourcePoolLink = pool.documentSelfLink;
        initialState.regionID = this.region;
        initialState.instanceAdapterReference = UriUtils.buildUri(ServiceHost.LOCAL_HOST,
                this.host.getPort(),
                AWSUriPaths.AWS_NETWORK_ADAPTER,
                null);

        TestUtils.postNetwork(this.host, initialState, response);
        NetworkState networkState = response.getBody(NetworkState.class);

        // set up network task state
        ProvisionNetworkTaskState task = new ProvisionNetworkTaskState();
        task.requestType = NetworkInstanceRequest.InstanceRequestType.CREATE;
        task.networkDescriptionLink = networkState.documentSelfLink;
        Operation provision = new Operation();
        provisionNetwork(task, provision);
        ProvisionNetworkTaskState ps = provision.getBody(ProvisionNetworkTaskState.class);
        waitForTaskFailure(this.host, UriUtils.buildUri(this.host, ps.documentSelfLink));


    }

    private void validateAWSArtifacts(String networkDescriptionLink, AuthCredentialsServiceState creds) throws Throwable {

        NetworkState net = getNetworkState(networkDescriptionLink);

        AWSNetworkService netSVC = new AWSNetworkService();
        AmazonEC2AsyncClient client = AWSUtils.getAsyncClient(creds, this.region, false,
                getExecutor());
        // if any artifact is not present then an error will be thrown
        assertNotNull(
                netSVC.getVPC(net.customProperties.get(AWS_VPC_ID), client));
        assertNotNull(netSVC.getInternetGateway(net.customProperties.get(AWS_GATEWAY_ID), client));
        assertNotNull(netSVC.getSubnet(net.customProperties.get(AWS_SUBNET_ID), client));
    }

    private NetworkState getNetworkState(String networkLink) throws Throwable {
        Operation response = new Operation();
        TestUtils.getNetworkState(this.host,networkLink,response);
        return response.getBody(NetworkState.class);
    }

    private void provisionNetwork(ProvisionNetworkTaskState ps, Operation response) throws Throwable {
        this.host.testStart(1);
        Operation startPost = Operation.createPost(this.provisionNetworkFactory)
                .setBody(ps)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        this.host.failIteration(e);
                        return;
                    }
                    response.setBody(o.getBody(ProvisionNetworkTaskState.class));
                    this.host.completeIteration();
                });
        this.host.send(startPost);
        this.host.testWait();

    }

    private void waitForTaskCompletion(VerificationHost host, URI provisioningTaskUri)
            throws Throwable {

        Date expiration = host.getTestExpiration();

        ProvisionNetworkTaskState provisioningTask;

        do {
            provisioningTask = host.getServiceState(null,
                    ProvisionNetworkTaskState.class,
                    provisioningTaskUri);

            if (provisioningTask.taskInfo.stage == TaskState.TaskStage.FAILED) {
                throw new IllegalStateException("Task failed:" + Utils.toJsonHtml(provisioningTask));
            }

            if (provisioningTask.taskInfo.stage == TaskState.TaskStage.FINISHED) {
                return;
            }

            Thread.sleep(1000);
        } while (new Date().before(expiration));

        host.log("Pending task:\n%s", Utils.toJsonHtml(provisioningTask));

        throw new TimeoutException("Some tasks never finished");
    }

    private void waitForTaskFailure(VerificationHost host, URI provisioningTaskUri)
            throws Throwable {

        Date expiration = host.getTestExpiration();

        ProvisionNetworkTaskState provisioningTask;

        do {
            provisioningTask = host.getServiceState(null,
                    ProvisionNetworkTaskState.class,
                    provisioningTaskUri);

            if (provisioningTask.taskInfo.stage == TaskState.TaskStage.FAILED) {
                return;
            }

            Thread.sleep(1000);
        } while (new Date().before(expiration));

        host.log("Pending task:\n%s", Utils.toJsonHtml(provisioningTask));

        throw new TimeoutException("Some tasks never finished");
    }

}
