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

import java.net.URI;
import java.util.ArrayList;
import java.util.Date;
import java.util.UUID;
import java.util.concurrent.TimeoutException;

import com.amazonaws.services.ec2.AmazonEC2AsyncClient;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.vmware.photon.controller.model.adapterapi.FirewallInstanceRequest;
import com.vmware.photon.controller.model.resources.FirewallService.FirewallState;
import com.vmware.photon.controller.model.resources.FirewallService.FirewallState.Allow;
import com.vmware.photon.controller.model.resources.NetworkService.NetworkState;
import com.vmware.photon.controller.model.resources.ResourcePoolService.ResourcePoolState;
import com.vmware.photon.controller.model.tasks.ProvisionFirewallTaskFactoryService;
import com.vmware.photon.controller.model.tasks.ProvisionFirewallTaskService.ProvisionFirewallTaskState;
import com.vmware.photon.controller.model.tasks.ProvisioningUtils;

import com.vmware.xenon.common.CommandLineArgumentParser;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.common.test.VerificationHost;
import com.vmware.xenon.services.common.AuthCredentialsService.AuthCredentialsServiceState;
import com.vmware.xenon.services.common.TenantFactoryService;


public class TestProvisionAWSFirewall  {

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
    private URI provisionFirewallFactory;


    @Before
    public void setUp() throws Exception {
        CommandLineArgumentParser.parseFromProperties(this);

        // ignore if any of the required properties are missing
        org.junit.Assume.assumeTrue(TestUtils.isNull(this.privateKey, this.privateKeyId, this.region, this.subnet));
        this.host = VerificationHost.create(0);
        try {
            this.host.start();
            ProvisioningUtils.startProvisioningServices(this.host);
            // start the aws fw service
            this.host.startService(
                    Operation.createPost(UriUtils.buildUri(host, AWSFirewallService.class)),
                    new AWSFirewallService());

            this.provisionFirewallFactory = UriUtils.buildUri(this.host,ProvisionFirewallTaskFactoryService.class);
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
    public void testProvisionAWSFirewall() throws Throwable {
        // first create network service
        Operation response = new Operation();
        NetworkState initialState = TestUtils.buildNetworkState(this.host);
        TestUtils.postNetwork(this.host, initialState, response);
        NetworkState networkState = response.getBody(NetworkState.class);

        // create credentials
        Operation authResponse = new Operation();
        TestUtils.postCredentials(this.host,authResponse,this.privateKey,this.privateKeyId);
        AuthCredentialsServiceState creds = authResponse.getBody(AuthCredentialsServiceState.class);

        // create resource pool
        Operation poolResponse = new Operation();
        TestUtils.postResourcePool(this.host,poolResponse);
        ResourcePoolState pool = poolResponse.getBody(ResourcePoolState.class);

        // create fw service
        Operation fwResponse = new Operation();
        FirewallState fwInitialState = buildFirewallState();
        fwInitialState.networkDescriptionLink = networkState.documentSelfLink;
        fwInitialState.ingress = getGlobalSSHRule();
        fwInitialState.egress = getGlobalSSHRule();
        fwInitialState.egress.get(0).ipRange = this.subnet;
        fwInitialState.authCredentialsLink = creds.documentSelfLink;
        fwInitialState.authCredentialsLink = creds.documentSelfLink;
        fwInitialState.resourcePoolLink = pool.documentSelfLink;
        fwInitialState.regionID = this.region;
        fwInitialState.instanceAdapterReference = UriUtils.buildUri(ServiceHost.LOCAL_HOST,
                this.host.getPort(),
                AWSUriPaths.AWS_FIREWALL_SERVICE,
                null);

        TestUtils.postFirewall(this.host,fwInitialState,fwResponse);
        FirewallState firewallState = fwResponse.getBody(FirewallState.class);


        // set up firewall task state
        ProvisionFirewallTaskState task = new ProvisionFirewallTaskState();
        task.requestType = FirewallInstanceRequest.InstanceRequestType.CREATE;
        task.firewallDescriptionLink = firewallState.documentSelfLink;

        Operation provision = new Operation();
        provisionFirewall(task, provision);
        ProvisionFirewallTaskState ps = provision.getBody(ProvisionFirewallTaskState.class);
        waitForTaskCompletion(this.host, UriUtils.buildUri(this.host, ps.documentSelfLink));
        validateAWSArtifacts(firewallState.documentSelfLink, creds);


        // reuse previous task, but switch to a delete
        task.requestType = FirewallInstanceRequest.InstanceRequestType.DELETE;
        Operation remove = new Operation();
        provisionFirewall(task, remove);
        ProvisionFirewallTaskState removeTask = remove.getBody(ProvisionFirewallTaskState.class);
        waitForTaskCompletion(this.host, UriUtils.buildUri(this.host, removeTask.documentSelfLink));

        // verify custom property is now set to no value
        FirewallState removedFW = getFirewallState(firewallState.documentSelfLink);
        assertTrue(removedFW.customProperties.get(AWSFirewallService.SECURITY_GROUP_ID).equalsIgnoreCase(AWSUtils.NO_VALUE));

    }


    @Test
    public void testInvalidAuthAWSFirewall() throws Throwable {
        // first create network service
        Operation response = new Operation();
        NetworkState initialState = TestUtils.buildNetworkState(this.host);
        TestUtils.postNetwork(this.host, initialState, response);
        NetworkState networkState = response.getBody(NetworkState.class);


        // create credentials
        Operation authResponse = new Operation();
        TestUtils.postCredentials(this.host,authResponse,this.privateKey,"invalid");
        AuthCredentialsServiceState creds = authResponse.getBody(AuthCredentialsServiceState.class);

        // create resource pool
        Operation poolResponse = new Operation();
        TestUtils.postResourcePool(this.host,poolResponse);
        ResourcePoolState pool = poolResponse.getBody(ResourcePoolState.class);

        // create fw service
        Operation fwResponse = new Operation();
        FirewallState fwInitialState = buildFirewallState();
        fwInitialState.networkDescriptionLink = networkState.documentSelfLink;
        fwInitialState.ingress = getGlobalSSHRule();
        fwInitialState.egress = getGlobalSSHRule();
        fwInitialState.authCredentialsLink = creds.documentSelfLink;
        fwInitialState.resourcePoolLink = pool.documentSelfLink;
        fwInitialState.regionID = this.region;
        fwInitialState.instanceAdapterReference = UriUtils.buildUri(ServiceHost.LOCAL_HOST,
                this.host.getPort(),
                AWSUriPaths.AWS_FIREWALL_SERVICE,
                null);

        TestUtils.postFirewall(this.host,fwInitialState,fwResponse);
        FirewallState firewallState = fwResponse.getBody(FirewallState.class);


        // set up firewall task state
        ProvisionFirewallTaskState task = new ProvisionFirewallTaskState();
        task.requestType = FirewallInstanceRequest.InstanceRequestType.CREATE;
        task.firewallDescriptionLink = firewallState.documentSelfLink;

        Operation provision = new Operation();
        provisionFirewall(task, provision);
        ProvisionFirewallTaskState ps = provision.getBody(ProvisionFirewallTaskState.class);
        waitForTaskFailure(this.host,UriUtils.buildUri(this.host, ps.documentSelfLink));

    }

    private void validateAWSArtifacts(String firewallDescriptionLink, AuthCredentialsServiceState creds) throws Throwable {

        FirewallState fw = getFirewallState(firewallDescriptionLink);

        AWSFirewallService fwSVC = new AWSFirewallService();
        AmazonEC2AsyncClient client = AWSUtils.getAsyncClient(creds,this.region,false);
        // if any artifact is not present then an error will be thrown
        assertNotNull(fwSVC.getSecurityGroupByID(client, fw.customProperties.get(AWSFirewallService.SECURITY_GROUP_ID)));
    }

    private FirewallState getFirewallState(String firewallLink) throws Throwable {
        Operation response = new Operation();
        getFirewallState(firewallLink, response);
        return response.getBody(FirewallState.class);
    }

    private void provisionFirewall(ProvisionFirewallTaskState ps, Operation response) throws Throwable {
        host.testStart(1);
        Operation startPost = Operation.createPost(this.provisionFirewallFactory)
                .setBody(ps)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        host.failIteration(e);
                        return;
                    }
                    response.setBody(o.getBody(ProvisionFirewallTaskState.class));
                    host.completeIteration();
                });
        host.send(startPost);
        host.testWait();

    }

    private void getFirewallState(String firewallLink,Operation response) throws Throwable {

        host.testStart(1);
        URI firewallURI = UriUtils.buildUri(this.host,firewallLink);
        Operation startGet = Operation.createGet(firewallURI)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        host.failIteration(e);
                        return;
                    }
                    response.setBody(o.getBody(FirewallState.class));
                    host.completeIteration();
                });
        host.send(startGet);
        host.testWait();

    }

    private FirewallState buildFirewallState() {
        URI tenantFactoryURI = UriUtils.buildUri(host, TenantFactoryService.class);
        FirewallState firewall = new FirewallState();
        firewall.id = UUID.randomUUID().toString();

        firewall.tenantLinks = new ArrayList<>();
        firewall.tenantLinks.add(UriUtils.buildUriPath(tenantFactoryURI.getPath(), "tenantA"));
        return firewall;
    }

    public static void waitForTaskCompletion(VerificationHost host, URI provisioningTaskUri)
            throws Throwable {

        Date expiration = host.getTestExpiration();

        ProvisionFirewallTaskState provisioningTask;

        do {
            provisioningTask = host.getServiceState(null,
                    ProvisionFirewallTaskState.class,
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

    public static void waitForTaskFailure(VerificationHost host, URI provisioningTaskUri)
            throws Throwable {

        Date expiration = host.getTestExpiration();

        ProvisionFirewallTaskState provisioningTask;

        do {
            provisioningTask = host.getServiceState(null,
                    ProvisionFirewallTaskState.class,
                    provisioningTaskUri);

            if (provisioningTask.taskInfo.stage == TaskState.TaskStage.FAILED) {
                return;
            }

            Thread.sleep(1000);
        } while (new Date().before(expiration));

        host.log("Pending task:\n%s", Utils.toJsonHtml(provisioningTask));

        throw new TimeoutException("Some tasks never finished");
    }

    private static ArrayList<Allow> getGlobalSSHRule() {
        ArrayList<Allow> rules = new ArrayList<>();

        Allow ssh = new Allow();
        ssh.name = "ssh-allow";
        ssh.protocol = "tcp";
        ssh.ipRange = "0.0.0.0/0";
        ssh.ports = new ArrayList<String>();
        ssh.ports.add("22");
        rules.add(ssh);

        return rules;
    }

}

