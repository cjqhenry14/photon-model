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

import static com.vmware.photon.controller.model.adapters.awsadapter.AWSConstants.INSTANCE_STATE;
import static com.vmware.photon.controller.model.adapters.awsadapter.AWSConstants.INSTANCE_STATE_PENDING;
import static com.vmware.photon.controller.model.adapters.awsadapter.AWSConstants.INSTANCE_STATE_RUNNING;
import static com.vmware.photon.controller.model.adapters.awsadapter.AWSConstants.INSTANCE_STATE_SHUTTING_DOWN;
import static com.vmware.photon.controller.model.adapters.awsadapter.AWSConstants.INSTANCE_STATE_STOPPED;
import static com.vmware.photon.controller.model.adapters.awsadapter.AWSConstants.INSTANCE_STATE_STOPPING;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.regions.Region;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.cloudwatch.AmazonCloudWatchAsyncClient;
import com.amazonaws.services.ec2.AmazonEC2AsyncClient;
import com.amazonaws.services.ec2.model.AuthorizeSecurityGroupIngressRequest;
import com.amazonaws.services.ec2.model.CreateSecurityGroupRequest;
import com.amazonaws.services.ec2.model.CreateSecurityGroupResult;
import com.amazonaws.services.ec2.model.CreateTagsRequest;
import com.amazonaws.services.ec2.model.DescribeSecurityGroupsRequest;
import com.amazonaws.services.ec2.model.DescribeSecurityGroupsResult;
import com.amazonaws.services.ec2.model.DescribeTagsRequest;
import com.amazonaws.services.ec2.model.DescribeTagsResult;
import com.amazonaws.services.ec2.model.DescribeVpcsResult;
import com.amazonaws.services.ec2.model.Filter;
import com.amazonaws.services.ec2.model.Instance;
import com.amazonaws.services.ec2.model.InstanceState;
import com.amazonaws.services.ec2.model.IpPermission;
import com.amazonaws.services.ec2.model.SecurityGroup;
import com.amazonaws.services.ec2.model.Subnet;
import com.amazonaws.services.ec2.model.Tag;
import com.amazonaws.services.ec2.model.TagDescription;
import com.amazonaws.services.ec2.model.Vpc;

import com.vmware.photon.controller.model.resources.ComputeDescriptionService;
import com.vmware.photon.controller.model.resources.ComputeService.PowerState;
import com.vmware.photon.controller.model.resources.FirewallService.FirewallState.Allow;

import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.AuthCredentialsService.AuthCredentialsServiceState;

/**
 * AWS utils.
 */
public class AWSUtils {
    public static final String AWS_TAG_NAME = "Name";
    public static final String AWS_FILTER_RESOURCE_ID = "resource-id";
    public static final String AWS_FILTER_VPC_ID = "vpc-id";
    public static final String NO_VALUE = "no-value";
    public static final String TILDA = "~";
    private static final int EXECUTOR_SHUTDOWN_INTERVAL_MINUTES = 5;
    public static final String DEFAULT_SECURITY_GROUP_NAME = "cell-manager-security-group";
    public static final String DEFAULT_SECURITY_GROUP_DESC = "VMware Cell Manager security group";
    public static final int[] DEFAULT_ALLOWED_PORTS = { 22, 443, 80, 8080,
            2376, 2375, 1 };
    public static final String DEFAULT_ALLOWED_NETWORK = "0.0.0.0/0";
    public static final String DEFAULT_PROTOCOL = "tcp";

    public static AmazonEC2AsyncClient getAsyncClient(
            AuthCredentialsServiceState credentials, String region,
            boolean isMockRequest, ExecutorService executorService) {
        AmazonEC2AsyncClient ec2AsyncClient = new AmazonEC2AsyncClient(
                new BasicAWSCredentials(credentials.privateKeyId,
                        credentials.privateKey),
                executorService);

        ec2AsyncClient.setRegion(Region.getRegion(Regions.fromName(region)));

        // make a call to validate credentials
        if (!isMockRequest) {
            ec2AsyncClient.describeAvailabilityZones();

        }
        return ec2AsyncClient;

    }

    public static AmazonCloudWatchAsyncClient getStatsAsyncClient(
            AuthCredentialsServiceState credentials, String region,
            ExecutorService executorService, boolean isMockRequest) {
        AmazonCloudWatchAsyncClient client = new AmazonCloudWatchAsyncClient(
                new BasicAWSCredentials(credentials.privateKeyId,
                        credentials.privateKey),
                executorService);

        client.setRegion(Region.getRegion(Regions.fromName(region)));
        // make a call to validate credentials
        if (!isMockRequest) {
            client.describeAlarms();
        }
        return client;
    }

    /*
     * Synchronous call to AWS to create tags for the specified resources The list of tags will be
     * applied to all provided resources
     */
    public static void createTags(Collection<String> resources,
            Collection<Tag> tags, AmazonEC2AsyncClient client) {
        CreateTagsRequest req = new CreateTagsRequest()
                .withResources(resources).withTags(tags);

        client.createTags(req);
    }

