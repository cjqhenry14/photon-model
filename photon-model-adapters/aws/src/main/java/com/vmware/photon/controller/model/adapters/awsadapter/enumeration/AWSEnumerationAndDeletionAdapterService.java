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

import static com.vmware.photon.controller.model.adapters.awsadapter.AWSConstants.getQueryResultLimit;
import static com.vmware.photon.controller.model.adapters.awsadapter.AWSUtils.getAWSNonTerminatedInstancesFilter;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentSkipListMap;

import com.amazonaws.handlers.AsyncHandler;
import com.amazonaws.services.ec2.AmazonEC2AsyncClient;
import com.amazonaws.services.ec2.model.DescribeInstancesRequest;
import com.amazonaws.services.ec2.model.DescribeInstancesResult;
import com.amazonaws.services.ec2.model.Filter;
import com.amazonaws.services.ec2.model.Instance;
import com.amazonaws.services.ec2.model.Reservation;

import com.vmware.photon.controller.model.adapterapi.ComputeEnumerateResourceRequest;
import com.vmware.photon.controller.model.adapterapi.EnumerationAction;
import com.vmware.photon.controller.model.adapters.awsadapter.AWSUriPaths;
import com.vmware.photon.controller.model.adapters.awsadapter.enumeration.AWSEnumerationAdapterService.AWSEnumerationRequest;
import com.vmware.photon.controller.model.adapters.awsadapter.util.AWSClientManager;
import com.vmware.photon.controller.model.adapters.awsadapter.util.AWSClientManagerFactory;
import com.vmware.photon.controller.model.adapters.util.AdapterUtils;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription;
import com.vmware.photon.controller.model.resources.ComputeService;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;

import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.OperationContext;
import com.vmware.xenon.common.OperationJoin;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.AuthCredentialsService;
import com.vmware.xenon.services.common.QueryTask;
import com.vmware.xenon.services.common.QueryTask.Query;
import com.vmware.xenon.services.common.QueryTask.QuerySpecification.QueryOption;
import com.vmware.xenon.services.common.ServiceUriPaths;

/**
 * Enumeration Adapter for the Amazon Web Services. Performs a list call to the AWS API
 * and reconciles the local state with the state on the remote system. It starts by looking at the local state in the system.
 * Queries the remote endpoint to check if the same instances exist there. In case some items are found to be deleted on the
 * remote endpoint then it goes ahead and deletes them from the local system.
 *
 */
public class AWSEnumerationAndDeletionAdapterService extends StatelessService {
    public static final String SELF_LINK = AWSUriPaths.AWS_ENUMERATION_DELETION_ADAPTER;
    private AWSClientManager clientManager;

    public static enum AWSEnumerationDeletionStages {
        ENUMERATE, ERROR
    }

    public static enum AWSEnumerationDeletionSubStage {
        GET_LOCAL_RESOURCES, GET_REMOTE_RESOURCES, COMPARE, DELETE_COMPUTE_STATES, GET_NEXT_PAGE, ENUMERATION_STOP
    }

    public AWSEnumerationAndDeletionAdapterService() {
        super.toggleOption(ServiceOption.INSTRUMENTATION, true);
        this.clientManager = AWSClientManagerFactory.getClientManager(false);
    }

    /**
     * The enumeration service context that holds all the information needed to determine the list of instances
     * that need to be deleted from the system as they have been terminated from the remote instance.
     */
    public static class EnumerationDeletionContext {
        public AmazonEC2AsyncClient amazonEC2Client;
        public ComputeEnumerateResourceRequest computeEnumerationRequest;
        public AuthCredentialsService.AuthCredentialsServiceState parentAuth;
        public ComputeDescription computeHostDescription;
        public ComputeState hostComputeState;
        public AWSEnumerationDeletionStages stage;
        public AWSEnumerationDeletionSubStage subStage;
        public Throwable error;
        // Mapping of instance Id and the compute state that represents it in the local system.
        public Map<String, ComputeState> localInstanceIds;
        // Set of all the instance Ids of the non terminated instances on AWS
        public Set<String> remoteInstanceIds;
        // Map of Instance Ids and compute states that have to be deleted from the local system.
        public List<ComputeState> instancesToBeDeleted;
        // Synchronized map to keep track if an enumeration service has been started in listening
        // mode for a host
        public Map<String, Boolean> enumerationHostMap;
        public Operation awsAdapterOperation;
        public List<Operation> deleteOperations;
        // The next page link for the next set of results to fetch from the local system.
        public String nextPageLink;
        public int pageNo = 0;

