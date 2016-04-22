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

import com.vmware.vim25.LocalizedMethodFault;

/**
 */
public final class VimUtils {

    public static final String SCHEME_FILE = "file";

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
        String possibleWrapperType = type.getName() + "FaultMsg";

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

        if (!SCHEME_FILE.equals(uri.getScheme())) {
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
}
