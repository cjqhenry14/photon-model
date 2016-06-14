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

import static com.vmware.photon.controller.model.adapters.awsadapter.AWSUtils.DEFAULT_SECURITY_GROUP_DESC;
import static com.vmware.photon.controller.model.adapters.awsadapter.AWSUtils.DEFAULT_SECURITY_GROUP_NAME;
import static com.vmware.photon.controller.model.adapters.awsadapter.AWSUtils.buildRules;
import static com.vmware.photon.controller.model.adapters.awsadapter.AWSUtils.createSecurityGroup;
import static com.vmware.photon.controller.model.adapters.awsadapter.AWSUtils.getSecurityGroup;
import static com.vmware.photon.controller.model.adapters.awsadapter.AWSUtils.updateIngressRules;

import java.net.URI;
import java.util.HashMap;
import java.util.List;

import com.amazonaws.services.ec2.AmazonEC2AsyncClient;
import com.amazonaws.services.ec2.model.AuthorizeSecurityGroupEgressRequest;
import com.amazonaws.services.ec2.model.DeleteSecurityGroupRequest;
import com.amazonaws.services.ec2.model.DescribeSecurityGroupsRequest;
import com.amazonaws.services.ec2.model.DescribeSecurityGroupsResult;
import com.amazonaws.services.ec2.model.IpPermission;
import com.amazonaws.services.ec2.model.SecurityGroup;

import com.vmware.photon.controller.model.adapterapi.FirewallInstanceRequest;
import com.vmware.photon.controller.model.adapters.awsadapter.util.AWSClientManager;
import com.vmware.photon.controller.model.adapters.awsadapter.util.AWSClientManagerFactory;
import com.vmware.photon.controller.model.adapters.util.AdapterUtils;
import com.vmware.photon.controller.model.resources.FirewallService.FirewallState;
import com.vmware.photon.controller.model.resources.FirewallService.FirewallState.Allow;
import com.vmware.photon.controller.model.tasks.ProvisionFirewallTaskService.ProvisionFirewallTaskState;

import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.services.common.AuthCredentialsService.AuthCredentialsServiceState;

/**
 * Firewall service for AWS. AWS Firewalls are implemented by a SecurityGroup
 * which will be the primary artifact created and managed.
 */
public class AWSFirewallService extends StatelessService {
    public static final String SELF_LINK = AWSUriPaths.AWS_FIREWALL_ADAPTER;
    public static final String SECURITY_GROUP_ID = "awsSecurityGroupID";
    public static final String NAME_PREFIX = "vmw";

    private AWSClientManager clientManager;

    public AWSFirewallService() {
        this.clientManager = AWSClientManagerFactory.getClientManager(false);
    }

    /**
     * Firewall stages.
     */
    public enum FirewallStage {
        FW_TASK_STATE, CREDENTIALS, AWS_CLIENT, FIREWALL_STATE, PROVISION_SECURITY_GROUP, UPDATE_RULES, REMOVE_SECURITY_GROUP, FINISHED, FAILED
    }

    /**
     * Firewall request stages.
     */
    public static class AWSFirewallRequestState {
        public AmazonEC2AsyncClient client;
        transient Operation fwOperation;
        public AuthCredentialsServiceState credentials;
        public FirewallInstanceRequest firewallRequest;
        public FirewallState firewall;
        public String securityGroupID;
        public FirewallStage stage;
        public ProvisionFirewallTaskState firewallTaskState;
        public Throwable error;

    }

