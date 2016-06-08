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

import static com.vmware.photon.controller.model.adapters.awsadapter.AWSConstants.HYPHEN;
import static com.vmware.photon.controller.model.adapters.awsadapter.AWSConstants.PRIVATE_INTERFACE;
import static com.vmware.photon.controller.model.adapters.awsadapter.AWSConstants.PUBLIC_INTERFACE;

import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import com.amazonaws.handlers.AsyncHandler;
import com.amazonaws.services.ec2.AmazonEC2AsyncClient;
import com.amazonaws.services.ec2.model.Instance;
import com.amazonaws.services.ec2.model.RunInstancesRequest;
import com.amazonaws.services.ec2.model.RunInstancesResult;
import com.amazonaws.services.ec2.model.TerminateInstancesRequest;
import com.amazonaws.services.ec2.model.TerminateInstancesResult;

import com.vmware.photon.controller.model.adapterapi.ComputeInstanceRequest;
import com.vmware.photon.controller.model.adapterapi.ComputeInstanceRequest.InstanceRequestType;
import com.vmware.photon.controller.model.adapters.awsadapter.util.AWSClientManager;
import com.vmware.photon.controller.model.adapters.util.AdapterUtils;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeStateWithDescription;
import com.vmware.photon.controller.model.resources.DiskService.DiskState;
import com.vmware.photon.controller.model.resources.DiskService.DiskType;
import com.vmware.photon.controller.model.resources.NetworkInterfaceService;
import com.vmware.photon.controller.model.resources.NetworkInterfaceService.NetworkInterfaceState;

import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Operation.CompletionHandler;
import com.vmware.xenon.common.OperationContext;
import com.vmware.xenon.common.OperationJoin;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.AuthCredentialsService.AuthCredentialsServiceState;

/**
 * Adapter to create an EC2 instance on AWS.
 *
 */
public class AWSInstanceService extends StatelessService {

    public static final String SELF_LINK = AWSUriPaths.AWS_INSTANCE_ADAPTER;

    public static final String AWS_ENVIRONMENT_NAME = "AWS_EC2";

    private AWSClientManager clientManager;

    // The security group specifies things such as the ports to be open,
    // firewall rules etc and is
    // specific to an instance and should come from the compute desc for the VM

    private static final String AWS_RUNNING_NAME = "running";
    private static final String AWS_TERMINATED_NAME = "terminated";

    public AWSInstanceService() {
        this.clientManager = new AWSClientManager();
    }

    @Override
    public void handleStop(Operation op) {
        this.clientManager.cleanUp();
        super.handleStop(op);
    }

    @Override
    public void handlePatch(Operation op) {
        if (!op.hasBody()) {
            op.fail(new IllegalArgumentException("body is required"));
            return;
        }
        AWSAllocation aws = new AWSAllocation(
                op.getBody(ComputeInstanceRequest.class));
        switch (aws.computeRequest.requestType) {
        case VALIDATE_CREDENTIALS:
            aws.stage = AWSStages.PARENTAUTH;
            aws.awsOperation = op;
            handleAllocation(aws);
            break;
        default:
            op.complete();
            if (aws.computeRequest.isMockRequest
                    && aws.computeRequest.requestType == ComputeInstanceRequest.InstanceRequestType.CREATE) {
                AdapterUtils.sendPatchToProvisioningTask(this,
                        aws.computeRequest.provisioningTaskReference);
                return;
            }
            handleAllocation(aws);
        }
    }

