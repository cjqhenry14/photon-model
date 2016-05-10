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

import static com.vmware.photon.controller.model.adapters.util.AdapterUtils.updateDurationStats;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

import com.amazonaws.services.ec2.model.Instance;

import com.vmware.photon.controller.model.adapters.awsadapter.AWSConstants;
import com.vmware.photon.controller.model.adapters.awsadapter.AWSUriPaths;
import com.vmware.photon.controller.model.adapters.awsadapter.AWSUtils;
import com.vmware.photon.controller.model.adapters.util.AdapterUtils;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService;
import com.vmware.photon.controller.model.resources.ComputeService;
import com.vmware.photon.controller.model.resources.NetworkInterfaceService;
import com.vmware.photon.controller.model.resources.NetworkInterfaceService.NetworkInterfaceState;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.OperationJoin;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;

/**
 * Stateless service for the creation of compute states. It accepts a list of AWS instances that need to be created in the
 * local system.It also accepts a few additional fields required for mapping the referential integrity relationships
 * for the compute state when it is persisted in the local system.
 */
public class AWSComputeStateCreationAdapterService extends StatelessService {

    public static final String SELF_LINK = AWSUriPaths.AWS_COMPUTE_STATE_CREATION_ADAPTER;

    public static enum AWSComputeStateCreationStage {
        POPULATE_COMPUTESTATES, CREATE_COMPUTESTATES, SIGNAL_COMPLETION
    }

    public AWSComputeStateCreationAdapterService() {
        super.toggleOption(ServiceOption.INSTRUMENTATION, true);
    }

    /**
     * Data holder for information related a compute state that needs to be created in the local system.
     *
     */
    public static class AWSComputeStateForCreation {
        public List<Instance> instancesToBeCreated;
        public String resourcePoolLink;
        public String parentComputeLink;
        public URI parentTaskLink;
        boolean isMock;
        public List<String> tenantLinks;
    }

    /**
     * The service context that is created for representing the list of instances received into a list of compute states
     * that will be persisted in the system.
     *
     */
    public static class AWSComputeServiceCreationContext {
        AWSComputeStateForCreation computeState;
        public List<Operation> createOperations;
        public int instanceToBeCreatedCounter = 0;
        public AWSComputeStateCreationStage creationStage;
        // Cached operation to signal completion to the AWS instance adapter once all the compute
        // states are successfully created.
        public Operation awsAdapterOperation;
        public long startTime;

        public AWSComputeServiceCreationContext(AWSComputeStateForCreation computeState, Operation op) {
            this.computeState = computeState;
            createOperations = new ArrayList<Operation>();
            creationStage = AWSComputeStateCreationStage.POPULATE_COMPUTESTATES;
            awsAdapterOperation = op;
            startTime = Utils.getNowMicrosUtc();
        }
    }

    @Override
    public void handlePatch(Operation op) {
        if (!op.hasBody()) {
            op.fail(new IllegalArgumentException("body is required"));
            return;
        }
        AWSComputeStateForCreation cs = op.getBody(AWSComputeStateForCreation.class);
        AWSComputeServiceCreationContext context = new AWSComputeServiceCreationContext(cs, op);
        if (cs.isMock) {
            op.complete();
        }
        handleComputeStateCreation(context);
    }

    /**
     * Creates the compute states in the local document store based on the AWS instances received from the remote endpoint.
     * @param context The local service context that has all the information needed to create the additional compute states
     * in the local system.
     */
    private void handleComputeStateCreation(AWSComputeServiceCreationContext context) {
        switch (context.creationStage) {
        case POPULATE_COMPUTESTATES:
            createComputeStates(context, AWSComputeStateCreationStage.CREATE_COMPUTESTATES);
            break;
        case CREATE_COMPUTESTATES:
            kickOffComputeStateCreation(context, AWSComputeStateCreationStage.SIGNAL_COMPLETION);
            break;
        case SIGNAL_COMPLETION:
            updateDurationStats(this, context.startTime);
            context.awsAdapterOperation.complete();
            break;
        default:
            Throwable t = new IllegalArgumentException(
                    "Unknown AWS enumeration:compute state creation stage");
            AdapterUtils.sendFailurePatchToEnumerationTask(this,
                    context.computeState.parentTaskLink, t);
            break;
        }
    }

