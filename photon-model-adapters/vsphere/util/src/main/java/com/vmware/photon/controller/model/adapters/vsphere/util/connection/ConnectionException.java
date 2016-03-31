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

package com.vmware.photon.controller.model.adapters.vsphere.util.connection;

/**
 * ConnectionException is the base exception thrown by connection classes,
 * making this a runtime exception means that catching it is optional preventing clutter,
 * basing all connection related exceptions on this class means
 * that you may decide to catch ConnectionException to deal with any issues underneath
 * the connection infrastructure. Basing all connection classes' exceptions
 * on ConnectionException means that all new exceptions originating in the connection
 * related utilities are decoupled from any other subsystem.
 */
public class ConnectionException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public ConnectionException(String message, Throwable throwable) {
        super(message, throwable);
    }
}
