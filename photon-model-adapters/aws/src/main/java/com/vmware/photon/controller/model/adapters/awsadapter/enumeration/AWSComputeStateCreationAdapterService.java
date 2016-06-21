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

import static com.vmware.photon.controller.model.adapters.awsadapter.AWSConstants.AWS_ATTACHMENT_VPC_FILTER;
import static com.vmware.photon.controller.model.adapters.awsadapter.AWSConstants.AWS_GATEWAY_ID;
import static com.vmware.photon.controller.model.adapters.awsadapter.AWSConstants.AWS_MAIN_ROUTE_ASSOCIATION;
import static com.vmware.photon.controller.model.adapters.awsadapter.AWSConstants.AWS_VPC_ROUTE_TABLE_ID;
import static com.vmware.photon.controller.model.adapters.awsadapter.AWSUtils.AWS_FILTER_VPC_ID;
import static com.vmware.photon.controller.model.adapters.awsadapter.util.AWSEnumerationUtils.getCDsRepresentingVMsInLocalSystemCreatedByEnumerationQuery;
import static com.vmware.photon.controller.model.adapters.awsadapter.util.AWSEnumerationUtils.getKeyForComputeDescriptionFromCD;
import static com.vmware.photon.controller.model.adapters.awsadapter.util.AWSEnumerationUtils.getKeyForComputeDescriptionFromInstance;
import static com.vmware.photon.controller.model.adapters.awsadapter.util.AWSEnumerationUtils.getRepresentativeListOfCDsFromInstanceList;
import static com.vmware.photon.controller.model.adapters.awsadapter.util.AWSEnumerationUtils.mapInstanceToComputeState;
import static com.vmware.photon.controller.model.adapters.awsadapter.util.AWSNetworkUtils.createOperationToUpdateOrCreateNetworkInterface;
import static com.vmware.photon.controller.model.adapters.awsadapter.util.AWSNetworkUtils.createQueryToGetExistingNetworkStatesFilteredByDiscoveredVPCs;
import static com.vmware.photon.controller.model.adapters.awsadapter.util.AWSNetworkUtils.getExistingNetworkInterfaceLink;
import static com.vmware.photon.controller.model.adapters.awsadapter.util.AWSNetworkUtils.mapIPAddressToNetworkInterfaceState;
import static com.vmware.photon.controller.model.adapters.awsadapter.util.AWSNetworkUtils.mapInstanceIPAddressToNICCreationOperations;
import static com.vmware.photon.controller.model.adapters.awsadapter.util.AWSNetworkUtils.mapVPCToNetworkState;
import static com.vmware.photon.controller.model.adapters.util.AdapterUtils.createPatchOperation;
import static com.vmware.photon.controller.model.adapters.util.AdapterUtils.createPostOperation;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import com.amazonaws.handlers.AsyncHandler;
import com.amazonaws.services.ec2.AmazonEC2AsyncClient;
import com.amazonaws.services.ec2.model.DescribeInternetGatewaysRequest;
import com.amazonaws.services.ec2.model.DescribeInternetGatewaysResult;
import com.amazonaws.services.ec2.model.DescribeRouteTablesRequest;
import com.amazonaws.services.ec2.model.DescribeRouteTablesResult;
import com.amazonaws.services.ec2.model.DescribeVpcsRequest;
import com.amazonaws.services.ec2.model.DescribeVpcsResult;
import com.amazonaws.services.ec2.model.Filter;
import com.amazonaws.services.ec2.model.Instance;
import com.amazonaws.services.ec2.model.InternetGateway;
import com.amazonaws.services.ec2.model.InternetGatewayAttachment;
import com.amazonaws.services.ec2.model.RouteTable;
import com.amazonaws.services.ec2.model.Tag;
import com.amazonaws.services.ec2.model.Vpc;

