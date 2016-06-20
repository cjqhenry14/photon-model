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

import java.lang.reflect.Constructor;
import java.net.URI;

import com.vmware.photon.controller.model.adapters.vsphere.util.VimNames;
import com.vmware.photon.controller.model.adapters.vsphere.util.VimPath;
import com.vmware.photon.controller.model.adapters.vsphere.util.connection.Connection;
import com.vmware.photon.controller.model.adapters.vsphere.util.connection.WaitForValues;
import com.vmware.vim25.InvalidCollectorVersionFaultMsg;
import com.vmware.vim25.InvalidPropertyFaultMsg;
import com.vmware.vim25.LocalizedMethodFault;
import com.vmware.vim25.ManagedObjectReference;
import com.vmware.vim25.RuntimeFaultFaultMsg;
import com.vmware.vim25.TaskInfo;
import com.vmware.vim25.TaskInfoState;

/**
 */
public final class VimUtils {

    public static final String SCHEME_DATASTORE = "datastore";

    private static final String DELIMITER = ":";
    public static final String EXCEPTION_SUFFIX = "FaultMsg";

    private VimUtils() {

    }

    /**
     * This method never returns but throws an Exception wrapping the fault.
     *
     * @param lmf
     * @param <T>
     * @return
     * @throws Exception
     */
    public static <T> T rethrow(LocalizedMethodFault lmf) throws Exception {
        Class<?> type = lmf.getFault().getClass();
        String possibleWrapperType = type.getName() + EXCEPTION_SUFFIX;

        Exception ex;
        try {
            ClassLoader cl = type.getClassLoader();
            Class<?> faultClass = cl.loadClass(possibleWrapperType);
            Constructor<?> ctor = faultClass.getConstructor(String.class, type);
            ex = (Exception) ctor.newInstance(lmf.getLocalizedMessage(), lmf.getFault());
        } catch (ReflectiveOperationException e) {
            throw new GenericVimFault(lmf.getLocalizedMessage(), lmf.getFault());
        }

        throw ex;
    }

    /**
     * Converts an URI in the format file://datastoreName/path/to/file to a string like
     * "[datastoreName] /path/to/file".
     *
     * @param uri
     * @return
     */
    public static String uriToDatastorePath(URI uri) {
        if (uri == null) {
            return null;
        }

        if (!SCHEME_DATASTORE.equals(uri.getScheme())) {
            throw new IllegalArgumentException("Expected datastore scheme, found" + uri);
        }

        String path = uri.getSchemeSpecificPart();
        // strip leading slashes
        int i = 0;
        while (i < path.length() && path.charAt(i) == '/') {
            i++;
        }
        path = path.substring(i);

        // separator between datastore and path
        i = path.indexOf('/');
        if (i <= 0) {
            throw new IllegalArgumentException("Path to datastore not found:" + uri);
        }

        String ds = path.substring(0, i);
        path = path.substring(i + 1);

        return String.format("[%s] %s", ds, path);
    }

    /**
     * Serializes a MoRef into a String.
     *
     * @param ref
     * @return
     */
    public static String convertMoRefToString(ManagedObjectReference ref) {
        if (ref == null) {
            return null;
        }

        return ref.getType() + DELIMITER + ref.getValue();
    }

    /**
     * Return the first non-null value or null if all values are null.
     * @param values
     * @return
     */
    @SafeVarargs
    public static <T> T firstNonNull(T... values) {
        for (T s : values) {
            if (s != null) {
                return s;
            }
        }

        return null;
    }

    /**
     * Builds a MoRef from a string produced bu {@link #convertMoRefToString(ManagedObjectReference)}
     * @param s
     * @return
     */
    public static ManagedObjectReference convertStringToMoRef(String s) {
        if (s == null) {
            return null;
        }

        String[] parts = s.split(":");
        if (parts.length != 2) {
            throw new IllegalArgumentException(
                    "Cannot convert string '" + s
                            + "' to ManagedObjectReference: expected Type:Value format");
        }

        if (parts[0].length() == 0) {
            throw new IllegalArgumentException("Missing Type in '" + s + "'");
        }

        if (parts[1].length() == 0) {
            throw new IllegalArgumentException("Missing Value in '" + s + "'");
        }
        ManagedObjectReference ref = new ManagedObjectReference();
        ref.setType(parts[0]);
        ref.setValue(parts[1]);
        return ref;
    }

    public static TaskInfo waitTaskEnd(Connection connection, ManagedObjectReference task)
            throws InvalidCollectorVersionFaultMsg, InvalidPropertyFaultMsg, RuntimeFaultFaultMsg {
        WaitForValues waitForValues = new WaitForValues(connection);

        Object[] info = waitForValues.wait(task,
                new String[] { VimPath.task_info },
                new String[] { VimPath.task_info_state },
                new Object[][] { new Object[] {
                        TaskInfoState.SUCCESS,
                        TaskInfoState.ERROR
                } });

        return (TaskInfo) info[0];
    }

    public static boolean isVirtualMachine(ManagedObjectReference obj) {
        if (obj == null) {
            return false;
        }

        return VimNames.TYPE_VM.equals(obj.getType());
    }
}
