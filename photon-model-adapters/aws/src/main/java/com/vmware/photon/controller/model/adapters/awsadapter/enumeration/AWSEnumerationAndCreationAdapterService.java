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

import static com.vmware.photon.controller.model.adapters.awsadapter.AWSConstants.getQueryPageSize;
import static com.vmware.photon.controller.model.adapters.awsadapter.AWSUtils.getAWSNonTerminatedInstancesFilter;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.function.Consumer;

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
import com.vmware.photon.controller.model.adapters.awsadapter.enumeration.AWSComputeDescriptionCreationAdapterService.AWSComputeDescriptionCreationState;
import com.vmware.photon.controller.model.adapters.awsadapter.enumeration.AWSComputeStateCreationAdapterService.AWSComputeStateForCreation;
import com.vmware.photon.controller.model.adapters.awsadapter.enumeration.AWSEnumerationAdapterService.AWSEnumerationRequest;
import com.vmware.photon.controller.model.adapters.awsadapter.util.AWSClientManager;
import com.vmware.photon.controller.model.adapters.util.AdapterUtils;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription;
import com.vmware.photon.controller.model.resources.ComputeService;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.util.PhotonModelUtils;

import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.OperationContext;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.AuthCredentialsService;
import com.vmware.xenon.services.common.QueryTask;
import com.vmware.xenon.services.common.QueryTask.Query;
import com.vmware.xenon.services.common.QueryTask.Query.Occurance;
import com.vmware.xenon.services.common.QueryTask.QuerySpecification.QueryOption;
import com.vmware.xenon.services.common.ServiceUriPaths;

/**
 * Enumeration Adapter for the Amazon Web Services. Performs a list call to the AWS API
 * and reconciles the local state with the state on the remote system. It lists the instances on the remote system.
 * Compares those with the local system and creates the instances that are missing in the local system.
 *
 */
public class AWSEnumerationAndCreationAdapterService extends StatelessService {
    public static final String SELF_LINK = AWSUriPaths.AWS_ENUMERATION_CREATION_ADAPTER;
    private AWSClientManager clientManager;

    public static enum AWSEnumerationCreationStages {
        CLIENT, ENUMERATE, ERROR
    }

    public static enum AWSEnumerationCreationSubStage {
        QUERY_LOCAL_RESOURCES, COMPARE, CREATE_COMPUTE_DESCRIPTIONS, CREATE_COMPUTE_STATES, GET_NEXT_PAGE, ENUMERATION_STOP
    }

    public AWSEnumerationAndCreationAdapterService() {
        super.toggleOption(ServiceOption.INSTRUMENTATION, true);
        this.clientManager = new AWSClientManager();
    }

    /**
     * The enumeration service context that holds all the information needed to determine the list of instances
     * that need to be represented in the system.
     */
    public static class EnumerationCreationContext {
        public AmazonEC2AsyncClient amazonEC2Client;
        public ComputeEnumerateResourceRequest computeEnumerationRequest;
        public AuthCredentialsService.AuthCredentialsServiceState parentAuth;
        public ComputeDescription computeHostDescription;
        public ComputeState hostComputeState;
        public AWSEnumerationCreationStages stage;
        public AWSEnumerationCreationSubStage subStage;
        public Throwable error;
        public int pageNo;
        // Mapping of instance Id and the compute state Id in the local system.
        public Map<String, String> localAWSInstanceIds;
        public Map<String, Instance> remoteAWSInstances;
        public List<Instance> instancesToBeCreated;
        public Map<String, Instance> instancesToBeUpdated;
        // Synchronized map to keep track if an enumeration service has been started in listening
        // mode for a host
        public Map<String, Boolean> enumerationHostMap;
        // The request object that is populated and sent to AWS to get the list of instances.
        public DescribeInstancesRequest describeInstancesRequest;
        // The async handler that works with the response received from AWS
        public AsyncHandler<DescribeInstancesRequest, DescribeInstancesResult> resultHandler;
        // The token to use to retrieve the next page of results from AWS. This value is null when
        // there are no more results to return.
        public String nextToken;
        public Operation awsAdapterOperation;