import com.vmware.photon.controller.model.adapters.awsadapter.AWSUriPaths;
import com.vmware.photon.controller.model.adapters.awsadapter.AWSUtils;
import com.vmware.photon.controller.model.adapters.awsadapter.util.AWSClientManager;
import com.vmware.photon.controller.model.adapters.awsadapter.util.AWSClientManagerFactory;
import com.vmware.photon.controller.model.adapters.util.AdapterUtils;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription;
import com.vmware.photon.controller.model.resources.ComputeService;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.resources.NetworkInterfaceService;
import com.vmware.photon.controller.model.resources.NetworkInterfaceService.NetworkInterfaceState;
import com.vmware.photon.controller.model.resources.NetworkService;
import com.vmware.photon.controller.model.resources.NetworkService.NetworkState;

import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.OperationContext;
import com.vmware.xenon.common.OperationJoin;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.AuthCredentialsService;
import com.vmware.xenon.services.common.QueryTask;
import com.vmware.xenon.services.common.ServiceUriPaths;

/**
 * Stateless service for the creation of compute states. It accepts a list of AWS instances that need to be created in the
 * local system.It also accepts a few additional fields required for mapping the referential integrity relationships
 * for the compute state when it is persisted in the local system.
 */
public class AWSComputeStateCreationAdapterService extends StatelessService {

    public static final String SELF_LINK = AWSUriPaths.AWS_COMPUTE_STATE_CREATION_ADAPTER;
    private AWSClientManager clientManager;

    public static enum AWSComputeStateCreationStage {
        GET_RELATED_COMPUTE_DESCRIPTIONS, POPULATE_COMPUTESTATES, CREATE_NETWORK_STATE, CREATE_COMPUTESTATES, SIGNAL_COMPLETION,
    }

    public static enum AWSNetworkCreationStage {
        CLIENT, GET_REMOTE_VPC, GET_LOCAL_NETWORK_STATES, GET_INTERNET_GATEWAY, GET_MAIN_ROUTE_TABLE, CREATE_NETWORKSTATE
    }

    /**
     * Wrapper class used to hold the tags returned from AWS. Used for the JSON serialization/de-serialization work.
     */
    public static class AWSTags {
        public List<Tag> awsTags;

        public AWSTags(List<Tag> tags) {
            this.awsTags = tags;
        }
    }

    public AWSComputeStateCreationAdapterService() {
        super.toggleOption(ServiceOption.INSTRUMENTATION, true);
        this.clientManager = AWSClientManagerFactory.getClientManager(false);
    }

    /**
     * Data holder for information related a compute state that needs to be created in the local system.
     *
     */
    public static class AWSComputeStateForCreation {
        public List<Instance> instancesToBeCreated;
        public Map<String, Instance> instancesToBeUpdated;
        public Map<String, ComputeState> computeStatesToBeUpdated;
        public String resourcePoolLink;
        public String parentComputeLink;
        public AuthCredentialsService.AuthCredentialsServiceState parentAuth;
        public String regionId;
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
        public AmazonEC2AsyncClient amazonEC2Client;
        public AWSComputeStateForCreation computeState;
        public List<Operation> createOrUpdateOperations;
        public int instanceToBeCreatedCounter = 0;
        public AWSComputeStateCreationStage creationStage;
        public AWSNetworkCreationStage networkCreationStage;
        // Holds the mapping between the instanceType (t2.micro etc) and the document self link to
        // that compute description.
        public Map<String, String> computeDescriptionMap;
        // Map for saving AWS VPC and network state associations for the discovered VPCs
        public Map<String, NetworkState> discoveredVpcNetworkStateMap;
        // Map for local network states. The key is the vpc-id.
        public Map<String, NetworkState> localNetworkStateMap;
        // Cached operation to signal completion to the AWS instance adapter once all the compute
        // states are successfully created.
        public Operation awsAdapterOperation;

