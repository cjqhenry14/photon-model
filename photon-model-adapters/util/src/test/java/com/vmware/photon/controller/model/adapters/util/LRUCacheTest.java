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

package com.vmware.photon.controller.model.adapters.util;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Unit test for {@link LRUCache}
 */
public class LRUCacheTest {
    public static int INITIAL_SIZE = 2;
    public static int MAXIMUM_SIZE = 5;
    public static int INDEX_TO_ACCESS = 0;
    public static final String TEST_ENTRY = "test-entry";

    @Test
    public void testLRUCacheSizeIsBounded() {
        LRUCache<Integer, String> testCache = new LRUCache<Integer, String>(INITIAL_SIZE,
                MAXIMUM_SIZE);
        for (int i = 0; i < MAXIMUM_SIZE + 2; i++) {
            testCache.put(i, TEST_ENTRY);
        }
        assertTrue(testCache.size() == MAXIMUM_SIZE);
    }

    @Test
    public void testLRUCacheRemovesEntriesBasedOnAccessOrder() {
        LRUCache<Integer, String> testCache = new LRUCache<Integer, String>(INITIAL_SIZE,
                MAXIMUM_SIZE);
        // Based on insertion order "0" is the eldest entry
        for (int i = 0; i < MAXIMUM_SIZE; i++) {
            testCache.put(i, TEST_ENTRY);
        }
        // Accessing "0" makes it the youngest entry and it should not be evicted when a new element
        // is added.
        testCache.get(INDEX_TO_ACCESS);
        testCache.put(MAXIMUM_SIZE, TEST_ENTRY);
        assertTrue(testCache.containsKey(INDEX_TO_ACCESS));

        // The entry at "1" should be the eldest and evicted to make room.
        assertFalse(testCache.containsKey(INDEX_TO_ACCESS + 1));
    }

}