    /*
     * method will act much like handlePatch, but without persistence as this is a stateless
     * service. Each call to the service will result in a synchronous execution of the stages below
     *
     * Each stage is responsible for setting the next stage on success -- the next stage is passed
     * into action methods
     */
    private void handleAllocation(AWSAllocation aws) {
        switch (aws.stage) {
        case VMDESC:
            getVMDescription(aws, AWSStages.PARENTDESC);
            break;
        case PARENTDESC:
            getParentDescription(aws, AWSStages.PARENTAUTH);
            break;
        case PARENTAUTH:
            getParentAuth(aws, AWSStages.CLIENT);
            break;
        case CLIENT:
            aws.amazonEC2Client = this.clientManager.getOrCreateEC2Client(aws.parentAuth,
                    getRequestRegionId(aws), this,
                    aws.computeRequest.provisioningTaskReference, aws.computeRequest.isMockRequest,
                    false);
            // now that we have a client lets move onto the next step
            switch (aws.computeRequest.requestType) {
            case CREATE:
                aws.stage = AWSStages.VMDISKS;
                handleAllocation(aws);
                break;
            case DELETE:
            case DELETE_DOCUMENTS_ONLY:
                aws.stage = AWSStages.DELETE;
                handleAllocation(aws);
                break;
            case VALIDATE_CREDENTIALS:
                // complete
                aws.awsOperation.complete();
                break;
            default:
                aws.error = new Exception("Unknown AWS provisioning stage");
                aws.stage = AWSStages.ERROR;
                handleAllocation(aws);
            }

            break;
        case DELETE:
            deleteInstance(aws);
            break;
        case VMDISKS:
            getVMDisks(aws, AWSStages.FIREWALL);
            break;
        case FIREWALL:
            aws.securityGroupId = aws.fwService.allocateSecurityGroup(aws);
            aws.subnetId = getSubnetId(aws);
            aws.stage = AWSStages.CREATE;
            handleAllocation(aws);
            break;
        case CREATE:
            createInstance(aws);
            break;
        case ERROR:
            if (aws.computeRequest.provisioningTaskReference != null) {
                AdapterUtils.sendFailurePatchToProvisioningTask(this,
                        aws.computeRequest.provisioningTaskReference, aws.error);
            } else {
                aws.awsOperation.fail(aws.error);
            }
            break;
        case DONE:
            break;
        default:
            logSevere("Unhandled stage: %s", aws.stage.toString());
            break;
        }
    }

    /*
     * method will be responsible for getting the compute description for the requested resource and
     * then passing to the next step
     */
    private void getVMDescription(AWSAllocation aws, AWSStages next) {
        Consumer<Operation> onSuccess = (op) -> {
            aws.child = op.getBody(ComputeStateWithDescription.class);
            aws.stage = next;
            handleAllocation(aws);
        };
        URI computeUri = UriUtils.extendUriWithQuery(
                aws.computeRequest.computeReference, UriUtils.URI_PARAM_ODATA_EXPAND,
                Boolean.TRUE.toString());
        AdapterUtils.getServiceState(this, computeUri, onSuccess, getFailureConsumer(aws));
    }

    /*
     * Method will get the service for the identified link
     */
    private void getParentDescription(AWSAllocation aws, AWSStages next) {
        Consumer<Operation> onSuccess = (op) -> {
            aws.parent = op.getBody(ComputeStateWithDescription.class);
            aws.stage = next;
            handleAllocation(aws);
        };
        URI parentURI = UriUtils
                .buildExpandLinksQueryUri(UriUtils.buildUri(this.getHost(), aws.child.parentLink));
        AdapterUtils.getServiceState(this, parentURI, onSuccess, getFailureConsumer(aws));
    }

    private void getParentAuth(AWSAllocation aws, AWSStages next) {
        String parentAuthLink;
        if (aws.computeRequest.requestType == InstanceRequestType.VALIDATE_CREDENTIALS) {
            parentAuthLink = aws.computeRequest.authCredentialsLink;
        } else {
            parentAuthLink = aws.parent.description.authCredentialsLink;
        }
        Consumer<Operation> onSuccess = (op) -> {
            aws.parentAuth = op.getBody(AuthCredentialsServiceState.class);
            aws.stage = next;
            handleAllocation(aws);
        };
        AdapterUtils.getServiceState(this, parentAuthLink, onSuccess, getFailureConsumer(aws));
    }

    private Consumer<Throwable> getFailureConsumer(AWSAllocation aws) {
        return (t) -> {
            aws.stage = AWSStages.ERROR;
            aws.error = t;
            handleAllocation(aws);
        };
    }