        public EnumerationCreationContext(AWSEnumerationRequest request, Operation op) {
            awsAdapterOperation = op;
            computeEnumerationRequest = request.computeEnumerateResourceRequest;
            parentAuth = request.parentAuth;
            computeHostDescription = request.computeHostDescription;
            enumerationHostMap = new ConcurrentSkipListMap<String, Boolean>();
            localAWSInstanceIds = new ConcurrentSkipListMap<String, String>();
            instancesToBeUpdated = new ConcurrentSkipListMap<String, Instance>();
            remoteAWSInstances = new ConcurrentSkipListMap<String, Instance>();
            instancesToBeCreated = new ArrayList<Instance>();
            stage = AWSEnumerationCreationStages.CLIENT;
            subStage = AWSEnumerationCreationSubStage.QUERY_LOCAL_RESOURCES;
            pageNo = 1;
        }
    }

    @Override
    public void handleStart(Operation startPost) {
        startHelperServices(startPost);
        super.handleStart(startPost);
    }

    @Override
    public void handleStop(Operation op) {
        this.clientManager.cleanUp();
        super.handleStop(op);
    }

    @Override
    public void handlePatch(Operation op) {
        setOperationHandlerInvokeTimeStat(op);
        if (!op.hasBody()) {
            op.fail(new IllegalArgumentException("body is required"));
            return;
        }
        EnumerationCreationContext awsEnumerationContext = new EnumerationCreationContext(
                op.getBody(AWSEnumerationRequest.class), op);
        if (awsEnumerationContext.computeEnumerationRequest.isMockRequest) {
            // patch status to parent task
            AdapterUtils.sendPatchToEnumerationTask(this,
                    awsEnumerationContext.computeEnumerationRequest.enumerationTaskReference);
            return;
        }
        handleEnumerationRequest(awsEnumerationContext);
    }

    /**
     * Starts the related services for the Enumeration Service
     */
    private void startHelperServices(Operation startPost) {
        Operation postAWScomputeDescriptionService = Operation
                .createPost(UriUtils.buildUri(this.getHost(),
                        AWSComputeDescriptionCreationAdapterService.SELF_LINK))
                .setReferer(this.getUri());

        Operation postAWscomputeStateService = Operation.createPost(
                UriUtils.buildUri(this.getHost(),
                        AWSComputeStateCreationAdapterService.SELF_LINK))
                .setReferer(this.getUri());

        this.getHost().startService(postAWScomputeDescriptionService,
                new AWSComputeDescriptionCreationAdapterService());
        this.getHost().startService(postAWscomputeStateService,
                new AWSComputeStateCreationAdapterService());

        Consumer<Operation> onSuccess = (o) -> {
            this.logInfo(
                    "Successfully started up all the services related to the AWS Enumeration Creation Service");
            return;
        };
        Set<URI> serviceURLS = new HashSet<URI>();
        serviceURLS.add(
                UriUtils.buildUri(this.getHost(),
                        AWSComputeDescriptionCreationAdapterService.SELF_LINK));
        serviceURLS
                .add(UriUtils.buildUri(this.getHost(),
                        AWSComputeStateCreationAdapterService.SELF_LINK));
        PhotonModelUtils.checkFactoryAvailability(this, startPost, serviceURLS, onSuccess);
    }

