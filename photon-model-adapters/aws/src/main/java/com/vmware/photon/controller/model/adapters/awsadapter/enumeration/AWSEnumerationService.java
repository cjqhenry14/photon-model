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

import static com.vmware.photon.controller.model.adapters.awsadapter.AWSUtils.cleanupEC2ClientResources;

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
import com.amazonaws.services.ec2.model.Instance;
import com.amazonaws.services.ec2.model.Reservation;

import com.vmware.photon.controller.model.adapterapi.ComputeEnumerateResourceRequest;
import com.vmware.photon.controller.model.adapterapi.EnumerationAction;
import com.vmware.photon.controller.model.adapters.awsadapter.AWSConstants;
import com.vmware.photon.controller.model.adapters.awsadapter.AWSUriPaths;
import com.vmware.photon.controller.model.adapters.awsadapter.AWSUtils;
import com.vmware.photon.controller.model.adapters.awsadapter.enumeration.AWSComputeDescriptionCreationService.AWSComputeDescriptionState;
import com.vmware.photon.controller.model.adapters.awsadapter.enumeration.AWSComputeDescriptionCreationService.ComputeDescCreationStage;
import com.vmware.photon.controller.model.adapters.awsadapter.enumeration.AWSComputeStateCreationService.AWSComputeState;
import com.vmware.photon.controller.model.adapters.util.AdapterUtils;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription;
import com.vmware.photon.controller.model.resources.ComputeService;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.tasks.ComputeSubTaskService;
import com.vmware.photon.controller.model.util.PhotonModelUtils;

import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.AuthCredentialsService;
import com.vmware.xenon.services.common.AuthCredentialsService.AuthCredentialsServiceState;
import com.vmware.xenon.services.common.QueryTask;
import com.vmware.xenon.services.common.QueryTask.Query;
import com.vmware.xenon.services.common.QueryTask.QuerySpecification.QueryOption;
import com.vmware.xenon.services.common.ServiceUriPaths;

/**
 * Enumeration Adapter for the Amazon Web Services. Performs a list call to the AWS API
 * and reconciles the local state with the state on the remote system.
 *
 */
public class AWSEnumerationService extends StatelessService {

    public static final String SELF_LINK = AWSUriPaths.AWS_ENUMERATION_SERVICE;

    public static enum AWSEnumerationStages {
        HOSTDESC, PARENTAUTH, CLIENT, ENUMERATE, ERROR
    }

    public static enum AWSEnumerationSubStage {
        QUERY_LOCAL_RESOURCES, COMPARE, CREATE_COMPUTE_DESCRIPTIONS, PATCH_COMPLETION
    }

    /**
     * Enumeration request state.
     */
    public static class EnumerationContext {

        public AmazonEC2AsyncClient amazonEC2Client;
        public ComputeEnumerateResourceRequest computeEnumerationRequest;
        public AuthCredentialsService.AuthCredentialsServiceState parentAuth;
        public ComputeDescription computeHostDescription;
        public ComputeState hostComputeState;
        public AWSEnumerationStages stage;
        public AWSEnumerationSubStage subStage;
        public Throwable error;
        // Mapping of instance Id and the compute state Id in the local system.
        public Map<String, String> localAWSInstanceIds;
        public Map<String, Instance> remoteAWSInstances;
        List<Instance> instancesToBeCreated;
        // Set to hold the representative set of compute descriptions to be created in the system.
        Set<String> computeDescriptionSet;
        // Synchronized map to keep track if an enumeration service has been started in listening
        // mode for a host
        public Map<String, Boolean> enumerationHostMap;

        public EnumerationContext(ComputeEnumerateResourceRequest request) {
            computeEnumerationRequest = request;
            enumerationHostMap = new ConcurrentSkipListMap<String, Boolean>();
            localAWSInstanceIds = new ConcurrentSkipListMap<String, String>();
            remoteAWSInstances = new ConcurrentSkipListMap<String, Instance>();
            instancesToBeCreated = new ArrayList<Instance>();
            stage = AWSEnumerationStages.HOSTDESC;
            subStage = AWSEnumerationSubStage.QUERY_LOCAL_RESOURCES;
            computeDescriptionSet = new HashSet<String>();
        }
    }

