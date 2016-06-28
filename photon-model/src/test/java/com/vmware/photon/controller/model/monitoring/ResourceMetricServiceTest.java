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

package com.vmware.photon.controller.model.monitoring;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.net.URI;
import java.util.EnumSet;
import java.util.concurrent.TimeUnit;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import org.junit.runners.Suite.SuiteClasses;
import org.junit.runners.model.InitializationError;
import org.junit.runners.model.RunnerBuilder;

import com.vmware.photon.controller.model.helpers.BaseModelTest;
import com.vmware.photon.controller.model.monitoring.ResourceMetricService;

import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;

/**
 * This class implements tests for the {@link ResourceMetricService} class.
 */
@RunWith(ResourceMetricServiceTest.class)
@SuiteClasses({ ResourceMetricServiceTest.ConstructorTest.class,
        ResourceMetricServiceTest.HandleStartTest.class })
public class ResourceMetricServiceTest extends Suite {

    public ResourceMetricServiceTest(Class<?> klass, RunnerBuilder builder)
            throws InitializationError {
        super(klass, builder);
    }

    private static ResourceMetricService.ResourceMetric buildValidStartState() {
        ResourceMetricService.ResourceMetric statState = new ResourceMetricService.ResourceMetric();
        statState.value = Double.valueOf(1000);
        statState.timestampMicrosUtc = TimeUnit.MICROSECONDS.toMillis(Utils.getNowMicrosUtc());
        return statState;
    }

    /**
     * This class implements tests for the constructor.
     */
    public static class ConstructorTest {
        private ResourceMetricService StatsService = new ResourceMetricService();

        @Before
        public void setupTest() {
            this.StatsService = new ResourceMetricService();
        }

        @Test
        public void testServiceOptions() {
            EnumSet<Service.ServiceOption> expected = EnumSet.of(
                    Service.ServiceOption.CONCURRENT_GET_HANDLING,
                    Service.ServiceOption.PERSISTENCE,
                    Service.ServiceOption.ON_DEMAND_LOAD);
            assertThat(this.StatsService.getOptions(), is(expected));
        }
    }

    /**
     * This class implements tests for the handleStart method.
     */
    public static class HandleStartTest extends BaseModelTest {
        @Test
        public void testValidStartState() throws Throwable {
            ResourceMetricService.ResourceMetric startState = buildValidStartState();
            ResourceMetricService.ResourceMetric returnState = postServiceSynchronously(
                    ResourceMetricService.FACTORY_LINK,
                            startState, ResourceMetricService.ResourceMetric.class);

            assertNotNull(returnState);
            assertThat(returnState.value, is(startState.value));
            assertThat(returnState.timestampMicrosUtc, is(startState.timestampMicrosUtc));
        }

        @Test
        public void testDuplicatePost() throws Throwable {
            ResourceMetricService.ResourceMetric startState = buildValidStartState();
            ResourceMetricService.ResourceMetric returnState = postServiceSynchronously(
                    ResourceMetricService.FACTORY_LINK,
                            startState, ResourceMetricService.ResourceMetric.class);

            assertNotNull(returnState);
            assertThat(returnState.value, is(startState.value));
            startState.value = Double.valueOf(2000);
            returnState = postServiceSynchronously(ResourceMetricService.FACTORY_LINK,
                            startState, ResourceMetricService.ResourceMetric.class);
            assertThat(returnState.value, is(startState.value));
        }

        @Test
        public void testMissingBody() throws Throwable {
            postServiceSynchronously(
                    ResourceMetricService.FACTORY_LINK,
                    null,
                    ResourceMetricService.ResourceMetric.class,
                    IllegalArgumentException.class);
        }

        @Test
        public void testMissingValue() throws Throwable {
            ResourceMetricService.ResourceMetric startState = buildValidStartState();
            startState.value = null;

            postServiceSynchronously(ResourceMetricService.FACTORY_LINK,
                    startState, ResourceMetricService.ResourceMetric.class,
                    IllegalArgumentException.class);
        }

        @Test
        public void testMissingTimestamp() throws Throwable {
            ResourceMetricService.ResourceMetric startState = buildValidStartState();
            startState.timestampMicrosUtc = null;

            postServiceSynchronously(ResourceMetricService.FACTORY_LINK,
                    startState, ResourceMetricService.ResourceMetric.class,
                    IllegalArgumentException.class);
        }

        @Test
        public void testIdempotentPostService() throws Throwable {
            URI factoryUri = UriUtils.buildFactoryUri(host, ResourceMetricService.class);
            this.host.startFactory(new ResourceMetricService());
            this.host.waitForServiceAvailable(ResourceMetricService.FACTORY_LINK);

            ResourceMetricService.ResourceMetric metric = new ResourceMetricService.ResourceMetric();
            metric.documentSelfLink = "default";
            metric.value = new Double(1000);
            metric.timestampMicrosUtc = Utils.getNowMicrosUtc();

            this.host.testStart(1);
            this.host.send(Operation.createPost(factoryUri)
                    .setBody(metric)
                    .setCompletion(
                            (o, e) -> {
                                if (e != null) {
                                    this.host.failIteration(e);
                                    return;
                                }

                                this.host.send(Operation.createPost(factoryUri)
                                        .setBody(metric)
                                        .setCompletion(
                                                (o2, e2) -> {
                                                    if (e2 != null) {
                                                        this.host.failIteration(e2);
                                                        return;
                                                    }

                                                    ResourceMetricService.ResourceMetric metric2 = o2
                                                            .getBody(
                                                                    ResourceMetricService.ResourceMetric.class);
                                                    try {
                                                        assertNotNull(metric2);
                                                        assertEquals(new Double(1000),
                                                                metric2.value);
                                                        this.host.completeIteration();
                                                    } catch (AssertionError e3) {
                                                        this.host.failIteration(e3);
                                                    }
                                                }));
                            }));
            this.host.testWait();
        }
    }
}