    /**
     * Handles the different steps required to hit the AWS endpoint and get the set of resources available and
     * proceed to update the state in the local system based on the received data.
     *
     */
    private void handleEnumerationRequest(EnumerationCreationContext aws) {
        switch (aws.stage) {
        case CLIENT:
            getAWSAsyncClient(aws, AWSEnumerationCreationStages.ENUMERATE);
            break;
        case ENUMERATE:
            switch (aws.computeEnumerationRequest.enumerationAction) {
            case START:
                if (aws.enumerationHostMap
                        .containsKey(getHostEnumKey(aws.computeHostDescription))) {
                    logInfo("Enumeration for creation already started for %s",
                            aws.computeHostDescription.name);
                } else {
                    aws.enumerationHostMap.put(getHostEnumKey(aws.computeHostDescription), true);
                    logInfo("Started enumeration for creation for %s",
                            aws.computeHostDescription.name);
                }
                aws.computeEnumerationRequest.enumerationAction = EnumerationAction.REFRESH;
                handleEnumerationRequest(aws);
                break;
            case REFRESH:
                if (aws.pageNo == 1) {
                    logInfo("Running enumeration service for creation in refresh mode for %s",
                            aws.computeHostDescription.name);
                }
                logInfo("Processing page %d ", aws.pageNo);
                aws.pageNo++;
                if (aws.describeInstancesRequest == null) {
                    creatAWSRequestAndAsyncHandler(aws);
                }
                aws.amazonEC2Client.describeInstancesAsync(aws.describeInstancesRequest,
                        aws.resultHandler);
                break;
            case STOP:
                if (!aws.enumerationHostMap
                        .containsKey(getHostEnumKey(aws.computeHostDescription))) {
                    logInfo("Enumeration for creation is not running or has already been stopped for %s",
                            aws.computeHostDescription.name);
                } else {
                    aws.enumerationHostMap.remove(getHostEnumKey(aws.computeHostDescription));
                    logInfo("Stopping enumeration service for creation for %s",
                            aws.computeHostDescription.name);
                }
                setOperationDurationStat(aws.awsAdapterOperation);
                aws.awsAdapterOperation.complete();
                break;
            default:
                break;
            }
            break;
        case ERROR:
            AdapterUtils.sendFailurePatchToEnumerationTask(this,
                    aws.computeEnumerationRequest.enumerationTaskReference, aws.error);
            break;
        default:
            logSevere("Unknown AWS enumeration stage %s ", aws.stage.toString());
            aws.error = new Exception("Unknown AWS enumeration stage %s");
            AdapterUtils.sendFailurePatchToEnumerationTask(this,
                    aws.computeEnumerationRequest.enumerationTaskReference, aws.error);
            break;
        }
    }

