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

package com.vmware.photon.controller.model.tasks;

import java.util.EnumSet;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;

import org.junit.Before;
import org.junit.Test;

import com.vmware.xenon.common.Service;

/**
 * This class implements tests for the
 * {@link ResourceAllocationTaskFactoryService} class.
 */
public class ResourceAllocationTaskFactoryServiceTest {
    private ResourceAllocationTaskFactoryService resourceAllocationTaskFactoryService;

    @Before
    public void setUpTest() {
        this.resourceAllocationTaskFactoryService = new ResourceAllocationTaskFactoryService();
    }

    @Test
    public void testServiceOptions() {

        EnumSet<Service.ServiceOption> expected = EnumSet.of(
                Service.ServiceOption.REPLICATION,
                Service.ServiceOption.CONCURRENT_GET_HANDLING,
                Service.ServiceOption.CONCURRENT_UPDATE_HANDLING,
                Service.ServiceOption.INSTRUMENTATION,
                Service.ServiceOption.FACTORY);

        assertThat(this.resourceAllocationTaskFactoryService.getOptions(),
                is(expected));
    }

    @Test
    public void testCreateServiceInstance() throws Throwable {
        Service service = this.resourceAllocationTaskFactoryService
                .createServiceInstance();
        assertThat(service, instanceOf(ResourceAllocationTaskService.class));
    }
}