    /**
     * Method to create Compute States associated with the instances received from the AWS host.
     * @param next
     * @param instancesToBeCreated
     */
    private void createComputeStates(AWSComputeServiceCreationContext context,
            AWSComputeStateCreationStage next) {
        if (context.computeState.instancesToBeCreated == null
                || context.computeState.instancesToBeCreated.size() == 0) {
            logInfo("No instances need to be created in the local system");
            context.creationStage = next;
            handleComputeStateCreation(context);
            return;
        }
        logInfo("Need to create %d compute states in the local system",
                context.computeState.instancesToBeCreated.size());
        for (int i = 0; i < context.computeState.instancesToBeCreated.size(); i++) {
            context.instanceToBeCreatedCounter = i;
            populateComputeState(context);
        }
        context.creationStage = next;
        handleComputeStateCreation(context);

    }

    /**
     * Populates the compute state / network link associated with an AWS VM instance and creates an operation for posting it.
     * @param csDetails
     */
    private void populateComputeState(AWSComputeServiceCreationContext context) {
        Instance instance = context.computeState.instancesToBeCreated
                .get(context.instanceToBeCreatedCounter);
        ComputeService.ComputeState computeState = new ComputeService.ComputeState();
        computeState.id = UUID.randomUUID().toString();
        computeState.parentLink = context.computeState.parentComputeLink;

        computeState.resourcePoolLink = context.computeState.resourcePoolLink;
        // Compute descriptions are created with well defined names and can be located using the
        // instanceType
        computeState.descriptionLink = UriUtils.buildUriPath(
                ComputeDescriptionService.FACTORY_LINK, instance.getInstanceType());

        // TODO VSYM-375 for adding disk information

        computeState.address = instance.getPublicIpAddress();
        computeState.powerState = AWSUtils.mapToPowerState(instance.getState());
        computeState.customProperties = new HashMap<String, String>();
        computeState.customProperties.put(AWSConstants.AWS_INSTANCE_ID,
                instance.getInstanceId());
        computeState.networkLinks = new ArrayList<String>();
        computeState.networkLinks.add(UriUtils.buildUriPath(
                NetworkInterfaceService.FACTORY_LINK,
                instance.getInstanceId()));
        computeState.tenantLinks = context.computeState.tenantLinks;

        // network
        NetworkInterfaceState networkState = new NetworkInterfaceState();
        networkState.address = instance.getPrivateIpAddress();
        networkState.id = instance.getInstanceId();
        networkState.tenantLinks = context.computeState.tenantLinks;

        Operation postComputeState = Operation
                .createPost(this, ComputeService.FACTORY_LINK)
                .setBody(computeState)
                .setReferer(this.getHost().getUri());

        Operation postNetworkInterface = Operation
                .createPost(this,
                        NetworkInterfaceService.FACTORY_LINK)
                .setBody(networkState)
                .setReferer(getHost().getUri());
        context.createOperations.add(postComputeState);
        context.createOperations.add(postNetworkInterface);
    }

    /**
     * Kicks off the creation of all the identified compute states and networks and
     * creates a join handler for the successful completion of each one of those.
     * Patches completion to parent once all the entities are created successfully.
     */
    private void kickOffComputeStateCreation(AWSComputeServiceCreationContext context,
            AWSComputeStateCreationStage next) {
        if (context.createOperations == null || context.createOperations.size() == 0) {
            logInfo("There are no compute states or networks to be created");
            context.creationStage = next;
            handleComputeStateCreation(context);
            return;
        }
        OperationJoin.JoinedCompletionHandler joinCompletion = (ox,
                exc) -> {
            if (exc != null) {
                logSevere(
                        "Error creating a compute state and the associated network %s",
                        exc.get(0));
                AdapterUtils.sendFailurePatchToEnumerationTask(this,
                        context.computeState.parentTaskLink, exc.values().iterator().next());

            }
            logInfo("Successfully created all the networks and compute states.");
            context.creationStage = next;
            handleComputeStateCreation(context);
            return;
        };
        OperationJoin joinOp = OperationJoin.create(context.createOperations);
        joinOp.setCompletion(joinCompletion);
        joinOp.sendWith(getHost());

    }
}
