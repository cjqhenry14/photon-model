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

import java.util.Map;

import com.vmware.photon.controller.model.adapters.vsphere.util.connection.Connection;
import com.vmware.photon.controller.model.adapters.vsphere.util.connection.helpers.BaseHelper;
import com.vmware.photon.controller.model.adapters.vsphere.util.connection.helpers.GetMoRef;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeStateWithDescription;
import com.vmware.vim25.InvalidPropertyFaultMsg;
import com.vmware.vim25.ManagedObjectReference;
import com.vmware.vim25.RuntimeFaultFaultMsg;

/**
 * A simple client for vsphere. Consist of a valid connection and some context.
 */
public class Client extends BaseHelper {
    private final ComputeStateWithDescription computeState;

    private final ManagedObjectReference datacenter;
    private final GetMoRef get;
    private ManagedObjectReference vmFolder;
    private ManagedObjectReference datastore;

    public Client(Connection connection, ComputeStateWithDescription stateWithDescription)
            throws ClientException {
        super(connection);

        this.get = new GetMoRef(this.connection);
        this.computeState = stateWithDescription;

        String id = stateWithDescription.description.dataCenterId;

        try {
            datacenter = findDatacenterById(id);
        } catch (RuntimeFaultFaultMsg | InvalidPropertyFaultMsg e) {
            throw new ClientException(
                    String.format("Error looking for datacenter for id '%s'", id), e);
        }
    }

    public ManagedObjectReference getDatacenter() {
        return this.datacenter;
    }

    public ManagedObjectReference getVmFolder()
            throws InvalidPropertyFaultMsg, RuntimeFaultFaultMsg {
        if (this.vmFolder == null) {
            this.vmFolder = get
                    .entityProp(connection.getServiceContent().getRootFolder(), "vmFolder");
        }
        return this.vmFolder;
    }

    public ManagedObjectReference getDatastore()
            throws InvalidPropertyFaultMsg, RuntimeFaultFaultMsg, ClientException {

        if (this.datastore == null) {
            this.datastore = findDatastoreById(computeState.description.dataStoreId);
        }

        return this.datastore;
    }

    /**
     * Return the datastore for the given id or fails with ClientException if nothing is found.
     * @param id
     * @return
     * @throws InvalidPropertyFaultMsg
     * @throws RuntimeFaultFaultMsg
     * @throws ClientException
     */
    private ManagedObjectReference findDatastoreById(String id)
            throws InvalidPropertyFaultMsg, RuntimeFaultFaultMsg, ClientException {
        Map<String, ManagedObjectReference> allFound = get
                .inContainerByType(connection.getServiceContent().getRootFolder(),
                        VimTypes.DATASTORE);

        if (id == null || id.length() == 0) {
            if (allFound.isEmpty()) {
                throw new ClientException("No datastores found");
            } else if (allFound.size() > 1) {
                throw new ClientException(
                        "More than one datastore exist, please specify a datastore");
            } else {
                return allFound.values().iterator().next();
            }
        }

        for (ManagedObjectReference moref : allFound.values()) {
            if (moref.getValue().equals(id)) {
                return moref;
            }
        }

        throw new ClientException(String.format("Datastore for id '%s' not found", id));
    }

    /**
     * Returns the datacenter for an Id, or the first datacenter found if id is empty/null
     * @param id
     * @return
     * @throws InvalidPropertyFaultMsg
     * @throws RuntimeFaultFaultMsg
     * @throws ClientException
     */
    private ManagedObjectReference findDatacenterById(String id) throws InvalidPropertyFaultMsg,
            RuntimeFaultFaultMsg, ClientException {

        Map<String, ManagedObjectReference> allDatacenters = get
                .inContainerByType(connection.getServiceContent().getRootFolder(),
                        VimTypes.DATACENTER);

        if (id == null || id.length() == 0) {
            // just use the first found
            if (allDatacenters.isEmpty()) {
                throw new ClientException("No datacenters found");
            } else {
                return allDatacenters.values().iterator().next();
            }
        }

        for (ManagedObjectReference moref : allDatacenters.values()) {
            if (moref.getValue().equals(id)) {
                return moref;
            }
        }

        throw new ClientException(String.format("Datacenter for id '%s' not found", id));
    }

    public void deleteInstance() {
        // TODO
    }

    public void createInstance() {
        // TODO
    }

    public static class ClientException extends Exception {
        private static final long serialVersionUID = 1L;

        public ClientException(String message, Throwable cause) {
            super(message, cause);
        }

        public ClientException(String message) {
            super(message);
        }
    }
}
