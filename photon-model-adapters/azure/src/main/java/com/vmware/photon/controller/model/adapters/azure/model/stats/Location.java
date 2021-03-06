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

package com.vmware.photon.controller.model.adapters.azure.model.stats;

import java.util.ArrayList;
import java.util.List;

/**
 * PODO equivalent of the JSON returned by /metricDefinitions endpoint
 *
 */
public class Location {
    private String tableEndpoint;
    private List<TableInfo> tableInfo = new ArrayList<TableInfo>();
    private String partitionKey;

    public List<TableInfo> getTableInfo() {
        return this.tableInfo;
    }

    public String getPartitionKey() {
        return this.partitionKey;
    }
}
