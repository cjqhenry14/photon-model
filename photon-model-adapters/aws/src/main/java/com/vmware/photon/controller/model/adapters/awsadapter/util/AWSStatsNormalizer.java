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

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import com.vmware.photon.controller.model.adapters.awsadapter.AWSConstants;
import com.vmware.photon.controller.model.constants.PhotonModelConstants;

public class AWSStatsNormalizer {
    private static final Map<String, String> PHOTON_MODEL_UNIT_MAP;
    private static final Map<String, String> PHOTON_MODEL_STATS_MAP;

    static {
        // Map of AWS-specific Units to Photon-Model Units
        Map<String, String> unitMap = new HashMap<>();
        unitMap.put(AWSConstants.UNIT_COST,
                PhotonModelConstants.UNIT_COST);
        unitMap.put(AWSConstants.UNIT_BYTES,
                PhotonModelConstants.UNIT_BYTES);
        unitMap.put(AWSConstants.UNIT_COUNT,
                PhotonModelConstants.UNIT_COUNT);
        unitMap.put(AWSConstants.UNIT_PERCENT,
                PhotonModelConstants.UNIT_PERCENT);
        PHOTON_MODEL_UNIT_MAP = Collections.unmodifiableMap(unitMap);

        // Map of AWS-specific stat keys to Photon-Model stat keys
        Map<String, String> statMap = new HashMap<>();
        statMap.put(AWSConstants.CPU_UTILIZATION,
                PhotonModelConstants.CPU_UTILIZATION_PERCENT);
        statMap.put(AWSConstants.DISK_READ_BYTES,
                PhotonModelConstants.DISK_READ_BYTES);
        statMap.put(AWSConstants.DISK_WRITE_BYTES,
                PhotonModelConstants.DISK_WRITE_BYTES);
        statMap.put(AWSConstants.NETWORK_IN,
                PhotonModelConstants.NETWORK_IN_BYTES);
        statMap.put(AWSConstants.NETWORK_OUT,
                PhotonModelConstants.NETWORK_OUT_BYTES);
        statMap.put(AWSConstants.CPU_CREDIT_USAGE,
                PhotonModelConstants.CPU_CREDIT_USAGE_COUNT);
        statMap.put(AWSConstants.CPU_CREDIT_BALANCE,
                PhotonModelConstants.CPU_CREDIT_BALANCE_COUNT);
        statMap.put(AWSConstants.DISK_READ_OPS,
                PhotonModelConstants.DISK_READ_OPS_COUNT);
        statMap.put(AWSConstants.DISK_WRITE_OPS,
                PhotonModelConstants.DISK_WRITE_OPS_COUNT);
        statMap.put(AWSConstants.NETWORK_PACKETS_IN,
                PhotonModelConstants.NETWORK_PACKETS_IN_COUNT);
        statMap.put(AWSConstants.NETWORK_PACKETS_OUT,
                PhotonModelConstants.NETWORK_PACKETS_OUT_COUNT);
        statMap.put(AWSConstants.STATUS_CHECK_FAILED,
                PhotonModelConstants.STATUS_CHECK_FAILED_COUNT);
        statMap.put(AWSConstants.STATUS_CHECK_FAILED_INSTANCE,
                PhotonModelConstants.STATUS_CHECK_FAILED_COUNT_INSTANCE);
        statMap.put(AWSConstants.STATUS_CHECK_FAILED_SYSTEM,
                PhotonModelConstants.STATUS_CHECK_FAILED_COUNT_SYSTEM);
        statMap.put(AWSConstants.ESTIMATED_CHARGES,
                PhotonModelConstants.ESTIMATED_CHARGES);
        statMap.put(AWSConstants.BURN_RATE,
                PhotonModelConstants.BURN_RATE_PER_HOUR);
        PHOTON_MODEL_STATS_MAP = Collections.unmodifiableMap(statMap);
    }

    public static String getNormalizedUnitValue(String cloudSpecificUnit) {
        return PHOTON_MODEL_UNIT_MAP.get(cloudSpecificUnit);
    }

    public static String getNormalizedStatKeyValue(String cloudSpecificStatKey) {
        return PHOTON_MODEL_STATS_MAP.get(cloudSpecificStatKey);
    }
}
