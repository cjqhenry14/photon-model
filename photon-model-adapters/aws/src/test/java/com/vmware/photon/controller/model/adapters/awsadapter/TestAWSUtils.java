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

import static com.vmware.photon.controller.model.adapters.awsadapter.TestUtils.getExecutor;

import java.util.List;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.services.ec2.AmazonEC2AsyncClient;
import com.amazonaws.services.ec2.model.TagDescription;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import com.vmware.photon.controller.model.PhotonModelServices;
import com.vmware.photon.controller.model.tasks.PhotonModelTaskServices;
import com.vmware.xenon.common.CommandLineArgumentParser;
import com.vmware.xenon.common.test.VerificationHost;
import com.vmware.xenon.services.common.AuthCredentialsService.AuthCredentialsServiceState;

public class TestAWSUtils {
    /*
    * This test requires the following three command line variables.
    * If they are not present the tests will be ignored
    * Pass them into the test with the -Ddcp.variable=value syntax
    * i.e -Ddcp.privateKey="XXXXXXXXXXXXXXXXXXXX"
    *     -Ddcp.privateKeyId="YYYYYYYYYYYYYYYYYY"
    *     -Ddcp.region="us-east-1"
    *
    * privateKey & privateKeyId are credentials to an AWS VPC account
    * region is the ec2 region where the tests should be run (us-east-1)
    */

    public static final String TEST_NAME = "VMW-Testing";

    // command line options
    public String privateKey;
    public String privateKeyId;
    public String region;

    VerificationHost host;

    @Rule
    public ExpectedException expectedEx = ExpectedException.none();

    @Before
    public void setUp() throws Exception {
        CommandLineArgumentParser.parseFromProperties(this);

        // ignore if any of the required properties are missing
        org.junit.Assume.assumeTrue(TestUtils.isNull(this.privateKey, this.privateKeyId, this.region));

        this.host = VerificationHost.create(0);
        try {
            this.host.start();
            PhotonModelServices.startServices(this.host);
            PhotonModelTaskServices.startServices(this.host);
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
    public void testClientCreation() throws Throwable {
        AuthCredentialsServiceState creds = new AuthCredentialsServiceState();
        creds.privateKey = this.privateKey;
        creds.privateKeyId = this.privateKeyId;
        AWSUtils.getAsyncClient(creds, this.region, false, getExecutor());
    }

    @Test
    public void testInvalidClientCredentials() throws Throwable {
        this.expectedEx.expect(AmazonServiceException.class);
        AuthCredentialsServiceState creds = new AuthCredentialsServiceState();
        creds.privateKey = "bar";
        creds.privateKeyId = "foo";
        AWSUtils.getAsyncClient(creds, this.region, false, getExecutor());
    }

    @Test
    public void testResourceNaming() throws Throwable {
        boolean tagFound = false;
        AmazonEC2AsyncClient client = TestUtils.getClient(this.privateKeyId,this.privateKey,this.region,false);

        //create something to name
        AWSNetworkService svc = new AWSNetworkService();
        String vpcID = svc.createVPC("10.20.0.0/16",client);
        AWSUtils.setResourceName(vpcID, TEST_NAME, client);
        List<TagDescription> tags = AWSUtils.getResourceTags(vpcID,client);

        for (TagDescription tagDesc:tags) {
            if (tagDesc.getKey().equalsIgnoreCase(AWSUtils.AWS_TAG_NAME)) {
                assertTrue(tagDesc.getValue().equalsIgnoreCase(TEST_NAME));
                tagFound = true;
                break;
            }
        }
        // ensure we found the tag
        assertTrue(tagFound);
        svc.deleteVPC(vpcID,client);

    }
}
