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

package com.vmware.photon.controller.model.tasks.monitoring;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import com.vmware.photon.controller.model.adapterapi.ComputeStatsRequest;
import com.vmware.photon.controller.model.adapterapi.ComputeStatsResponse;
import com.vmware.photon.controller.model.adapterapi.ComputeStatsResponse.ComputeStats;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceStats.ServiceStat;
import com.vmware.xenon.common.StatelessService;

public class MockStatsAdapter extends StatelessService {

    public static final String SELF_LINK = "/mock-stats-adapter";
    public static final String KEY_1 = "key-1";
    public static final String KEY_2 = "key-2";
    public static final String UNIT_1 = "unit1";
    public static final String UNIT_2 = "unit2";

    private static double counter = 0;

    @Override
    public void handleRequest(Operation op) {
        if (!op.hasBody()) {
            op.fail(new IllegalArgumentException("body is required"));
            return;
        }
        switch (op.getAction()) {
        case PATCH:
            op.complete();
            ComputeStatsRequest statsRequest = op.getBody(ComputeStatsRequest.class);
            ComputeStatsResponse statsResponse = new ComputeStatsResponse();
            Map<String, ServiceStat> statValues = new HashMap<String, ServiceStat>();
            counter++;
            ServiceStat key1 = new ServiceStat();
            key1.latestValue = counter;
            key1.unit = UNIT_1;
            statValues.put(KEY_1, key1);
            ServiceStat key2 = new ServiceStat();
            key2.latestValue = counter;
            key2.unit = UNIT_2;
            statValues.put(KEY_2, key2);
            ComputeStats cStat = new ComputeStats();
            cStat.statValues = statValues;
            cStat.computeLink = statsRequest.computeReference.getPath();
            statsResponse.statsList = new ArrayList<ComputeStats>();
            statsResponse.statsList.add(cStat);
            statsResponse.taskStage = statsRequest.nextStage;
            this.sendRequest(Operation.createPatch(statsRequest.parentTaskReference)
                    .setBody(statsResponse));
            break;
        default:
            super.handleRequest(op);
        }
    }

}