    /*
     * method will retrieve disks for targeted image
     */
    private void getVMDisks(AWSAllocation aws, AWSStages next) {
        if (aws.child.diskLinks == null || aws.child.diskLinks.size() == 0) {
            aws.error = new IllegalStateException(
                    "a minimum of 1 disk is required");
            aws.stage = AWSStages.ERROR;
            handleAllocation(aws);
            return;
        }
        Collection<Operation> operations = new ArrayList<>();
        // iterate thru disks and create operations
        for (String disk : aws.child.diskLinks) {
            operations.add(Operation.createGet(UriUtils.buildUri(
                    this.getHost(), disk)));
        }

        OperationJoin operationJoin = OperationJoin.create(operations)
                .setCompletion(
                        (ops, exc) -> {
                            if (exc != null) {
                                aws.error = new IllegalStateException(
                                        "Error getting disk information");
                                aws.stage = AWSStages.ERROR;
                                handleAllocation(aws);
                                return;
                            }

                            aws.childDisks = new HashMap<>();
                            for (Operation op : ops.values()) {
                                DiskState disk = op.getBody(DiskState.class);
                                aws.childDisks.put(disk.type, disk);
                            }
                            aws.stage = next;
                            handleAllocation(aws);
                        });
        operationJoin.sendWith(this);
    }

    private void createInstance(AWSAllocation aws) {
        if (aws.computeRequest.isMockRequest) {
            AdapterUtils.sendPatchToProvisioningTask(this,
                    aws.computeRequest.provisioningTaskReference);
            return;
        }

        DiskState bootDisk = aws.childDisks.get(DiskType.HDD);
        if (bootDisk == null) {
            AdapterUtils.sendFailurePatchToProvisioningTask(this,
                    aws.computeRequest.provisioningTaskReference,
                    new IllegalStateException("AWS bootDisk not specified"));
            return;
        }

        if (bootDisk.bootConfig != null && bootDisk.bootConfig.files.length > 1) {
            AdapterUtils.sendFailurePatchToProvisioningTask(this,
                    aws.computeRequest.provisioningTaskReference,
                    new IllegalStateException("Only 1 configuration file allowed"));
            return;
        }

        URI imageId = bootDisk.sourceImageReference;
        if (imageId == null) {
            aws.error = new IllegalStateException("AWS ImageId not specified");
            aws.stage = AWSStages.ERROR;
            handleAllocation(aws);
            return;
        }

        // This a single disk state with a bootConfig. There's no expectation
        // that it does exists, but if it does, we only support cloud configs at
        // this point.
        String cloudConfig = null;
        if (bootDisk.bootConfig != null && bootDisk.bootConfig.files.length > 0) {
            cloudConfig = bootDisk.bootConfig.files[0].contents;
        }

        String instanceType = aws.child.description.instanceType;
        if (instanceType == null) { // fallback to legacy usage of name
            instanceType = aws.child.description.name;
        }
        if (instanceType == null) {
            aws.error = new IllegalStateException(
                    "AWS Instance type not specified");
            aws.stage = AWSStages.ERROR;
            handleAllocation(aws);
            return;
        }

        // let's try one more time for the security group -- just in case it
        // didn't
        // get retrieved during the firewall stage
        //
        // This will be removed in EN-1251
        if (aws.securityGroupId == null) {
            aws.securityGroupId = aws.fwService.getSecurityGroup(
                    aws.amazonEC2Client).getGroupId();
            // if we still don't have it kill allocation
            if (aws.securityGroupId == null) {
                aws.error = new IllegalStateException("SecurityGroup not found");
                aws.stage = AWSStages.ERROR;
                handleAllocation(aws);
                return;
            }
        }

        RunInstancesRequest runInstancesRequest = new RunInstancesRequest()
                .withImageId(imageId.toString()).withInstanceType(instanceType)
                .withMinCount(1).withMaxCount(1)
                .withMonitoring(true)
                .withSecurityGroupIds(aws.securityGroupId);

        // use the subnet if provided
        if (aws.subnetId != null) {
            runInstancesRequest = runInstancesRequest.withSubnetId(aws.subnetId);
        }

        if (cloudConfig != null) {
            try {
                runInstancesRequest.setUserData(Base64.getEncoder()
                        .encodeToString(cloudConfig.getBytes(Utils.CHARSET)));
            } catch (UnsupportedEncodingException e) {
                aws.error = new IllegalStateException(
                        "Error encoding user data");
                aws.stage = AWSStages.ERROR;
                handleAllocation(aws);
                return;
            }
        }

        // handler invoked once the EC2 runInstancesAsync commands completes
        AsyncHandler<RunInstancesRequest, RunInstancesResult> creationHandler = buildCreationCallbackHandler(
                this, aws.computeRequest, aws.child, aws.amazonEC2Client);
        aws.amazonEC2Client.runInstancesAsync(runInstancesRequest,
                creationHandler);
    }