        public EnumerationDeletionContext(AWSEnumerationRequest request, Operation op) {
            this.computeEnumerationRequest = request.computeEnumerateResourceRequest;
            this.awsAdapterOperation = op;
            this.parentAuth = request.parentAuth;
            this.computeHostDescription = request.computeHostDescription;
            this.enumerationHostMap = new ConcurrentSkipListMap<String, Boolean>();
            this.localInstanceIds = new ConcurrentSkipListMap<String, ComputeState>();
            this.remoteInstanceIds = new HashSet<String>();
            this.instancesToBeDeleted = new ArrayList<ComputeState>();
            this.deleteOperations = new ArrayList<Operation>();
            this.stage = AWSEnumerationDeletionStages.ENUMERATE;
            this.subStage = AWSEnumerationDeletionSubStage.GET_LOCAL_RESOURCES;
        }
    }

    @Override
    public void handleStop(Operation op) {
        AWSClientManagerFactory.returnClientManager(this.clientManager, false);
        super.handleStop(op);
    }

    @Override
    public void handlePatch(Operation op) {
        setOperationHandlerInvokeTimeStat(op);
        if (!op.hasBody()) {
            op.fail(new IllegalArgumentException("body is required"));
            return;
        }
        EnumerationDeletionContext awsEnumerationContext = new EnumerationDeletionContext(
                op.getBody(AWSEnumerationRequest.class), op);
        if (awsEnumerationContext.computeEnumerationRequest.isMockRequest) {
            // patch status to parent task
            AdapterUtils.sendPatchToEnumerationTask(this,
                    awsEnumerationContext.computeEnumerationRequest.enumerationTaskReference);
            return;
        }
        handleEnumerationRequestForDeletion(awsEnumerationContext);
    }

    /**
     * Handles the different steps required to process the local resources , get the corresponding resources from the remote endpoint
     * and delete the instances from the local system that do not exist on the remote system any longer.
     */
    private void handleEnumerationRequestForDeletion(EnumerationDeletionContext aws) {
        switch (aws.stage) {
        case ENUMERATE:
            switch (aws.computeEnumerationRequest.enumerationAction) {
            case START:
                if (aws.enumerationHostMap
                        .containsKey(getHostEnumKey(aws.computeHostDescription))) {
                    logInfo("Enumeration for deletion already started for %s",
                            aws.computeHostDescription.environmentName);
                } else {
                    aws.enumerationHostMap.put(getHostEnumKey(aws.computeHostDescription), true);
                    logInfo("Started deletion enumeration for %s",
                            aws.computeHostDescription.environmentName);
                }
                aws.computeEnumerationRequest.enumerationAction = EnumerationAction.REFRESH;
                handleEnumerationRequestForDeletion(aws);
                break;
            case REFRESH:
                logInfo("Running enumeration service for deletion in refresh mode for %s",
                        aws.computeHostDescription.environmentName);
                deleteResourcesInLocalSystem(aws);
                break;
            case STOP:
                if (!aws.enumerationHostMap
                        .containsKey(getHostEnumKey(aws.computeHostDescription))) {
                    logInfo("Enumeration for deletion is not running or has already been stopped for %s",
                            aws.computeHostDescription.environmentName);
                } else {
                    aws.enumerationHostMap.remove(getHostEnumKey(aws.computeHostDescription));
                    logInfo("Stopping deletion enumeration service for %s",
                            aws.computeHostDescription.environmentName);
                }
                setOperationDurationStat(aws.awsAdapterOperation);
                aws.awsAdapterOperation.complete();
                break;
            default:
                logSevere("Unknown AWS enumeration action %s ",
                        aws.computeEnumerationRequest.enumerationAction.toString());
                Throwable t = new Exception("Unknown AWS enumeration action");
                signalErrorToEnumerationAdapter(aws, t);
                break;
            }
            break;
        case ERROR:
            AdapterUtils.sendFailurePatchToEnumerationTask(this,
                    aws.computeEnumerationRequest.enumerationTaskReference, aws.error);
            break;
        default:
            logSevere("Unknown AWS enumeration stage %s ", aws.stage.toString());
            Throwable t = new Exception("Unknown AWS enumeration stage");
            signalErrorToEnumerationAdapter(aws, t);
            break;
        }
    }

    /**
     * Method that generates a string key to represent the host for which the enumeration task is being performed.
     * @param computeHost The compute host representing the Amzon EC2 cloud.
     * @return
     */
    private String getHostEnumKey(ComputeDescription computeHost) {
        return ("hostLink:" + computeHost.documentSelfLink + "-enumerationAdapterReference:"
                + computeHost.enumerationAdapterReference);
    }

