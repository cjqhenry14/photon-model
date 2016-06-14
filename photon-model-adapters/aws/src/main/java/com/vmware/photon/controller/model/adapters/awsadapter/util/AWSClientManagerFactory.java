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

package com.vmware.photon.controller.model.adapters.awsadapter.util;

/**
 * Holds instances of the client manager to be shared by all the AWS adapters to avoid the
 * creation of caches on a per adapter level. Holds two instances of the client manager
 * mapping to the EC2 client Cache and the CloudWatch client cache.
 */
public class AWSClientManagerFactory {
    private static AWSClientManager ec2ClientManager;
    private static AWSClientManager statsClientManager;
    private static int ec2ClientReferenceCount = 0;
    private static int statsClientReferenceCount = 0;

    /**
     * Returns a reference to an EC2 client manager instance if it exists. Creates a new one
     * if it does not exist.
     */
    public static synchronized AWSClientManager getClientManager(boolean statsFlag) {
        if (statsFlag) {
            if (statsClientManager == null) {
                statsClientManager = new AWSClientManager(true);
            }
            statsClientReferenceCount++;
            return statsClientManager;
        }
        if (ec2ClientManager == null) {
            ec2ClientManager = new AWSClientManager();
        }
        ec2ClientReferenceCount++;
        return ec2ClientManager;
    }

    /**
     * Decrements the reference count for the EC2 client manager. If the reference count goes down to zero, then
     * the shared cache is cleared out.
     */
    public static synchronized void returnClientManager(AWSClientManager clientManager,
            boolean statsFlag) {
        if (statsFlag) {
            if (clientManager != statsClientManager) {
                throw new IllegalArgumentException(
                        "Incorrect client manager reference passed to the method.");
            }
            statsClientReferenceCount--;
            return;
        }
        if (clientManager != ec2ClientManager) {
            throw new IllegalArgumentException(
                    "Incorrect client manager reference passed to the method.");
        }
        ec2ClientReferenceCount--;
        // check to shut down the individual clients as the clients share a common executor pool.
        if (ec2ClientReferenceCount == 0 && statsClientReferenceCount == 0) {
            cleanupClientManager(false);
        }
    }

    /**
     * Invokes the cleanup code on the client manager once they are not referenced by any of the adapters.
     */
    private static void cleanupClientManager(boolean statsFlag) {
        if (statsFlag) {
            statsClientManager.cleanUp();
            return;
        }
        ec2ClientManager.cleanUp();
    }

    public static int getEc2ClientReferenceCount() {
        return ec2ClientReferenceCount;
    }

    public static int getStatsClientReferenceCount() {
        return statsClientReferenceCount;
    }

}
