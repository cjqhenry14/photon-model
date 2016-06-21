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

import static com.vmware.photon.controller.model.adapters.awsadapter.AWSConstants.AWS_SUBNET_ID;
import static com.vmware.photon.controller.model.adapters.awsadapter.AWSConstants.AWS_VPC_ID;
import static com.vmware.photon.controller.model.adapters.awsadapter.AWSConstants.PRIVATE_INTERFACE;
import static com.vmware.photon.controller.model.adapters.awsadapter.AWSConstants.PUBLIC_INTERFACE;
import static com.vmware.photon.controller.model.adapters.awsadapter.AWSUtils.TILDA;
import static com.vmware.photon.controller.model.adapters.awsadapter.util.AWSEnumerationUtils.getIdFromDocumentLink;
import static com.vmware.photon.controller.model.adapters.util.AdapterUtils.createPatchOperation;
import static com.vmware.photon.controller.model.adapters.util.AdapterUtils.createPostOperation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.amazonaws.services.ec2.model.Instance;

import com.vmware.photon.controller.model.adapters.awsadapter.AWSUriPaths;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.resources.NetworkInterfaceService;
import com.vmware.photon.controller.model.resources.NetworkInterfaceService.NetworkInterfaceState;
import com.vmware.photon.controller.model.resources.NetworkService;
import com.vmware.photon.controller.model.resources.NetworkService.NetworkState;

import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.services.common.QueryTask;
import com.vmware.xenon.services.common.QueryTask.Query;
import com.vmware.xenon.services.common.QueryTask.Query.Occurance;
import com.vmware.xenon.services.common.QueryTask.QuerySpecification.QueryOption;

/**
 * Utility class to hold methods used across different enumeration classes for creating network states and NICs etc.
 */
public class AWSNetworkUtils {

    public static NetworkState mapVPCToNetworkState(Instance instance, String regionId,
            String resourcePoolLink, String authCredentialsLink, List<String> tenantLinks) {
        if (instance == null) {
            throw new IllegalArgumentException("Cannot map VPC to network state for null instance");
        }
        NetworkState networkState = new NetworkState();
        networkState.id = instance.getVpcId();
        networkState.name = instance.getVpcId();
        networkState.regionID = regionId;
        networkState.resourcePoolLink = resourcePoolLink;
        networkState.authCredentialsLink = authCredentialsLink;
        networkState.instanceAdapterReference = UriUtils
                .buildUri(AWSUriPaths.AWS_INSTANCE_ADAPTER);
        networkState.tenantLinks = tenantLinks;
        networkState.customProperties = new HashMap<String, String>();
        networkState.customProperties.put(AWS_VPC_ID,
                instance.getVpcId());
        networkState.customProperties.put(AWS_SUBNET_ID,
                instance.getSubnetId());
        return networkState;
    }

    /**
     * Maps the IP address on the EC2 instance to the corresponding Network Interface Cards in the local system.
     * @param instance The EC2 instance for which the NICs are to be created.
     * @param publicInterfaceFlag The flag that indicates if this a public or private IP address
     * @param tenantLinks The tenants that can access this entity once persisted in the system.
     * @param existingLink The link to the NIC that is already associated with the compute state.
     * @return
     */
    public static NetworkInterfaceState mapIPAddressToNetworkInterfaceState(Instance instance,
            boolean publicInterfaceFlag, List<String> tenantLinks, String existingLink) {
        NetworkInterfaceState networkInterface = new NetworkInterfaceState();
        networkInterface.tenantLinks = tenantLinks;
        // Map public interface
        if (publicInterfaceFlag) {
            networkInterface.address = instance.getPublicIpAddress();
            networkInterface.id = instance.getInstanceId() + TILDA + PUBLIC_INTERFACE;
            // Setting the public/private keyword in the documentSelfLink to aid in the UPDATE
            // scenario.
            if (existingLink == null) {
                networkInterface.documentSelfLink = UUID.randomUUID().toString() + TILDA
                        + PUBLIC_INTERFACE;
            } else {
                networkInterface.documentSelfLink = getIdFromDocumentLink(existingLink);
            }
        } else {
            // Map private interface
            networkInterface.address = instance.getPrivateIpAddress();
            networkInterface.id = instance.getInstanceId() + TILDA + PRIVATE_INTERFACE;
            if (existingLink == null) {
                networkInterface.documentSelfLink = UUID.randomUUID().toString() + TILDA
                        + PRIVATE_INTERFACE;
            } else {
                networkInterface.documentSelfLink = getIdFromDocumentLink(existingLink);
            }
        }
        return networkInterface;
    }