    /**
     * Uses the received enumeration information and compares it against it the state of the local system and then tries to
     * find and fix the gaps. At a high level this is the sequence of steps that is followed:
     * 1) Create a query to get the list of local compute states
     * 2) Compare the list of local resources against the list received from the AWS endpoint.
     * 3) In case some instances have been terminated on the AWS endpoint, mark those instances for deletion in the local system.
     * 4) Delete the compute state and network associated with that AWS instance from the local system that have been terminated on AWS.
     * @param aws
     */
    private void deleteResourcesInLocalSystem(EnumerationDeletionContext aws) {
        switch (aws.subStage) {
        case GET_LOCAL_RESOURCES:
            getLocalResources(aws,
                    AWSEnumerationDeletionSubStage.GET_REMOTE_RESOURCES);
            break;
        case GET_REMOTE_RESOURCES:
            getRemoteInstances(aws, AWSEnumerationDeletionSubStage.COMPARE);
            break;
        case COMPARE:
            compareResources(aws, AWSEnumerationDeletionSubStage.DELETE_COMPUTE_STATES);
            break;
        case DELETE_COMPUTE_STATES:
            AWSEnumerationDeletionSubStage next;
            if (aws.instancesToBeDeleted == null || aws.instancesToBeDeleted.size() == 0) {
                logInfo("There are no compute states to be deleted in the system");
                if (aws.nextPageLink == null) {
                    aws.subStage = AWSEnumerationDeletionSubStage.ENUMERATION_STOP;
                } else {
                    aws.subStage = AWSEnumerationDeletionSubStage.GET_NEXT_PAGE;
                }
                deleteResourcesInLocalSystem(aws);
                return;
            } else {
                if (aws.nextPageLink == null) {
                    next = AWSEnumerationDeletionSubStage.ENUMERATION_STOP;
                } else {
                    next = AWSEnumerationDeletionSubStage.GET_NEXT_PAGE;
                }
                deleteComputeStates(aws, next);
            }
            break;
        case GET_NEXT_PAGE:
            getNextPageFromLocalSystem(aws, AWSEnumerationDeletionSubStage.GET_REMOTE_RESOURCES);
            break;
        case ENUMERATION_STOP:
            logInfo("Stopping enumeration");
            stopEnumeration(aws);
            break;
        default:
            Throwable t = new Exception("Unknown AWS enumeration deletion sub stage");
            signalErrorToEnumerationAdapter(aws, t);
        }
    }

