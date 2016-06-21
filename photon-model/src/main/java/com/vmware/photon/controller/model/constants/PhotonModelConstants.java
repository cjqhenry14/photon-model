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

package com.vmware.photon.controller.model.constants;

public class PhotonModelConstants {

    // Photon-Model Metric related Constants
    public static final String CPU_UTILIZATION_PERCENT = "CPUUtilizationPercent";
    public static final String DISK_READ_BYTES = "DiskReadBytes";
    public static final String DISK_WRITE_BYTES = "DiskWriteBytes";
    public static final String NETWORK_IN_BYTES = "NetworkInBytes";
    public static final String NETWORK_OUT_BYTES = "NetworkOutBytes";
    public static final String CPU_CREDIT_USAGE_COUNT = "CPUCreditUsageCount";
    public static final String CPU_CREDIT_BALANCE_COUNT = "CPUCreditBalanceCount";
    public static final String DISK_READ_OPS_COUNT = "DiskReadOperationsCount";
    public static final String DISK_WRITE_OPS_COUNT = "DiskWriteOperationsCount";
    public static final String NETWORK_PACKETS_IN_COUNT = "NetworkPacketsInCount";
    public static final String NETWORK_PACKETS_OUT_COUNT = "NetworkPacketsOutCount";
    public static final String STATUS_CHECK_FAILED_COUNT = "StatusCheckFailedCount";
    public static final String STATUS_CHECK_FAILED_COUNT_INSTANCE = "StatusCheckFailedCount_Instance";
    public static final String STATUS_CHECK_FAILED_COUNT_SYSTEM = "StatusCheckFailedCount_System";
    public static final String ESTIMATED_CHARGES = "EstimatedCharges";
    public static final String BURN_RATE_PER_HOUR = "BurnRatePerHour";

    public static final String DISK_WRITE_TIME_SECONDS = "DiskWriteTimeSeconds";
    public static final String DISK_READ_TIME_SECONDS = "DiskReadTimeSeconds";
    public static final String MEMORY_AVAILABLE_BYTES = "MemoryAvailableBytes";
    public static final String MEMORY_USED_BYTES = "MemoryUsedBytes";

    // Photon-Model Metric Unit related constants
    public static final String UNIT_COUNT = "Count";
    public static final String UNIT_BYTES = "Bytes";
    public static final String UNIT_PERCENT = "Percent";
    public static final String UNIT_COST = "USD";

    // Photon-Model specific constants
    public static final String API_CALL_COUNT = "APICallCount";
    public static final String SOURCE_TASK_LINK = "SourceTaskLink";
}
