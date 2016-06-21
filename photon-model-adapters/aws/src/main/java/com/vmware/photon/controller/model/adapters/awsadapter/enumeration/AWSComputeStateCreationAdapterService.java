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
import static com.vmware.photon.controller.model.adapters.awsadapter.AWSConstants.AWS_SUBNET_ID;
import static com.vmware.photon.controller.model.adapters.awsadapter.AWSConstants.AWS_TAGS;
import static com.vmware.photon.controller.model.adapters.awsadapter.AWSConstants.AWS_VPC_ID;
import static com.vmware.photon.controller.model.adapters.awsadapter.AWSConstants.AWS_VPC_ROUTE_TABLE_ID;
import static com.vmware.photon.controller.model.adapters.awsadapter.AWSConstants.HYPHEN;
import static com.vmware.photon.controller.model.adapters.awsadapter.AWSConstants.PRIVATE_INTERFACE;
import static com.vmware.photon.controller.model.adapters.awsadapter.AWSConstants.PUBLIC_INTERFACE;
import static com.vmware.photon.controller.model.adapters.awsadapter.AWSUtils.AWS_FILTER_VPC_ID;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
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
import com.vmware.photon.controller.model.resources.ComputeDescriptionService;
import com.vmware.photon.controller.model.resources.ComputeService;
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

/**
 * Stateless service for the creation of compute states. It accepts a list of AWS instances that need to be created in the
 * local system.It also accepts a few additional fields required for mapping the referential integrity relationships
 * for the compute state when it is persisted in the local system.
 */
public class AWSComputeStateCreationAdapterService extends StatelessService {

    public static final String SELF_LINK = AWSUriPaths.AWS_COMPUTE_STATE_CREATION_ADAPTER;
    private AWSClientManager clientManager;

    public static enum AWSComputeStateCreationStage {
        POPULATE_COMPUTESTATES, CREATE_NETWORK_STATE, CREATE_COMPUTESTATES, SIGNAL_COMPLETION,
    }

    public static enum AWSNetworkCreationStage {
        CLIENT, GET_VPC, GET_INTERNET_GATEWAY, GET_MAIN_ROUTE_TABLE, CREATE_NETWORKSTATE
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
        public List<Operation> createorUpdateOperations;
        public int instanceToBeCreatedCounter = 0;
        public AWSComputeStateCreationStage creationStage;
        public AWSNetworkCreationStage networkCreationStage;
        // Map for saving AWS VPC and network state associations for the discovered VPCs
        public Map<String, NetworkState> vpcNetworkStateMap;
        // Cached operation to signal completion to the AWS instance adapter once all the compute
        // states are successfully created.
        public Operation awsAdapterOperation;

