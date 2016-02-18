/*
 * Copyright 2015 VMware, Inc. All Rights Reserved.
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

import com.vmware.photon.controller.model.adapterapi.FirewallInstanceRequest;
import com.vmware.photon.controller.model.resources.FirewallService.FirewallState;
import com.vmware.photon.controller.model.resources.FirewallService.FirewallState.Allow;
import com.vmware.photon.controller.model.tasks.ProvisionFirewallTaskService.ProvisionFirewallTaskState;

import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.services.common.AuthCredentialsService.AuthCredentialsServiceState;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.services.ec2.AmazonEC2AsyncClient;
import com.amazonaws.services.ec2.model.AuthorizeSecurityGroupEgressRequest;
import com.amazonaws.services.ec2.model.AuthorizeSecurityGroupIngressRequest;
import com.amazonaws.services.ec2.model.CreateSecurityGroupRequest;
import com.amazonaws.services.ec2.model.CreateSecurityGroupResult;
import com.amazonaws.services.ec2.model.DeleteSecurityGroupRequest;
import com.amazonaws.services.ec2.model.DescribeSecurityGroupsRequest;
import com.amazonaws.services.ec2.model.DescribeSecurityGroupsResult;
import com.amazonaws.services.ec2.model.IpPermission;
import com.amazonaws.services.ec2.model.SecurityGroup;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * Firewall service for AWS.  AWS Firewalls are implemented by a SecurityGroup
 * which will be the primary artifact created and managed.
 */
public class AWSFirewallService extends StatelessService {
    public static final String SELF_LINK = AWSUriPaths.AWS_FIREWALL_SERVICE;
    public static final String SECURITY_GROUP_ID = "awsSecurityGroupID";
    public static final String DEFAULT_SECURITY_GROUP_NAME = "cell-manager-security-group";
    public static final String DEFAULT_SECURITY_GROUP_DESC = "VMware Cell Manager security group";
    protected static final int[] DEFAULT_ALLOWED_PORTS = { 22, 443, 80, 8080, 2376, 2375, 1 };
    public static final String DEFAULT_ALLOWED_NETWORK = "0.0.0.0/0";
    public static final String DEFAULT_PROTOCOL = "tcp";
    public static final String NAME_PREFIX = "vmw";

    /**
     * Firewall stages.
     */
    public enum FirewallStage {
        FW_TASK_STATE,
        CREDENTIALS,
        AWS_CLIENT,
        FIREWALL_STATE,
        PROVISION_SECURITY_GROUP,
        UPDATE_RULES,
        REMOVE_SECURITY_GROUP,
        FINISHED,
        FAILED
    }

    /**
     * Firewall request stages.
     */
    public static class AWSFirewallRequestState {

        transient Operation fwOperation;
        public AmazonEC2AsyncClient client;
        public AuthCredentialsServiceState credentials;
        public FirewallInstanceRequest firewallRequest;
        public FirewallState firewall;
        public String securityGroupID;
        public FirewallStage stage;
        public ProvisionFirewallTaskState firewallTaskState;
        public Throwable error;

    }



    @Override
    public void handleRequest(Operation op) {

        switch (op.getAction()) {
        case PATCH:
            if (!op.hasBody()) {
                op.fail(new IllegalArgumentException("body is required"));
                return;
            }
            // initialize request state object
            AWSFirewallRequestState requestState = new AWSFirewallRequestState();
            requestState.fwOperation = op;
            requestState.firewallRequest = op.getBody(FirewallInstanceRequest.class);
            requestState.stage = FirewallStage.FW_TASK_STATE;
            handleStages(requestState);
            break;
        default:
            super.handleRequest(op);
        }
    }