    @Override
    public void handleStop(Operation op) {
        AWSClientManagerFactory.returnClientManager(this.clientManager, false);
        super.handleStop(op);
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
            requestState.firewallRequest = op
                    .getBody(FirewallInstanceRequest.class);
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
            requestState.client = this.clientManager.getOrCreateEC2Client(requestState.credentials,
                    requestState.firewall.regionID, this,
                    requestState.firewallRequest.provisioningTaskReference,
                    requestState.firewallRequest.isMockRequest, false);
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
            requestState.securityGroupID = createSecurityGroup(
                    requestState.client, sgName, DEFAULT_SECURITY_GROUP_DESC, null);
            updateFirewallProperties(SECURITY_GROUP_ID,
                    requestState.securityGroupID, requestState,
                    FirewallStage.UPDATE_RULES);
            break;
        case UPDATE_RULES:
            updateIngressRules(requestState.client,
                    requestState.firewall.ingress, requestState.securityGroupID);
            updateEgressRules(requestState.client,
                    requestState.firewall.egress, requestState.securityGroupID);
            requestState.stage = FirewallStage.FINISHED;
            handleStages(requestState);
            break;
        case REMOVE_SECURITY_GROUP:
            deleteSecurityGroup(requestState.client,
                    getCustomProperty(requestState, SECURITY_GROUP_ID));
            updateFirewallProperties(SECURITY_GROUP_ID, AWSUtils.NO_VALUE,
                    requestState, FirewallStage.FINISHED);
            break;
        case FAILED:
            if (requestState.firewallRequest.provisioningTaskReference != null) {
                AdapterUtils.sendFailurePatchToProvisioningTask(this,
                        requestState.firewallRequest.provisioningTaskReference,
                        requestState.error);
            } else {
                requestState.fwOperation.fail(requestState.error);
            }
            break;
        case FINISHED:
            requestState.fwOperation.complete();
            AdapterUtils.sendNetworkFinishPatch(this,
                    requestState.firewallRequest.provisioningTaskReference);
            return;
        default:
            break;
        }

    }

    private String getCustomProperty(AWSFirewallRequestState requestState,
            String key) {
        return requestState.firewall.customProperties.get(key);
    }

    private void updateFirewallProperties(String key, String value,
            AWSFirewallRequestState requestState, FirewallStage next) {
        if (requestState.firewall.customProperties == null) {
            requestState.firewall.customProperties = new HashMap<>();
        }

        requestState.firewall.customProperties.put(key, value);

        URI networkURI = UriUtils.buildUri(this.getHost(),
                requestState.firewallTaskState.firewallDescriptionLink);
        sendRequest(Operation.createPatch(networkURI)
                .setBody(requestState.firewall).setCompletion((o, e) -> {
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

    private void getFirewallTaskState(AWSFirewallRequestState requestState,
            FirewallStage next) {
        sendRequest(Operation.createGet(
                requestState.firewallRequest.provisioningTaskReference)
                .setCompletion(
                        (o, e) -> {
                            if (e != null) {
                                requestState.stage = FirewallStage.FAILED;
                                requestState.error = e;
                                handleStages(requestState);
                                return;
                            }
                            requestState.firewallTaskState = o
                                    .getBody(ProvisionFirewallTaskState.class);
                            requestState.stage = next;
                            handleStages(requestState);
                        }));
    }

    private void getCredentials(AWSFirewallRequestState requestState,
            FirewallStage next) {
        URI authURI = UriUtils.buildUri(this.getHost(),
                requestState.firewall.authCredentialsLink);

        sendRequest(Operation.createGet(authURI).setCompletion(
                (o, e) -> {
                    if (e != null) {
                        requestState.stage = FirewallStage.FAILED;
                        requestState.error = e;
                        handleStages(requestState);
                        return;
                    }
                    requestState.credentials = o
                            .getBody(AuthCredentialsServiceState.class);
                    requestState.stage = next;
                    handleStages(requestState);
                }));
    }

    private void getFirewallState(AWSFirewallRequestState requestState,
            FirewallStage next) {
        sendRequest(Operation.createGet(
                requestState.firewallRequest.firewallReference).setCompletion(
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

    public SecurityGroup getSecurityGroupByID(AmazonEC2AsyncClient client,
            String groupID) {
        SecurityGroup cellGroup = null;

        DescribeSecurityGroupsRequest req = new DescribeSecurityGroupsRequest()
                .withGroupIds(groupID);
        DescribeSecurityGroupsResult cellGroups = client
                .describeSecurityGroups(req);
        if (cellGroups != null) {
            cellGroup = cellGroups.getSecurityGroups().get(0);
        }
        return cellGroup;
    }

    public void deleteSecurityGroup(AmazonEC2AsyncClient client) {
        SecurityGroup group = getSecurityGroup(client,
                DEFAULT_SECURITY_GROUP_NAME);
        if (group != null) {
            deleteSecurityGroup(client, group.getGroupId());
        }
    }

    public void deleteSecurityGroup(AmazonEC2AsyncClient client, String groupId) {

        DeleteSecurityGroupRequest req = new DeleteSecurityGroupRequest()
                .withGroupId(groupId);

        client.deleteSecurityGroup(req);
    }

    public void updateEgressRules(AmazonEC2AsyncClient client,
            List<Allow> rules, String groupId) {
        updateEgressRules(client, groupId, buildRules(rules));
    }

    public void updateEgressRules(AmazonEC2AsyncClient client, String groupId,
            List<IpPermission> rules) {
        AuthorizeSecurityGroupEgressRequest req = new AuthorizeSecurityGroupEgressRequest()
                .withGroupId(groupId).withIpPermissions(rules);
        client.authorizeSecurityGroupEgress(req);
    }

}
