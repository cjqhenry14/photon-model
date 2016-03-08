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
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.common.UriUtils;

public class MockStatsAdapter extends StatelessService {

    public static final String SELF_LINK = "/mock-stats-adapter";
    public static final String KEY_1 = "key-1";
    public static final String KEY_2 = "key-2";

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
            Map<String, Double> statValues = new HashMap<String, Double>();
            counter++;
            statValues.put(KEY_1, counter);
            statValues.put(KEY_2, counter);
            ComputeStats cStat = new ComputeStats();
            cStat.statValues = statValues;
            cStat.computeLink = statsRequest.computeLink;
            statsResponse.statsList = new ArrayList<ComputeStats>();
            statsResponse.statsList.add(cStat);
            statsResponse.taskStage = statsRequest.nextStage;
            this.sendRequest(Operation.createPatch(UriUtils.buildUri(getHost(), statsRequest.parentTaskLink))
                    .setBody(statsResponse));
            break;
        default:
            super.handleRequest(op);
        }
    }

}