    private static class AWSCreationHandler implements
            AsyncHandler<RunInstancesRequest, RunInstancesResult> {

        private StatelessService service;
        private ComputeInstanceRequest computeReq;
        private ComputeStateWithDescription computeDesc;
        private AmazonEC2AsyncClient amazonEC2Client;
        private OperationContext opContext;

        private AWSCreationHandler(StatelessService service,
                ComputeInstanceRequest computeReq,
                ComputeStateWithDescription computeDesc,
                AmazonEC2AsyncClient amazonEC2Client) {
            this.service = service;
            this.computeReq = computeReq;
            this.computeDesc = computeDesc;
            this.amazonEC2Client = amazonEC2Client;
            this.opContext = OperationContext.getOperationContext();
        }

        @Override
        public void onError(Exception exception) {
            OperationContext.restoreOperationContext(this.opContext);
            AdapterUtils.sendFailurePatchToProvisioningTask(this.service,
                    this.computeReq.provisioningTaskReference, exception);
        }

        @Override
        public void onSuccess(RunInstancesRequest request,
                RunInstancesResult result) {
            List<Operation> createOperations = new ArrayList<Operation>();
            // consumer to be invoked once a VM is in the running state
            Consumer<Instance> consumer = instance -> {
                OperationContext.restoreOperationContext(opContext);
                if (instance == null) {
                    AdapterUtils.sendFailurePatchToProvisioningTask(service,
                            computeReq.provisioningTaskReference,
                            new IllegalStateException("Error getting instance EC2 instance"));
                    return;
                }
                ComputeStateWithDescription resultDesc = new ComputeStateWithDescription();
                resultDesc.address = instance.getPublicIpAddress();
                resultDesc.powerState = AWSUtils.mapToPowerState(instance.getState());
                if (computeDesc.customProperties == null) {
                    resultDesc.customProperties = new HashMap<String, String>();
                } else {
                    resultDesc.customProperties = computeDesc.customProperties;
                }
                resultDesc.id = instance.getInstanceId();
                resultDesc.customProperties.put(AWSConstants.AWS_VPC_ID,
                        instance.getVpcId());

                // NIC - Private
                NetworkInterfaceState privateNICState = new NetworkInterfaceState();
                privateNICState.address = instance.getPrivateIpAddress();
                privateNICState.id = instance.getInstanceId() + HYPHEN + PRIVATE_INTERFACE;
                privateNICState.documentSelfLink = privateNICState.id;
                privateNICState.tenantLinks = computeDesc.tenantLinks;

                // Compute State Network Links
                resultDesc.networkLinks = new ArrayList<String>();
                resultDesc.networkLinks.add(UriUtils.buildUriPath(
                        NetworkInterfaceService.FACTORY_LINK,
                        privateNICState.id));

                // NIC - Public
                if (instance.getPublicIpAddress() != null) {
                    NetworkInterfaceState publicNICState = new NetworkInterfaceState();
                    publicNICState.address = instance.getPublicIpAddress();
                    publicNICState.id = instance.getInstanceId() + HYPHEN + PUBLIC_INTERFACE;
                    publicNICState.documentSelfLink = publicNICState.id;
                    publicNICState.tenantLinks = computeDesc.tenantLinks;

                    Operation postPublicNetworkInterface = Operation
                            .createPost(service,
                                    NetworkInterfaceService.FACTORY_LINK)
                            .setBody(publicNICState)
                            .setReferer(service.getHost().getUri());
                    createOperations.add(postPublicNetworkInterface);

                    resultDesc.networkLinks.add(UriUtils.buildUriPath(
                            NetworkInterfaceService.FACTORY_LINK,
                            publicNICState.id));
                }
                // Create operations
                Operation patchState = Operation
                        .createPatch(computeReq.computeReference)
                        .setBody(resultDesc)
                        .setReferer(service.getHost().getUri());
                createOperations.add(patchState);

                Operation postPrivateNetworkInterface = Operation
                        .createPost(
                                UriUtils.buildUri(
                                        service.getHost(),
                                        NetworkInterfaceService.FACTORY_LINK))
                        .setBody(privateNICState)
                        .setReferer(service.getHost().getUri());
                createOperations.add(postPrivateNetworkInterface);

                OperationJoin.JoinedCompletionHandler joinCompletion = (ox,
                        exc) -> {
                    if (exc != null) {
                        AdapterUtils.sendFailurePatchToProvisioningTask(service,
                                computeReq.provisioningTaskReference,
                                new IllegalStateException("Error updating VM state"));
                        return;
                    }
                    AdapterUtils.sendPatchToProvisioningTask(service,
                            computeReq.provisioningTaskReference);
                };
                OperationJoin joinOp = OperationJoin.create(createOperations);
                joinOp.setCompletion(joinCompletion);
                joinOp.sendWith(service.getHost());
            };

            String instanceId = result.getReservation().getInstances().get(0)
                    .getInstanceId();
            AWSTaskStatusChecker.create(amazonEC2Client, instanceId,
                    AWSInstanceService.AWS_RUNNING_NAME, consumer, computeReq,
                    service).start();
        }
    }