        public AWSComputeServiceCreationContext(AWSComputeStateForCreation computeState,
                Operation op) {
            this.computeState = computeState;
            this.createorUpdateOperations = new ArrayList<Operation>();
            this.vpcNetworkStateMap = new HashMap<String, NetworkState>();
            this.creationStage = AWSComputeStateCreationStage.POPULATE_COMPUTESTATES;
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
        case POPULATE_COMPUTESTATES:
            createOperations(context, AWSComputeStateCreationStage.CREATE_NETWORK_STATE);
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
     * Method to create Compute States associated with the instances received from the AWS host.
     * @param next
     * @param instancesToBeCreated
     */
    private void createOperations(AWSComputeServiceCreationContext context,
            AWSComputeStateCreationStage next) {
        if (context.computeState.instancesToBeCreated == null
                || context.computeState.instancesToBeCreated.size() == 0) {
            logInfo("No instances need to be created in the local system");
            context.creationStage = next;
            handleComputeStateCreateOrUpdate(context);
            return;
        }
        logInfo("Need to create %d compute states in the local system",
                context.computeState.instancesToBeCreated.size());
        for (int i = 0; i < context.computeState.instancesToBeCreated.size(); i++) {
            populateComputeStateAndNetworks(context,
                    context.computeState.instancesToBeCreated.get(i), null, true);
        }
        logInfo("Need to update %d compute states in the local system",
                context.computeState.instancesToBeUpdated.size());
        for (String key : context.computeState.instancesToBeUpdated.keySet()) {
            populateComputeStateAndNetworks(context,
                    context.computeState.instancesToBeUpdated.get(key), key, false);
        }
        context.creationStage = next;
        handleComputeStateCreateOrUpdate(context);

    }

    /**
     * Populates the compute state / network link associated with an AWS VM instance and creates an operation for posting it.
     * @param csDetails
     */
    private void populateComputeStateAndNetworks(AWSComputeServiceCreationContext context,
            Instance instance, String existingComputeStateDocumentLink,
            boolean createFlag) {
        ComputeService.ComputeState computeState = new ComputeService.ComputeState();
        computeState.id = instance.getInstanceId();
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
        if (!instance.getTags().isEmpty()) {
            computeState.customProperties.put(AWS_TAGS,
                    Utils.toJson(new AWSTags(instance.getTags())));
        }
        computeState.tenantLinks = context.computeState.tenantLinks;

        // Network State. Create one network state mapping to each VPC that is discovered during
        // enumeration.
        computeState.customProperties.put(AWS_VPC_ID,
                instance.getVpcId());
        if (!context.vpcNetworkStateMap.containsKey(instance.getVpcId())) {
            NetworkState networkState = new NetworkState();
            networkState.id = instance.getVpcId();
            networkState.documentSelfLink = networkState.id;
            networkState.name = instance.getVpcId();
            networkState.regionID = context.computeState.regionId;
            networkState.resourcePoolLink = context.computeState.resourcePoolLink;
            networkState.authCredentialsLink = context.computeState.parentAuth.documentSelfLink;
            networkState.instanceAdapterReference = UriUtils
                    .buildUri(AWSUriPaths.AWS_INSTANCE_ADAPTER);
            networkState.tenantLinks = context.computeState.tenantLinks;
            networkState.customProperties = new HashMap<String, String>();
            networkState.customProperties.put(AWS_VPC_ID,
                    instance.getVpcId());
            networkState.customProperties.put(AWS_SUBNET_ID,
                    instance.getSubnetId());
            context.vpcNetworkStateMap.put(instance.getVpcId(), networkState);
        }
        // NIC - Private
        NetworkInterfaceState privateNICState = new NetworkInterfaceState();
        privateNICState.address = instance.getPrivateIpAddress();
        privateNICState.id = instance.getInstanceId() + HYPHEN + PRIVATE_INTERFACE;
        privateNICState.documentSelfLink = privateNICState.id;
        privateNICState.tenantLinks = context.computeState.tenantLinks;

        // Compute State Network Links
        computeState.networkLinks = new ArrayList<String>();
        computeState.networkLinks.add(UriUtils.buildUriPath(
                NetworkInterfaceService.FACTORY_LINK,
                privateNICState.id));

        // NIC - Public
        if (instance.getPublicIpAddress() != null) {
            NetworkInterfaceState publicNICState = new NetworkInterfaceState();
            publicNICState.address = instance.getPublicIpAddress();
            publicNICState.id = instance.getInstanceId() + HYPHEN + PUBLIC_INTERFACE;
            publicNICState.documentSelfLink = publicNICState.id;
            publicNICState.tenantLinks = context.computeState.tenantLinks;

            Operation postPublicNetworkInterface = Operation
                    .createPost(this,
                            NetworkInterfaceService.FACTORY_LINK)
                    .setBody(publicNICState)
                    .setReferer(getHost().getUri());

            context.createorUpdateOperations.add(postPublicNetworkInterface);

            computeState.networkLinks.add(UriUtils.buildUriPath(
                    NetworkInterfaceService.FACTORY_LINK,
                    publicNICState.id));
        }
        Operation postPrivateNetworkInterface = Operation
                .createPost(this,
                        NetworkInterfaceService.FACTORY_LINK)
                .setBody(privateNICState)
                .setReferer(getHost().getUri());

        context.createorUpdateOperations.add(postPrivateNetworkInterface);

        if (createFlag) {
            // Operations for creation of compute state.
            Operation postComputeState = Operation
                    .createPost(this, ComputeService.FACTORY_LINK)
                    .setBody(computeState)
                    .setReferer(this.getHost().getUri());
            context.createorUpdateOperations.add(postComputeState);
        } else { // Operations for update to compute state.
            URI computeStateURI = UriUtils.buildUri(this.getHost(),
                    existingComputeStateDocumentLink);
            Operation patchComputeState = Operation
                    .createPatch(computeStateURI)
                    .setBody(computeState)
                    .setReferer(this.getHost().getUri());
            context.createorUpdateOperations.add(patchComputeState);
        }
    }

    /**
     * Kicks off the creation of all the identified compute states and networks and
     * creates a join handler for the successful completion of each one of those.
     * Patches completion to parent once all the entities are created successfully.
     */
    private void kickOffComputeStateCreation(AWSComputeServiceCreationContext context,
            AWSComputeStateCreationStage next) {
        if (context.createorUpdateOperations == null
                || context.createorUpdateOperations.size() == 0) {
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
        OperationJoin joinOp = OperationJoin.create(context.createorUpdateOperations);
        joinOp.setCompletion(joinCompletion);
        joinOp.sendWith(getHost());

    }

    /**
     * Creates the network state mapping to the VPC that was discovered on AWS
     */
    private void createorUpdateNetworkState(AWSComputeServiceCreationContext context,
            AWSComputeStateCreationStage next) {
        if (context.vpcNetworkStateMap == null || context.vpcNetworkStateMap.size() == 0) {
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
            getAWSAsyncClient(context, AWSNetworkCreationStage.GET_VPC);
            break;
        case GET_VPC:
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
     * @param aws
     */
    private void getAWSAsyncClient(AWSComputeServiceCreationContext context,
            AWSNetworkCreationStage next) {
        context.amazonEC2Client = this.clientManager.getOrCreateEC2Client(
                context.computeState.parentAuth, context.computeState.regionId,
                this, context.computeState.parentTaskLink, false, true);
        context.networkCreationStage = next;
        handleNetworkStateChanges(context);
    }

    /**
     * Gets the VPC information from AWS. The CIDR block information is persisted in the network state corresponding
     * to the VPC.
     */
    private void getVPCInformation(AWSComputeServiceCreationContext context,
            AWSNetworkCreationStage next) {
        DescribeVpcsRequest vpcRequest = new DescribeVpcsRequest();
        vpcRequest.getVpcIds().addAll(context.vpcNetworkStateMap.keySet());
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
                NetworkState networkStateToUpdate = this.aws.vpcNetworkStateMap
                        .get(resultVPC.getVpcId());
                networkStateToUpdate.subnetCIDR = resultVPC.getCidrBlock();
                if (networkStateToUpdate.subnetCIDR == null) {
                    this.service.logWarning("AWS did not return CIDR information for VPC %s",
                            resultVPC.toString());
                }
                this.aws.vpcNetworkStateMap.put(resultVPC.getVpcId(), networkStateToUpdate);
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
        List<String> vpcList = new ArrayList<String>(context.vpcNetworkStateMap.keySet());
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
                    if (this.aws.vpcNetworkStateMap.containsKey(attachment.getVpcId())) {
                        NetworkState networkStateToUpdate = this.aws.vpcNetworkStateMap
                                .get(attachment.getVpcId());
                        networkStateToUpdate.customProperties.put(AWS_GATEWAY_ID,
                                resultGateway.getInternetGatewayId());
                        this.aws.vpcNetworkStateMap.put(attachment.getVpcId(), networkStateToUpdate);
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
        List<String> vpcList = new ArrayList<String>(context.vpcNetworkStateMap.keySet());

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
                if (this.aws.vpcNetworkStateMap.containsKey(routeTable.getVpcId())) {
                    NetworkState networkStateToUpdate = this.aws.vpcNetworkStateMap
                            .get(routeTable.getVpcId());
                    networkStateToUpdate.customProperties.put(AWS_VPC_ROUTE_TABLE_ID,
                            routeTable.getRouteTableId());
                    this.aws.vpcNetworkStateMap.put(routeTable.getVpcId(), networkStateToUpdate);
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
        if (context.vpcNetworkStateMap == null || context.vpcNetworkStateMap.size() == 0) {
            logInfo("No new VPCs have been discovered.Nothing to do.");
            // If there are no local VPCs , create all the discovered VPCs in the system.
        } else {
            for (String remoteVPCId : context.vpcNetworkStateMap.keySet()) {
                NetworkState networkState = context.vpcNetworkStateMap.get(remoteVPCId);
                Operation postNetworkState = Operation
                        .createPost(this, NetworkService.FACTORY_LINK)
                        .setBody(networkState)
                        .setReferer(this.getHost().getUri());
                context.createorUpdateOperations.add(postNetworkState);
            }
        }
        handleComputeStateCreateOrUpdate(context);
    }

}
