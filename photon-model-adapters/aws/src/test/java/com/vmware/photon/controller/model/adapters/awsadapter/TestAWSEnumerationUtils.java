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

import static com.vmware.photon.controller.model.adapters.awsadapter.AWSUtils.TILDA;
import static com.vmware.photon.controller.model.adapters.awsadapter.AWSUtils.getRegionId;
import static com.vmware.photon.controller.model.adapters.awsadapter.util.AWSEnumerationUtils.getInstanceTypeFromComputeDescriptionKey;
import static com.vmware.photon.controller.model.adapters.awsadapter.util.AWSEnumerationUtils.getKeyForComputeDescriptionFromInstance;
import static com.vmware.photon.controller.model.adapters.awsadapter.util.AWSEnumerationUtils.getNetworkIdFromComputeDescriptionKey;
import static com.vmware.photon.controller.model.adapters.awsadapter.util.AWSEnumerationUtils.getRegionIdFromComputeDescriptionKey;

import com.amazonaws.services.ec2.model.Instance;
import com.amazonaws.services.ec2.model.Placement;

import org.junit.Test;

public class TestAWSEnumerationUtils {

    public static final String AWS_INSTANCE_ID = "i-345678";
    public static final String AWS_REGION_ID = "us-east-1";
    public static final String AWS_ZONE_ID = "us-east-1b";
    public static final String AWS_VPC_ID = "vpc-4567";
    public static final String AWS_INSTANCE_TYPE = "t2.micro";
    public static final String AWS_COMPUTE_DESCRIPTION_KEY = AWS_REGION_ID + TILDA
            + AWS_INSTANCE_TYPE + TILDA + AWS_VPC_ID;

    @Test
    public void testGetComputeDescriptionKeyFromAWSInstance() throws Throwable {
        Instance awsInstance = new Instance();
        awsInstance.setInstanceId(AWS_INSTANCE_ID);
        Placement placement = new Placement();
        placement.setAvailabilityZone(AWS_ZONE_ID);
        awsInstance.setPlacement(placement);
        String regionId = getRegionId(awsInstance);
        awsInstance.setInstanceType(AWS_INSTANCE_TYPE);
        awsInstance.setVpcId(AWS_VPC_ID);
        assertEquals(AWS_REGION_ID, regionId);
        String computeDescriptionKey = getKeyForComputeDescriptionFromInstance(awsInstance);
        assertEquals(AWS_COMPUTE_DESCRIPTION_KEY, computeDescriptionKey);
    }

    @Test
    public void testGetIndividualAttributesFromComputeDescriptionKey() throws Throwable {
        String instanceType = getInstanceTypeFromComputeDescriptionKey(AWS_COMPUTE_DESCRIPTION_KEY);
        assertEquals(AWS_INSTANCE_TYPE, instanceType);

        String regionID = getRegionIdFromComputeDescriptionKey(AWS_COMPUTE_DESCRIPTION_KEY);
        assertEquals(AWS_REGION_ID, regionID);

        String vpcID = getNetworkIdFromComputeDescriptionKey(AWS_COMPUTE_DESCRIPTION_KEY);
        assertEquals(AWS_VPC_ID, vpcID);
    }

}
