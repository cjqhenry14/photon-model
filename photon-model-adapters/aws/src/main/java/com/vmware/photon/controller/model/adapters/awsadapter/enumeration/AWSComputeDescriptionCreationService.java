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

import java.net.URI;
import java.util.ArrayList;
import java.util.UUID;

import com.vmware.photon.controller.model.adapters.awsadapter.AWSInstanceService;
import com.vmware.photon.controller.model.adapters.awsadapter.AWSUriPaths;
import com.vmware.photon.controller.model.adapters.util.AdapterUtils;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription.ComputeType;

import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.services.common.QueryTask;
import com.vmware.xenon.services.common.QueryTask.Query;
import com.vmware.xenon.services.common.ServiceUriPaths;

/**
 * Task service for the creation of compute descriptions.It accepts the instance type and the zone Id for the compute
 * description to be created.It first checks if there is an existing compute description with that information. If there
 * isn't already one then it creates a new compute description with that information.
 */
public class AWSComputeDescriptionCreationService extends StatelessService {

    public static final String SELF_LINK = com.vmware.photon.controller.model.adapters.awsadapter.AWSUriPaths.AWS_COMPUTE_DESCRIPTION_TASK_SERVICE;

    public static enum ComputeDescCreationStage {
        CHECK_EXISTING_CD_QUERY, CHECK_QUERY_RESULT, CREATE_COMPUTEDESC, PATCH_PARENT
    }

    /**
     * Data holder for information related a compute description that has to be created on the local system.
     *
     */
    public static class AWSComputeDescriptionState {
        public String zoneId;
        public String instanceType;
        public ComputeDescCreationStage creationStage;
        public boolean computeDescriptionExists;
        public boolean isMock;
        public String authCredentiaslLink;
        public TaskState taskState;
        /**
         * Link that initiated this task.
         */
        public URI parentTaskLink;
        public URI enumerationTaskLink;

    }

    @Override
    public void handlePatch(Operation op) {
        if (!op.hasBody()) {
            op.fail(new IllegalArgumentException("body is required"));
            return;
        }
        op.complete();

        AWSComputeDescriptionState cdState = op.getBody(AWSComputeDescriptionState.class);
        if (cdState.isMock) {
            // patch status to parent task
            AdapterUtils.sendPatchToTask(this, cdState.parentTaskLink);
            return;
        }
        handleComputeDescriptionCreation(cdState);
    }

    /**
     * Method to handle the steps required to create a new compute description entry in the system based on
     * the enumeration data received from the AWS endpoint. Do the following :
     * 1) Based on the instance received, create a query to check if a compute description exists corresponding to the instance.
     * 2) Fetch the results of the compute description query and determine if a an entry already exists for the given instance.
     * 3) If a compute description does not exist then create a new one.
     * @param vm
     */
    private void handleComputeDescriptionCreation(AWSComputeDescriptionState cd) {
        switch (cd.creationStage) {
        case CHECK_EXISTING_CD_QUERY:
            checkIfComputeDescriptionExists(cd, ComputeDescCreationStage.CHECK_QUERY_RESULT);
            break;
        case CHECK_QUERY_RESULT:
            if (!cd.computeDescriptionExists) {
                cd.creationStage = ComputeDescCreationStage.CREATE_COMPUTEDESC;
            } else {
                cd.creationStage = ComputeDescCreationStage.PATCH_PARENT;
            }
            handleComputeDescriptionCreation(cd);
            break;
        case CREATE_COMPUTEDESC:
            createComputeDescription(cd, ComputeDescCreationStage.PATCH_PARENT);
            break;
        case PATCH_PARENT:
            AdapterUtils.sendPatchToTask(this, cd.parentTaskLink);
            break;
        default:
            Throwable t = new Exception(
                    "Unknown AWS enumeration:compute description creation stage");
            AdapterUtils.sendFailurePatchToTask(this, cd.parentTaskLink, t);
        }
    }

