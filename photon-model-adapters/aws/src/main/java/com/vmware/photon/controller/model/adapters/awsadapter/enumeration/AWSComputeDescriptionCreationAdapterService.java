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

import static com.vmware.photon.controller.model.adapters.awsadapter.util.AWSEnumerationUtils.getCDsRepresentingVMsInLocalSystemCreatedByEnumerationQuery;
import static com.vmware.photon.controller.model.adapters.awsadapter.util.AWSEnumerationUtils.getInstanceTypeFromComputeDescriptionKey;
import static com.vmware.photon.controller.model.adapters.awsadapter.util.AWSEnumerationUtils.getKeyForComputeDescriptionFromCD;
import static com.vmware.photon.controller.model.adapters.awsadapter.util.AWSEnumerationUtils.getNetworkIdFromComputeDescriptionKey;
import static com.vmware.photon.controller.model.adapters.awsadapter.util.AWSEnumerationUtils.getRegionIdFromComputeDescriptionKey;
import static com.vmware.photon.controller.model.adapters.awsadapter.util.AWSEnumerationUtils.getRepresentativeListOfCDsFromInstanceList;
import static com.vmware.photon.controller.model.constants.PhotonModelConstants.SOURCE_TASK_LINK;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.amazonaws.services.ec2.model.Instance;

import com.vmware.photon.controller.model.adapters.awsadapter.AWSInstanceService;
import com.vmware.photon.controller.model.adapters.awsadapter.AWSUriPaths;
import com.vmware.photon.controller.model.adapters.util.AdapterUtils;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription.ComputeType;
import com.vmware.photon.controller.model.tasks.ResourceEnumerationTaskService;

import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.OperationJoin;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.QueryTask;
import com.vmware.xenon.services.common.ServiceUriPaths;

/**
 * Stateless service for the creation of compute descriptions that are discovered during the enumeration phase.
 * It first represents all the instances in a representative set of compute descriptions. Further checks if these
 * compute descriptions exist in the system. If they dont exist in the system then creates them in the local document store.
 */
public class AWSComputeDescriptionCreationAdapterService extends StatelessService {

    public static final String SELF_LINK = AWSUriPaths.AWS_COMPUTE_DESCRIPTION_CREATION_ADAPTER;

    public static enum AWSComputeDescCreationStage {
        GET_REPRESENTATIVE_LIST, QUERY_LOCAL_COMPUTE_DESCRIPTIONS, COMPARE, POPULATE_COMPUTEDESC, CREATE_COMPUTEDESC, SIGNAL_COMPLETION
    }

    public AWSComputeDescriptionCreationAdapterService() {
        super.toggleOption(ServiceOption.INSTRUMENTATION, true);
    }

    /**
     * Holds the list of instances for which compute descriptions have to be created.
     */
    public static class AWSComputeDescriptionCreationState {
        public List<Instance> instancesToBeCreated;
        public String regionId;
        public URI parentTaskLink;
        public String authCredentiaslLink;
        public boolean isMock;
        public List<String> tenantLinks;
    }

    /**
     * The local service context that is created to identify and create a representative set of compute descriptions
     * that are required to be created in the system based on the enumeration data received from AWS.
     */
    public static class AWSComputeDescriptionCreationServiceContext {
        public List<Operation> createOperations;
        public Map<String, String> localComputeDescriptionMap;
        public Set<String> representativeComputeDescriptionSet;
        public int instanceToBeCreatedCounter = 0;
        public List<String> computeDescriptionsToBeCreatedList;
        public AWSComputeDescCreationStage creationStage;
        public AWSComputeDescriptionCreationState cdState;
        // Cached operation to signal completion to the AWS instance adapter once all the compute
        // descriptions are successfully created.
        public Operation awsAdapterOperation;