    /**
     * Method to instantiate the AWS Async client for future use
     * @param aws
     */
    private void getAWSAsyncClient(EnumerationCreationContext aws,
            AWSEnumerationCreationStages next) {
        aws.amazonEC2Client = clientManager.getOrCreateEC2Client(aws.parentAuth,
                aws.computeHostDescription.zoneId, this,
                aws.computeEnumerationRequest.enumerationTaskReference,
                aws.computeEnumerationRequest.isMockRequest, true);
        aws.stage = next;
        handleEnumerationRequest(aws);
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
     * Initializes and saves a reference to the request object that is sent to AWS to get a page of instances. Also saves an instance
     * to the async handler that will be used to handle the responses received from AWS. It sets the nextToken value in the request
     * object sent to AWS for getting the next page of results from AWS.
     * @param aws
     */
    private void creatAWSRequestAndAsyncHandler(EnumerationCreationContext aws) {
        DescribeInstancesRequest request = new DescribeInstancesRequest();
        Filter runningInstanceFilter = getAWSNonTerminatedInstancesFilter();
        request.getFilters().add(runningInstanceFilter);
        request.setMaxResults(getQueryPageSize());
        request.setNextToken(aws.nextToken);
        aws.describeInstancesRequest = request;
        AsyncHandler<DescribeInstancesRequest, DescribeInstancesResult> resultHandler = new AWSEnumerationAsyncHandler(
                this, aws);
        aws.resultHandler = resultHandler;
    }

    /**
     * The async handler to handle the success and errors received after invoking the describe instances
     * API on AWS
     */
    public static class AWSEnumerationAsyncHandler implements
            AsyncHandler<DescribeInstancesRequest, DescribeInstancesResult> {

        private AWSEnumerationAndCreationAdapterService service;
        private EnumerationCreationContext aws;
        private OperationContext opContext;

        private AWSEnumerationAsyncHandler(AWSEnumerationAndCreationAdapterService service,
                EnumerationCreationContext aws) {
            this.service = service;
            this.aws = aws;
            this.opContext = OperationContext.getOperationContext();
        }

        @Override
        public void onError(Exception exception) {
            OperationContext.restoreOperationContext(opContext);
            AdapterUtils.sendFailurePatchToEnumerationTask(service,
                    aws.computeEnumerationRequest.enumerationTaskReference,
                    exception);

        }

        @Override
        public void onSuccess(DescribeInstancesRequest request,
                DescribeInstancesResult result) {
            OperationContext.restoreOperationContext(opContext);
            int totalNumberOfInstances = 0;
            // Print the details of the instances discovered on the AWS endpoint
            for (Reservation r : result.getReservations()) {
                for (Instance i : r.getInstances()) {
                    service.logInfo("%d=====Instance details %s =====",
                            ++totalNumberOfInstances,
                            i.getInstanceId());
                    aws.remoteAWSInstances.put(i.getInstanceId(), i);
                }
            }
            service.logInfo("Successfully enumerated %d instances on the AWS host",
                    totalNumberOfInstances);
            // Save the reference to the next token that will be used to retrieve the next page of
            // results from AWS.
            aws.nextToken = result.getNextToken();
            // Since there is filtering of resources at source, there can be a case when no
            // resources are returned from AWS.
            if (aws.remoteAWSInstances.size() == 0) {
                if (aws.nextToken != null) {
                    aws.subStage = AWSEnumerationCreationSubStage.GET_NEXT_PAGE;
                } else {
                    aws.subStage = AWSEnumerationCreationSubStage.ENUMERATION_STOP;
                }
            }
            handleReceivedEnumerationData();
        }

        /**
         * Uses the received enumeration information and compares it against it the state of the local system and then tries to
         * find and fix the gaps. At a high level this is the sequence of steps that is followed:
         * 1) Create a query to get the list of local compute states
         * 2) Compare the list of local resources against the list received from the AWS endpoint.
         * 3) Create the instances not know to the local system. These are represented using a combination
         * of compute descriptions and compute states.
         * 4) Find and create a representative list of compute descriptions.
         * 5) Create compute states to represent each and every VM that was discovered on the AWS endpoint.
         */
        private void handleReceivedEnumerationData() {
            switch (aws.subStage) {
            case QUERY_LOCAL_RESOURCES:
                getLocalResources(AWSEnumerationCreationSubStage.COMPARE);
                break;
            case COMPARE:
                compareLocalStateWithEnumerationData(
                        AWSEnumerationCreationSubStage.CREATE_COMPUTE_DESCRIPTIONS);
                break;
            case CREATE_COMPUTE_DESCRIPTIONS:
                if (aws.instancesToBeCreated.size() > 0) {
                    createComputeDescriptions(AWSEnumerationCreationSubStage.CREATE_COMPUTE_STATES);
                } else {
                    if (aws.nextToken == null) {
                        aws.subStage = AWSEnumerationCreationSubStage.ENUMERATION_STOP;
                    } else {
                        aws.subStage = AWSEnumerationCreationSubStage.GET_NEXT_PAGE;
                    }
                    handleReceivedEnumerationData();
                }
                break;
            case CREATE_COMPUTE_STATES:
                AWSEnumerationCreationSubStage next;
                if (aws.nextToken == null) {
                    next = AWSEnumerationCreationSubStage.ENUMERATION_STOP;
                } else {
                    next = AWSEnumerationCreationSubStage.GET_NEXT_PAGE;
                }
                createComputeStates(next);
                break;
            case GET_NEXT_PAGE:
                getNextPageFromEnumerationAdapter(
                        AWSEnumerationCreationSubStage.QUERY_LOCAL_RESOURCES);
                break;
            case ENUMERATION_STOP:
                signalStopToEnumerationAdapter();
                break;
            default:
                Throwable t = new Exception("Unknown AWS enumeration sub stage");
                signalErrorToEnumerationAdapter(t);
            }
        }

        /**
         * Query the local data store and retrieve all the the compute states that exist filtered by the instanceIds
         * that are received in the enumeration data from AWS.
         */
        public void getLocalResources(AWSEnumerationCreationSubStage next) {
            // query all ComputeState resources for the cluster filtered by the received set of
            // instance Ids
            QueryTask q = new QueryTask();
            q.setDirect(true);
            q.querySpec = new QueryTask.QuerySpecification();
            q.querySpec.options.add(QueryOption.EXPAND_CONTENT);
            q.querySpec.query = Query.Builder.create()
                    .addKindFieldClause(ComputeService.ComputeState.class)
                    .addFieldClause(ComputeState.FIELD_NAME_PARENT_LINK,
                            aws.computeEnumerationRequest.parentComputeLink)
                    .build();

            QueryTask.Query instanceIdFilterParentQuery = new QueryTask.Query();
            instanceIdFilterParentQuery.occurance = Occurance.MUST_OCCUR;
            for (String instanceId : aws.remoteAWSInstances.keySet()) {
                QueryTask.Query instanceIdFilter = new QueryTask.Query()
                        .setTermPropertyName(ComputeState.FIELD_NAME_ID)
                        .setTermMatchValue(instanceId);
                instanceIdFilter.occurance = QueryTask.Query.Occurance.SHOULD_OCCUR;
                instanceIdFilterParentQuery.addBooleanClause(instanceIdFilter);
            }
            q.querySpec.query.addBooleanClause(instanceIdFilterParentQuery);
            q.documentSelfLink = UUID.randomUUID().toString();
            q.tenantLinks = aws.computeHostDescription.tenantLinks;
            // create the query to find resources
            service.sendRequest(Operation
                    .createPost(service, ServiceUriPaths.CORE_QUERY_TASKS)
                    .setBody(q)
                    .setCompletion((o, e) -> {
                        if (e != null) {
                            service.logSevere("Failure retrieving query results: %s",
                                    e.toString());
                            signalErrorToEnumerationAdapter(e);
                            return;
                        }
                        QueryTask responseTask = o.getBody(QueryTask.class);
                        for (Object s : responseTask.results.documents.values()) {
                            ComputeState localInstance = Utils.fromJson(s,
                                    ComputeService.ComputeState.class);
                            aws.localAWSInstanceIds.put(localInstance.id,
                                    localInstance.documentSelfLink);
                        }
                        service.logInfo(
                                "Got result of the query to get local resources. There are %d instances known to the system.",
                                responseTask.results.documentCount);
                        aws.subStage = next;
                        handleReceivedEnumerationData();
                        return;
                    }));
        }

        /**
         * Compares the local list of VMs against what is received from the AWS endpoint. Saves a list of the VMs that
         * have to be created in the local system to correspond to the remote AWS endpoint.
         */
        private void compareLocalStateWithEnumerationData(AWSEnumerationCreationSubStage next) {
            // No remote instances
            if (aws.remoteAWSInstances == null || aws.remoteAWSInstances.size() == 0) {
                service.logInfo(
                        "No resources discovered on the remote system. Nothing to be created locally");
                // no local instances
            } else if (aws.localAWSInstanceIds == null || aws.localAWSInstanceIds.size() == 0) {
                for (String key : aws.remoteAWSInstances.keySet()) {
                    aws.instancesToBeCreated.add(aws.remoteAWSInstances.get(key));
                }
                // compare and add the ones that do not exist locally for creation. Mark others
                // for updates.
            } else {
                for (String key : aws.remoteAWSInstances.keySet()) {
                    if (!aws.localAWSInstanceIds.containsKey(key)) {
                        aws.instancesToBeCreated.add(aws.remoteAWSInstances.get(key));
                        // A map of the local compute state id and the corresponding latest
                        // state on AWS
                    } else {
                        aws.instancesToBeUpdated.put(aws.localAWSInstanceIds.get(key),
                                aws.remoteAWSInstances.get(key));
                    }
                }
            }
            aws.subStage = next;
            handleReceivedEnumerationData();
        }

        /**
         * Posts a compute description to the compute description service for creation.
         * @param documentSelfLink
         */
        private void createComputeDescriptions(AWSEnumerationCreationSubStage next) {
            AWSComputeDescriptionCreationState cd = new AWSComputeDescriptionCreationState();
            cd.instancesToBeCreated = aws.instancesToBeCreated;
            cd.parentTaskLink = aws.computeEnumerationRequest.enumerationTaskReference;
            cd.authCredentiaslLink = aws.parentAuth.documentSelfLink;
            cd.tenantLinks = aws.computeHostDescription.tenantLinks;

            service.sendRequest(Operation
                    .createPatch(service, AWSComputeDescriptionCreationAdapterService.SELF_LINK)
                    .setBody(cd)
                    .setCompletion((o, e) -> {
                        if (e != null) {
                            service.logSevere(
                                    "Failure creating compute descriptions %s",
                                    Utils.toString(e));
                            signalErrorToEnumerationAdapter(e);
                            return;
                        } else {
                            service.logInfo(
                                    "Successfully created compute descriptions. Proceeding to next state.");
                            aws.subStage = next;
                            handleReceivedEnumerationData();
                            return;
                        }
                    }));
        }

        /**
         * Creates the compute states that represent the instances received from AWS during enumeration.
         * @param next
         */
        private void createComputeStates(AWSEnumerationCreationSubStage next) {
            AWSComputeStateForCreation awsComputeState = new AWSComputeStateForCreation();
            awsComputeState.instancesToBeCreated = aws.instancesToBeCreated;
            awsComputeState.instancesToBeUpdated = aws.instancesToBeUpdated;
            awsComputeState.parentComputeLink = aws.computeEnumerationRequest.parentComputeLink;
            awsComputeState.resourcePoolLink = aws.computeEnumerationRequest.resourcePoolLink;
            awsComputeState.parentTaskLink = aws.computeEnumerationRequest.enumerationTaskReference;
            awsComputeState.tenantLinks = aws.computeHostDescription.tenantLinks;
            awsComputeState.parentAuth = aws.parentAuth;
            awsComputeState.regionId = aws.computeHostDescription.zoneId;

            service.sendRequest(Operation
                    .createPatch(service, AWSComputeStateCreationAdapterService.SELF_LINK)
                    .setBody(awsComputeState)
                    .setCompletion((o, e) -> {
                        if (e != null) {
                            service.logSevere(
                                    "Failure creating compute states %s",
                                    Utils.toString(e));
                            signalErrorToEnumerationAdapter(e);
                            return;
                        } else {
                            service.logInfo(
                                    "Successfully created compute states. Proceeding to next state.");
                            aws.subStage = next;
                            handleReceivedEnumerationData();
                            return;
                        }
                    }));
        }

        /**
         * Signals Enumeration Stop to the AWS enumeration adapter. The AWS enumeration adapter will in turn patch the
         * parent task to indicate completion.
         */
        private void signalStopToEnumerationAdapter() {
            aws.computeEnumerationRequest.enumerationAction = EnumerationAction.STOP;
            service.handleEnumerationRequest(aws);
        }

        /**
         * Signals error to the AWS enumeration adapter. The adapter will in turn clean up resources and signal error to the parent task.
         */
        private void signalErrorToEnumerationAdapter(Throwable t) {
            aws.error = t;
            aws.stage = AWSEnumerationCreationStages.ERROR;
            service.handleEnumerationRequest(aws);
        }

        /**
         * Calls the AWS enumeration adapter to get the next page from AWSs
         * @param next
         */
        private void getNextPageFromEnumerationAdapter(AWSEnumerationCreationSubStage next) {
            // Reset all the results from the last page that was processed.
            aws.remoteAWSInstances.clear();
            aws.instancesToBeCreated.clear();
            aws.localAWSInstanceIds.clear();
            aws.describeInstancesRequest.setNextToken(aws.nextToken);
            aws.subStage = next;
            service.handleEnumerationRequest(aws);
        }
    }
}