    /**
     * Checking if a compute description corresponding to the AWS instance exists in the system. The compute
     * description is looked up based on the combination of the following fields
     * - Supported Children (Docker Container)
     * - Name (instance type),
     * - Environment name(AWS),
     * - ZoneId(placement).
     * @param instance The instance returned from the AWS endpoint.
     */
    private void checkIfComputeDescriptionExists(AWSComputeDescriptionState cd,
            ComputeDescCreationStage next) {
        // Check if compute description exists in the system for the said instance.
        QueryTask.Query supportedChildrenClause = new QueryTask.Query()
                .setTermPropertyName(
                        QueryTask.QuerySpecification
                                .buildCollectionItemName(
                                        ComputeDescriptionService.ComputeDescription.FIELD_NAME_SUPPORTED_CHILDREN))
                .setTermMatchValue(ComputeType.DOCKER_CONTAINER.toString());

        QueryTask q = new QueryTask();
        q.setDirect(true);
        q.querySpec = new QueryTask.QuerySpecification();
        q.querySpec.query = Query.Builder.create()
                .addKindFieldClause(ComputeDescription.class)
                .addFieldClause(ComputeDescription.FIELD_NAME_ZONE_ID,
                        cd.zoneId)
                .addFieldClause(ComputeDescription.FIELD_NAME_NAME,
                        cd.instanceType)
                .addFieldClause(ComputeDescription.FIELD_NAME_ENVIRONMENT_NAME,
                        AWSInstanceService.AWS_ENVIRONMENT_NAME)
                .build().addBooleanClause(supportedChildrenClause);

        q.documentSelfLink = UUID.randomUUID().toString();
        // create the query to find an existing compute description
        this.sendRequest(Operation
                .createPost(this, ServiceUriPaths.CORE_QUERY_TASKS)
                .setBody(q)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        this.logWarning("Failure retrieving query results: %s",
                                e.toString());
                        AdapterUtils.sendFailurePatchToTask(this,
                                cd.enumerationTaskLink,
                                e);
                        return;
                    }
                    QueryTask responseTask = o.getBody(QueryTask.class);
                    if (responseTask.results.documentCount > 0) {
                        cd.computeDescriptionExists = true;
                    } else {
                        cd.computeDescriptionExists = false;
                    }
                    cd.creationStage = next;
                    logInfo(
                            "Query completed.Result for compute description exists : %s",
                            cd.computeDescriptionExists);
                    handleComputeDescriptionCreation(cd);
                    return;
                }));
    }

    /**
     * Creates a compute description based on the VM instance information received from AWS and populates
     * that in a common place.
     * @param vm
     */
    public void createComputeDescription(AWSComputeDescriptionState cDetails,
            ComputeDescCreationStage next) {
        // Create a compute description for the AWS instance at hand
        ComputeDescriptionService.ComputeDescription cd = new ComputeDescriptionService.ComputeDescription();
        cd.instanceAdapterReference = UriUtils.buildUri(getHost(),
                AWSUriPaths.AWS_INSTANCE_SERVICE);
        cd.enumerationAdapterReference = UriUtils.buildUri(getHost(),
                AWSUriPaths.AWS_ENUMERATION_SERVICE);
        cd.statsAdapterReference = UriUtils.buildUri(getHost(),
                AWSUriPaths.AWS_STATS_SERVICE);
        cd.supportedChildren = new ArrayList<>();
        cd.supportedChildren.add(ComputeType.DOCKER_CONTAINER.toString());
        cd.environmentName = AWSInstanceService.AWS_ENVIRONMENT_NAME;
        cd.id = cDetails.instanceType;
        cd.documentSelfLink = cd.id;
        cd.name = cDetails.instanceType;
        cd.zoneId = cDetails.zoneId;

        // security group is not being returned currently in the VM. Add additional logic VSYM-326.

        sendRequest(Operation
                .createPost(this, ComputeDescriptionService.FACTORY_LINK)
                .setBody(cd)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        AdapterUtils.sendFailurePatchToTask(this, cDetails.enumerationTaskLink, e);
                        return;
                    }
                    ComputeDescription responseCD = o
                            .getBody(ComputeDescription.class);
                    logInfo("Successfully created compute description %s",
                            responseCD.documentSelfLink);
                    cDetails.creationStage = next;
                    handleComputeDescriptionCreation(cDetails);
                }));
    }
}
