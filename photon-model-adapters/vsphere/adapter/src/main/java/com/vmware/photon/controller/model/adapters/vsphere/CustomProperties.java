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

package com.vmware.photon.controller.model.adapters.vsphere;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.resources.SnapshotService.SnapshotState;
import com.vmware.vim25.ManagedObjectReference;

/**
 * Provides a convinience get/put interface over classes that hold customProperties.
 * Does type conversion, default values and creates and empty map and assigns it to the
 * owner on the first call to any put* method.
 */
public class CustomProperties {
    /**
     * Key for the MoRef of a vm provisioned by the adapter.
     */
    public static final String VM_MOREF = "vm.moref";

    public static final String SNAPSHOT_MOREF = "snapshot.moref";

    public static final String VM_FOLDER_PATH = "vm.folder.path";

    private final Supplier<Map<String, String>> getPropsForRead;
    private final Supplier<Map<String, String>> getPropsForWrite;
    private final Consumer<String> remove;

    public CustomProperties(SnapshotState snapshot) {
        getPropsForRead = () -> {
            if (snapshot.customProperties == null) {
                return Collections.emptyMap();
            } else {
                return snapshot.customProperties;
            }
        };

        getPropsForWrite = () -> {
            if (snapshot.customProperties == null) {
                snapshot.customProperties = new HashMap<>();
            }

            return snapshot.customProperties;
        };

        remove = (String key) -> {
            if (snapshot.customProperties != null) {
                snapshot.customProperties.remove(key);
            }
        };
    }

    public static CustomProperties of(ComputeDescription desc) {
        return new CustomProperties(desc);
    }

    public static CustomProperties of(ComputeState desc) {
        return new CustomProperties(desc);
    }

    public static CustomProperties of(SnapshotState snapshot) {
        return new CustomProperties(snapshot);
    }

    protected CustomProperties(ComputeDescription description) {
        if (description == null) {
            throw new NullPointerException("description cannot be null");
        }

        getPropsForRead = () -> {
            if (description.customProperties == null) {
                return Collections.emptyMap();
            } else {
                return description.customProperties;
            }
        };

        getPropsForWrite = () -> {
            if (description.customProperties == null) {
                description.customProperties = new HashMap<>();
            }

            return description.customProperties;
        };

        remove = (String key) -> {
            if (description.customProperties != null) {
                description.customProperties.remove(key);
            }
        };
    }

    protected CustomProperties(ComputeState state) {
        if (state == null) {
            throw new NullPointerException("state cannot be null");
        }

        getPropsForRead = () -> {
            if (state.customProperties == null) {
                return Collections.emptyMap();
            } else {
                return state.customProperties;
            }
        };

        getPropsForWrite = () -> {
            if (state.customProperties == null) {
                state.customProperties = new HashMap<>();
            }

            return state.customProperties;
        };

        remove = (String key) -> {
            if (state.customProperties != null) {
                state.customProperties.remove(key);
            }
        };
    }

    public String getString(String key) {
        return getString(key, null);
    }

    public String getString(String key, String defaultValue) {
        String result = getPropsForRead.get().get(key);
        if (result == null) {
            return defaultValue;
        } else {
            return result;
        }
    }

    public ManagedObjectReference getMoRef(String key) {
        return VimUtils.convertStringToMoRef(getString(key));
    }

    public Integer getInt(String key, Integer defaultValue) {
        String s = getString(key);
        if (s == null) {
            return defaultValue;
        }

        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    public Long getLong(String key, Long defaultValue) {
        String s = getString(key);
        if (s == null) {
            return defaultValue;
        }

        try {
            return Long.parseLong(s);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    public CustomProperties put(String key, ManagedObjectReference moref) {
        return put(key, VimUtils.convertMoRefToString(moref));
    }

    public CustomProperties put(String key, String s) {
        if (s == null) {
            remove.accept(key);
        } else {
            getPropsForWrite.get().put(key, s);
        }

        return this;
    }

    public CustomProperties put(String key, Integer i) {
        if (i == null) {
            remove.accept(key);
        } else {
            put(key, Integer.toString(i));
        }

        return this;
    }

    public CustomProperties put(String key, Long i) {
        if (i == null) {
            remove.accept(key);
        } else {
            put(key, Long.toString(i));
        }

        return this;
    }
}