        public AWSComputeDescriptionCreationServiceContext(AWSComputeDescriptionCreationState cdState,
                Operation op) {
            this.cdState = cdState;
            this.localComputeDescriptionMap = new HashMap<String, String>();
            this.representativeComputeDescriptionSet = new HashSet<String>();
            this.computeDescriptionsToBeCreatedList = new ArrayList<String>();
            this.createOperations = new ArrayList<Operation>();
            this.creationStage = AWSComputeDescCreationStage.GET_REPRESENTATIVE_LIST;
            this.awsAdapterOperation = op;
        }
    }

    @Override
    public void handlePatch(Operation op) {
        setOperationHandlerInvokeTimeStat(op);
        if (!op.hasBody()) {
            op.fail(new IllegalArgumentException("body is required"));
            return;
        }
        AWSComputeDescriptionCreationState cdState = op.getBody(AWSComputeDescriptionCreationState.class);
        AWSComputeDescriptionCreationServiceContext context = new AWSComputeDescriptionCreationServiceContext(
                cdState, op);
        if (cdState.isMock) {
            op.complete();
        }
        handleComputeDescriptionCreation(context);
    }

    /**
     *
     */
    private void handleComputeDescriptionCreation(AWSComputeDescriptionCreationServiceContext context) {
        switch (context.creationStage) {
        case GET_REPRESENTATIVE_LIST:
            getRepresentativeListOfComputeDescriptions(context,
                    AWSComputeDescCreationStage.QUERY_LOCAL_COMPUTE_DESCRIPTIONS);
            break;
        case QUERY_LOCAL_COMPUTE_DESCRIPTIONS:
            if (context.representativeComputeDescriptionSet.size() > 0) {
                getLocalComputeDescriptions(context, AWSComputeDescCreationStage.COMPARE);
            } else {
                context.creationStage = AWSComputeDescCreationStage.SIGNAL_COMPLETION;
                handleComputeDescriptionCreation(context);
            }
            break;
        case COMPARE:
            compareLocalStateWithEnumerationData(context,
                    AWSComputeDescCreationStage.POPULATE_COMPUTEDESC);
            break;
        case POPULATE_COMPUTEDESC:
            if (context.computeDescriptionsToBeCreatedList.size() > 0) {
                populateComputeDescriptions(context,
                        AWSComputeDescCreationStage.CREATE_COMPUTEDESC);
            } else {
                context.creationStage = AWSComputeDescCreationStage.SIGNAL_COMPLETION;
                handleComputeDescriptionCreation(context);
            }
            break;
        case CREATE_COMPUTEDESC:
            createComputeDescriptions(context, AWSComputeDescCreationStage.SIGNAL_COMPLETION);
            break;
        case SIGNAL_COMPLETION:
            setOperationDurationStat(context.awsAdapterOperation);
            context.awsAdapterOperation.complete();
            break;
        default:
            Throwable t = new IllegalArgumentException(
                    "Unknown AWS enumeration:compute description creation stage");
            AdapterUtils.sendFailurePatchToEnumerationTask(this, context.cdState.parentTaskLink, t);
        }
    }

    /**
     * From the list of instances that are received from AWS arrive at the minimal set of compute descriptions that need
     * to be created locally to represent them.
     *
     * This logic basically tries to map n number of discovered instances to m compute descriptions.
     * The attributes of the instance considered to map to compute descriptions are the statically known template type
     * attributes that are expected to be fixed for the life of a virtual machine.
     *
     * These include
     * 1) Region Id
     * 2) Instance Type
     * 3) Network Id.
     *
     * Once the instances are mapped to these limited set of compute descriptions. Checks are performed to see if such compute descriptions
     * exist in the system. Else they are created.
     *
     * @param context
     * @param next
     */
    private void getRepresentativeListOfComputeDescriptions(
            AWSComputeDescriptionCreationServiceContext context, AWSComputeDescCreationStage next) {
        context.representativeComputeDescriptionSet = getRepresentativeListOfCDsFromInstanceList(
                context.cdState.instancesToBeCreated);
        context.creationStage = next;
        handleComputeDescriptionCreation(context);
    }

