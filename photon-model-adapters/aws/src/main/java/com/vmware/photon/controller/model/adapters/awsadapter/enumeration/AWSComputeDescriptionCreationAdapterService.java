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

import static com.vmware.photon.controller.model.adapters.awsadapter.AWSUtils.TILDA;
import static com.vmware.photon.controller.model.adapters.awsadapter.AWSUtils.getRegionId;
import static com.vmware.xenon.common.UriUtils.URI_PATH_CHAR;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.amazonaws.services.ec2.model.Instance;

import com.vmware.photon.controller.model.adapters.awsadapter.AWSInstanceService;
import com.vmware.photon.controller.model.adapters.awsadapter.AWSUriPaths;
import com.vmware.photon.controller.model.adapters.util.AdapterUtils;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription.ComputeType;

import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.OperationJoin;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.QueryTask;
import com.vmware.xenon.services.common.QueryTask.Query;
import com.vmware.xenon.services.common.QueryTask.Query.Occurance;
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
        public Set<String> localComputeDescriptionSet;
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
            localComputeDescriptionSet = new HashSet<String>();
            representativeComputeDescriptionSet = new HashSet<String>();
            computeDescriptionsToBeCreatedList = new ArrayList<String>();
            createOperations = new ArrayList<Operation>();
            creationStage = AWSComputeDescCreationStage.GET_REPRESENTATIVE_LIST;
            awsAdapterOperation = op;
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
     * @param context
     * @param next
     */
    private void getRepresentativeListOfComputeDescriptions(
            AWSComputeDescriptionCreationServiceContext context, AWSComputeDescCreationStage next) {
        for (Instance instance : context.cdState.instancesToBeCreated) {
            context.representativeComputeDescriptionSet.add(getKeyForComputeDescription(instance));
        }
        logInfo("The instances received from AWS are represented by %d additional compute descriptions. ",
                context.representativeComputeDescriptionSet.size());
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
        // Region Id will be common to all the instances as the EC2 client has this value set during
        // each invocation.
        String instanceKey = context.representativeComputeDescriptionSet.iterator().next();
        String zoneId = instanceKey.substring(0, instanceKey.indexOf(TILDA));
        QueryTask.Query supportedChildrenClause = new QueryTask.Query()
                .setTermPropertyName(
                        QueryTask.QuerySpecification
                                .buildCollectionItemName(
                                        ComputeDescriptionService.ComputeDescription.FIELD_NAME_SUPPORTED_CHILDREN))
                .setTermMatchValue(ComputeType.DOCKER_CONTAINER.toString());

        QueryTask q = new QueryTask();
        q.setDirect(true);
        q.querySpec = new QueryTask.QuerySpecification();
        q.querySpec.query = Query.Builder.create()
                .addKindFieldClause(ComputeDescription.class)
                .addFieldClause(ComputeDescription.FIELD_NAME_ENVIRONMENT_NAME,
                        AWSInstanceService.AWS_ENVIRONMENT_NAME)
                .addFieldClause(ComputeDescription.FIELD_NAME_ZONE_ID, zoneId)
                .build().addBooleanClause(supportedChildrenClause);

        // Instance type should fall in one of the passed in values
        QueryTask.Query instanceTypeFilterParentQuery = new QueryTask.Query();
        instanceTypeFilterParentQuery.occurance = Occurance.MUST_OCCUR;
        for (String key : context.representativeComputeDescriptionSet) {
            QueryTask.Query instanceTypeFilter = new QueryTask.Query()
                    .setTermPropertyName(ComputeDescription.FIELD_NAME_ID)
                    .setTermMatchValue(key.substring(key.indexOf(TILDA) + 1));
            instanceTypeFilter.occurance = QueryTask.Query.Occurance.SHOULD_OCCUR;
            instanceTypeFilterParentQuery.addBooleanClause(instanceTypeFilter);
        }
        q.querySpec.query.addBooleanClause(instanceTypeFilterParentQuery);

        q.documentSelfLink = UUID.randomUUID().toString();
        q.tenantLinks = context.cdState.tenantLinks;
        // create the query to find an existing compute description
        this.sendRequest(Operation
                .createPost(this, ServiceUriPaths.CORE_QUERY_TASKS)
                .setBody(q)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        this.logWarning("Failure retrieving query results: %s",
                                e.toString());
                        AdapterUtils.sendFailurePatchToEnumerationTask(this,
                                context.cdState.parentTaskLink, e);
                    }
                    QueryTask responseTask = o.getBody(QueryTask.class);
                    if (responseTask != null && responseTask.results.documentCount > 0) {
                        for (String docLink : responseTask.results.documentLinks) {
                            context.localComputeDescriptionSet.add(getIdFromDocumentLink(docLink));
                        }
                        logInfo("%d compute descriptions already exist in the system that match the supplied criteria. ",
                                context.localComputeDescriptionSet.size());
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
        } else if (context.localComputeDescriptionSet == null
                || context.localComputeDescriptionSet.size() == 0) {
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
                String instanceType = key.substring(key.indexOf(TILDA) + 1);
                if (!context.localComputeDescriptionSet.contains(instanceType)) {
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
            context.creationStage = next;
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
        computeDescription.zoneId = key.substring(0, key.indexOf(TILDA));
        computeDescription.id = key.substring(key.indexOf(TILDA) + 1);
        computeDescription.documentSelfLink = computeDescription.id;
        computeDescription.name = computeDescription.id;
        computeDescription.tenantLinks = cd.cdState.tenantLinks;

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

    /**
     * Gets the key to uniquely represent a compute description that needs to be created in the system.
     * Currently uses regionId and instanceType. TODO harden key as more logic is realized in this service.
     */
    private String getKeyForComputeDescription(Instance i) {
        // Representing the compute-description as a key regionId~instanceType
        return getRegionId(i).concat(TILDA).concat(i.getInstanceType());
    }

    /**
     * Extracts the id from the document link. This is the unique identifier of the document returned as part of the result.
     * @param documentLink
     * @return
     */
    private String getIdFromDocumentLink(String documentLink) {
        return documentLink.substring(documentLink.lastIndexOf(URI_PATH_CHAR) + 1);
    }
}
