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

import com.vmware.vim25.LocalizedMethodFault;

/**
 */
public final class VimUtils {

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
}