    // callback to be invoked when a VM creation operation returns
    private AsyncHandler<RunInstancesRequest, RunInstancesResult> buildCreationCallbackHandler(
            StatelessService service, ComputeInstanceRequest computeReq,
            ComputeStateWithDescription computeDesc,
            AmazonEC2AsyncClient amazonEC2Client) {
        return new AWSCreationHandler(service, computeReq, computeDesc,
                amazonEC2Client);
    }

    private void deleteInstance(AWSAllocation aws) {

        if (aws.computeRequest.isMockRequest ||
                aws.computeRequest.requestType == InstanceRequestType.DELETE_DOCUMENTS_ONLY) {
            deleteComputeResource(this, aws.child, aws.computeRequest);
            return;
        }

        String instanceId = aws.child.id;
        if (instanceId == null) {
            aws.error = new IllegalStateException(
                    "AWS InstanceId not available");
            aws.stage = AWSStages.ERROR;
            handleAllocation(aws);
            return;
        }

        List<String> instanceIdList = new ArrayList<String>();
        instanceIdList.add(instanceId);
        TerminateInstancesRequest termRequest = new TerminateInstancesRequest(
                instanceIdList);
        StatelessService service = this;
        AsyncHandler<TerminateInstancesRequest, TerminateInstancesResult> terminateHandler = buildTerminationCallbackHandler(
                service, aws.computeRequest, aws.child, aws.amazonEC2Client,
                instanceId);
        aws.amazonEC2Client.terminateInstancesAsync(termRequest,
                terminateHandler);
    }

