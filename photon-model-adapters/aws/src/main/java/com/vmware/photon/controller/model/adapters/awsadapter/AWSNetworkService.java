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

package com.vmware.photon.controller.model.adapters.awsadapter;

import static com.vmware.photon.controller.model.adapters.awsadapter.AWSUtils.cleanupEC2ClientResources;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import com.amazonaws.services.ec2.AmazonEC2AsyncClient;
import com.amazonaws.services.ec2.model.AttachInternetGatewayRequest;
import com.amazonaws.services.ec2.model.CreateInternetGatewayResult;
import com.amazonaws.services.ec2.model.CreateRouteRequest;
import com.amazonaws.services.ec2.model.CreateSubnetRequest;
import com.amazonaws.services.ec2.model.CreateSubnetResult;
import com.amazonaws.services.ec2.model.CreateVpcRequest;
import com.amazonaws.services.ec2.model.CreateVpcResult;
import com.amazonaws.services.ec2.model.DeleteInternetGatewayRequest;
import com.amazonaws.services.ec2.model.DeleteSubnetRequest;
import com.amazonaws.services.ec2.model.DeleteVpcRequest;
import com.amazonaws.services.ec2.model.DescribeInternetGatewaysRequest;
import com.amazonaws.services.ec2.model.DescribeInternetGatewaysResult;
import com.amazonaws.services.ec2.model.DescribeRouteTablesRequest;
import com.amazonaws.services.ec2.model.DescribeRouteTablesResult;
import com.amazonaws.services.ec2.model.DescribeSubnetsRequest;
import com.amazonaws.services.ec2.model.DescribeSubnetsResult;
import com.amazonaws.services.ec2.model.DescribeVpcsRequest;
import com.amazonaws.services.ec2.model.DescribeVpcsResult;
import com.amazonaws.services.ec2.model.DetachInternetGatewayRequest;
import com.amazonaws.services.ec2.model.Filter;
import com.amazonaws.services.ec2.model.InternetGateway;
import com.amazonaws.services.ec2.model.RouteTable;
import com.amazonaws.services.ec2.model.Subnet;
import com.amazonaws.services.ec2.model.Vpc;

import com.vmware.photon.controller.model.adapterapi.NetworkInstanceRequest;
import com.vmware.photon.controller.model.adapters.util.AdapterUtils;
import com.vmware.photon.controller.model.resources.NetworkService.NetworkState;
import com.vmware.photon.controller.model.tasks.ProvisionNetworkTaskService.ProvisionNetworkTaskState;

import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.services.common.AuthCredentialsService.AuthCredentialsServiceState;

/**
 * Adapter for provisioning a netwotk on AWS.
 */
public class AWSNetworkService extends StatelessService {

    public static final String SELF_LINK = AWSUriPaths.AWS_NETWORK_SERVICE;

    public static final String MAIN_ROUTE_ASSOCIATION = "association.main";
    public static final String VPC_ID = "awsVpcID";
    public static final String SUBNET_ID = "awsSubnetID";
    public static final String GATEWAY_ID = "awsGatewayID";
    public static final String VPC_ROUTE_TABLE_ID = "awsMainRouteTableID";
    public static final String ROUTE_DEST_ALL = "0.0.0.0/0";

    /**
     * Stages for netwotk provisioning.
     */
    public enum NetworkStage {
        NETWORK_TASK_STATE, CREDENTIALS, AWS_CLIENT, NETWORK_STATE, PROVISION_VPC, REMOVE_VPC, PROVISION_SUBNET, REMOVE_SUBNET, PROVISION_GATEWAY, REMOVE_GATEWAY, PROVISION_ROUTE, REMOVE_ROUTE, FINISHED, FAILED
    }

    /**
     * Network request state.
     */
    public static class AWSNetworkRequestState {
        transient Operation netOps;
        public AuthCredentialsServiceState credentials;
        public NetworkInstanceRequest networkRequest;
        public NetworkState network;
        public NetworkStage stage;
        public ProvisionNetworkTaskState networkTaskState;
        public Throwable error;
        public AmazonEC2AsyncClient client;

    }

    @Override
    public void handleRequest(Operation op) {
        switch (op.getAction()) {
        case PATCH:
            if (!op.hasBody()) {
                op.fail(new IllegalArgumentException("body is required"));
                return;
            }
            // initialize allocation object
            AWSNetworkRequestState awsNet = new AWSNetworkRequestState();
            awsNet.netOps = op;
            awsNet.networkRequest = op.getBody(NetworkInstanceRequest.class);
            awsNet.stage = NetworkStage.NETWORK_TASK_STATE;
            handleStages(awsNet);
            break;
        default:
            super.handleRequest(op);
        }
    }