    /**
     *Compares the IP addresses of the instance on AWS and maps those to the network interfaces in the
     *system.
     *1) If an existing mapping is found for a private or public interface then it is updated.
     *2) Else a new mapping is creating.
     *3) The string "public-interface/private-interfaces" is embedded in the document self link
     *along with the UUID to avoid collisions while save some extra lookups during updates.
     */
    public static Operation createOperationToUpdateOrCreateNetworkInterface(
            ComputeState existingComputeState, NetworkInterfaceState networkInterface,
            List<String> tenantLinks, StatelessService service, boolean isPublic) {
        String existingInterfaceLink = getExistingNetworkInterfaceLink(existingComputeState,
                isPublic);
        Operation networkInterfaceOperation = null;
        // If existing NIC is null create one.
        if (existingInterfaceLink == null) {
            networkInterfaceOperation = createPostOperation(service, networkInterface,
                    NetworkInterfaceService.FACTORY_LINK);
        } else {
            networkInterfaceOperation = createPatchOperation(
                    service, networkInterface, existingInterfaceLink);
        }
        return networkInterfaceOperation;
    }

    /**
     * Returns the link to the existing network interface based on the public/private identifier embedded
     * in the documentSelfLink
     */
    public static String getExistingNetworkInterfaceLink(ComputeState existingComputeState,
            boolean isPublic) {
        String existingInterfaceLink = null;
        // Determine the URI representing the existing public/private interfaces.
        for (String networkLink : existingComputeState.networkLinks) {
            if (isPublic && networkLink.contains(PUBLIC_INTERFACE)) {
                existingInterfaceLink = networkLink;
                break;
            } else if (networkLink.contains(PRIVATE_INTERFACE)) {
                existingInterfaceLink = networkLink;
                break;
            }
        }
        return existingInterfaceLink;
    }

    /**
     * Maps the ip addresses of the instances on AWS to create operations for the network interface cards.
     */
    public static List<Operation> mapInstanceIPAddressToNICCreationOperations(
            Instance instance, ComputeState resultDesc, List<String> tenantLinks,
            StatelessService service) {
        List<Operation> createOperations = new ArrayList<Operation>();
        // NIC - Private
        NetworkInterfaceState privateNICState = mapIPAddressToNetworkInterfaceState(
                instance, false, tenantLinks, null);
        Operation postPrivateNetworkInterface = createPostOperation(
                service, privateNICState, NetworkInterfaceService.FACTORY_LINK);
        createOperations.add(postPrivateNetworkInterface);
        // Compute State Network Links
        resultDesc.networkLinks = new ArrayList<String>();
        resultDesc.networkLinks.add(UriUtils.buildUriPath(
                NetworkInterfaceService.FACTORY_LINK,
                privateNICState.documentSelfLink));

        // NIC - Public
        if (instance.getPublicIpAddress() != null) {
            NetworkInterfaceState publicNICState = mapIPAddressToNetworkInterfaceState(
                    instance, true, tenantLinks, null);
            Operation postPublicNetworkInterface = createPostOperation(
                    service, publicNICState, NetworkInterfaceService.FACTORY_LINK);
            createOperations.add(postPublicNetworkInterface);
            resultDesc.networkLinks.add(UriUtils.buildUriPath(
                    NetworkInterfaceService.FACTORY_LINK,
                    publicNICState.documentSelfLink));
        }
        return createOperations;
    }

    /**
     * Creates the query to retrieve existing network states filtered by the discovered VPCs.
     */
    public static QueryTask createQueryToGetExistingNetworkStatesFilteredByDiscoveredVPCs(
            Set<String> vpcIds, List<String> tenantLinks) {
        // instance Ids
        QueryTask q = new QueryTask();
        q.setDirect(true);
        q.querySpec = new QueryTask.QuerySpecification();
        q.querySpec.options.add(QueryOption.EXPAND_CONTENT);
        q.querySpec.query = Query.Builder.create()
                .addKindFieldClause(NetworkService.NetworkState.class)
                .build();

        QueryTask.Query networkStateIdFilterParentQuery = new QueryTask.Query();
        networkStateIdFilterParentQuery.occurance = Occurance.MUST_OCCUR;
        for (String vpcId : vpcIds) {
            QueryTask.Query networkStateIdFilter = new QueryTask.Query()
                    .setTermPropertyName(ComputeState.FIELD_NAME_ID)
                    .setTermMatchValue(vpcId);
            networkStateIdFilter.occurance = QueryTask.Query.Occurance.SHOULD_OCCUR;
            networkStateIdFilterParentQuery.addBooleanClause(networkStateIdFilter);
        }
        q.querySpec.query.addBooleanClause(networkStateIdFilterParentQuery);
        q.documentSelfLink = UUID.randomUUID().toString();
        q.tenantLinks = tenantLinks;
        return q;
    }
}