    @Override
    public void handleStart(Operation startPost) {
        startHelperServices(startPost);
        super.handleStart(startPost);
    }

    @Override
    public void handlePatch(Operation op) {
        if (!op.hasBody()) {
            op.fail(new IllegalArgumentException("body is required"));
            return;
        }
        op.complete();
        EnumerationContext awsEnumerationData = new EnumerationContext(
                op.getBody(ComputeEnumerateResourceRequest.class));
        validateState(awsEnumerationData);
        if (awsEnumerationData.computeEnumerationRequest.isMockRequest) {
            // patch status to parent task
            AdapterUtils.sendPatchToTask(this,
                    awsEnumerationData.computeEnumerationRequest.enumerationTaskReference);
            return;
        }
        handleEnumerationRequest(awsEnumerationData);
    }
    /**
     * Starts the related services for the Enumeration Service
     */
    private void startHelperServices(Operation startPost) {
        Operation postAWScomputeDescriptionService = Operation
                .createPost(UriUtils.buildUri(this.getHost(),
                        AWSComputeDescriptionCreationService.SELF_LINK))
                .setReferer(this.getUri());

        Operation postAWscomputeStateService = Operation.createPost(
                UriUtils.buildUri(this.getHost(),
                        AWSComputeStateCreationService.SELF_LINK))
                .setReferer(this.getUri());

        this.getHost().startService(postAWScomputeDescriptionService,
                new AWSComputeDescriptionCreationService());
        this.getHost().startService(postAWscomputeStateService,
                new AWSComputeStateCreationService());

        Consumer<Operation> onSuccess = (o) -> {
            this.logInfo(
                    "Successfully started up all the services related to the AWS Enumeration Service");
            return;
        };
        Set<URI> serviceURLS = new HashSet<URI>();
        serviceURLS.add(
                UriUtils.buildUri(this.getHost(), AWSComputeDescriptionCreationService.SELF_LINK));
        serviceURLS
                .add(UriUtils.buildUri(this.getHost(), AWSComputeStateCreationService.SELF_LINK));
        PhotonModelUtils.checkFactoryAvailability(this, startPost, serviceURLS, onSuccess);
    }

    /**
     * Handles the different steps required to hit the AWS endpoint and get the set of resources available and
     * proceed to update the state in the local system based on the received data.
     *
     */
    private void handleEnumerationRequest(EnumerationContext aws) {
        switch (aws.stage) {
        case HOSTDESC:
            getHostComputeDescription(aws, AWSEnumerationStages.PARENTAUTH);
            break;
        case PARENTAUTH:
            getParentAuth(aws, AWSEnumerationStages.CLIENT);
            break;
        case CLIENT:
            getAWSAsyncClient(aws, AWSEnumerationStages.ENUMERATE);
            break;
        case ENUMERATE:
            switch (aws.computeEnumerationRequest.enumerationAction) {
            case START:
                if (!aws.enumerationHostMap
                        .containsKey(getHostEnumKey(aws.computeHostDescription))) {
                    logInfo("Enumeration already started for %s", aws.computeHostDescription);
                } else {
                    aws.enumerationHostMap.put(getHostEnumKey(aws.computeHostDescription), true);
                    logInfo("Started enumeration for %s", aws.computeHostDescription.name);
                }
                break;
            case REFRESH:
                logInfo("Running enumeration service in refresh mode for %s",
                        aws.computeHostDescription.name);
                AsyncHandler<DescribeInstancesRequest, DescribeInstancesResult> resultHandler = new AWSEnumerationAsyncHandler(
                        this, aws);
                aws.amazonEC2Client.describeInstancesAsync(resultHandler);
                break;
            case STOP:
                if (!aws.enumerationHostMap
                        .containsKey(getHostEnumKey(aws.computeHostDescription))) {
                    logInfo("Enumeration is not running or has already been stopped for %s",
                            aws.computeHostDescription.name);
                } else {
                    logInfo("Stopping enumeration service for %s", aws.computeHostDescription);
                }
                cleanupEC2ClientResources(aws.amazonEC2Client);
                break;
            default:
                break;
            }
            break;
        case ERROR:
            patchErrorToParentTask(aws);
            cleanupEC2ClientResources(aws.amazonEC2Client);
            break;
        default:
            cleanupEC2ClientResources(aws.amazonEC2Client);
            aws.error = new Exception("Unknown AWS enumeration stage");
            patchErrorToParentTask(aws);
            break;
        }
    }

