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

import static org.junit.Assert.assertTrue;

import java.util.List;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.services.ec2.model.InternetGateway;
import com.amazonaws.services.ec2.model.InternetGatewayAttachment;
import com.amazonaws.services.ec2.model.RouteTable;
import com.amazonaws.services.ec2.model.Vpc;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import com.vmware.photon.controller.model.tasks.ProvisioningUtils;
import com.vmware.photon.controller.model.tasks.TaskUtils;

import com.vmware.xenon.common.CommandLineArgumentParser;
import com.vmware.xenon.common.test.VerificationHost;


public class TestAWSNetworkService {
    /*
    * This test requires the following four command line variables.
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


    VerificationHost host;

    AWSNetworkService netSvc;
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

            this.netSvc = new AWSNetworkService();
            this.aws = new AWSAllocation(null);
            this.aws.amazonEC2Client = TestUtils.getClient(this.privateKeyId,
                    this.privateKey, this.region, false);
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
    public void testGetDefaultVPCSubnet() throws Throwable {
        String sub = this.netSvc.getDefaultVPCSubnet(aws);
        // should always return an RFC1918 address
        TaskUtils.isRFC1918(sub);
    }

    /*
     * Test will first create a VPC then create a subnet associated to that VPC
     * Once complete it will delete the VPC and associated subnet
     */
    @Test
    public void testVPCAndSubnet() throws Throwable {
        String vpcID = this.netSvc.createVPC(this.subnet,this.aws.amazonEC2Client);
        assertTrue(vpcID != null);

        String subnetID = this.netSvc.createSubnet(this.subnet,vpcID,this.aws.amazonEC2Client);
        assertTrue(subnetID != null);

        // ensure getters works..
        assertTrue(this.netSvc.getVPC(vpcID,this.aws.amazonEC2Client).getVpcId().equalsIgnoreCase(vpcID));
        assertTrue(this.netSvc.getSubnet(subnetID, this.aws.amazonEC2Client).getSubnetId().equalsIgnoreCase(subnetID));

        // delete subnet / vpc
        this.netSvc.deleteSubnet(subnetID,this.aws.amazonEC2Client);
        this.netSvc.deleteVPC(vpcID,this.aws.amazonEC2Client);

        // verify vpc deletion
        // Since only one exception can be thrown in a test we will only verify the removal
        // of the VPC.  VPC removal requires all related objects be removed, so if the VPC
        // is gone then it's safe to say the subnet is as well
        expectedEx.expect(AmazonServiceException.class);
        expectedEx.expectMessage("InvalidVpcID.NotFound");
        this.netSvc.getVPC(vpcID,this.aws.amazonEC2Client);
    }

    @Test
    public void testCreateInternetGateway() throws Throwable {
        String gatewayID = this.netSvc.createInternetGateway(this.aws.amazonEC2Client);
        assertTrue(gatewayID != null);
        assertTrue(this.netSvc.getInternetGateway(gatewayID, this.aws.amazonEC2Client).getInternetGatewayId().equalsIgnoreCase(gatewayID));
        this.netSvc.deleteInternetGateway(gatewayID,this.aws.amazonEC2Client);
    }

    @Test
    public void testGetMainRouteTable() throws Throwable {
        Vpc defVPC = this.netSvc.getDefaultVPC(this.aws.amazonEC2Client);
        assertTrue(defVPC != null);
        RouteTable routeTable = this.netSvc.getMainRouteTable(defVPC.getVpcId(), this.aws.amazonEC2Client);
        assertTrue(routeTable != null);
    }

    /*
     * Test covers the necessary elements for a successful environment creation
     * These environmental elements are necessary before any VM instances can be
     * created
     *
     * - Internet Gateway
     * - VPC
     * - Subnet
     * - Route to IG
     *
     */
    @Test
    public void testEnvironmentCreation() throws Throwable {
        boolean attached = false;

        String gatewayID = this.netSvc.createInternetGateway(this.aws.amazonEC2Client);
        assertTrue(gatewayID != null);
        String vpcID = this.netSvc.createVPC(this.subnet,this.aws.amazonEC2Client);
        assertTrue(vpcID != null);
        String subnetID = this.netSvc.createSubnet(this.subnet,vpcID,this.aws.amazonEC2Client);

        this.netSvc.attachInternetGateway(vpcID, gatewayID, this.aws.amazonEC2Client);
        InternetGateway gw = this.netSvc.getInternetGateway(gatewayID,this.aws.amazonEC2Client);
        List<InternetGatewayAttachment> attachments = gw.getAttachments();
        // ensure we are attached to newly created vpc
        for (InternetGatewayAttachment attachment : attachments) {
            if (attachment.getVpcId().equalsIgnoreCase(vpcID)) {
                attached = true;
                break;
            }
        }
        assertTrue(attached);
        RouteTable routeTable = this.netSvc.getMainRouteTable(vpcID,this.aws.amazonEC2Client);
        this.netSvc.createInternetRoute(gatewayID, routeTable.getRouteTableId(), "0.0.0.0/0", this.aws.amazonEC2Client);

        //remove resources
        this.netSvc.detachInternetGateway(vpcID, gatewayID, this.aws.amazonEC2Client);
        this.netSvc.deleteInternetGateway(gatewayID,this.aws.amazonEC2Client);
        this.netSvc.deleteSubnet(subnetID,this.aws.amazonEC2Client);
        this.netSvc.deleteVPC(vpcID,this.aws.amazonEC2Client);
    }


    @Test
    public void TestGetInvalidVPC() throws Throwable {
        expectedEx.expect(AmazonServiceException.class);
        expectedEx.expectMessage("InvalidVpcID.NotFound");
        this.netSvc.getVPC("1234",this.aws.amazonEC2Client);
    }

    @Test
    public void testGetInvalidSubnet() throws Throwable {
        expectedEx.expect(AmazonServiceException.class);
        expectedEx.expectMessage("InvalidSubnetID.NotFound");
        this.netSvc.getSubnet("1234", this.aws.amazonEC2Client);
    }
}
