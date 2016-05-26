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

    static {
        // Map of AWS-specific Units to Photon-Model Units
        Map<String, String> unitMap = new HashMap<>();
        unitMap.put(AWSConstants.AWS_UNIT_COST,
                PhotonModelConstants.PHOTON_MODEL_UNIT_COST);
        unitMap.put(AWSConstants.AWS_UNIT_BYTES,
                PhotonModelConstants.PHOTON_MODEL_UNIT_BYTES);
        unitMap.put(AWSConstants.AWS_UNIT_COUNT,
                PhotonModelConstants.PHOTON_MODEL_UNIT_COUNT);
        unitMap.put(AWSConstants.AWS_UNIT_PERCENT,
                PhotonModelConstants.PHOTON_MODEL_UNIT_PERCENT);
        PHOTON_MODEL_UNIT_MAP = Collections.unmodifiableMap(unitMap);
    }

    public static String getNormalizedUnitValue(String cloudSpecificUnit) {
        return PHOTON_MODEL_UNIT_MAP.get(cloudSpecificUnit);
    }
}
