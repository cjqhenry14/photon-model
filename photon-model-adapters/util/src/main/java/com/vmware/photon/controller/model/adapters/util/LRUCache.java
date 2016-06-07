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

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Implements a least recently used cache by using some of existing Java APIs. The two distinguishing
 * features of this cache
 *   - It has a fixed size
 *   - Every time a new entry is added to this cache, an additional check is performed to see if the cache
 *   has reached its maximum size and then the last entry is evicted to maintain a fixed size.
 */
public class LRUCache<K, V> extends LinkedHashMap<K, V> {
    private static final long serialVersionUID = 1L;
    private static final float loadFactor = 0.75f;
    public int maxSize;

    public LRUCache(int initialSize, int maxSize) {
        // Maintains the entries of the map in the access order of the individual components
        super(initialSize, loadFactor, true);
        this.maxSize = maxSize;
    }

    /**
     *This method is called each time a new entry is added to the map. It does bounds checking to see if the map
     *has grown to its maximum size and then evicts entries based on the set algorithm (access order or insertion order).
     */
    protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
        return this.size() > maxSize;
    }
}
