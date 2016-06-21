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

import static com.vmware.photon.controller.model.adapters.awsadapter.util.AWSClientManagerFactory.getClientManager;
import static com.vmware.photon.controller.model.adapters.awsadapter.util.AWSClientManagerFactory.getEc2ClientReferenceCount;
import static com.vmware.photon.controller.model.adapters.awsadapter.util.AWSClientManagerFactory.getStatsClientReferenceCount;
import static com.vmware.photon.controller.model.adapters.awsadapter.util.AWSClientManagerFactory.returnClientManager;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;

import com.amazonaws.services.ec2.AmazonEC2AsyncClient;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.vmware.photon.controller.model.adapters.awsadapter.util.AWSClientManager;

import com.vmware.xenon.common.BasicReusableHostTestCase;
import com.vmware.xenon.common.CommandLineArgumentParser;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.services.common.AuthCredentialsService.AuthCredentialsServiceState;

/**
 *Tests that the AWS client instances use a common executor pool for calling into AWS
 *and all the client caches and executor pools are cleaned up when the AWS adapters
 *are shutdown.
 *
 */
public class TestAWSClientManagement extends BasicReusableHostTestCase {
    public static final int count1 = 1;
    public static final int count2 = 2;
    public static final int count0 = 0;
    public StatelessService instanceService;
    public StatelessService statsService;
    public AuthCredentialsServiceState creds;
    public String accessKey = "accessKey";
    public String secretKey = "secretKey";
    public AmazonEC2AsyncClient client;
    boolean isMock = true;

    @Before
    public void setUp() throws Exception {
        CommandLineArgumentParser.parseFromProperties(this);
        List<String> serviceSelfLinks = new ArrayList<String>();
        try {
            // TODO: VSYM-992 - improve test/remove arbitrary timeout
            this.instanceService = new AWSInstanceService();
            this.host.startService(
                    Operation.createPost(UriUtils.buildUri(this.host,
                            AWSInstanceService.class)),
                    this.instanceService);
            serviceSelfLinks.add(AWSInstanceService.SELF_LINK);

            this.statsService = new AWSStatsService();
            this.host.startService(
                    Operation.createPost(UriUtils.buildUri(this.host,
                            AWSStatsService.class)),
                    this.statsService);
            serviceSelfLinks.add(AWSStatsService.SELF_LINK);

            this.host.waitForServiceAvailable(AWSStatsService.SELF_LINK);
            this.host.waitForServiceAvailable(AWSInstanceService.SELF_LINK);
        } catch (Throwable e) {
            this.host.log("Error starting up services for the test %s", e.getMessage());
            throw new Exception(e);
        }
    }

    @After
    public void tearDown() throws InterruptedException {
        if (this.host == null) {
            return;
        }
    }

    //
    @Test
    public void testAWSClientManagement() throws Throwable {
        this.host.setTimeoutSeconds(60);
        assertEquals(count1, getEc2ClientReferenceCount());
        assertEquals(count1, getStatsClientReferenceCount());

        // Getting a reference to the client manager in the test
        AWSClientManager clientManager = getClientManager(false);
        assertEquals(count2, getEc2ClientReferenceCount());

        // Getting an AWSclient from the client manager
        this.creds = new AuthCredentialsServiceState();
        this.creds.privateKey = this.accessKey;
        this.creds.privateKeyId = this.secretKey;

        this.client = clientManager.getOrCreateEC2Client(this.creds, TestAWSSetupUtils.zoneId,
                this.instanceService, null,
                this.isMock, false);
        assertEquals(count1, clientManager.getCacheCount(false));

        // Requesting another AWS client with the same set of credentials will not
        // create a new entry in the cache
        this.client = clientManager.getOrCreateEC2Client(this.creds, TestAWSSetupUtils.zoneId,
                this.instanceService, null,
                this.isMock, false);
        assertEquals(count1, clientManager.getCacheCount(false));

        // Saving a reference to the executor associated with the client to chec
        // if it is shutdown when no references remain

        ExecutorService executorService = this.client.getExecutorService();

        // Emulating shutdown of individual services to check that the client resources are
        // cleaned up as expected.
        this.host.sendAndWaitExpectSuccess(
                Operation.createDelete(UriUtils.buildUri(this.host, AWSInstanceService.SELF_LINK)));
        assertEquals(count1, getEc2ClientReferenceCount());
        this.host.sendAndWaitExpectSuccess(
                Operation.createDelete(UriUtils.buildUri(this.host, AWSStatsService.SELF_LINK)));
        assertEquals(count0, getStatsClientReferenceCount());

        // Returning the references from the test
        returnClientManager(clientManager, false);
        assertEquals(count0, getEc2ClientReferenceCount());

        // Asserts that when all the references to the common cache has been returned back then the
        // executor is shutDown
        assertTrue(executorService.isShutdown());

    }
}
