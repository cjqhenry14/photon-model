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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.services.ec2.AmazonEC2AsyncClient;
import com.amazonaws.services.ec2.model.IpPermission;
import com.amazonaws.services.ec2.model.SecurityGroup;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import com.vmware.photon.controller.model.resources.FirewallService.FirewallState.Allow;
import com.vmware.photon.controller.model.tasks.ProvisioningUtils;

import com.vmware.xenon.common.CommandLineArgumentParser;
import com.vmware.xenon.common.test.VerificationHost;

public class TestAWSFirewallService {

    /*
    * This test requires the following four command line variables.
    * If they are not present the tests will be ignored
    * Pass them into the test with the -Dxenon.variable=value syntax
    * i.e -Dxenon.subnet="10.1.0.0/16"
    *
    *
    * privateKey & privateKeyId are credentials to an AWS VPC account
    * region is the ec2 region where the tests should be run (us-east-1)
    * subnet is the RFC-1918 subnet of the default VPC
    *
    * Test assumes the default CM Security group is NOT present in the provided
    * AWS account / zone -- if it is present the tests will fail
    */
    public String privateKey;
    public String privateKeyId;
    public String region;
    public String subnet;
    public AmazonEC2AsyncClient client;

    VerificationHost host;

    AWSFirewallService svc;
    AWSAllocation aws;

    @Rule
    public ExpectedException expectedEx = ExpectedException.none();