    /*
     * Create tags for a resource based on the passed map of name / value pairs
     */
    public static void createTags(String resourceID, Map<String, String> tags,
            AmazonEC2AsyncClient client) {
        ArrayList<Tag> awsTags = new ArrayList<>();
        if (tags != null) {
            tags.forEach((k, v) -> {
                Tag t = new Tag().withKey(k).withValue(v);
                awsTags.add(t);
            });
        }
        ArrayList<String> resource = new ArrayList<>();
        resource.add(resourceID);
        createTags(resource, awsTags, client);
    }

    /*
     * An AWS Resource name is actually a Tag that must be applied to a resource. This helper method
     * will take an AWS resourceID and Name and apply that as a tag
     */
    public static void setResourceName(String resourceID, String name,
            AmazonEC2AsyncClient client) {
        Tag awsName = new Tag().withKey(AWS_TAG_NAME).withValue(name);
        ArrayList<Tag> tags = new ArrayList<>();
        tags.add(awsName);
        ArrayList<String> resource = new ArrayList<>();
        resource.add(resourceID);
        createTags(resource, tags, client);
    }

    /*
     * Return the tags for a giving resource
     */
    public static List<TagDescription> getResourceTags(String resourceID,
            AmazonEC2AsyncClient client) {
        Filter resource = new Filter().withName(AWS_FILTER_RESOURCE_ID)
                .withValues(resourceID);
        DescribeTagsRequest req = new DescribeTagsRequest()
                .withFilters(resource);
        DescribeTagsResult result = client.describeTags(req);
        return result.getTags();
    }

    public static Filter getFilter(String name, String value) {
        return new Filter().withName(name).withValues(value);
    }

    /**
     * Releases all the resources allocated for the use of the AWS EC2 client.
     */
    public static void cleanupEC2ClientResources(AmazonEC2AsyncClient client) {
        if (client != null) {
            client.shutdown();
            // To ensure that no requests are made on a client on which shutdown has already been
            // invoked.
            client = null;
        }
    }

    /**
     * Releases all the resources allocated for the use of the AWS CloudWatchAsync client.
     */
    public static void cleanupCloudWatchClientResources(AmazonCloudWatchAsyncClient client) {
        if (client != null) {
            client.shutdown();
            // To ensure that no requests are made on a client on which shutdown has already been
            // invoked.
            client = null;
        }
    }

    /**
     * Returns the region Id for the AWS instance
     * @param vm
     * @return the region id
     */
    public static String getRegionId(Instance i) {
        // Drop the zone suffix "a" ,"b" etc to get the region Id.
        String zoneId = i.getPlacement().getAvailabilityZone();
        String regiondId = zoneId.substring(0, zoneId.length() - 1);
        return regiondId;
    }

    /**
     * Maps the Aws machine state to {@link PowerState}
     * @param state
     * @return the {@link PowerState} of the machine
     */
    public static PowerState mapToPowerState(InstanceState state) {
        PowerState powerState = PowerState.UNKNOWN;
        switch (state.getCode()) {
        case 16:
            powerState = PowerState.ON;
            break;
        case 80:
            powerState = PowerState.OFF;
            break;
        default:
            break;
        }
        return powerState;
    }

    /**
     * Creates a filter for the instances that are in non terminated state on the AWS endpoint.
     * @return
     */
    public static Filter getAWSNonTerminatedInstancesFilter() {
        // Create a filter to only get non terminated instances from the remote instance.
        List<String> stateValues = new ArrayList<String>(Arrays.asList(INSTANCE_STATE_RUNNING,
                INSTANCE_STATE_PENDING, INSTANCE_STATE_STOPPING, INSTANCE_STATE_STOPPED,
                INSTANCE_STATE_SHUTTING_DOWN));
        Filter runningInstanceFilter = new Filter();
        runningInstanceFilter.setName(INSTANCE_STATE);
        runningInstanceFilter.setValues(stateValues);
        return runningInstanceFilter;
    }

    /**
     * Waits for termination of given executor service.
     */
    public static void awaitTermination(Logger logger, ExecutorService executor) {
        try {
            if (!executor.awaitTermination(EXECUTOR_SHUTDOWN_INTERVAL_MINUTES, TimeUnit.MINUTES)) {
                logger.log(Level.WARNING,
                        "Executor service can't be shutdown for AWS. Trying to shutdown now...");
                executor.shutdownNow();
            }
            logger.log(Level.FINE, "Executor service shutdown for AWS");
        } catch (InterruptedException e) {
            logger.log(Level.SEVERE, Utils.toString(e));
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            logger.log(Level.SEVERE, Utils.toString(e));
        }
    }

