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

package com.vmware.photon.controller.model.adapters.vsphere.util.connection.helpers;

import java.util.Arrays;
import java.util.List;

import org.w3c.dom.Element;

import com.vmware.photon.controller.model.adapters.vsphere.util.connection.Connection;
import com.vmware.vim25.HttpNfcLeaseState;
import com.vmware.vim25.InvalidCollectorVersionFaultMsg;
import com.vmware.vim25.InvalidPropertyFaultMsg;
import com.vmware.vim25.ManagedObjectReference;
import com.vmware.vim25.ObjectSpec;
import com.vmware.vim25.ObjectUpdate;
import com.vmware.vim25.ObjectUpdateKind;
import com.vmware.vim25.PropertyChange;
import com.vmware.vim25.PropertyChangeOp;
import com.vmware.vim25.PropertyFilterSpec;
import com.vmware.vim25.PropertyFilterUpdate;
import com.vmware.vim25.PropertySpec;
import com.vmware.vim25.RuntimeFaultFaultMsg;
import com.vmware.vim25.ServiceContent;
import com.vmware.vim25.UpdateSet;
import com.vmware.vim25.VimPortType;
import com.vmware.vim25.WaitOptions;

public class WaitForValues extends BaseHelper {
    public WaitForValues(final Connection connection) {
        super(connection);
    }

    /**
     * Handle Updates for a single object. waits till expected values of
     * properties to check are reached Destroys the ObjectFilter when done.
     *
     * @param objmor       MOR of the Object to wait for
     * @param filterProps  Properties list to filter
     * @param endWaitProps Properties list to check for expected values these be properties
     *                     of a property in the filter properties list
     * @param expectedVals values for properties to end the wait
     * @return true indicating expected values were met, and false otherwise
     * @throws RuntimeFaultFaultMsg
     * @throws InvalidPropertyFaultMsg
     * @throws InvalidCollectorVersionFaultMsg
     *
     */
    public Object[] wait(ManagedObjectReference objmor,
            String[] filterProps, String[] endWaitProps, Object[][] expectedVals)
            throws InvalidPropertyFaultMsg, RuntimeFaultFaultMsg,
            InvalidCollectorVersionFaultMsg {
        VimPortType vimPort;
        ManagedObjectReference filterSpecRef = null;
        ServiceContent serviceContent;

        try {
            vimPort = this.connection.getVimPort();
            serviceContent = this.connection.getServiceContent();
        } catch (Throwable cause) {
            throw new BaseHelper.HelperException(cause);
        }

        // version string is initially null
        String version = "";
        Object[] endVals = new Object[endWaitProps.length];
        Object[] filterVals = new Object[filterProps.length];
        String stateVal = null;

        PropertyFilterSpec spec = propertyFilterSpec(objmor, filterProps);

        filterSpecRef =
                vimPort.createFilter(serviceContent.getPropertyCollector(), spec,
                        true);

        boolean reached = false;

        UpdateSet updateset = null;
        List<PropertyFilterUpdate> filtupary = null;
        List<ObjectUpdate> objupary = null;
        List<PropertyChange> propchgary = null;
        while (!reached) {
            updateset =
                    vimPort.waitForUpdatesEx(serviceContent.getPropertyCollector(),
                            version, new WaitOptions());
            if (updateset == null || updateset.getFilterSet() == null) {
                continue;
            }
            version = updateset.getVersion();

            // Make this code more general purpose when PropCol changes later.
            filtupary = updateset.getFilterSet();

            for (PropertyFilterUpdate filtup : filtupary) {
                objupary = filtup.getObjectSet();
                for (ObjectUpdate objup : objupary) {
                    // TODO: https://jira-hzn.eng.vmware.com/browse/VSYM-331
                    if (objup.getKind() == ObjectUpdateKind.MODIFY
                            || objup.getKind() == ObjectUpdateKind.ENTER
                            || objup.getKind() == ObjectUpdateKind.LEAVE) {
                        propchgary = objup.getChangeSet();
                        for (PropertyChange propchg : propchgary) {
                            updateValues(endWaitProps, endVals, propchg);
                            updateValues(filterProps, filterVals, propchg);
                        }
                    }
                }
            }

            Object expctdval = null;
            // Check if the expected values have been reached and exit the loop
            // if done.
            // Also exit the WaitForUpdates loop if this is the case.
            for (int chgi = 0; chgi < endVals.length && !reached; chgi++) {
                for (int vali = 0; vali < expectedVals[chgi].length && !reached; vali++) {
                    expctdval = expectedVals[chgi][vali];
                    if (endVals[chgi] == null) {
                        // Do Nothing
                    } else if (endVals[chgi].toString().contains("val: null")) {
                        // Due to some issue in JAX-WS De-serialization getting the information from
                        // the nodes
                        Element stateElement = (Element) endVals[chgi];
                        if (stateElement != null && stateElement.getFirstChild() != null) {
                            stateVal = stateElement.getFirstChild().getTextContent();
                            reached = expctdval.toString().equalsIgnoreCase(stateVal) || reached;
                        }
                    } else {
                        expctdval = expectedVals[chgi][vali];
                        reached = expctdval.equals(endVals[chgi]) || reached;
                        stateVal = "filtervals";
                    }
                }
            }
        }
        Object[] retVal = null;
        // Destroy the filter when we are done.
        try {
            vimPort.destroyPropertyFilter(filterSpecRef);
        } catch (RuntimeFaultFaultMsg e) {
            e.printStackTrace();
        }
        if (stateVal != null) {
            if (stateVal.equalsIgnoreCase("ready")) {
                retVal = new Object[] { HttpNfcLeaseState.READY };
            }
            if (stateVal.equalsIgnoreCase("error")) {
                retVal = new Object[] { HttpNfcLeaseState.ERROR };
            }
            if (stateVal.equals("filtervals")) {
                retVal = filterVals;
            }
        } else {
            retVal = new Object[] { HttpNfcLeaseState.ERROR };
        }
        return retVal;
    }

    public PropertyFilterSpec propertyFilterSpec(ManagedObjectReference objmor,
            String[] filterProps) {
        PropertyFilterSpec spec = new PropertyFilterSpec();
        ObjectSpec oSpec = new ObjectSpec();
        oSpec.setObj(objmor);
        oSpec.setSkip(Boolean.FALSE);
        spec.getObjectSet().add(oSpec);

        PropertySpec pSpec = new PropertySpec();
        pSpec.getPathSet().addAll(Arrays.asList(filterProps));
        pSpec.setType(objmor.getType());
        spec.getPropSet().add(pSpec);
        return spec;
    }

    void updateValues(String[] props, Object[] vals, PropertyChange propchg) {
        for (int findi = 0; findi < props.length; findi++) {
            if (propchg.getName().lastIndexOf(props[findi]) >= 0) {
                if (propchg.getOp() == PropertyChangeOp.REMOVE) {
                    vals[findi] = "";
                } else {
                    vals[findi] = propchg.getVal();
                }
            }
        }
    }
}
