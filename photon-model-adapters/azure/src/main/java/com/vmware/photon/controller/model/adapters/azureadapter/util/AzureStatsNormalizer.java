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

package com.vmware.photon.controller.model.adapters.azureadapter.util;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import com.vmware.photon.controller.model.adapters.azureadapter.AzureConstants;
import com.vmware.photon.controller.model.constants.PhotonModelConstants;

public class AzureStatsNormalizer {
    private static final Map<String, String> PHOTON_MODEL_UNIT_MAP;
    private static final Map<String, String> PHOTON_MODEL_STATS_MAP;

    static {
        // Map of Azure-specific Units to Photon-Model Units
        Map<String, String> unitMap = new HashMap<>();
        PHOTON_MODEL_UNIT_MAP = Collections.unmodifiableMap(unitMap);

        // Map of Azure-specific stat keys to Photon-Model stat keys
        Map<String, String> statMap = new HashMap<>();
        statMap.put(AzureConstants.NETWORK_PACKETS_IN,
                PhotonModelConstants.NETWORK_PACKETS_IN_COUNT);
        statMap.put(AzureConstants.NETWORK_PACKETS_OUT,
                PhotonModelConstants.NETWORK_PACKETS_OUT_COUNT);
        statMap.put(AzureConstants.DISK_WRITE_TIME,
                PhotonModelConstants.DISK_WRITE_TIME_SECONDS);
        statMap.put(AzureConstants.DISK_READ_TIME,
                PhotonModelConstants.DISK_READ_TIME_SECONDS);
        statMap.put(AzureConstants.CPU_UTILIZATION,
                PhotonModelConstants.CPU_UTILIZATION_PERCENT);
        statMap.put(AzureConstants.MEMORY_AVAILABLE,
                PhotonModelConstants.MEMORY_AVAILABLE_BYTES);
        statMap.put(AzureConstants.MEMORY_USED,
                PhotonModelConstants.MEMORY_USED_BYTES);
        PHOTON_MODEL_STATS_MAP = Collections.unmodifiableMap(statMap);
    }

    public static String getNormalizedUnitValue(String cloudSpecificUnit) {
        return PHOTON_MODEL_UNIT_MAP.get(cloudSpecificUnit);
    }

    public static String getNormalizedStatKeyValue(String cloudSpecificStatKey) {
        return PHOTON_MODEL_STATS_MAP.get(cloudSpecificStatKey);
    }
}
