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
import java.util.List;
import java.util.function.Consumer;

import com.vmware.photon.controller.model.adapterapi.ComputeEnumerateResourceRequest;
import com.vmware.photon.controller.model.adapterapi.EnumerationAction;
import com.vmware.photon.controller.model.adapters.awsadapter.AWSUriPaths;
import com.vmware.photon.controller.model.adapters.util.AdapterUtils;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription;

import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.OperationJoin;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.AuthCredentialsService;
import com.vmware.xenon.services.common.AuthCredentialsService.AuthCredentialsServiceState;

/**
 * Enumeration Adapter for the Amazon Web Services. Performs a list call to the AWS API
 * and reconciles the local state with the state on the remote system. It lists the instances on the remote system.
 * Compares those with the local system and creates the instances that are missing in the local system.
 *
 */
public class AWSEnumerationAdapterService extends StatelessService {

    public static final String SELF_LINK = AWSUriPaths.AWS_ENUMERATION_ADAPTER;

    public AWSEnumerationAdapterService() {
        super.toggleOption(ServiceOption.INSTRUMENTATION, true);
    }

    public static enum AWSEnumerationStages {
        HOSTDESC, PARENTAUTH, KICKOFF_ENUMERATION, PATCH_COMPLETION, ERROR
    }

    /**
     * Holds the compute resource request and other data that is required by the helper flows to perform resource enumeration on AWS.
     *
     */
    public static class AWSEnumerationRequest {
        public ComputeEnumerateResourceRequest computeEnumerateResourceRequest;
        public AuthCredentialsService.AuthCredentialsServiceState parentAuth;
        public ComputeDescription computeHostDescription;

        public AWSEnumerationRequest(ComputeEnumerateResourceRequest request,
                AuthCredentialsService.AuthCredentialsServiceState parentAuth,
                ComputeDescription computeHostDescription) {
            this.computeEnumerateResourceRequest = request;
            this.parentAuth = parentAuth;
            this.computeHostDescription = computeHostDescription;

        }
    }
    /**
     * The enumeration service context needed to spawn off control to the creation and deletion adapters for AWS.
     */
    public static class EnumerationContext {

        public ComputeEnumerateResourceRequest computeEnumerationRequest;
        public AuthCredentialsService.AuthCredentialsServiceState parentAuth;
        public ComputeDescription computeHostDescription;
        public AWSEnumerationStages stage;
        public List<Operation> enumerationOperations;
        public Throwable error;
        public Operation awsAdapterOperation;

        public EnumerationContext(ComputeEnumerateResourceRequest request, Operation op) {
            this.computeEnumerationRequest = request;
            this.stage = AWSEnumerationStages.HOSTDESC;
            this.enumerationOperations = new ArrayList<Operation>();
            this.awsAdapterOperation = op;
        }
    }

    @Override
    public void handleStart(Operation startPost) {
        startHelperServices(startPost);
        super.handleStart(startPost);
    }

    @Override
    public void handlePatch(Operation op) {
        setOperationHandlerInvokeTimeStat(op);
        if (!op.hasBody()) {
            op.fail(new IllegalArgumentException("body is required"));
            return;
        }
        op.complete();
        EnumerationContext awsEnumerationContext = new EnumerationContext(
                op.getBody(ComputeEnumerateResourceRequest.class), op);
        validateState(awsEnumerationContext);
        if (awsEnumerationContext.computeEnumerationRequest.isMockRequest) {
            // patch status to parent task
            AdapterUtils.sendPatchToEnumerationTask(this,
                    awsEnumerationContext.computeEnumerationRequest.taskReference);
            return;
        }
        handleEnumerationRequest(awsEnumerationContext);
    }

    /**
     * Starts the related services for the Enumeration Service
     */
    public void startHelperServices(Operation startPost) {
        Operation patchAWSEnumerationCreationService = Operation
                .createPatch(UriUtils.buildUri(this.getHost(),
                        AWSEnumerationAndCreationAdapterService.SELF_LINK))
                .setReferer(this.getUri());

        Operation patchAWSEnumerationDeletionService = Operation.createPatch(
                UriUtils.buildUri(this.getHost(),
                        AWSEnumerationAndDeletionAdapterService.SELF_LINK))
                .setReferer(this.getUri());

        this.getHost().startService(patchAWSEnumerationCreationService,
                new AWSEnumerationAndCreationAdapterService());
        this.getHost().startService(patchAWSEnumerationDeletionService,
                new AWSEnumerationAndDeletionAdapterService());

        getHost().registerForServiceAvailability((o, e) -> {
            if (e != null) {
                String message = "Failed to start up all the services related to the AWS Enumeration Adapter Service";
                this.logInfo(message);
                throw new IllegalStateException(message);
            }
            this.logInfo(
                    "Successfully started up all the services related to the AWS Enumeration Adapter Service");
        }, AWSEnumerationAndCreationAdapterService.SELF_LINK,
                AWSEnumerationAndDeletionAdapterService.SELF_LINK);
    }