    @Before
    public void setUp() throws Exception {
        CommandLineArgumentParser.parseFromProperties(this);
        // ignore if any of the required properties are missing
        org.junit.Assume.assumeTrue(TestUtils.isNull(privateKey, privateKeyId, region, subnet));
        this.host = VerificationHost.create(0);
        try {
            this.host.start();
            ProvisioningUtils.startProvisioningServices(this.host);
            this.svc = new AWSFirewallService();
            this.client = TestUtils.getClient(this.privateKeyId,this.privateKey,this.region,false);
            // legacy that can be removed when instance
            // refactored
            this.aws = new AWSAllocation(null);
            this.aws.amazonEC2Client = this.client;

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

    /*
     * request a group that doesn't exist
     */
    @Test
    public void testInvalidGetSecurityGroup() throws Throwable {
        expectedEx.expect(AmazonServiceException.class);
        svc.getSecurityGroup(this.client, "foo-bar");
    }

    /*
     * Create the default CM group, get or describe the default group
     * and then delete the group
     */
    @Test
    public void testDefaultSecurityGroup() throws Throwable {
        svc.createSecurityGroup(this.client, null);
        svc.getSecurityGroup(this.client);
        svc.deleteSecurityGroup(this.client);
    }

    /*
     * Create the default CM group, get the group, verify the default
       permissions are in place.  Then delete the default group
     */

    @Test
    public void testDefaultSecurityGroupPorts() throws Throwable {
        // create the group
        String groupId = svc.createSecurityGroup(this.client, null);

        // allow the default ports
        svc.updateIngressRules(this.client, groupId, svc.getDefaultRules(this.subnet));

        // get the updated CM group
        SecurityGroup group = svc.getSecurityGroup(this.client);

        List<IpPermission> rules = group.getIpPermissions();

        assertTrue(rules.size() > 0);
        validateDefaultRules(rules);

        // lets delete the default CM group
        svc.deleteSecurityGroup(this.client);
    }

    /*
     * Negative test attempting to delete the non-existent
     * default CM security group
     */
    @Test
    public void testDeleteMissingGroup() throws Throwable {
        expectedEx.expect(AmazonServiceException.class);
        expectedEx.expectMessage("The security group 'cell-manager-security-group' does not exist");

        // lets delete the default CM group
        // which doesn't exist
        svc.deleteSecurityGroup(this.client);
    }

    /*
     * create a new security group via the allocation method
     */
    @Test
    public void testAllocateSecurityGroup() throws Throwable {
        svc.allocateSecurityGroup(this.aws);
        SecurityGroup group = svc.getSecurityGroup(this.client);
        validateDefaultRules(group.getIpPermissions());
        svc.deleteSecurityGroup(this.client);
    }

    /*
     * update an existing security group to the required default ports
     */
    @Test
    public void testAllocateSecurityGroupUpdate() throws Throwable {
        String groupId = svc.createSecurityGroup(this.client,
                AWSFirewallService.DEFAULT_SECURITY_GROUP_NAME,
                AWSFirewallService.DEFAULT_SECURITY_GROUP_DESC,
                null);

        List<IpPermission> rules = new ArrayList<>();
        rules.add(new IpPermission()
                .withIpProtocol(AWSFirewallService.DEFAULT_PROTOCOL)
                .withFromPort(22)
                .withToPort(22)
                .withIpRanges(AWSFirewallService.DEFAULT_ALLOWED_NETWORK));
        svc.updateIngressRules(this.client, groupId, rules);
        svc.allocateSecurityGroup(this.aws);
        SecurityGroup updatedGroup = svc.getSecurityGroup(this.client,
                AWSFirewallService.DEFAULT_SECURITY_GROUP_NAME);
        validateDefaultRules(updatedGroup.getIpPermissions());
        svc.deleteSecurityGroup(this.client);
    }

    /*
     * Test conversion of Allow rules to AWS IpPermssions
     */

    @Test
    public void testBuildRules() throws Throwable {
        ArrayList<Allow> rules = TestUtils.getAllowIngressRules();
        List<IpPermission> awsRules = svc.buildRules(rules);

        for (IpPermission rule : awsRules) {
            assertDefaultRules(rule);
        }

    }


    /*
     * Test updating ingress rules with the Firewall Service Allow
     * object
     */

    @Test
    public void testUpdateIngressRules() throws Throwable {
        String groupID = svc.createSecurityGroup(this.client, null);
        ArrayList<Allow> rules = TestUtils.getAllowIngressRules();
        svc.updateIngressRules(this.client,groupID,svc.buildRules(rules));
        SecurityGroup awsSG = svc.getSecurityGroupByID(this.client,groupID);

        List<IpPermission> ingress = awsSG.getIpPermissions();

        for (IpPermission rule : ingress) {
            assertDefaultRules(rule);
        }

        svc.deleteSecurityGroup(this.client,groupID);

    }

    private void assertDefaultRules(IpPermission rule) {
        assertTrue(rule.getIpProtocol().equalsIgnoreCase(AWSFirewallService.DEFAULT_PROTOCOL));
        assertTrue(rule.getIpRanges().get(0).equalsIgnoreCase(AWSFirewallService.DEFAULT_ALLOWED_NETWORK));
        assertTrue(rule.getFromPort() == 22 || rule.getFromPort() == 80 || rule.getFromPort() == 41000);
        assertTrue(rule.getToPort() == 22 || rule.getToPort() == 80 || rule.getToPort() == 42000);
    }

    private void validateDefaultRules(List<IpPermission> rules) throws Throwable {
        ArrayList<Integer> ports = new ArrayList<>();
        for (int port : AWSFirewallService.DEFAULT_ALLOWED_PORTS) {
            ports.add(port);
        }

        for (IpPermission rule : rules) {
            assertTrue(rule.getIpProtocol().equalsIgnoreCase(AWSFirewallService.DEFAULT_PROTOCOL));
            if (rule.getFromPort() == 1) {
                assertTrue(rule.getIpRanges().get(0)
                        .equalsIgnoreCase(this.subnet));
                assertTrue(rule.getToPort() == 65535);
            } else {
                assertTrue(rule.getIpRanges().get(0)
                        .equalsIgnoreCase(AWSFirewallService.DEFAULT_ALLOWED_NETWORK));
                assertEquals(rule.getFromPort(), rule.getToPort());
                assertTrue(ports.contains(rule.getToPort()));
            }
        }
    }

}