    public void handleStages(AWSFirewallRequestState requestState) {
        switch (requestState.stage) {
        case FW_TASK_STATE:
            getFirewallTaskState(requestState, FirewallStage.FIREWALL_STATE);
            break;
        case FIREWALL_STATE:
            getFirewallState(requestState, FirewallStage.CREDENTIALS);
            break;
        case CREDENTIALS:
            getCredentials(requestState, FirewallStage.AWS_CLIENT);
            break;
        case AWS_CLIENT:
            try {
                requestState.client = AWSUtils.getAsyncClient(requestState.credentials,
                        requestState.firewall.regionID, false);
            } catch (Throwable e) {
                handleFailure(requestState, e);
                break;
            }
            if (requestState.firewallRequest.requestType == FirewallInstanceRequest.InstanceRequestType.CREATE) {
                requestState.stage = FirewallStage.PROVISION_SECURITY_GROUP;
            } else {
                requestState.stage = FirewallStage.REMOVE_SECURITY_GROUP;
            }
            handleStages(requestState);
            break;
        case PROVISION_SECURITY_GROUP:
            // create security group name from task id for now
            String sgName = NAME_PREFIX + requestState.firewall.id;
            requestState.securityGroupID = createSecurityGroup(requestState.client,
                    sgName, DEFAULT_SECURITY_GROUP_DESC);
            updateFirewallProperties(SECURITY_GROUP_ID, requestState.securityGroupID,
                    requestState, FirewallStage.UPDATE_RULES);
            break;
        case UPDATE_RULES:
            updateIngressRules(requestState.client, requestState.firewall.ingress, requestState.securityGroupID);
            updateEgressRules(requestState.client, requestState.firewall.egress, requestState.securityGroupID);
            requestState.stage = FirewallStage.FINISHED;
            handleStages(requestState);
            break;
        case REMOVE_SECURITY_GROUP:
            deleteSecurityGroup(requestState.client, getCustomProperty(requestState, SECURITY_GROUP_ID));
            updateFirewallProperties(SECURITY_GROUP_ID, AWSUtils.NO_VALUE, requestState, FirewallStage.FINISHED);
            break;
        case FAILED:
            if (requestState.firewallRequest.provisioningTaskReference != null) {
                AWSUtils.sendFailurePatchToTask(this,
                        requestState.firewallRequest.provisioningTaskReference,
                        requestState.error);
            } else {
                requestState.fwOperation.fail(requestState.error);
            }
            break;
        case FINISHED:
            requestState.fwOperation.complete();
            AWSUtils.sendNetworkFinishPatch(this, requestState.firewallRequest.provisioningTaskReference);
            return;
        default:
            break;
        }

    }

    private void handleFailure(AWSFirewallRequestState requestState, Throwable e) {
        logSevere(e);
        requestState.error = e;
        requestState.stage = FirewallStage.FAILED;
        handleStages(requestState);
    }

    private String getCustomProperty(AWSFirewallRequestState requestState, String key) {
        return requestState.firewall.customProperties.get(key);
    }

    private void updateFirewallProperties(String key, String value,
            AWSFirewallRequestState requestState, FirewallStage next) {
        if (requestState.firewall.customProperties == null) {
            requestState.firewall.customProperties = new HashMap<>();
        }

        requestState.firewall.customProperties.put(key, value);

        URI networkURI = UriUtils.buildUri(this.getHost(), requestState.firewallTaskState.firewallDescriptionLink);
        sendRequest(Operation
                .createPatch(networkURI)
                .setBody(requestState.firewall)
                .setCompletion(
                        (o, e) -> {
                            if (e != null) {
                                requestState.stage = FirewallStage.FAILED;
                                requestState.error = e;
                                handleStages(requestState);
                                return;
                            }
                            requestState.stage = next;
                            handleStages(requestState);
                        }));

    }