        public AWSComputeServiceCreationContext(AWSComputeStateForCreation computeState,
                Operation op) {
            this.computeState = computeState;
            this.createOrUpdateOperations = new ArrayList<Operation>();
            this.discoveredVpcNetworkStateMap = new HashMap<String, NetworkState>();
            this.localNetworkStateMap = new HashMap<String, NetworkState>();
            this.computeDescriptionMap = new HashMap<String, String>();
            this.creationStage = AWSComputeStateCreationStage.GET_RELATED_COMPUTE_DESCRIPTIONS;
            this.networkCreationStage = AWSNetworkCreationStage.CLIENT;
            this.awsAdapterOperation = op;
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
        AWSComputeStateForCreation cs = op.getBody(AWSComputeStateForCreation.class);
        AWSComputeServiceCreationContext context = new AWSComputeServiceCreationContext(cs, op);
        if (cs.isMock) {
            op.complete();
        }
        handleComputeStateCreateOrUpdate(context);
    }

    /**
     * Creates the compute states in the local document store based on the AWS instances received from the remote endpoint.
     * @param context The local service context that has all the information needed to create the additional compute states
     * in the local system.
     */
    private void handleComputeStateCreateOrUpdate(AWSComputeServiceCreationContext context) {
        switch (context.creationStage) {
        case GET_RELATED_COMPUTE_DESCRIPTIONS:
            getRelatedComputeDescriptions(context,
                    AWSComputeStateCreationStage.POPULATE_COMPUTESTATES);
            break;
        case POPULATE_COMPUTESTATES:
            populateOperations(context, AWSComputeStateCreationStage.CREATE_NETWORK_STATE);
            break;
        case CREATE_NETWORK_STATE:
            createorUpdateNetworkState(context, AWSComputeStateCreationStage.CREATE_COMPUTESTATES);
            break;
        case CREATE_COMPUTESTATES:
            kickOffComputeStateCreation(context, AWSComputeStateCreationStage.SIGNAL_COMPLETION);
            break;
        case SIGNAL_COMPLETION:
            setOperationDurationStat(context.awsAdapterOperation);
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
     * Looks up the compute descriptions associated with the compute states to be created in the system.
     */
    private void getRelatedComputeDescriptions(AWSComputeServiceCreationContext context,
            AWSComputeStateCreationStage next) {
        // Get the related compute descriptions for all the compute states are to be updated and
        // created.
        HashSet<String> representativeCDSet = getRepresentativeListOfCDsFromInstanceList(
                context.computeState.instancesToBeCreated);
        representativeCDSet.addAll(getRepresentativeListOfCDsFromInstanceList(
                context.computeState.instancesToBeUpdated.values()));

        QueryTask q = getCDsRepresentingVMsInLocalSystemCreatedByEnumerationQuery(representativeCDSet,
                context.computeState.tenantLinks,
                this, context.computeState.parentTaskLink, context.computeState.regionId);

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
                                context.computeState.parentTaskLink, e);
                    }
                    QueryTask responseTask = o.getBody(QueryTask.class);
                    if (responseTask != null && responseTask.results.documentCount > 0) {
                        for (Object s : responseTask.results.documents.values()) {
                            ComputeDescription localComputeDescription = Utils.fromJson(s,
                                    ComputeDescription.class);
                            context.computeDescriptionMap.put(
                                    getKeyForComputeDescriptionFromCD(localComputeDescription),
                                    localComputeDescription.documentSelfLink);
                        }
                        logInfo(
                                "%d compute descriptions already exist in the system that match the supplied criteria. ",
                                context.computeDescriptionMap.size());
                    } else {
                        logInfo("No matching compute descriptions exist in the system.");
                    }
                    context.creationStage = next;
                    handleComputeStateCreateOrUpdate(context);
                }));

    }

    /**
     * Method to create Compute States associated with the instances received from the AWS host.
     */
    private void populateOperations(AWSComputeServiceCreationContext context,
            AWSComputeStateCreationStage next) {
        if (context.computeState.instancesToBeCreated == null
                || context.computeState.instancesToBeCreated.size() == 0) {
            logInfo("No instances need to be created in the local system");
        } else {
            logInfo("Need to create %d compute states in the local system",
                    context.computeState.instancesToBeCreated.size());
            for (int i = 0; i < context.computeState.instancesToBeCreated.size(); i++) {
                populateComputeStateAndNetworksForCreation(context,
                        context.computeState.instancesToBeCreated.get(i));
            }
        }
        if (context.computeState.instancesToBeUpdated == null
                || context.computeState.instancesToBeUpdated.size() == 0) {
            logInfo("No instances need to be updated in the local system");
        } else {
            logInfo("Need to update %d compute states in the local system",
                    context.computeState.instancesToBeUpdated.size());
            for (String instanceId : context.computeState.instancesToBeUpdated
                    .keySet()) {
                populateComputeStateAndNetworksForUpdates(context,
                        context.computeState.instancesToBeUpdated.get(instanceId),
                        context.computeState.computeStatesToBeUpdated.get(instanceId));
            }
        }
        context.creationStage = next;
        handleComputeStateCreateOrUpdate(context);

    }

    /**
     * Populates the compute state / network link associated with an AWS VM instance and creates an operation for posting it.
     */
    private void populateComputeStateAndNetworksForCreation(
            AWSComputeServiceCreationContext context,
            Instance instance) {
        ComputeService.ComputeState computeState = mapInstanceToComputeState(instance,
                context.computeState.parentComputeLink, context.computeState.resourcePoolLink,
                context.computeDescriptionMap
                        .get(getKeyForComputeDescriptionFromInstance(instance)),
                context.computeState.tenantLinks);

        // Network State. Create one network state mapping to each VPC that is discovered during
        // enumeration.
        if (!context.discoveredVpcNetworkStateMap.containsKey(instance.getVpcId())) {
            NetworkState networkState = mapVPCToNetworkState(instance,
                    context.computeState.regionId,
                    context.computeState.resourcePoolLink,
                    context.computeState.parentAuth.documentSelfLink,
                    context.computeState.tenantLinks);

            context.discoveredVpcNetworkStateMap.put(instance.getVpcId(), networkState);
        }

        // Create operations
        List<Operation> networkOperations = mapInstanceIPAddressToNICCreationOperations(
                instance, computeState, context.computeState.tenantLinks, this);
        if (networkOperations != null && !networkOperations.isEmpty()) {
            context.createOrUpdateOperations.addAll(networkOperations);
        }
        // Create operation for compute state once all the
        Operation postComputeState = createPostOperation(this, computeState,
                ComputeService.FACTORY_LINK);
        context.createOrUpdateOperations.add(postComputeState);
    }

    /**
     * Populates the compute state / network link associated with an AWS VM instance and creates an operation for PATCHing existing
     * compute and network interfaces .
     */
    private void populateComputeStateAndNetworksForUpdates(AWSComputeServiceCreationContext context,
            Instance instance, ComputeState existingComputeState) {
        // Operation for update to compute state.
        ComputeService.ComputeState computeState = mapInstanceToComputeState(instance,
                context.computeState.parentComputeLink, context.computeState.resourcePoolLink,
                existingComputeState.descriptionLink,
                context.computeState.tenantLinks);

        // NIC - Private
        String existingNICLink = getExistingNetworkInterfaceLink(existingComputeState, false);
        NetworkInterfaceState privateNICState = mapIPAddressToNetworkInterfaceState(instance,
                false, context.computeState.tenantLinks, existingNICLink);

        Operation privateNICOperation = createOperationToUpdateOrCreateNetworkInterface(
                existingComputeState, privateNICState,
                context.computeState.tenantLinks, this, false);
        context.createOrUpdateOperations.add(privateNICOperation);
        computeState.networkLinks = new ArrayList<String>();
        computeState.networkLinks.add(UriUtils.buildUriPath(
                NetworkInterfaceService.FACTORY_LINK,
                privateNICState.documentSelfLink));

        // NIC - Public
        if (instance.getPublicIpAddress() != null) {
            existingNICLink = getExistingNetworkInterfaceLink(existingComputeState, true);
            NetworkInterfaceState publicNICState = mapIPAddressToNetworkInterfaceState(instance,
                    true, context.computeState.tenantLinks, existingNICLink);
            Operation postPublicNetworkInterfaceOperation = createOperationToUpdateOrCreateNetworkInterface(
                    existingComputeState, publicNICState,
                    context.computeState.tenantLinks, this, true);
            context.createOrUpdateOperations.add(postPublicNetworkInterfaceOperation);
            computeState.networkLinks.add(UriUtils.buildUriPath(
                    NetworkInterfaceService.FACTORY_LINK, publicNICState.documentSelfLink));
        }

        // Create operation for compute state once all the associated network entities are accounted
        // for.
        Operation patchComputeState = createPatchOperation(this,
                computeState, existingComputeState.documentSelfLink);
        context.createOrUpdateOperations.add(patchComputeState);

    }

    /**
     * Kicks off the creation of all the identified compute states and networks and
     * creates a join handler for the successful completion of each one of those.
     * Patches completion to parent once all the entities are created successfully.
     */
    private void kickOffComputeStateCreation(AWSComputeServiceCreationContext context,
            AWSComputeStateCreationStage next) {
        if (context.createOrUpdateOperations == null
                || context.createOrUpdateOperations.size() == 0) {
            logInfo("There are no compute states or networks to be created");
            context.creationStage = next;
            handleComputeStateCreateOrUpdate(context);
            return;
        }
        OperationJoin.JoinedCompletionHandler joinCompletion = (ox,
                exc) -> {
            if (exc != null) {
                logSevere(
                        "Error creating a compute state and the associated network %s",
                        Utils.toString(exc));
                AdapterUtils.sendFailurePatchToEnumerationTask(this,
                        context.computeState.parentTaskLink, exc.values().iterator().next());

            }
            logInfo("Successfully created all the networks and compute states.");
            context.creationStage = next;
            handleComputeStateCreateOrUpdate(context);
            return;
        };
        OperationJoin joinOp = OperationJoin.create(context.createOrUpdateOperations);
        joinOp.setCompletion(joinCompletion);
        joinOp.sendWith(getHost());

    }

    /**
     * Creates the network state mapping to the VPC that was discovered on AWS
     */
    private void createorUpdateNetworkState(AWSComputeServiceCreationContext context,
            AWSComputeStateCreationStage next) {
        if (context.discoveredVpcNetworkStateMap == null
                || context.discoveredVpcNetworkStateMap.size() == 0) {
            context.creationStage = next;
            handleComputeStateCreateOrUpdate(context);
            return;
        }
        context.networkCreationStage = AWSNetworkCreationStage.CLIENT;
        // Setting the next stage with which the network creation completion will call into the main
        // flow.
        context.creationStage = next;
        handleNetworkStateChanges(context);
    }

    /**
     * Handles the process to create and EC2 Async client and get all the VPC related information from AWS.
     * At the very least it gets the CIDR block information for the VPC, the connected internet gateway (if any) and
     * the main route table information for the VPC.
     */
    private void handleNetworkStateChanges(AWSComputeServiceCreationContext context) {
        switch (context.networkCreationStage) {
        case CLIENT:
            getAWSAsyncClient(context, AWSNetworkCreationStage.GET_LOCAL_NETWORK_STATES);
            break;
        case GET_LOCAL_NETWORK_STATES:
            getLocalNetworkStates(context, AWSNetworkCreationStage.GET_REMOTE_VPC);
            break;
        case GET_REMOTE_VPC:
            getVPCInformation(context, AWSNetworkCreationStage.GET_INTERNET_GATEWAY);
            break;
        case GET_INTERNET_GATEWAY:
            getInternetGatewayInformation(context, AWSNetworkCreationStage.GET_MAIN_ROUTE_TABLE);
            break;
        case GET_MAIN_ROUTE_TABLE:
            getMainRouteTableInformation(context, AWSNetworkCreationStage.CREATE_NETWORKSTATE);
            break;
        case CREATE_NETWORKSTATE:
            createNetworkStateOperations(context);
            break;
        default:
            Throwable t = new IllegalArgumentException(
                    "Unknown AWS enumeration:network state creation stage");
            AdapterUtils.sendFailurePatchToEnumerationTask(this,
                    context.computeState.parentTaskLink, t);
            break;
        }
    }

    /**
     * Method to instantiate the AWS Async client for future use
     */
    private void getAWSAsyncClient(AWSComputeServiceCreationContext context,
            AWSNetworkCreationStage next) {
        context.amazonEC2Client = this.clientManager.getOrCreateEC2Client(
                context.computeState.parentAuth, context.computeState.regionId,
                this, context.computeState.parentTaskLink, true);
        context.networkCreationStage = next;
        handleNetworkStateChanges(context);
    }

    /**
     * Gets the VPC information from the local database to perform updates to existing network states.
     */
    private void getLocalNetworkStates(AWSComputeServiceCreationContext context,
            AWSNetworkCreationStage next) {
        QueryTask q = createQueryToGetExistingNetworkStatesFilteredByDiscoveredVPCs(
                context.discoveredVpcNetworkStateMap.keySet(), context.computeState.tenantLinks);
        // create the query to find resources

        sendRequest(Operation
                .createPost(this, ServiceUriPaths.CORE_QUERY_TASKS)
                .setBody(q)
                .setConnectionSharing(true)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        logSevere("Failure retrieving query results: %s",
                                e.toString());
                        AdapterUtils.sendFailurePatchToEnumerationTask(this,
                                context.computeState.parentTaskLink, e);
                        return;
                    }
                    QueryTask responseTask = o.getBody(QueryTask.class);
                    for (Object s : responseTask.results.documents.values()) {
                        NetworkState networkState = Utils.fromJson(s,
                                NetworkState.class);
                        context.localNetworkStateMap.put(networkState.id,
                                networkState);
                    }
                    logInfo("Result of query to get local networks. There are %d network states known to the system.",
                            responseTask.results.documentCount);
                    context.networkCreationStage = next;
                    handleNetworkStateChanges(context);
                    return;
                }));

    }

    /**
     * Gets the VPC information from AWS. The CIDR block information is persisted in the network state corresponding
     * to the VPC.
     */
    private void getVPCInformation(AWSComputeServiceCreationContext context,
            AWSNetworkCreationStage next) {
        DescribeVpcsRequest vpcRequest = new DescribeVpcsRequest();
        vpcRequest.getVpcIds().addAll(context.discoveredVpcNetworkStateMap.keySet());
        AWSVPCAsyncHandler asyncHandler = new AWSVPCAsyncHandler(this, next, context);
        context.amazonEC2Client.describeVpcsAsync(vpcRequest, asyncHandler);
    }

    /**
     * The async handler to handle the success and errors received after invoking the describe VPCs API on AWS
     */
    public static class AWSVPCAsyncHandler
            implements AsyncHandler<DescribeVpcsRequest, DescribeVpcsResult> {

        private AWSComputeStateCreationAdapterService service;
        private AWSComputeServiceCreationContext aws;
        private AWSNetworkCreationStage next;
        private OperationContext opContext;

        private AWSVPCAsyncHandler(AWSComputeStateCreationAdapterService service,
                AWSNetworkCreationStage next,
                AWSComputeServiceCreationContext aws) {
            this.service = service;
            this.aws = aws;
            this.next = next;
            this.opContext = OperationContext.getOperationContext();
        }

        @Override
        public void onError(Exception exception) {
            OperationContext.restoreOperationContext(this.opContext);
            AdapterUtils.sendFailurePatchToEnumerationTask(this.service,
                    this.aws.computeState.parentTaskLink,
                    exception);

        }

        @Override
        public void onSuccess(DescribeVpcsRequest request, DescribeVpcsResult result) {
            OperationContext.restoreOperationContext(this.opContext);
            // Update the CIDR blocks corresponding to the VPCs in the network state
            for (Vpc resultVPC : result.getVpcs()) {
                NetworkState networkStateToUpdate = this.aws.discoveredVpcNetworkStateMap
                        .get(resultVPC.getVpcId());
                networkStateToUpdate.subnetCIDR = resultVPC.getCidrBlock();
                if (networkStateToUpdate.subnetCIDR == null) {
                    this.service.logWarning("AWS did not return CIDR information for VPC %s",
                            resultVPC.toString());
                }
                this.aws.discoveredVpcNetworkStateMap.put(resultVPC.getVpcId(),
                        networkStateToUpdate);
            }
            this.aws.networkCreationStage = this.next;
            this.service.handleNetworkStateChanges(this.aws);
        }
    }

    /**
     * Gets the Internet gateways that are attached to the VPCs that were discovered during the enumeration process.
     */
    private void getInternetGatewayInformation(AWSComputeServiceCreationContext context,
            AWSNetworkCreationStage next) {
        DescribeInternetGatewaysRequest internetGatewayRequest = new DescribeInternetGatewaysRequest();
        List<String> vpcList = new ArrayList<String>(context.discoveredVpcNetworkStateMap.keySet());
        Filter filter = new Filter(AWS_ATTACHMENT_VPC_FILTER, vpcList);
        internetGatewayRequest.getFilters().add(filter);
        AWSInternetGatewayAsyncHandler asyncHandler = new AWSInternetGatewayAsyncHandler(this, next,
                context);
        context.amazonEC2Client.describeInternetGatewaysAsync(internetGatewayRequest, asyncHandler);
    }

    /**
     * The async handler to handle the success and errors received after invoking the describe Internet Gateways API on AWS
     */
    public static class AWSInternetGatewayAsyncHandler
            implements
            AsyncHandler<DescribeInternetGatewaysRequest, DescribeInternetGatewaysResult> {

        private AWSComputeStateCreationAdapterService service;
        private AWSComputeServiceCreationContext aws;
        private AWSNetworkCreationStage next;
        private OperationContext opContext;

        private AWSInternetGatewayAsyncHandler(AWSComputeStateCreationAdapterService service,
                AWSNetworkCreationStage next,
                AWSComputeServiceCreationContext aws) {
            this.service = service;
            this.aws = aws;
            this.next = next;
            this.opContext = OperationContext.getOperationContext();
        }

        @Override
        public void onError(Exception exception) {
            OperationContext.restoreOperationContext(this.opContext);
            AdapterUtils.sendFailurePatchToEnumerationTask(this.service,
                    this.aws.computeState.parentTaskLink,
                    exception);

        }

        /**
         * Update the Internet gateway information for the VPC in question. For the list of Internet gateways received
         * based on the vpc filter work through the list of attachments and VPCs and update the Internet gateway information
         * in the network state that maps to the VPC.
         */
        @Override
        public void onSuccess(DescribeInternetGatewaysRequest request,
                DescribeInternetGatewaysResult result) {
            OperationContext.restoreOperationContext(this.opContext);
            for (InternetGateway resultGateway : result.getInternetGateways()) {
                for (InternetGatewayAttachment attachment : resultGateway.getAttachments()) {
                    if (this.aws.discoveredVpcNetworkStateMap.containsKey(attachment.getVpcId())) {
                        NetworkState networkStateToUpdate = this.aws.discoveredVpcNetworkStateMap
                                .get(attachment.getVpcId());
                        networkStateToUpdate.customProperties.put(AWS_GATEWAY_ID,
                                resultGateway.getInternetGatewayId());
                        this.aws.discoveredVpcNetworkStateMap.put(attachment.getVpcId(),
                                networkStateToUpdate);
                    }
                }
            }
            this.aws.networkCreationStage = this.next;
            this.service.handleNetworkStateChanges(this.aws);
        }
    }

    /**
     * Gets the main route table information associated with a VPC that is being mapped to a network state in the system.     *
     */
    private void getMainRouteTableInformation(AWSComputeServiceCreationContext context,
            AWSNetworkCreationStage next) {
        DescribeRouteTablesRequest routeTablesRequest = new DescribeRouteTablesRequest();
        List<String> vpcList = new ArrayList<String>(context.discoveredVpcNetworkStateMap.keySet());

        // build filter list
        List<Filter> filters = new ArrayList<>();
        filters.add(new Filter(AWS_FILTER_VPC_ID, vpcList));
        filters.add(AWSUtils.getFilter(AWS_MAIN_ROUTE_ASSOCIATION, "true"));

        AWSMainRouteTableAsyncHandler asyncHandler = new AWSMainRouteTableAsyncHandler(this, next,
                context);
        context.amazonEC2Client.describeRouteTablesAsync(routeTablesRequest, asyncHandler);
    }

    /**
     * The async handler to handle the success and errors received after invoking the describe Route Tables API on AWS
     */
    public static class AWSMainRouteTableAsyncHandler
            implements
            AsyncHandler<DescribeRouteTablesRequest, DescribeRouteTablesResult> {

        private AWSComputeStateCreationAdapterService service;
        private AWSComputeServiceCreationContext aws;
        private AWSNetworkCreationStage next;
        private OperationContext opContext;

        private AWSMainRouteTableAsyncHandler(AWSComputeStateCreationAdapterService service,
                AWSNetworkCreationStage next,
                AWSComputeServiceCreationContext aws) {
            this.service = service;
            this.aws = aws;
            this.next = next;
            this.opContext = OperationContext.getOperationContext();
        }

        @Override
        public void onError(Exception exception) {
            OperationContext.restoreOperationContext(this.opContext);
            AdapterUtils.sendFailurePatchToEnumerationTask(this.service,
                    this.aws.computeState.parentTaskLink,
                    exception);

        }

        /**
         *Update the main route table information for the VPC that is being mapped to a network state. Query AWS for the
         *main route tables with a list of VPCs. From the result set find the relevant route table Id and upda
         */
        @Override
        public void onSuccess(DescribeRouteTablesRequest request,
                DescribeRouteTablesResult result) {
            OperationContext.restoreOperationContext(this.opContext);
            for (RouteTable routeTable : result.getRouteTables()) {
                if (this.aws.discoveredVpcNetworkStateMap.containsKey(routeTable.getVpcId())) {
                    NetworkState networkStateToUpdate = this.aws.discoveredVpcNetworkStateMap
                            .get(routeTable.getVpcId());
                    networkStateToUpdate.customProperties.put(AWS_VPC_ROUTE_TABLE_ID,
                            routeTable.getRouteTableId());
                    this.aws.discoveredVpcNetworkStateMap.put(routeTable.getVpcId(),
                            networkStateToUpdate);
                }
            }
            this.aws.networkCreationStage = this.next;
            this.service.handleNetworkStateChanges(this.aws);
        }
    }

    /**
     * Create the network state operations for all the VPCs that need to be created or updated in the system.
     */
    private void createNetworkStateOperations(AWSComputeServiceCreationContext context) {
        if (context.discoveredVpcNetworkStateMap == null
                || context.discoveredVpcNetworkStateMap.size() == 0) {
            logInfo("No new VPCs have been discovered.Nothing to do.");
        } else {
            for (String remoteVPCId : context.discoveredVpcNetworkStateMap.keySet()) {
                NetworkState networkState = context.discoveredVpcNetworkStateMap.get(remoteVPCId);
                Operation networkStateOperation = null;
                // If the local network state already exists for the VPC. Update it.
                if (context.localNetworkStateMap.containsKey(remoteVPCId)) {
                    networkStateOperation = createPatchOperation(this,
                            networkState,
                            context.localNetworkStateMap.get(remoteVPCId).documentSelfLink);
                    context.createOrUpdateOperations.add(networkStateOperation);
                    continue;
                }
                networkStateOperation = createPostOperation(this,
                        networkState, NetworkService.FACTORY_LINK);
                context.createOrUpdateOperations.add(networkStateOperation);
            }
        }
        handleComputeStateCreateOrUpdate(context);
    }

}