    /**
     * Method to retrieve the parent compute host on which the enumeration task will be performed.
     * @param aws
     */
    private void getHostComputeDescription(EnumerationContext aws, AWSEnumerationStages next) {
        Consumer<Operation> onSuccess = (op) -> {
            aws.computeHostDescription = op.getBody(ComputeDescription.class);
            aws.stage = next;
            handleEnumerationRequest(aws);
        };
        URI parentURI = UriUtils.buildExpandLinksQueryUri(
                UriUtils.buildUri(this.getHost(),
                        aws.computeEnumerationRequest.computeDescriptionLink));
        AdapterUtils.getServiceState(this, parentURI, onSuccess, getFailureConsumer(aws));
    }

    /**
     * Private method to arrive at the credentials needed to call the AWS API for enumerating the instances.
     * @param aws
     */
    private void getParentAuth(EnumerationContext aws, AWSEnumerationStages next) {
        URI authUri = UriUtils.buildUri(this.getHost(),
                aws.computeHostDescription.authCredentialsLink);
        Consumer<Operation> onSuccess = (op) -> {
            aws.parentAuth = op.getBody(AuthCredentialsServiceState.class);
            aws.stage = next;
            handleEnumerationRequest(aws);
        };
        AdapterUtils.getServiceState(this, authUri, onSuccess, getFailureConsumer(aws));
    }

    /**
     * Method to instantiate the AWS Async client for future use
     * @param aws
     */
    private void getAWSAsyncClient(EnumerationContext aws, AWSEnumerationStages next) {
        if (aws.amazonEC2Client == null) {
            try {
                aws.amazonEC2Client = AWSUtils.getAsyncClient(
                        aws.parentAuth, aws.computeHostDescription.zoneId,
                        aws.computeEnumerationRequest.isMockRequest,
                        getHost().allocateExecutor(this));
            } catch (Throwable e) {
                logSevere(e);
                aws.error = e;
                patchErrorToParentTask(aws);
            }
        }
        aws.stage = next;
        handleEnumerationRequest(aws);
    }

    /**
     * Method to get the failed consumer to handle the error that was raised.
     * @param aws The enumeration context
     * @return
     */
    private Consumer<Throwable> getFailureConsumer(EnumerationContext aws) {
        return (t) -> {
            aws.error = t;
            aws.stage = AWSEnumerationStages.ERROR;
            handleEnumerationRequest(aws);
        };
    }