    private class AWSTerminateHandler implements
            AsyncHandler<TerminateInstancesRequest, TerminateInstancesResult> {

        private StatelessService service;
        private ComputeInstanceRequest computeReq;
        private ComputeStateWithDescription computeDesc;
        private AmazonEC2AsyncClient amazonEC2Client;
        private OperationContext opContext;
        private String instanceId;

        private AWSTerminateHandler(StatelessService service,
                ComputeInstanceRequest computeReq,
                ComputeStateWithDescription computeDesc,
                AmazonEC2AsyncClient amazonEC2Client, String instanceId) {
            this.service = service;
            this.computeReq = computeReq;
            this.computeDesc = computeDesc;
            this.amazonEC2Client = amazonEC2Client;
            this.opContext = OperationContext.getOperationContext();
            this.instanceId = instanceId;
        }

        @Override
        public void onError(Exception exception) {
            OperationContext.restoreOperationContext(opContext);
            AdapterUtils.sendFailurePatchToProvisioningTask(service,
                    computeReq.provisioningTaskReference, exception);
        }

        @Override
        public void onSuccess(TerminateInstancesRequest request,
                TerminateInstancesResult result) {
            Consumer<Instance> consumer = new Consumer<Instance>() {

                @Override
                public void accept(Instance instance) {
                    OperationContext.restoreOperationContext(opContext);
                    if (instance == null) {
                        AdapterUtils.sendFailurePatchToProvisioningTask(service,
                                computeReq.provisioningTaskReference,
                                new IllegalStateException("Error getting instance"));
                        return;
                    }
                    deleteComputeResource(service, computeDesc, computeReq);
                }
            };
            AWSTaskStatusChecker.create(amazonEC2Client, instanceId,
                    AWSInstanceService.AWS_TERMINATED_NAME, consumer,
                    computeReq, service).start();
        }
    }

    // callback handler to be invoked once a aws terminate calls returns
    private AsyncHandler<TerminateInstancesRequest, TerminateInstancesResult> buildTerminationCallbackHandler(
            StatelessService service, ComputeInstanceRequest computeReq,
            ComputeStateWithDescription computeDesc,
            AmazonEC2AsyncClient amazonEC2Client, String instanceId) {
        return new AWSTerminateHandler(service, computeReq, computeDesc,
                amazonEC2Client, instanceId);
    }

    private void deleteComputeResource(StatelessService service,
            ComputeStateWithDescription computeDesc,
            ComputeInstanceRequest computeReq) {
        List<String> resourcesToDelete = new ArrayList<>();
        resourcesToDelete.add(computeDesc.documentSelfLink);
        if (computeDesc.diskLinks != null) {
            resourcesToDelete.addAll(computeDesc.diskLinks);
        }
        AtomicInteger deleteCallbackCount = new AtomicInteger(0);
        CompletionHandler deletionKickoffCompletion = (sendDeleteOp,
                sendDeleteEx) -> {
            if (sendDeleteEx != null) {
                AdapterUtils.sendFailurePatchToProvisioningTask(this,
                        computeReq.provisioningTaskReference, sendDeleteEx);
                return;
            }
            if (deleteCallbackCount.incrementAndGet() == resourcesToDelete
                    .size()) {
                AdapterUtils.sendPatchToProvisioningTask(this,
                        computeReq.provisioningTaskReference);
            }
        };
        for (String resourcetoDelete : resourcesToDelete) {
            sendRequest(Operation
                    .createDelete(
                            UriUtils.buildUri(service.getHost(),
                                    resourcetoDelete))
                    .setBody(new ServiceDocument())
                    .setCompletion(deletionKickoffCompletion));
        }
    }

    /*
     * Simple helper method to get the region id from either the compute request or the child
     * description.
     *
     * The child description will be null during a credential validation operation.
     */
    private String getRequestRegionId(AWSAllocation aws) {
        String regionId;
        if (aws.child == null) {
            regionId = aws.computeRequest.regionId;
        } else {
            regionId = aws.child.description.zoneId;
        }
        return regionId;
    }

    /*
     * Helper method to get amazon subnet id provided in the description custom properties
     */
    private String getSubnetId(AWSAllocation aws) {
        if (aws.child != null
                && aws.child.description != null
                && aws.child.description.customProperties != null) {
            return aws.child.description.customProperties.get(AWSConstants.AWS_SUBNET_ID);
        }
        return null;
    }

}
