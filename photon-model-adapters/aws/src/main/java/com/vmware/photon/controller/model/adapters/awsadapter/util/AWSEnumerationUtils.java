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

package com.vmware.photon.controller.model.adapters.awsadapter.util;

import static com.vmware.photon.controller.model.adapters.awsadapter.AWSConstants.AWS_TAGS;
import static com.vmware.photon.controller.model.adapters.awsadapter.AWSConstants.AWS_VPC_ID;
import static com.vmware.photon.controller.model.adapters.awsadapter.AWSUtils.TILDA;
import static com.vmware.photon.controller.model.adapters.awsadapter.AWSUtils.getRegionId;
import static com.vmware.photon.controller.model.constants.PhotonModelConstants.SOURCE_TASK_LINK;
import static com.vmware.xenon.common.UriUtils.URI_PATH_CHAR;

import java.net.URI;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.amazonaws.services.ec2.model.Instance;

import com.vmware.photon.controller.model.adapters.awsadapter.AWSInstanceService;
import com.vmware.photon.controller.model.adapters.awsadapter.AWSUtils;
import com.vmware.photon.controller.model.adapters.awsadapter.enumeration.AWSComputeStateCreationAdapterService.AWSTags;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription;
import com.vmware.photon.controller.model.resources.ComputeService;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.tasks.ResourceEnumerationTaskService;

import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.QueryTask;
import com.vmware.xenon.services.common.QueryTask.Query;
import com.vmware.xenon.services.common.QueryTask.Query.Occurance;
import com.vmware.xenon.services.common.QueryTask.QuerySpecification.QueryOption;

/**
 * Utility class to hold methods used across different enumeration classes.
 */
public class AWSEnumerationUtils {

    /**
     * Gets the key to uniquely represent a compute description that needs to be created in the system.
     * Currently uses regionId and instanceType and networkId and is represented as below:
     * us-east-1~t2.micro~vpc-3cbd
     *
     * TODO harden key as more logic is realized in the enumeration
     * service.
     */
    public static String getKeyForComputeDescriptionFromInstance(Instance i) {
        // Representing the compute-description as a key regionId~instanceType~networkId
        return getRegionId(i).concat(TILDA)
                .concat(i.getInstanceType().concat(TILDA).concat(i.getVpcId()));
    }

    /**
     * Returns the key that includes the attributes that define this compute description. This key is
     * used to link the compute states to the correct compute descriptions.
     */
    public static String getKeyForComputeDescriptionFromCD(ComputeDescription computeDescription) {
        // Representing the compute-description as a key regionId~instanceType~networkId
        return computeDescription.regionId.concat(TILDA).concat(computeDescription.instanceType)
                .concat(TILDA).concat(computeDescription.networkId);
    }

    /**
     * Returns the instanceType from the compute description key which is created to look like
     * regionId~instanceType~networkId
     */
    public static String getInstanceTypeFromComputeDescriptionKey(String computeDescriptionKey) {
        return computeDescriptionKey.substring(computeDescriptionKey.indexOf(TILDA) + 1,
                computeDescriptionKey.lastIndexOf(TILDA));
    }

    /**
     * Returns the regionId from the compute description key that looks like  regionId~instanceType~networkId
     */
    public static String getRegionIdFromComputeDescriptionKey(String computeDescriptionKey) {
        return computeDescriptionKey.substring(0, computeDescriptionKey.indexOf(TILDA));
    }

    /**
     * Return the networkId from the compute description key that looks like regionId~instanceType~networkId
     */
    public static String getNetworkIdFromComputeDescriptionKey(String computeDescriptionKey) {
        return computeDescriptionKey.substring(computeDescriptionKey.lastIndexOf(TILDA) + 1,
                computeDescriptionKey.length());
    }

    /**
     * From the list of instances that are received from AWS arrive at the minimal set of compute descriptions that need
     * to be created locally to represent them.The compute descriptions are represented as regionId~instanceType~networkId
     * and put into a hashset. As a result, a representative set is created to represent all the discovered VMs.
     * @param context
     * @param next
     */
    public static HashSet<String> getRepresentativeListOfCDsFromInstanceList(
            Collection<Instance> instanceList) {
        HashSet<String> representativeCDSet = new HashSet<String>();
        for (Instance instance : instanceList) {
            representativeCDSet.add(getKeyForComputeDescriptionFromInstance(instance));
        }
        return representativeCDSet;
    }

