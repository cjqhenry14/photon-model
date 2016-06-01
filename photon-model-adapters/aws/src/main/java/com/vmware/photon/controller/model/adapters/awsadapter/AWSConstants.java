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

import com.vmware.photon.controller.model.UriPaths;

public class AWSConstants {
    public static final String AWS_SECURITY_GROUP = "awsSecurityGroup";
    public static final String AWS_SECURITY_GROUP_ID = "awsSecurityGroupId";
    public static final String AWS_TAGS = "awsTags";
    public static final String AWS_VPC_ID = "awsVpcId";
    public static final String AWS_SUBNET_ID = "awsSubnetId";
    public static final String AWS_GATEWAY_ID = "awsGatewayID";
    public static final String AWS_VPC_ROUTE_TABLE_ID = "awsMainRouteTableID";
    public static final String AWS_MAIN_ROUTE_ASSOCIATION = "association.main";
    public static final String INSTANCE_STATE = "instance-state-name";
    public static final String INSTANCE_STATE_RUNNING = "running";
    public static final String INSTANCE_STATE_PENDING = "pending";
    public static final String INSTANCE_STATE_STOPPING = "stopping";
    public static final String INSTANCE_STATE_STOPPED = "stopped";
    public static final String INSTANCE_STATE_SHUTTING_DOWN = "shutting-down";
    public static final String HYPHEN = "-";
    public static final String PUBLIC_INTERFACE = "public-interface";
    public static final String PRIVATE_INTERFACE = "private-interface";
    public static final String AWS_ATTACHMENT_VPC_FILTER = "attachment.vpc-id";
    public static final String PROPERTY_NAME_QUERY_PAGE_SIZE = UriPaths.PROPERTY_PREFIX
            + AWSConstants.QUERY_PAGE_SIZE;
    private static int QUERY_PAGE_SIZE = Integer.getInteger(PROPERTY_NAME_QUERY_PAGE_SIZE, 50);
    public static final String PROPERTY_NAME_QUERY_RESULT_LIMIT = UriPaths.PROPERTY_PREFIX
            + AWSConstants.QUERY_PAGE_SIZE;
    private static int QUERY_RESULT_LIMIT = Integer.getInteger(PROPERTY_NAME_QUERY_RESULT_LIMIT,
            50);

    // AWS Metric related Constants
    public static final String CPU_UTILIZATION = "CPUUtilization";
    public static final String DISK_READ_BYTES = "DiskReadBytes";
    public static final String DISK_WRITE_BYTES = "DiskWriteBytes";
    public static final String NETWORK_IN = "NetworkIn";
    public static final String NETWORK_OUT = "NetworkOut";
    public static final String CPU_CREDIT_USAGE = "CPUCreditUsage";
    public static final String CPU_CREDIT_BALANCE = "CPUCreditBalance";
    public static final String DISK_READ_OPS = "DiskReadOps";
    public static final String DISK_WRITE_OPS = "DiskWriteOps";
    public static final String NETWORK_PACKETS_IN = "NetworkPacketsIn";
    public static final String NETWORK_PACKETS_OUT = "NetworkPacketsOut";
    public static final String STATUS_CHECK_FAILED = "StatusCheckFailed";
    public static final String STATUS_CHECK_FAILED_INSTANCE = "StatusCheckFailed_Instance";
    public static final String STATUS_CHECK_FAILED_SYSTEM = "StatusCheckFailed_System";
    public static final String ESTIMATED_CHARGES = "EstimatedCharges";
    public static final String BURN_RATE = "BurnRatePerHour";

    // AWS Metric Unit related constants
    public static final String UNIT_COUNT = "Count";
    public static final String UNIT_BYTES = "Bytes";
    public static final String UNIT_PERCENT = "Percent";
    public static final String UNIT_COST = "USD";

    public static void setQueryPageSize(int size) {
        QUERY_PAGE_SIZE = size;
    }

    public static int getQueryPageSize() {
        return QUERY_PAGE_SIZE;
    }

    public static void setQueryResultLimit(int resultLimit) {
        QUERY_RESULT_LIMIT = resultLimit;
    }

    public static int getQueryResultLimit() {
        return QUERY_RESULT_LIMIT;
    }
}