    /**
     * Get all the compute descriptions already in the system and filtered by
     * - Supported Children (Docker Container)
     * - Environment name(AWS),
     * - Name (instance type),
     * - ZoneId(placement).
     * @param instance The instance returned from the AWS endpoint.
     */
    private void getLocalComputeDescriptions(AWSComputeDescriptionCreationServiceContext context,
            AWSComputeDescCreationStage next) {
        QueryTask q = getCDsRepresentingVMsInLocalSystemCreatedByEnumerationQuery(
                context.representativeComputeDescriptionSet, context.cdState.tenantLinks,
                this, context.cdState.parentTaskLink, context.cdState.regionId);

        // create the query to find an existing compute description
        sendRequest(Operation
                .createPost(this, ServiceUriPaths.CORE_QUERY_TASKS)
                .setBody(q)
                .setConnectionSharing(true)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        logWarning("Failure retrieving query results: %s",
                                e.toString());
                        AdapterUtils.sendFailurePatchToEnumerationTask(this,
                                context.cdState.parentTaskLink, e);
                    }
                    QueryTask responseTask = o.getBody(QueryTask.class);
                    if (responseTask != null && responseTask.results.documentCount > 0) {
                        for (Object s : responseTask.results.documents.values()) {
                            ComputeDescription localComputeDescription = Utils.fromJson(s,
                                    ComputeDescription.class);
                            context.localComputeDescriptionMap.put(
                                    getKeyForComputeDescriptionFromCD(localComputeDescription),
                                    localComputeDescription.documentSelfLink);
                        }
                        logInfo(
                                "%d compute descriptions already exist in the system that match the supplied criteria. ",
                                context.localComputeDescriptionMap.size());

                    } else {
                        logInfo("No matching compute descriptions exist in the system.");
                    }
                    context.creationStage = next;
                    handleComputeDescriptionCreation(context);
                }));

    }

    /**
    *
    *Compares the locally known compute descriptions with the new list of compute descriptions to be created.
    *Identifies only the ones that do not exist locally and need to be created.
    *
    * @param context The compute description service context to be used for the creation of the compute descriptions.
    * @param next The next stage in the workflow for the compute description creation.
    */
    private void compareLocalStateWithEnumerationData(AWSComputeDescriptionCreationServiceContext context,
            AWSComputeDescCreationStage next) {
        if (context.representativeComputeDescriptionSet == null
                || context.representativeComputeDescriptionSet.size() == 0) {
            logInfo("No new compute descriptions discovered on the remote system");
        } else if (context.localComputeDescriptionMap == null
                || context.localComputeDescriptionMap.size() == 0) {
            logInfo("No compute descriptions found in the local system. Need to create all of them");
            Iterator<String> iterator = context.representativeComputeDescriptionSet.iterator();
            while (iterator.hasNext()) {
                String key = iterator.next();
                context.computeDescriptionsToBeCreatedList.add(key);
            }
        } else { // compare and add the ones that do not exist locally
            Iterator<String> i = context.representativeComputeDescriptionSet.iterator();
            while (i.hasNext()) {
                String key = i.next();
                if (!context.localComputeDescriptionMap.containsKey(key)) {
                    context.computeDescriptionsToBeCreatedList.add(key);
                }
            }
            logInfo("%d additional compute descriptions are required to be created in the system.",
                    context.computeDescriptionsToBeCreatedList.size());
        }
        context.creationStage = next;
        handleComputeDescriptionCreation(context);
    }

    /**
     * Method to create Compute States associated with the instances received from the AWS host.
     * @param next
     * @param instancesToBeCreated
     */
    private void populateComputeDescriptions(AWSComputeDescriptionCreationServiceContext context,
            AWSComputeDescCreationStage next) {
        if (context.computeDescriptionsToBeCreatedList == null
                || context.computeDescriptionsToBeCreatedList.size() == 0) {
            logInfo("No compute descriptions needed to be created in the local system");
            context.creationStage = AWSComputeDescCreationStage.SIGNAL_COMPLETION;
            handleComputeDescriptionCreation(context);
            return;
        }
        logInfo("Need to create %d compute descriptions in the local system",
                context.computeDescriptionsToBeCreatedList.size());
        for (int i = 0; i < context.computeDescriptionsToBeCreatedList.size(); i++) {
            context.instanceToBeCreatedCounter = i;
            createComputeDescriptionOperations(context);
        }
        context.creationStage = next;
        handleComputeDescriptionCreation(context);
    }

    /**
     * Creates a compute description based on the VM instance information received from AWS. Futher creates an operation
     * that will post to the compute description service for the creation of the compute description.
     * @param AWSComputeDescriptionCreationState The compute description state object that will be used for the creation of the
     * compute description.
     */
    public void createComputeDescriptionOperations(AWSComputeDescriptionCreationServiceContext cd) {
        // Create a compute description for the AWS instance at hand
        String key = cd.computeDescriptionsToBeCreatedList.get(cd.instanceToBeCreatedCounter);
        ComputeDescriptionService.ComputeDescription computeDescription = new ComputeDescriptionService.ComputeDescription();
        computeDescription.instanceAdapterReference = UriUtils.buildUri(getHost(),
                AWSUriPaths.AWS_INSTANCE_ADAPTER);
        computeDescription.enumerationAdapterReference = UriUtils.buildUri(getHost(),
                AWSUriPaths.AWS_ENUMERATION_CREATION_ADAPTER);
        computeDescription.statsAdapterReference = UriUtils.buildUri(getHost(),
                AWSUriPaths.AWS_STATS_ADAPTER);
        computeDescription.supportedChildren = new ArrayList<>();
        computeDescription.supportedChildren.add(ComputeType.DOCKER_CONTAINER.toString());
        computeDescription.environmentName = AWSInstanceService.AWS_ENVIRONMENT_NAME;
        // Change this once we make the overarching change to correctly use zoneId and regionId from
        // AWS.
        computeDescription.zoneId = getRegionIdFromComputeDescriptionKey(key);
        computeDescription.regionId = getRegionIdFromComputeDescriptionKey(key);
        computeDescription.id = getInstanceTypeFromComputeDescriptionKey(key);
        computeDescription.instanceType = computeDescription.id;
        computeDescription.networkId = getNetworkIdFromComputeDescriptionKey(key);
        computeDescription.name = computeDescription.id;
        computeDescription.tenantLinks = cd.cdState.tenantLinks;
        // Book keeping information about the creation of the compute description in the system.
        computeDescription.customProperties = new HashMap<String, String>();
        computeDescription.customProperties.put(SOURCE_TASK_LINK,
                ResourceEnumerationTaskService.FACTORY_LINK);

        // security group is not being returned currently in the VM. Add additional logic VSYM-326.

        Operation createCD = Operation.createPost(this, ComputeDescriptionService.FACTORY_LINK)
                .setBody(computeDescription)
                .setReferer(getHost().getUri());
        cd.createOperations.add(createCD);
    }

    /**
     * Kicks off the creation of all the identified compute descriptions and creates a join handler to handle the successful
     * completion of of those operations. Once all the compute descriptions are successfully created moves the state machine
     * to the next stage.
     */
    private void createComputeDescriptions(AWSComputeDescriptionCreationServiceContext context,
            AWSComputeDescCreationStage next) {
        if (context.createOperations == null || context.createOperations.size() == 0) {
            logInfo("There are no compute descriptions to be created");
            context.creationStage = next;
            handleComputeDescriptionCreation(context);
            return;
        }
        OperationJoin.JoinedCompletionHandler joinCompletion = (ox,
                exc) -> {
            if (exc != null) {
                logSevere("Failure creating compute descriptions. Exception is %s",
                        Utils.toString(exc));
                AdapterUtils.sendFailurePatchToEnumerationTask(this,
                        context.cdState.parentTaskLink, exc.values().iterator().next());
            }
            logInfo("Successfully created all the compute descriptions");
            context.creationStage = next;
            handleComputeDescriptionCreation(context);
            return;
        };
        OperationJoin joinOp = OperationJoin.create(context.createOperations);
        joinOp.setCompletion(joinCompletion);
        joinOp.sendWith(getHost());
    }
}