    /**
     * Get the list of compute states already known to the local system. Filter them by parent compute
     * link : AWS.
     */
    public void getLocalResources(EnumerationDeletionContext aws,
            AWSEnumerationDeletionSubStage next) {
        // query all ComputeState resources known to the local system.
        logInfo("Getting local resources for which state has to be re-conciled with the remote system.");
        int resultLimit = getQueryResultLimit();
        Query query = Query.Builder.create()
                .addKindFieldClause(ComputeService.ComputeState.class)
                .addFieldClause(ComputeState.FIELD_NAME_PARENT_LINK,
                        aws.computeEnumerationRequest.parentComputeLink)
                .build();
        QueryTask.Builder queryTaskBuilder = QueryTask.Builder.createDirectTask()
                .setQuery(query).setResultLimit(resultLimit);
        queryTaskBuilder.addOption(QueryOption.EXPAND_CONTENT);

        QueryTask queryTask = queryTaskBuilder.build();
        queryTask.tenantLinks = queryTask.tenantLinks = aws.computeHostDescription.tenantLinks;

        // create the query to find resources
        sendRequest(Operation
                .createPost(this, ServiceUriPaths.CORE_QUERY_TASKS)
                .setBody(queryTask)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        logSevere("Failure retrieving query results: %s",
                                e.toString());
                        signalErrorToEnumerationAdapter(aws, e);
                        return;
                    }
                    QueryTask responseTask = populateLocalInstanceInformationFromQueryResults(aws,
                            o);
                    logInfo("Got result of the query to get local resources for page No. %d "
                            + "There are %d instances known to the system.",
                            aws.pageNo, responseTask.results.documentCount);
                    aws.subStage = next;
                    deleteResourcesInLocalSystem(aws);
                    return;
                }));
    }

    /**
     * Populates the local instance information from the query results.
     */
    public QueryTask populateLocalInstanceInformationFromQueryResults(
            EnumerationDeletionContext aws, Operation op) {
        QueryTask responseTask = op.getBody(QueryTask.class);
        for (Object s : responseTask.results.documents.values()) {
            ComputeState localInstance = Utils.fromJson(s,
                    ComputeService.ComputeState.class);
            aws.localInstanceIds.put(localInstance.id, localInstance);
        }
        aws.pageNo++;
        aws.nextPageLink = responseTask.results.nextPageLink;
        logInfo("Next page link is %s", aws.nextPageLink);
        return responseTask;
    }

    /**
     * Get the instances from AWS filtered by the instances Ids known to the local system.
     */
    public void getRemoteInstances(EnumerationDeletionContext aws,
            AWSEnumerationDeletionSubStage next) {
        if (aws.localInstanceIds == null || aws.localInstanceIds.size() == 0) {
            logInfo("There are no local records found for which state needs to be fetched from the remote system.");
            aws.subStage = next;
            deleteResourcesInLocalSystem(aws);
            return;
        }
        DescribeInstancesRequest request = new DescribeInstancesRequest();
        Filter runningInstanceFilter = getAWSNonTerminatedInstancesFilter();
        request.getFilters().add(runningInstanceFilter);
        // Get only the instances from the remote system for which a compute state exists in the
        // local system.
        logInfo("Fetching instance details for %d instances on the AWS endpoint",
                aws.localInstanceIds.keySet().size());
        request.getInstanceIds().addAll(new ArrayList<String>(aws.localInstanceIds.keySet()));
        AsyncHandler<DescribeInstancesRequest, DescribeInstancesResult> resultHandler = new AWSEnumerationAsyncHandler(
                this, aws, next);
        aws.amazonEC2Client = this.clientManager.getOrCreateEC2Client(aws.parentAuth,
                aws.computeHostDescription.zoneId, this,
                aws.computeEnumerationRequest.enumerationTaskReference,
                aws.computeEnumerationRequest.isMockRequest, true);
        aws.amazonEC2Client.describeInstancesAsync(request,
                resultHandler);
    }

    /**
     * The async handler to handle the success and errors received after invoking the describe instances
     * API on AWS
     */
    public static class AWSEnumerationAsyncHandler implements
            AsyncHandler<DescribeInstancesRequest, DescribeInstancesResult> {

        private StatelessService service;
        private EnumerationDeletionContext aws;
        public AWSEnumerationDeletionSubStage next;
        private OperationContext opContext;

        private AWSEnumerationAsyncHandler(StatelessService service,
                EnumerationDeletionContext aws, AWSEnumerationDeletionSubStage next) {
            this.service = service;
            this.aws = aws;
            this.next = next;
            this.opContext = OperationContext.getOperationContext();
        }

        @Override
        public void onError(Exception exception) {
            OperationContext.restoreOperationContext(this.opContext);
            this.service.logSevere(exception);
            AdapterUtils.sendFailurePatchToEnumerationTask(this.service,
                    this.aws.computeEnumerationRequest.enumerationTaskReference,
                    exception);

        }

        @Override
        public void onSuccess(DescribeInstancesRequest request,
                DescribeInstancesResult result) {
            OperationContext.restoreOperationContext(this.opContext);
            int totalNumberOfInstances = 0;
            // Print the details of the instances discovered on the AWS endpoint
            for (Reservation r : result.getReservations()) {
                for (Instance i : r.getInstances()) {
                    this.service.logInfo("%d=====Instance details %s =====",
                            ++totalNumberOfInstances,
                            i.getInstanceId());
                    this.aws.remoteInstanceIds.add(i.getInstanceId());
                }
            }
            this.service.logInfo("Successfully enumerated %d instances on the AWS host",
                    totalNumberOfInstances);
            this.aws.subStage = this.next;
            ((AWSEnumerationAndDeletionAdapterService) this.service).deleteResourcesInLocalSystem(this.aws);
            return;
        }
    }

    /**
     * Compares the state between what is known to the local system and what is retrieved from AWS. If some instances are terminated on the AWS
     * endpoint then they are marked for deletion on the local system.
     */
    public void compareResources(EnumerationDeletionContext aws,
            AWSEnumerationDeletionSubStage next) {
        // No local resources
        if (aws.localInstanceIds == null || aws.localInstanceIds.size() == 0) {
            logInfo("No local resources found. Nothing to delete.");
            // No remote instances
        } else if (aws.remoteInstanceIds == null || aws.remoteInstanceIds.size() == 0) {
            logInfo("No resources discovered on the remote system. Everything (if existing) on the local system should be deleted.");
            aws.instancesToBeDeleted.addAll(aws.localInstanceIds.values());
            logInfo("====Deleting compute state for instance Ids %s ====",
                    aws.localInstanceIds.keySet().toString());
        } else { // compare and mark the instances for deletion that have been terminated from the
                 // AWS endpoint.
            for (String key : aws.localInstanceIds.keySet()) {
                if (!aws.remoteInstanceIds.contains(key)) {
                    aws.instancesToBeDeleted.add(aws.localInstanceIds.get(key));
                    logInfo("====Deleting compute state for instance Id %s ====", key);
                }
            }
            logInfo("%d instances need to be deleted from the local system as they have been terminated on the remote endpoint.",
                    aws.instancesToBeDeleted.size());
        }
        aws.subStage = next;
        deleteResourcesInLocalSystem(aws);
        return;
    }

    /**
     * Creates operations for the deletion of all the compute states and networks from the local system for which
     * the AWS instance has been terminated from the remote instance.Kicks off the deletion of all the identified compute states and networks for which the actual AWS instance has been terminated.
     */
    private void deleteComputeStates(EnumerationDeletionContext context,
            AWSEnumerationDeletionSubStage next) {

        // Create delete operations for the compute states that have to be deleted from the system.
        for (ComputeState computeStateToDelete : context.instancesToBeDeleted) {
            Operation deleteComputeStateOperation = Operation
                    .createDelete(UriUtils.buildUri(this.getHost(),
                            computeStateToDelete.documentSelfLink))
                    .setReferer(getHost().getUri());
            context.deleteOperations.add(deleteComputeStateOperation);
            // Create delete operations for all the network links associated with each of the
            // compute states.
            for (String networkLinkTodelete : computeStateToDelete.networkLinks) {
                Operation deleteNetworkOperation = Operation
                        .createDelete(UriUtils.buildUri(this.getHost(),
                                networkLinkTodelete))
                        .setReferer(getHost().getUri());
                context.deleteOperations.add(deleteNetworkOperation);
            }
        }
        // Kick off deletion operations with a join handler.
        if (context.deleteOperations == null || context.deleteOperations.size() == 0) {
            logInfo("There are no compute states to be deleted from the system.");
            context.subStage = next;
            deleteResourcesInLocalSystem(context);
            return;
        }
        OperationJoin.JoinedCompletionHandler joinCompletion = (ox,
                exc) -> {
            if (exc != null) {
                logSevere("Failure deleting compute states from the local system",
                        Utils.toString(exc));
                signalErrorToEnumerationAdapter(context, exc.values().iterator().next());
                return;

            }
            logInfo("Successfully deleted compute states and networks from the local system. Proceeding to next state.");
            context.subStage = next;
            deleteResourcesInLocalSystem(context);
            return;
        };
        OperationJoin joinOp = OperationJoin.create(context.deleteOperations);
        joinOp.setCompletion(joinCompletion);
        joinOp.sendWith(getHost());
    }

    /**
     * Signals Enumeration Stop to the AWS enumeration adapter. The AWS enumeration adapter will in turn patch the
     * parent task to indicate completion.
     */
    public void stopEnumeration(EnumerationDeletionContext aws) {
        aws.computeEnumerationRequest.enumerationAction = EnumerationAction.STOP;
        handleEnumerationRequestForDeletion(aws);
    }

    /**
     * Signals error to the AWS enumeration adapter. The adapter will in turn clean up resources and signal error to the parent task.
     */
    public void signalErrorToEnumerationAdapter(EnumerationDeletionContext aws, Throwable t) {
        aws.error = t;
        aws.stage = AWSEnumerationDeletionStages.ERROR;
        handleEnumerationRequestForDeletion(aws);
    }

    /**
     * Gets the next page from the local system for which the state has to be reconciled after comparison with the remote AWS endpoint.
     */
    private void getNextPageFromLocalSystem(EnumerationDeletionContext aws,
            AWSEnumerationDeletionSubStage next) {
        aws.localInstanceIds.clear();
        aws.remoteInstanceIds.clear();
        aws.instancesToBeDeleted.clear();
        aws.deleteOperations.clear();
        logInfo("Getting next page of local records.");
        sendRequest(Operation
                .createGet(UriUtils.buildUri(getHost(), aws.nextPageLink))
                .setCompletion((o, e) -> {
                    if (e != null) {
                        logSevere("Failure retrieving next page from the local system: %s",
                                e.toString());
                        signalErrorToEnumerationAdapter(aws, e);
                        return;
                    }
                    QueryTask responseTask = populateLocalInstanceInformationFromQueryResults(aws,
                            o);
                    logInfo("Got page No. %d of local resources. "
                            + "There are %d instances in this page.", aws.pageNo,
                            responseTask.results.documentCount);
                    aws.subStage = next;
                    deleteResourcesInLocalSystem(aws);
                    return;
                }));
    }

}