    /**
     * Method to patch back the error to the parent task that was running this.
     * @param aws The enumeration context
     */
    private void patchErrorToParentTask(EnumerationContext aws) {
        if (aws.computeEnumerationRequest.enumerationTaskReference != null) {
            AdapterUtils.sendFailurePatchToTask(this,
                    aws.computeEnumerationRequest.enumerationTaskReference, aws.error);
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
     * Method to validate that the passed in Enumeration Request State is valid.
     * Validating that the parent compute link and the adapter links are populated
     * in the request.
     *
     * Also defaulting the EnumerationRequestType to REFRESH
     * @param AWSstate The enumeration context.
     */
    private void validateState(EnumerationContext AWSstate) {
        if (AWSstate.computeEnumerationRequest.computeDescriptionLink == null) {
            throw new IllegalArgumentException("computeDescriptionLink is required.");
        }
        if (AWSstate.computeEnumerationRequest.adapterManagementReference == null) {
            throw new IllegalArgumentException(
                    "adapterManagementReference is required.");
        }
        if (AWSstate.computeEnumerationRequest.parentComputeLink == null) {
            throw new IllegalArgumentException(
                    "parentComputeLink is required.");
        }
        if (AWSstate.computeEnumerationRequest.enumerationAction == null) {
            AWSstate.computeEnumerationRequest.enumerationAction = EnumerationAction.REFRESH;
        }
    }

    /**
     * The async handler to handle the success and errors received after invoking the describe instances
     * API on AWS
     */
    public static class AWSEnumerationAsyncHandler implements
            AsyncHandler<DescribeInstancesRequest, DescribeInstancesResult> {

        private static final int AWS_TERMINATED_CODE = 48;
        private StatelessService service;
        private EnumerationContext aws;

        private AWSEnumerationAsyncHandler(StatelessService service,
                EnumerationContext aws) {
            this.service = service;
            this.aws = aws;
        }

        @Override
        public void onError(Exception exception) {
            AdapterUtils.sendFailurePatchToTask(this.service,
                    aws.computeEnumerationRequest.enumerationTaskReference, exception);
        }

        @Override
        public void onSuccess(DescribeInstancesRequest request,
                DescribeInstancesResult result) {
            int totalNumberOfInstances = 0;
            // Print the details of the instances discovered on the AWS endpoint
            for (Reservation r : result.getReservations()) {
                for (Instance i : r.getInstances()) {
                    // Do not add information about terminated instances to the local system.
                    if (i.getState().getCode() != AWS_TERMINATED_CODE) {
                        service.logInfo("%d=====Instance details %s =====",
                                ++totalNumberOfInstances,
                                i.toString());
                        aws.remoteAWSInstances.put(i.getInstanceId(), i);
                    }
                }
            }
            service.logFine("Successfully enumerated %d instances on the AWS host",
                    totalNumberOfInstances);
            handleReceivedEnumerationData();
        }

        /**
         * Uses the received enumeration information and compares it against it the state of the local system and then tries to
         * find and fix the gaps. At a high level this is the sequence of steps that is followed:
         * 1) Create a query to get the list of local compute states
         * 2) Get the result of the above query.
         * 3) For each link in the result set get the complete compute state with the description.
         * 4) Compare the list of local resources against the list received from the AWS endpoint.
         * 5) Create the instances not know to the local system.
         */
        private void handleReceivedEnumerationData() {
            switch (aws.subStage) {
            case QUERY_LOCAL_RESOURCES:
                fireQueryToGetLocalResources(
                        AWSEnumerationSubStage.COMPARE);
                break;
            case COMPARE:
                compareLocalStateWithEnumerationData(
                        AWSEnumerationSubStage.CREATE_COMPUTE_DESCRIPTIONS);
                break;
            case CREATE_COMPUTE_DESCRIPTIONS:
                if (aws.instancesToBeCreated.size() > 0) {
                    createComputeDescriptions();
                } else {
                    aws.subStage = AWSEnumerationSubStage.PATCH_COMPLETION;
                    handleReceivedEnumerationData();
                }
                break;
            case PATCH_COMPLETION:
                patchCompletionToParent();
                break;
            default:
                aws.error = new Exception("Unknown AWS enumeration sub stage");
                AdapterUtils.sendFailurePatchToTask(service,
                        aws.computeEnumerationRequest.enumerationTaskReference, aws.error);
            }
        }

        /**
         * Creates a query to get all the resources in the local system filtered by the instance Ids received from the AWS
         * endpoint and fires off a query task to get the results.
         */
        public void fireQueryToGetLocalResources(AWSEnumerationSubStage next) {
            // query all ComputeState resources for the cluster
            List<Query> instanceIdFilters = new ArrayList<Query>();
            for (String instanceId : aws.remoteAWSInstances.keySet()) {
                QueryTask.Query instanceIdFilter = new QueryTask.Query()
                        .setTermPropertyName(
                                QueryTask.QuerySpecification
                                        .buildCompositeFieldName(
                                                ComputeState.FIELD_NAME_CUSTOM_PROPERTIES,
                                                AWSConstants.AWS_INSTANCE_ID))
                        .setTermMatchValue(instanceId);
                instanceIdFilters.add(instanceIdFilter);
            }

            QueryTask q = new QueryTask();
            q.setDirect(true);
            q.querySpec = new QueryTask.QuerySpecification();
            q.querySpec.options.add(QueryOption.EXPAND_CONTENT);
            q.querySpec.query = Query.Builder.create()
                    .addKindFieldClause(ComputeService.ComputeState.class)
                    .addFieldClause(ComputeState.FIELD_NAME_PARENT_LINK,
                            aws.computeEnumerationRequest.parentComputeLink)
                    .build();
            for (String instanceId : aws.remoteAWSInstances.keySet()) {
                QueryTask.Query instanceIdFilter = new QueryTask.Query()
                        .setTermPropertyName(
                                QueryTask.QuerySpecification
                                        .buildCompositeFieldName(
                                                ComputeState.FIELD_NAME_CUSTOM_PROPERTIES,
                                                AWSConstants.AWS_INSTANCE_ID))
                        .setTermMatchValue(instanceId);
                instanceIdFilter.occurance = QueryTask.Query.Occurance.SHOULD_OCCUR;
                q.querySpec.query.addBooleanClause(instanceIdFilter);
            }
            q.documentSelfLink = UUID.randomUUID().toString();
            // create the query to find resources
            service.sendRequest(Operation
                    .createPost(service, ServiceUriPaths.CORE_QUERY_TASKS)
                    .setBody(q)
                    .setCompletion((o, e) -> {
                        if (e != null) {
                            service.logSevere("Failure retrieving query results: %s",
                                    e.toString());
                            AdapterUtils.sendFailurePatchToTask(this.service,
                                    aws.computeEnumerationRequest.enumerationTaskReference, e);
                            return;
                        }
                        QueryTask responseTask = o.getBody(QueryTask.class);
                        for (Object s : responseTask.results.documents.values()) {
                            ComputeState localInstance = Utils.fromJson(s,
                                    ComputeService.ComputeState.class);
                            aws.localAWSInstanceIds.put(
                                    localInstance.customProperties
                                            .get(AWSConstants.AWS_INSTANCE_ID),
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
        private void compareLocalStateWithEnumerationData(AWSEnumerationSubStage next) {
            // Find all the instances to be created in the local system
            if (aws.remoteAWSInstances != null && aws.localAWSInstanceIds != null) {
                for (String key : aws.remoteAWSInstances.keySet()) {
                    if (!aws.localAWSInstanceIds.containsKey(key)) {
                        aws.instancesToBeCreated.add(aws.remoteAWSInstances.get(key));
                    }
                }
                aws.subStage = next;
                handleReceivedEnumerationData();
            }
        }

        /**
         * Signals completion to the parent task.
         */
        private void patchCompletionToParent() {
            service.logInfo(
                    "Completed enumeration. Signalling completion to the parent task");
            AdapterUtils.sendPatchToTask(service,
                    aws.computeEnumerationRequest.enumerationTaskReference);
        }

        /**
         * Method to create Compute descriptions associated with the instances received from the AWS host.
         * param next
         */
        private void createComputeDescriptions() {
            if (aws.instancesToBeCreated != null && aws.instancesToBeCreated.size() > 0) {
                service.logInfo(
                        "Need to create %d resources unknown to the local system",
                        aws.instancesToBeCreated.size());
                getRepresentativeListOfComputeDescriptions();
                for (String key : aws.computeDescriptionSet) {
                    service.logInfo("Creating compute description %s", key);
                    AWSComputeDescriptionState cd = new AWSComputeDescriptionState();
                    cd.creationStage = ComputeDescCreationStage.CHECK_EXISTING_CD_QUERY;
                    cd.zoneId = key.substring(0, key.indexOf("~"));
                    cd.instanceType = key.substring(key.indexOf("~") + 1);
                    cd.authCredentiaslLink = aws.parentAuth.documentSelfLink;
                    cd.enumerationTaskLink = aws.computeEnumerationRequest.enumerationTaskReference;
                    doPatchComputeDescription(cd, null);
                }
                service.logInfo("These resources are represented by %d new compute descriptions ",
                        aws.computeDescriptionSet.size());
            } else {
                service.logInfo("No instances need to be created in the local system");
            }
        }

        /**
         * Create a sub task that will track the ProvisionComputeHostTask
         * completions.
         */
        private void createSubTaskForComputeDescriptionCallbacks(AWSComputeDescriptionState cd) {
            ComputeSubTaskService.ComputeSubTaskState subTaskInitState = new ComputeSubTaskService.ComputeSubTaskState();
            // Tell the sub task where and what to patch on completion . Once all the compute
            // descriptions are created,
            // invoke the ComputeState task with the list of instances to be created.
            AWSComputeState awsComputeState = new AWSComputeState();
            awsComputeState.instancesToBeCreated = aws.instancesToBeCreated;
            awsComputeState.parentComputeLink = aws.computeEnumerationRequest.parentComputeLink;
            awsComputeState.resourcePoolLink = aws.computeEnumerationRequest.resourcePoolLink;
            awsComputeState.parentTaskLink = aws.computeEnumerationRequest.enumerationTaskReference;

            subTaskInitState.parentPatchBody = Utils.toJson(awsComputeState);
            subTaskInitState.parentTaskLink = AWSComputeStateCreationService.SELF_LINK;
            subTaskInitState.completionsRemaining = aws.computeDescriptionSet.size();
            Operation startPost = Operation
                    .createPost(service, UUID.randomUUID().toString())
                    .setBody(subTaskInitState)
                    .setCompletion(
                            (o, e) -> {
                                if (e != null) {
                                    service.logWarning("Failure creating sub task: %s",
                                            Utils.toString(e));
                                    AdapterUtils.sendFailurePatchToTask(service,
                                            aws.computeEnumerationRequest.enumerationTaskReference,
                                            e);
                                    return;
                                }
                                ComputeSubTaskService.ComputeSubTaskState body = o
                                        .getBody(ComputeSubTaskService.ComputeSubTaskState.class);
                                // continue, passing the sub task link
                                doPatchComputeDescription(cd,
                                        body.documentSelfLink);
                            });
            service.logInfo("Created a sub task for compute description creation");
            service.getHost().startService(startPost, new ComputeSubTaskService());
        }

        /**
         * Posts a compute description to the compute description service for creation.
         * @param documentSelfLink
         */
        private void doPatchComputeDescription(AWSComputeDescriptionState cd, String subTaskLink) {
            if (subTaskLink == null) {
                // recurse after creating a sub task
                createSubTaskForComputeDescriptionCallbacks(cd);
                return;
            }
            cd.parentTaskLink = UriUtils.buildUri(service.getHost(), subTaskLink);
            service.sendRequest(Operation
                    .createPatch(service, AWSComputeDescriptionCreationService.SELF_LINK)
                    .setBody(cd)
                    .setCompletion((o, e) -> {
                        if (e == null) {
                            return;
                        }
                        service.logSevere(
                                "Failure creating compute description creation task: %s",
                                Utils.toString(e));
                        AdapterUtils.sendFailurePatchToTask(service,
                                aws.computeEnumerationRequest.enumerationTaskReference,
                                e);
                        return;
                    }));

        }

        /**
         * Arrives at the representative set of compute descriptions for the instances discovered in AWS
         * and need to be created in the local system. TODO harden key to include more attributes?
         */
        private void getRepresentativeListOfComputeDescriptions() {
            for (Instance i : aws.instancesToBeCreated) {
                String key = getKeyForComputeDescription(i);
                aws.computeDescriptionSet.add(key);
            }
        }

        /**
         * Gets the key to uniquely represent a compute description that needs to be created in the system.
         * Currently uses regionId and instanceType
         */
        private String getKeyForComputeDescription(Instance i) {
            // Representing the compute-description as a key regionId~instanceType
            return getRegionId(i).concat("~").concat(i.getInstanceType());
        }

        /**
        * Returns the region Id for the AWS instance
        * @param vm
        * @return the region id
        */
        private String getRegionId(Instance i) {
            // Drop the zone suffix "a" ,"b" etc to get the region Id.
            String zoneId = i.getPlacement().getAvailabilityZone();
            String regiondId = zoneId.substring(0, zoneId.length() - 1);
            return regiondId;
        }

    }
}
