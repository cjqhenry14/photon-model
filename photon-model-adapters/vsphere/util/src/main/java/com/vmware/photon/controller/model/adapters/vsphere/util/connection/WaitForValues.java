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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.w3c.dom.Element;

import com.vmware.vim25.HttpNfcLeaseState;
import com.vmware.vim25.InvalidCollectorVersionFaultMsg;
import com.vmware.vim25.InvalidPropertyFaultMsg;
import com.vmware.vim25.ManagedObjectReference;
import com.vmware.vim25.ObjectSpec;
import com.vmware.vim25.ObjectUpdate;
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

    public static final int DEFAULT_MAX_WAIT_SECONDS = 30;

    public WaitForValues(final Connection connection) {
        super(connection);
    }

    /**
     * @see #wait(ManagedObjectReference, String[], String[], Object[][], Integer)
     * @param moRef
     * @param fetchProps
     * @param propsToMatch
     * @param propsMatchValues
     * @return
     * @throws InvalidPropertyFaultMsg
     * @throws RuntimeFaultFaultMsg
     * @throws InvalidCollectorVersionFaultMsg
     */
    public Object[] wait(ManagedObjectReference moRef,
            String[] fetchProps, String[] propsToMatch, Object[][] propsMatchValues)
            throws InvalidPropertyFaultMsg, RuntimeFaultFaultMsg,
            InvalidCollectorVersionFaultMsg {
        return wait(moRef, fetchProps, propsToMatch, propsMatchValues, null);
    }

    /**
     * Handle Updates for a single object. waits till expected values of
     * properties to check are reached Destroys the ObjectFilter when done.
     * The matching properties will be added to the list of properties to fetch.
     *
     * @param moRef       MOR of the Object to wait for
     * @param fetchProps  Properties list to filter
     * @param propsToMatch Properties list to check for expected values these be properties
     *                     of a property in the filter properties list
     * @param propsMatchValues values for properties to end the wait
     * @param maxWaitSeconds how long to wait for the condition to be met, use null to wait forever
     * @return the requested properties or null if expected values not reached within timeout
     * @throws RuntimeFaultFaultMsg
     * @throws InvalidPropertyFaultMsg
     * @throws InvalidCollectorVersionFaultMsg
     *
     */
    public Object[] wait(ManagedObjectReference moRef,
            String[] fetchProps, String[] propsToMatch, Object[][] propsMatchValues,
            Integer maxWaitSeconds)
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
        Object[] endVals = new Object[propsToMatch.length];
        String stateVal = null;

        PropertyFilterSpec spec = propertyFilterSpec(moRef, fetchProps, propsToMatch);

        filterSpecRef = vimPort.createFilter(serviceContent.getPropertyCollector(), spec, true);

        boolean reached = false;

        UpdateSet updateset;
        List<PropertyFilterUpdate> filtupary;
        List<ObjectUpdate> objupary;
        List<PropertyChange> propchgary;

        // override maxWaitSeconds to give the timeout a chance to be hit earlier
        WaitOptions waitOptions = new WaitOptions();
        waitOptions.setMaxWaitSeconds(DEFAULT_MAX_WAIT_SECONDS);

        long timeout = -1;
        if (maxWaitSeconds != null) {
            timeout = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(maxWaitSeconds);
        }

        while (!reached && shouldContinue(timeout)) {
            updateset = vimPort.waitForUpdatesEx(serviceContent.getPropertyCollector(),
                    version, waitOptions);
            if (updateset == null || updateset.getFilterSet() == null) {
                continue;
            }
            version = updateset.getVersion();

            // Make this code more general purpose when PropCol changes later.
            filtupary = updateset.getFilterSet();

            for (PropertyFilterUpdate filtup : filtupary) {
                objupary = filtup.getObjectSet();
                for (ObjectUpdate objup : objupary) {
                    propchgary = objup.getChangeSet();
                    for (PropertyChange propchg : propchgary) {
                        updateValues(propsToMatch, endVals, propchg);
                    }
                }
            }

            // Check if the expected values have been reached and exit the loop
            // if done.
            // Also exit the WaitForUpdates loop if this is the case.
            for (int chgi = 0; chgi < endVals.length && !reached; chgi++) {
                for (int vali = 0; vali < propsMatchValues[chgi].length && !reached; vali++) {
                    Object expectedValue = propsMatchValues[chgi][vali];
                    Object endVal = endVals[chgi];

                    if (endVal == null) {
                        // Do Nothing
                    } else if (endVal.toString().contains("val: null")) {
                        // Due to some issue in JAX-WS De-serialization getting the information from
                        // the nodes
                        Element stateElement = (Element) endVal;
                        if (stateElement.getFirstChild() != null) {
                            stateVal = stateElement.getFirstChild().getTextContent();
                            reached =
                                    expectedValue.toString().equalsIgnoreCase(stateVal) || reached;
                        }
                    } else {
                        expectedValue = propsMatchValues[chgi][vali];
                        reached = expectedValue.equals(endVal) || reached;
                        stateVal = "filtervals";
                    }
                }
            }
        }

        if (!reached) {
            // got here but condition not reached; timeout
            return null;
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
                retVal = fetchFinalValues(moRef, fetchProps);
            }
        } else {
            retVal = new Object[] { HttpNfcLeaseState.ERROR };
        }
        return retVal;
    }

    private Object[] fetchFinalValues(ManagedObjectReference moRef, String[] fetchProps)
            throws InvalidPropertyFaultMsg, RuntimeFaultFaultMsg {

        Object[] res = new Object[fetchProps.length];
        GetMoRef get = new GetMoRef(connection);

        Map<String, Object> prop2Value = get.entityProps(moRef, fetchProps);
        for (int i = 0; i < fetchProps.length; i++) {
            String propName = fetchProps[i];
            Object propValue = prop2Value.get(propName);
            res[i] = propValue;
        }

        return res;
    }

    /**
     * Should attempts continue. -1 means yes, otherwise check time timeout value against current
     * time.
     *
     * @param timeout
     * @return
     */
    private boolean shouldContinue(long timeout) {
        if (timeout <= 0) {
            return true;
        }

        return timeout > System.currentTimeMillis();
    }

    private PropertyFilterSpec propertyFilterSpec(ManagedObjectReference objmor,
            String[] filterProps, String[] propsToMatch) {
        PropertyFilterSpec spec = new PropertyFilterSpec();
        ObjectSpec oSpec = new ObjectSpec();
        oSpec.setObj(objmor);
        oSpec.setSkip(Boolean.FALSE);
        spec.getObjectSet().add(oSpec);

        Set<String> uniqPropSet = new HashSet<>(Arrays.asList(filterProps));
        uniqPropSet.addAll(Arrays.asList(propsToMatch));

        PropertySpec pSpec = new PropertySpec();
        pSpec.getPathSet().addAll(new ArrayList<>(uniqPropSet));
        pSpec.setType(objmor.getType());
        spec.getPropSet().add(pSpec);
        return spec;
    }

    private void updateValues(String[] props, Object[] vals, PropertyChange propchg) {
        for (int i = 0; i < props.length; i++) {
            if (propchg.getName().equals(props[i])) {
                if (propchg.getOp() == PropertyChangeOp.REMOVE) {
                    vals[i] = null;
                } else {
                    vals[i] = propchg.getVal();
                }
            }
        }
    }
}