    public void handleStages(AWSNetworkRequestState awsNet) {
        switch (awsNet.stage) {
        case NETWORK_TASK_STATE:
            getNetworkTaskState(awsNet, NetworkStage.NETWORK_STATE);
            break;
        case NETWORK_STATE:
            getNetworkState(awsNet, NetworkStage.CREDENTIALS);
            break;
        case CREDENTIALS:
            getCredentials(awsNet, NetworkStage.AWS_CLIENT);
            break;
        case AWS_CLIENT:
            if (awsNet.client == null) {
                try {
                    awsNet.client = AWSUtils.getAsyncClient(awsNet.credentials,
                            awsNet.network.regionID, false, getHost().allocateExecutor(this));
                } catch (Throwable e) {
                    handleFailure(awsNet, e);
                    break;
                }
            }
            if (awsNet.networkRequest.requestType == NetworkInstanceRequest.InstanceRequestType.CREATE) {
                awsNet.stage = NetworkStage.PROVISION_VPC;
            } else {
                awsNet.stage = NetworkStage.REMOVE_GATEWAY;
            }
            handleStages(awsNet);
            break;
        case PROVISION_VPC:
            String vpcID = createVPC(awsNet.network.subnetCIDR, awsNet.client);
            updateNetworkProperties(VPC_ID, vpcID, awsNet,
                    NetworkStage.PROVISION_SUBNET);
            break;
        case PROVISION_SUBNET:
            String subnetID = createSubnet(awsNet.network.subnetCIDR,
                    getCustomProperty(awsNet, VPC_ID), awsNet.client);
            updateNetworkProperties(SUBNET_ID, subnetID, awsNet,
                    NetworkStage.PROVISION_GATEWAY);
            break;
        case PROVISION_GATEWAY:
            String gatewayID = createInternetGateway(awsNet.client);
            attachInternetGateway(getCustomProperty(awsNet, VPC_ID), gatewayID,
                    awsNet.client);
            updateNetworkProperties(GATEWAY_ID, gatewayID, awsNet,
                    NetworkStage.PROVISION_ROUTE);
            break;
        case PROVISION_ROUTE:
            RouteTable routeTable = getMainRouteTable(
                    awsNet.network.customProperties.get(VPC_ID), awsNet.client);
            createInternetRoute(getCustomProperty(awsNet, GATEWAY_ID),
                    routeTable.getRouteTableId(), ROUTE_DEST_ALL, awsNet.client);
            updateNetworkProperties(VPC_ROUTE_TABLE_ID,
                    routeTable.getRouteTableId(), awsNet, NetworkStage.FINISHED);
            break;
        case REMOVE_GATEWAY:
            detachInternetGateway(getCustomProperty(awsNet, VPC_ID),
                    getCustomProperty(awsNet, GATEWAY_ID), awsNet.client);
            deleteInternetGateway(getCustomProperty(awsNet, GATEWAY_ID),
                    awsNet.client);
            updateNetworkProperties(GATEWAY_ID, AWSUtils.NO_VALUE, awsNet,
                    NetworkStage.REMOVE_SUBNET);
            break;
        case REMOVE_SUBNET:
            deleteSubnet(getCustomProperty(awsNet, SUBNET_ID), awsNet.client);
            updateNetworkProperties(SUBNET_ID, AWSUtils.NO_VALUE, awsNet,
                    NetworkStage.REMOVE_ROUTE);
            break;
        case REMOVE_ROUTE:
            // only need to update the document, the AWS artifact will be
            // removed on VPC removal
            updateNetworkProperties(VPC_ROUTE_TABLE_ID, AWSUtils.NO_VALUE,
                    awsNet, NetworkStage.REMOVE_VPC);
            break;
        case REMOVE_VPC:
            deleteVPC(getCustomProperty(awsNet, VPC_ID), awsNet.client);
            updateNetworkProperties(VPC_ID, AWSUtils.NO_VALUE, awsNet,
                    NetworkStage.FINISHED);
            break;
        case FAILED:
            cleanupEC2ClientResources(awsNet.client);
            if (awsNet.networkRequest.provisioningTaskReference != null) {
                AdapterUtils.sendFailurePatchToProvisioningTask(this,
                        awsNet.networkRequest.provisioningTaskReference,
                        awsNet.error);
            } else {
                awsNet.netOps.fail(awsNet.error);
            }
            break;
        case FINISHED:
            cleanupEC2ClientResources(awsNet.client);
            awsNet.netOps.complete();
            AdapterUtils.sendNetworkFinishPatch(this,
                    awsNet.networkRequest.provisioningTaskReference);
            return;
        default:
            break;
        }

    }