    /*
     * method will create new or validate existing security group has the necessary settings for CM
     * to function. It will return the security group id that is required during instance
     * provisioning.
     */
    public static String allocateSecurityGroup(AWSAllocation aws) {
        String groupId;
        SecurityGroup group;

        // use the security group provided in the description properties
        String sgId = getFromCustomProperties(aws.child.description,
                AWSConstants.AWS_SECURITY_GROUP_ID);
        if (sgId != null) {
            return sgId;
        }

        // if the group doesn't exist an exception is thrown. We won't throw a
        // missing group exception
        // we will continue and create the group
        try {
            group = getSecurityGroup(aws.amazonEC2Client);
            if (group != null) {
                return group.getGroupId();
            }
        } catch (AmazonServiceException t) {
            if (!t.getMessage().contains(
                    DEFAULT_SECURITY_GROUP_NAME)) {
                throw t;
            }
        }

        // get the subnet cidr from the subnet provided in description properties (if any)
        String subnet = getSubnetFromDescription(aws);

        // if no subnet provided then get the default one for the default vpc
        if (subnet == null) {
            subnet = getDefaultVPCSubnet(aws);
        }

        // no subnet is not an option...
        if (subnet == null) {
            throw new AmazonServiceException("default VPC not found");
        }

        try {
            // create the security group for the the vpc
            // provided in the description properties (if any)
            String vpcId = getFromCustomProperties(aws.child.description, AWSConstants.AWS_VPC_ID);

            groupId = createSecurityGroup(aws.amazonEC2Client, vpcId);
            updateIngressRules(aws.amazonEC2Client, groupId,
                    getDefaultRules(subnet));
        } catch (AmazonServiceException t) {
            if (t.getMessage().contains(
                    DEFAULT_SECURITY_GROUP_NAME)) {
                return getSecurityGroup(aws.amazonEC2Client).getGroupId();
            } else {
                throw t;
            }
        }

        return groupId;
    }

    public static String getSubnetFromDescription(AWSAllocation aws) {
        String subnetId = getFromCustomProperties(aws.child.description,
                AWSConstants.AWS_SUBNET_ID);

        if (subnetId != null) {
            AWSNetworkService netSvc = new AWSNetworkService();
            Subnet subnet = netSvc.getSubnet(subnetId, aws.amazonEC2Client);
            return subnet.getCidrBlock();
        }

        return null;
    }

    public static String getFromCustomProperties(
            ComputeDescriptionService.ComputeDescription description,
            String key) {
        if (description == null || description.customProperties == null) {
            return null;
        }

        return description.customProperties.get(key);
    }

    public static String createSecurityGroup(AmazonEC2AsyncClient client, String vpcId) {
        return createSecurityGroup(client, DEFAULT_SECURITY_GROUP_NAME,
                DEFAULT_SECURITY_GROUP_DESC, vpcId);
    }

    public static String createSecurityGroup(AmazonEC2AsyncClient client, String name,
            String description, String vpcId) {

        CreateSecurityGroupRequest req = new CreateSecurityGroupRequest()
                .withDescription(description)
                .withGroupName(name);

        // set vpc for the security group if provided
        if (vpcId != null) {
            req = req.withVpcId(vpcId);
        }

        CreateSecurityGroupResult result = client.createSecurityGroup(req);

        return result.getGroupId();
    }

    public static SecurityGroup getSecurityGroup(AmazonEC2AsyncClient client) {
        return getSecurityGroup(client, DEFAULT_SECURITY_GROUP_NAME);
    }

    public static SecurityGroup getSecurityGroup(AmazonEC2AsyncClient client,
            String name) {
        SecurityGroup cellGroup = null;

        DescribeSecurityGroupsRequest req = new DescribeSecurityGroupsRequest()
                .withFilters(new Filter("group-name", Arrays.asList(name)));
        DescribeSecurityGroupsResult cellGroups = client
                .describeSecurityGroups(req);
        if (cellGroups != null && !cellGroups.getSecurityGroups().isEmpty()) {
            cellGroup = cellGroups.getSecurityGroups().get(0);
        }
        return cellGroup;
    }

    public static List<IpPermission> getDefaultRules(String subnet) {
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

    public static void updateIngressRules(AmazonEC2AsyncClient client,
            List<Allow> rules, String groupId) {
        updateIngressRules(client, groupId, buildRules(rules));
    }

    public static void updateIngressRules(AmazonEC2AsyncClient client, String groupId,
            List<IpPermission> rules) {
        AuthorizeSecurityGroupIngressRequest req = new AuthorizeSecurityGroupIngressRequest()
                .withGroupId(groupId).withIpPermissions(rules);
        client.authorizeSecurityGroupIngress(req);
    }

    public static IpPermission createRule(int port) {
        return createRule(port, port, DEFAULT_ALLOWED_NETWORK, DEFAULT_PROTOCOL);
    }

    public static IpPermission createRule(int fromPort, int toPort, String subnet,
            String protocol) {

        return new IpPermission().withIpProtocol(protocol)
                .withFromPort(fromPort).withToPort(toPort).withIpRanges(subnet);
    }

    /**
     * Builds the white list rules for the firewall
     */
    public static List<IpPermission> buildRules(List<Allow> allowRules) {
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
                awsRules.add(createRule(fromPort, toPort, rule.ipRange,
                        rule.protocol));
            }
        }
        return awsRules;
    }

    /**
     * Gets the subnet associated with the default VPC.
     */
    public static String getDefaultVPCSubnet(AWSAllocation aws) {
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

}