    /**
     * Get all the compute descriptions already in the system that correspond to virtual machine and filter by This query is primarily used during instance discovery to find compute descriptions that exist in the system
     * to match the instances received from AWS.
     * The query filters out compute descriptions that represent compute hosts and also checks for other conditions as below:
     * - Environment name(AWS),
     * - id (instance type),
     * - ZoneId(placement).
     * - Created from the enumeration task.
     * Compute hosts are modeled to support VM guests.So excluding them from the query to get
     * compute descriptions for VMs.
     * @param instance The instance returned from the AWS endpoint.
     */
    public static QueryTask getCDsRepresentingVMsInLocalSystemCreatedByEnumerationQuery(
            Set<String> representativeComputeDescriptionSet, List<String> tenantLinks,
            StatelessService service, URI parentTaskLink, String regionId) {
        String sourceTaskName = QueryTask.QuerySpecification
                .buildCompositeFieldName(ComputeService.ComputeState.FIELD_NAME_CUSTOM_PROPERTIES,
                        SOURCE_TASK_LINK);
        QueryTask.Query customPropClause = new QueryTask.Query()
                .setTermPropertyName(sourceTaskName).setTermMatchValue(
                        ResourceEnumerationTaskService.FACTORY_LINK);
        customPropClause.occurance = QueryTask.Query.Occurance.MUST_OCCUR;

        QueryTask q = new QueryTask();
        q.setDirect(true);
        q.querySpec = new QueryTask.QuerySpecification();
        q.querySpec.options.add(QueryOption.EXPAND_CONTENT);
        q.querySpec.query = Query.Builder.create()
                .addKindFieldClause(ComputeDescription.class)
                .addFieldClause(ComputeDescription.FIELD_NAME_ENVIRONMENT_NAME,
                        AWSInstanceService.AWS_ENVIRONMENT_NAME)
                .addFieldClause(ComputeDescription.FIELD_NAME_ZONE_ID, regionId)
                .build().addBooleanClause(customPropClause);

        // Instance type should fall in one of the passed in values
        QueryTask.Query instanceTypeFilterParentQuery = new QueryTask.Query();
        instanceTypeFilterParentQuery.occurance = Occurance.MUST_OCCUR;
        for (String key : representativeComputeDescriptionSet) {
            QueryTask.Query instanceTypeFilter = new QueryTask.Query()
                    .setTermPropertyName(ComputeDescription.FIELD_NAME_ID)
                    .setTermMatchValue(getInstanceTypeFromComputeDescriptionKey(key));
            instanceTypeFilter.occurance = QueryTask.Query.Occurance.SHOULD_OCCUR;
            instanceTypeFilterParentQuery.addBooleanClause(instanceTypeFilter);
        }
        q.querySpec.query.addBooleanClause(instanceTypeFilterParentQuery);

        q.documentSelfLink = UUID.randomUUID().toString();
        q.tenantLinks = tenantLinks;
        return q;
    }

    /**
     * Maps the instance discovered on AWS to a local compute state that will be persisted.
     */
    public static ComputeState mapInstanceToComputeState(Instance instance,
            String parentComputeLink, String resourcePoolLink, String computeDescriptionLink,
            List<String> tenantLinks) {
        ComputeService.ComputeState computeState = new ComputeService.ComputeState();
        computeState.id = instance.getInstanceId();
        computeState.parentLink = parentComputeLink;

        computeState.resourcePoolLink = resourcePoolLink;
        // Compute descriptions are looked up by the instanceType in the local list of CDs.
        computeState.descriptionLink = UriUtils
                .buildUriPath(computeDescriptionLink);

        // TODO VSYM-375 for adding disk information

        computeState.address = instance.getPublicIpAddress();
        computeState.powerState = AWSUtils.mapToPowerState(instance.getState());
        computeState.customProperties = new HashMap<String, String>();
        if (!instance.getTags().isEmpty()) {
            computeState.customProperties.put(AWS_TAGS,
                    Utils.toJson(new AWSTags(instance.getTags())));
        }
        computeState.customProperties.put(SOURCE_TASK_LINK,
                ResourceEnumerationTaskService.FACTORY_LINK);
        computeState.tenantLinks = tenantLinks;

        // Network State. Create one network state mapping to each VPC that is discovered during
        // enumeration.
        computeState.customProperties.put(AWS_VPC_ID,
                instance.getVpcId());
        return computeState;
    }

    /**
     * Extracts the id from the document link. This is the unique identifier of the document returned as part of the result.
     * @param documentLink
     * @return
     */
    public static String getIdFromDocumentLink(String documentLink) {
        return documentLink.substring(documentLink.lastIndexOf(URI_PATH_CHAR) + 1);
    }
}