    private void getFirewallTaskState(AWSFirewallRequestState requestState, FirewallStage next) {
        sendRequest(Operation
                .createGet(requestState.firewallRequest.provisioningTaskReference)
                .setCompletion(
                        (o, e) -> {
                            if (e != null) {
                                requestState.stage = FirewallStage.FAILED;
                                requestState.error = e;
                                handleStages(requestState);
                                return;
                            }
                            requestState.firewallTaskState = o.getBody(ProvisionFirewallTaskState.class);
                            requestState.stage = next;
                            handleStages(requestState);
                        }));
    }

    private void getCredentials(AWSFirewallRequestState requestState, FirewallStage next) {
        URI authURI = UriUtils.buildUri(this.getHost(), requestState.firewall.authCredentialsLink);

        sendRequest(Operation
                .createGet(authURI)
                .setCompletion(
                        (o, e) -> {
                            if (e != null) {
                                requestState.stage = FirewallStage.FAILED;
                                requestState.error = e;
                                handleStages(requestState);
                                return;
                            }
                            requestState.credentials = o.getBody(AuthCredentialsServiceState.class);
                            requestState.stage = next;
                            handleStages(requestState);
                        }));
    }

    private void getFirewallState(AWSFirewallRequestState requestState, FirewallStage next) {
        sendRequest(Operation
                .createGet(requestState.firewallRequest.firewallReference)
                .setCompletion(
                        (o, e) -> {
                            if (e != null) {
                                requestState.stage = FirewallStage.FAILED;
                                requestState.error = e;
                                handleStages(requestState);
                                return;
                            }
                            requestState.firewall = o.getBody(FirewallState.class);
                            requestState.stage = next;
                            handleStages(requestState);
                        }));
    }


    /*
     *   method will create new or validate existing security group
     *   has the necessary settings for CM to function.  It will return
     *   the security group id that is required during instance
     *   provisioning.
     */
    public String allocateSecurityGroup(AWSAllocation aws) {
        String groupId;
        SecurityGroup group;

        // if the group doesn't exist an exception is thrown.  We won't throw a missing group exception
        // we will continue and create the group
        try {
            group = getSecurityGroup(aws.amazonEC2Client);
            return group.getGroupId();
        } catch (AmazonServiceException t) {
            if (!t.getMessage().contains(
                    AWSFirewallService.DEFAULT_SECURITY_GROUP_NAME)) {
                throw t;
            }
        }

        AWSNetworkService netSvc = new AWSNetworkService();
        String subnet = netSvc.getDefaultVPCSubnet(aws);

        // no subnet is not an option...
        if (subnet == null) {
            throw new AmazonServiceException("default VPC not found");
        }

        try {
            groupId = createSecurityGroup(aws.amazonEC2Client);
            updateIngressRules(aws.amazonEC2Client, groupId, getDefaultRules(subnet));
        } catch (AmazonServiceException t) {
            if (t.getMessage().contains(AWSFirewallService.DEFAULT_SECURITY_GROUP_NAME)) {
                return getSecurityGroup(aws.amazonEC2Client).getGroupId();
            } else {
                throw t;
            }
        }

        return groupId;
    }

    public SecurityGroup getSecurityGroup(AmazonEC2AsyncClient client) {
        return getSecurityGroup(client, AWSFirewallService.DEFAULT_SECURITY_GROUP_NAME);
    }

    public SecurityGroup getSecurityGroup(AmazonEC2AsyncClient client, String name) {
        SecurityGroup cellGroup = null;

        DescribeSecurityGroupsRequest req = new DescribeSecurityGroupsRequest()
                .withGroupNames(name);
        DescribeSecurityGroupsResult cellGroups = client.describeSecurityGroups(req);
        if (cellGroups != null) {
            cellGroup = cellGroups.getSecurityGroups().get(0);
        }
        return cellGroup;
    }

    public SecurityGroup getSecurityGroupByID(AmazonEC2AsyncClient client, String groupID) {
        SecurityGroup cellGroup = null;

        DescribeSecurityGroupsRequest req = new DescribeSecurityGroupsRequest()
                .withGroupIds(groupID);
        DescribeSecurityGroupsResult cellGroups = client.describeSecurityGroups(req);
        if (cellGroups != null) {
            cellGroup = cellGroups.getSecurityGroups().get(0);
        }
        return cellGroup;
    }