    private void handleFailure(AWSNetworkRequestState awsNet, Throwable e) {
        logSevere(e);
        awsNet.error = e;
        awsNet.stage = NetworkStage.FAILED;
        handleStages(awsNet);
    }

    private String getCustomProperty(AWSNetworkRequestState aws, String key) {
        return aws.network.customProperties.get(key);
    }

    private void updateNetworkProperties(String key, String value,
            AWSNetworkRequestState aws, NetworkStage next) {
        if (aws.network.customProperties == null) {
            aws.network.customProperties = new HashMap<>();
        }

        aws.network.customProperties.put(key, value);

        URI networkURI = UriUtils.buildUri(this.getHost(),
                aws.networkTaskState.networkDescriptionLink);
        sendRequest(Operation.createPatch(networkURI).setBody(aws.network)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        aws.stage = NetworkStage.FAILED;
                        aws.error = e;
                        handleStages(aws);
                        return;
                    }
                    aws.stage = next;
                    handleStages(aws);
                }));

    }

    private void getCredentials(AWSNetworkRequestState aws, NetworkStage next) {
        URI authURI = UriUtils.buildUri(this.getHost(),
                aws.networkRequest.authCredentialsLink);

        sendRequest(Operation.createGet(authURI).setCompletion((o, e) -> {
            if (e != null) {
                aws.stage = NetworkStage.FAILED;
                aws.error = e;
                handleStages(aws);
                return;
            }
            aws.credentials = o.getBody(AuthCredentialsServiceState.class);
            aws.stage = next;
            handleStages(aws);
        }));
    }

    private void getNetworkState(AWSNetworkRequestState aws, NetworkStage next) {
        URI networkURI = UriUtils.buildUri(this.getHost(),
                aws.networkTaskState.networkDescriptionLink);

        sendRequest(Operation.createGet(networkURI).setCompletion((o, e) -> {
            if (e != null) {
                aws.stage = NetworkStage.FAILED;
                aws.error = e;
                handleStages(aws);
                return;
            }
            aws.network = o.getBody(NetworkState.class);
            aws.stage = next;
            handleStages(aws);
        }));
    }

    private void getNetworkTaskState(AWSNetworkRequestState aws,
            NetworkStage next) {
        sendRequest(Operation.createGet(
                aws.networkRequest.provisioningTaskReference).setCompletion(
                        (o, e) -> {
                            if (e != null) {
                                aws.stage = NetworkStage.FAILED;
                                aws.error = e;
                                handleStages(aws);
                                return;
                            }
                            aws.networkTaskState = o
                                    .getBody(ProvisionNetworkTaskState.class);
                            aws.stage = next;
                            handleStages(aws);
                        }));
    }

    public String getDefaultVPCSubnet(AWSAllocation aws) {
        String subnet = null;
        DescribeVpcsResult result = aws.amazonEC2Client.describeVpcs();
        List<Vpc> vpcs = result.getVpcs();

        for (Vpc vpc : vpcs) {
            if (vpc.isDefault()) {
                subnet = vpc.getCidrBlock();
            }
        }
        return subnet;
    }

    public Vpc getVPC(String vpcID, AmazonEC2AsyncClient client) {
        DescribeVpcsRequest req = new DescribeVpcsRequest().withVpcIds(vpcID);
        DescribeVpcsResult result = client.describeVpcs(req);
        List<Vpc> vpcs = result.getVpcs();
        if (vpcs != null && vpcs.size() == 1) {
            return vpcs.get(0);
        }
        return null;
    }

    /*
     * Get the default VPC - return null if no default specified
     */

    public Vpc getDefaultVPC(AmazonEC2AsyncClient client) {
        DescribeVpcsRequest req = new DescribeVpcsRequest();
        DescribeVpcsResult result = client.describeVpcs(req);
        List<Vpc> vpcs = result.getVpcs();
        for (Vpc vpc : vpcs) {
            if (vpc.isDefault()) {
                return vpc;
            }
        }
        return null;
    }

    /*
     * Creates the VPC and returns the VPC id
     */
    public String createVPC(String subnet, AmazonEC2AsyncClient client) {
        CreateVpcRequest req = new CreateVpcRequest().withCidrBlock(subnet);
        CreateVpcResult vpc = client.createVpc(req);

        return vpc.getVpc().getVpcId();
    }

    /*
     * Delete the specified VPC
     */
    public void deleteVPC(String vpcID, AmazonEC2AsyncClient client) {
        DeleteVpcRequest req = new DeleteVpcRequest().withVpcId(vpcID);
        client.deleteVpc(req);
    }

    public Subnet getSubnet(String subnetID, AmazonEC2AsyncClient client) {
        DescribeSubnetsRequest req = new DescribeSubnetsRequest()
                .withSubnetIds(subnetID);
        DescribeSubnetsResult subnetResult = client.describeSubnets(req);
        // if subnet not found an error thrown
        List<Subnet> subs = subnetResult.getSubnets();
        return subs.get(0);
    }

    /*
     * Creates the subnet and return the subnet id
     */
    public String createSubnet(String subnet, String vpcID,
            AmazonEC2AsyncClient client) {
        CreateSubnetRequest req = new CreateSubnetRequest().withCidrBlock(
                subnet).withVpcId(vpcID);
        CreateSubnetResult subnetResult = client.createSubnet(req);
        return subnetResult.getSubnet().getSubnetId();
    }

    /*
     * Delete the specified subnet
     */
    public void deleteSubnet(String subnetID, AmazonEC2AsyncClient client) {
        DeleteSubnetRequest req = new DeleteSubnetRequest()
                .withSubnetId(subnetID);
        client.deleteSubnet(req);
    }

    public String createInternetGateway(AmazonEC2AsyncClient client) {
        CreateInternetGatewayResult result = client.createInternetGateway();
        return result.getInternetGateway().getInternetGatewayId();
    }

    public InternetGateway getInternetGateway(String resourceID,
            AmazonEC2AsyncClient client) {
        DescribeInternetGatewaysRequest req = new DescribeInternetGatewaysRequest()
                .withInternetGatewayIds(resourceID);
        DescribeInternetGatewaysResult result = client
                .describeInternetGateways(req);
        return result.getInternetGateways().get(0);
    }

    public void deleteInternetGateway(String resourceID,
            AmazonEC2AsyncClient client) {
        DeleteInternetGatewayRequest req = new DeleteInternetGatewayRequest()
                .withInternetGatewayId(resourceID);
        client.deleteInternetGateway(req);
    }

    public void attachInternetGateway(String vpcID, String gatewayID,
            AmazonEC2AsyncClient client) {
        AttachInternetGatewayRequest req = new AttachInternetGatewayRequest()
                .withVpcId(vpcID).withInternetGatewayId(gatewayID);
        client.attachInternetGateway(req);
    }

    public void detachInternetGateway(String vpcID, String gatewayID,
            AmazonEC2AsyncClient client) {
        DetachInternetGatewayRequest req = new DetachInternetGatewayRequest()
                .withVpcId(vpcID).withInternetGatewayId(gatewayID);
        client.detachInternetGateway(req);
    }

    /*
     * Get the main route table for a given VPC
     */
    public RouteTable getMainRouteTable(String vpcID,
            AmazonEC2AsyncClient client) {
        // build filter list
        List<Filter> filters = new ArrayList<>();
        filters.add(AWSUtils.getFilter(AWSUtils.AWS_FILTER_VPC_ID, vpcID));
        filters.add(AWSUtils.getFilter(MAIN_ROUTE_ASSOCIATION, "true"));

        DescribeRouteTablesRequest req = new DescribeRouteTablesRequest()
                .withFilters(filters);
        DescribeRouteTablesResult result = client.describeRouteTables(req);

        // if nothing found error thrown, otherwise should be 1
        return result.getRouteTables().get(0);

    }

    /*
     * Create a route from a specified CIDR Subnet to a specific GW / Route Table
     */
    public void createInternetRoute(String gatewayID, String routeTableID,
            String subnet, AmazonEC2AsyncClient client) {
        CreateRouteRequest req = new CreateRouteRequest()
                .withGatewayId(gatewayID).withRouteTableId(routeTableID)
                .withDestinationCidrBlock(subnet);
        client.createRoute(req);
    }

}
