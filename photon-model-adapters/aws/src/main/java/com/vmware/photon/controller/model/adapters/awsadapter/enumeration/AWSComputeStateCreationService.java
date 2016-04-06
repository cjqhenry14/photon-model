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

package com.vmware.photon.controller.model.adapters.awsadapter.enumeration;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

import com.amazonaws.services.ec2.model.Instance;

import com.vmware.photon.controller.model.adapters.awsadapter.AWSConstants;
import com.vmware.photon.controller.model.adapters.awsadapter.AWSUriPaths;
import com.vmware.photon.controller.model.adapters.util.AdapterUtils;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService;
import com.vmware.photon.controller.model.resources.ComputeService;
import com.vmware.photon.controller.model.resources.NetworkInterfaceService;
import com.vmware.photon.controller.model.resources.NetworkInterfaceService.NetworkInterfaceState;

import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.OperationJoin;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.common.UriUtils;

/**
 * Task service for the creation of compute state. It accepts a list of AWS instances that need to be created in the
 * local system.It also accepts a few additional fields required for mapping the referential integrity relationships
 * for the compute state when it is persisted in the local system.
 */
public class AWSComputeStateCreationService extends StatelessService {

    public static final String SELF_LINK = AWSUriPaths.AWS_COMPUTE_STATE_TASK_SERVICE;

    /**
     * Data holder for information related a compute state that needs to be created in the local system.
     *
     */
    public static class AWSComputeState {
        public List<Instance> instancesToBeCreated;
        public List<Operation> createOperations;
        public int instanceToBeCreatedCounter = 0;
        public String resourcePoolLink;
        public String parentComputeLink;

        /**
         * Link where you need to patch completion.
         */
        public URI parentTaskLink;
        boolean isMock;

        public AWSComputeState() {
            instancesToBeCreated = new ArrayList<Instance>();
            createOperations = new ArrayList<Operation>();
        }
    }

    @Override
    public void handlePatch(Operation op) {
        if (!op.hasBody()) {
            op.fail(new IllegalArgumentException("body is required"));
            return;
        }
        op.complete();

        AWSComputeState cs = op.getBody(AWSComputeState.class);
        if (cs.isMock) {
            // patch status to parent task
            AdapterUtils.sendPatchToTask(this, cs.parentTaskLink);
            return;
        }
        createComputeStates(cs);
    }

    /**
     * Method to create Compute States associated with the instances received from the AWS host.
     * @param next
     * @param instancesToBeCreated
     */
    private void createComputeStates(AWSComputeState cs) {
        if (cs.instancesToBeCreated != null && cs.instancesToBeCreated.size() > 0) {
            logInfo(
                    "Need to create %d compute states in the local system",
                    cs.instancesToBeCreated.size());
            for (int i = 0; i < cs.instancesToBeCreated.size(); i++) {
                cs.instanceToBeCreatedCounter = i;
                populateComputeState(cs);
            }
            kickOffComputeStateCreationAndPatchParent(cs);
        } else {
            logInfo("No instances need to be created in the local system");
        }
    }

    /**
     * Populates the compute state / network link associated with an AWS VM instance and creates an operation for posting it.
     * @param csDetails
     */
    private void populateComputeState(AWSComputeState cs) {
        Instance instance = cs.instancesToBeCreated.get(cs.instanceToBeCreatedCounter);
        ComputeService.ComputeState computeState = new ComputeService.ComputeState();
        computeState.id = UUID.randomUUID().toString();
        computeState.parentLink = cs.parentComputeLink;

        computeState.resourcePoolLink = cs.resourcePoolLink;
        // Compute descriptions are created with well defined names and can be located using the
        // instanceType
        computeState.descriptionLink = UriUtils.buildUriPath(
                ComputeDescriptionService.FACTORY_LINK, instance.getInstanceType());

        // TODO VSYM-375 for adding disk information

        computeState.address = instance.getPublicIpAddress();
        computeState.customProperties = new HashMap<String, String>();
        computeState.customProperties.put(AWSConstants.AWS_INSTANCE_ID,
                instance.getInstanceId());
        computeState.networkLinks = new ArrayList<String>();
        computeState.networkLinks.add(UriUtils.buildUriPath(
                NetworkInterfaceService.FACTORY_LINK,
                instance.getInstanceId()));

        // network
        NetworkInterfaceState networkState = new NetworkInterfaceState();
        networkState.address = instance.getPrivateIpAddress();
        networkState.id = instance.getInstanceId();

        Operation postComputeState = Operation
                .createPost(this, ComputeService.FACTORY_LINK)
                .setBody(computeState)
                .setReferer(this.getHost().getUri());

        Operation postNetworkInterface = Operation
                .createPost(this,
                        NetworkInterfaceService.FACTORY_LINK)
                .setBody(networkState)
                .setReferer(getHost().getUri());
        cs.createOperations.add(postComputeState);
        cs.createOperations.add(postNetworkInterface);
    }

    /**
     * Kicks off the creation of all the identified compute states and networks and
     * creates a join handler for the successful completion of each one of those.
     * Patches completion to parent once all the entities are created successfully.
     */
    private void kickOffComputeStateCreationAndPatchParent(AWSComputeState cs) {
        if (cs.createOperations.size() > 0) {
            OperationJoin.JoinedCompletionHandler joinCompletion = (ox,
                    exc) -> {
                if (exc != null) {
                    logSevere(
                            "Error creating a compute state and the associated network %s",
                            exc.get(0));
                    AdapterUtils.sendFailurePatchToTask(this,
                            cs.parentTaskLink, exc.get(0));
                    return;
                }
                logInfo("Successfully created all the networks and compute states");
                AdapterUtils.sendPatchToTask(this,
                        cs.parentTaskLink);

            };
            OperationJoin joinOp = OperationJoin.create(cs.createOperations);
            joinOp.setCompletion(joinCompletion);
            joinOp.sendWith(getHost());
        } else {
            logInfo("There are no compute states or networks to be created");
        }
    }
}