    /**
     * Creates operations for the creation and deletion adapter services and spawns them off in parallel
     *
     */
    public void handleEnumerationRequest(EnumerationContext aws) {
        switch (aws.stage) {
        case HOSTDESC:
            getHostComputeDescription(aws, AWSEnumerationStages.PARENTAUTH);
            break;
        case PARENTAUTH:
            getParentAuth(aws, AWSEnumerationStages.KICKOFF_ENUMERATION);
            break;
        case KICKOFF_ENUMERATION:
            kickOffEnumerationWorkFlows(aws, AWSEnumerationStages.PATCH_COMPLETION);
            break;
        case PATCH_COMPLETION:
            setOperationDurationStat(aws.awsAdapterOperation);
            AdapterUtils.sendPatchToEnumerationTask(this,
                    aws.computeEnumerationRequest.taskReference);
            break;
        case ERROR:
            AdapterUtils.sendFailurePatchToEnumerationTask(this,
                    aws.computeEnumerationRequest.taskReference, aws.error);
            break;
        default:
            logSevere("Unknown AWS enumeration stage %s ", aws.stage.toString());
            aws.error = new Exception("Unknown AWS enumeration stage");
            AdapterUtils.sendFailurePatchToEnumerationTask(this,
                    aws.computeEnumerationRequest.taskReference, aws.error);
            break;

        }
    }
    /**
     * Method to validate that the passed in Enumeration Request State is valid.
     * Validating that the parent compute link and the adapter links are populated
     * in the request.
     *
     * Also defaulting the EnumerationRequestType to REFRESH
     * @param AWSstate The enumeration context.
     */
    public void validateState(EnumerationContext AWSstate) {
        if (AWSstate.computeEnumerationRequest.computeDescriptionLink == null) {
            throw new IllegalArgumentException("computeDescriptionLink is required.");
        }
        if (AWSstate.computeEnumerationRequest.adapterManagementReference == null) {
            throw new IllegalArgumentException(
                    "adapterManagementReference is required.");
        }
        if (AWSstate.computeEnumerationRequest.parentComputeLink == null) {
            throw new IllegalArgumentException(
                    "parentComputeLink is required.");
        }
        if (AWSstate.computeEnumerationRequest.enumerationAction == null) {
            AWSstate.computeEnumerationRequest.enumerationAction = EnumerationAction.START;
        }
    }

    /**
     * Kicks off the enumeration flows for creation and deletion.
     */
    public void kickOffEnumerationWorkFlows(EnumerationContext context,
            AWSEnumerationStages next) {
        AWSEnumerationRequest awsEnumerationRequest = new AWSEnumerationRequest(
                context.computeEnumerationRequest, context.parentAuth,
                context.computeHostDescription);

        Operation patchAWSCreationAdapterService = Operation
                .createPatch(this, AWSEnumerationAndCreationAdapterService.SELF_LINK)
                .setBody(awsEnumerationRequest)
                .setReferer(this.getHost().getUri());

        Operation patchAWSDeletionAdapterService = Operation
                .createPatch(this,
                        AWSEnumerationAndDeletionAdapterService.SELF_LINK)
                .setBody(awsEnumerationRequest)
                .setReferer(getHost().getUri());

        context.enumerationOperations.add(patchAWSCreationAdapterService);
        context.enumerationOperations.add(patchAWSDeletionAdapterService);

        if (context.enumerationOperations == null || context.enumerationOperations.size() == 0) {
            logInfo("There are no enumeration tasks to run.");
            context.stage = next;
            handleEnumerationRequest(context);
            return;
        }
        OperationJoin.JoinedCompletionHandler joinCompletion = (ox,
                exc) -> {
            if (exc != null) {
                logSevere(
                        "Error kicking off the enumeration workflows for AWS. %s",
                        Utils.toString(exc));
                AdapterUtils.sendFailurePatchToEnumerationTask(this,
                        context.computeEnumerationRequest.taskReference,
                        exc.values().iterator().next());

            }
            logInfo("Successfully completed the enumeration workflows for creation and deletion.");
            context.stage = next;
            handleEnumerationRequest(context);
            return;
        };
        OperationJoin joinOp = OperationJoin.create(context.enumerationOperations);
        joinOp.setCompletion(joinCompletion);
        joinOp.sendWith(getHost());
        logInfo("Kicked off enumeration creation and deletion workflows for AWS");

    }

    /**
     * Method to retrieve the parent compute host on which the enumeration task will be performed.
     * @param aws
     */
    private void getHostComputeDescription(EnumerationContext aws,
            AWSEnumerationStages next) {
        Consumer<Operation> onSuccess = (op) -> {
            aws.computeHostDescription = op.getBody(ComputeDescription.class);
            aws.stage = next;
            handleEnumerationRequest(aws);
        };
        URI parentURI = UriUtils.buildExpandLinksQueryUri(
                UriUtils.buildUri(this.getHost(),
                        aws.computeEnumerationRequest.computeDescriptionLink));
        AdapterUtils.getServiceState(this, parentURI, onSuccess, getFailureConsumer(aws));
    }

    /**
     * Private method to arrive at the credentials needed to call the AWS API for enumerating the instances.
     * @param aws
     */
    private void getParentAuth(EnumerationContext aws, AWSEnumerationStages next) {
        Consumer<Operation> onSuccess = (op) -> {
            aws.parentAuth = op.getBody(AuthCredentialsServiceState.class);
            aws.stage = next;
            handleEnumerationRequest(aws);
        };
        AdapterUtils.getServiceState(this, aws.computeHostDescription.authCredentialsLink,
                onSuccess, getFailureConsumer(aws));
    }

    /**
     * Method to get the failed consumer to handle the error that was raised.
     * @param aws The enumeration context
     * @return
     */
    private Consumer<Throwable> getFailureConsumer(EnumerationContext aws) {
        return (t) -> {
            aws.error = t;
            aws.stage = AWSEnumerationStages.ERROR;
            handleEnumerationRequest(aws);
        };
    }
}