    public String createSecurityGroup(AmazonEC2AsyncClient client) {
        return createSecurityGroup(client, DEFAULT_SECURITY_GROUP_NAME, DEFAULT_SECURITY_GROUP_DESC);
    }

    public String createSecurityGroup(AmazonEC2AsyncClient client, String name, String description) {

        CreateSecurityGroupRequest req = new CreateSecurityGroupRequest()
                .withDescription(description)
                .withGroupName(name);

        CreateSecurityGroupResult result = client.createSecurityGroup(req);

        return result.getGroupId();
    }

    public void deleteSecurityGroup(AmazonEC2AsyncClient client) {
        SecurityGroup group = getSecurityGroup(client, DEFAULT_SECURITY_GROUP_NAME);
        if (group != null) {
            deleteSecurityGroup(client, group.getGroupId());
        }
    }

    public void deleteSecurityGroup(AmazonEC2AsyncClient client, String groupId) {

        DeleteSecurityGroupRequest req = new DeleteSecurityGroupRequest()
                .withGroupId(groupId);

        client.deleteSecurityGroup(req);
    }

    public void updateIngressRules(AmazonEC2AsyncClient client, List<Allow> rules, String groupId) {
        updateIngressRules(client, groupId, buildRules(rules));
    }

    public void updateIngressRules(AmazonEC2AsyncClient client, String groupId, List<IpPermission> rules) {
        AuthorizeSecurityGroupIngressRequest req = new AuthorizeSecurityGroupIngressRequest()
                .withGroupId(groupId)
                .withIpPermissions(rules);
        client.authorizeSecurityGroupIngress(req);
    }

    public void updateEgressRules(AmazonEC2AsyncClient client, List<Allow> rules, String groupId) {
        updateEgressRules(client, groupId, buildRules(rules));
    }


    public void updateEgressRules(AmazonEC2AsyncClient client, String groupId, List<IpPermission> rules) {
        AuthorizeSecurityGroupEgressRequest req = new AuthorizeSecurityGroupEgressRequest()
                .withGroupId(groupId)
                .withIpPermissions(rules);
        client.authorizeSecurityGroupEgress(req);
    }

    private IpPermission createRule(int port) {
        return createRule(port, port, DEFAULT_ALLOWED_NETWORK, DEFAULT_PROTOCOL);
    }

    private IpPermission createRule(int fromPort, int toPort, String subnet, String protocol) {

        return new IpPermission()
                .withIpProtocol(protocol)
                .withFromPort(fromPort)
                .withToPort(toPort)
                .withIpRanges(subnet);
    }

    protected List<IpPermission> getDefaultRules(String subnet) {
        List<IpPermission> rules = new ArrayList<>();
        for (int port : DEFAULT_ALLOWED_PORTS) {
            if (port > 1) {
                rules.add(createRule(port));
            } else {
                rules.add(createRule(1, 65535, subnet, DEFAULT_PROTOCOL));
            }
        }
        return rules;
    }

    protected List<IpPermission> buildRules(List<Allow> allowRules) {
        ArrayList<IpPermission> awsRules = new ArrayList<>();
        for (Allow rule : allowRules) {
            for (String port : rule.ports) {
                int fromPort;
                int toPort;
                if (port.contains("-")) {
                    String[] ports = port.split("-");
                    fromPort = Integer.parseInt(ports[0]);
                    toPort = Integer.parseInt(ports[1]);
                } else {
                    fromPort = Integer.parseInt(port);
                    toPort = fromPort;
                }
                awsRules.add(createRule(fromPort, toPort, rule.ipRange, rule.protocol));
            }
        }
        return awsRules;
    }
}
