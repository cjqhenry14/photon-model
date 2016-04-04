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

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.regions.Region;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.cloudwatch.AmazonCloudWatchAsyncClient;
import com.amazonaws.services.ec2.AmazonEC2AsyncClient;
import com.amazonaws.services.ec2.model.CreateTagsRequest;
import com.amazonaws.services.ec2.model.DescribeTagsRequest;
import com.amazonaws.services.ec2.model.DescribeTagsResult;
import com.amazonaws.services.ec2.model.Filter;
import com.amazonaws.services.ec2.model.Tag;
import com.amazonaws.services.ec2.model.TagDescription;

import com.vmware.xenon.services.common.AuthCredentialsService.AuthCredentialsServiceState;

/**
 * AWS utils.
 */
public class AWSUtils {
    public static final String AWS_TAG_NAME = "Name";
    public static final String AWS_FILTER_RESOURCE_ID = "resource-id";
    public static final String AWS_FILTER_VPC_ID = "vpc-id";
    public static final String NO_VALUE = "no-value";

    public static AmazonEC2AsyncClient getAsyncClient(
            AuthCredentialsServiceState credentials, String region,
            boolean isMockRequest) {
        AmazonEC2AsyncClient client = new AmazonEC2AsyncClient(
                new BasicAWSCredentials(credentials.privateKeyId,
                        credentials.privateKey));

        client.setRegion(Region.getRegion(Regions.fromName(region)));

        // make a call to validate credentials
        if (!isMockRequest) {
            client.describeAvailabilityZones();
        }
        return client;
    }

    public static AmazonCloudWatchAsyncClient getStatsAsyncClient(
            AuthCredentialsServiceState credentials, String region) {
        AmazonCloudWatchAsyncClient client = new AmazonCloudWatchAsyncClient(
                new BasicAWSCredentials(credentials.privateKeyId,
                        credentials.privateKey));

        client.setRegion(Region.getRegion(Regions.fromName(region)));
        return client;
    }

    /*
     * Synchronous call to AWS to create tags for the specified resources The
     * list of tags will be applied to all provided resources
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
     * An AWS Resource name is actually a Tag that must be applied to a
     * resource. This helper method will take an AWS resourceID and Name and
     * apply that as a tag
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
}
