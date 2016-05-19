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

package com.vmware.photon.controller.model.adapters.azureadapter;

import static com.vmware.photon.controller.model.ComputeProperties.CUSTOM_DISPLAY_NAME;
import static com.vmware.photon.controller.model.adapters.azureadapter.AzureConstants.AZURE_OSDISK_CACHING;
import static com.vmware.photon.controller.model.adapters.azureadapter.AzureConstants.AZURE_STORAGE_ACCOUNT_KEY1;
import static com.vmware.photon.controller.model.adapters.azureadapter.AzureConstants.AZURE_STORAGE_ACCOUNT_KEY2;
import static com.vmware.photon.controller.model.adapters.azureadapter.AzureConstants.AZURE_STORAGE_ACCOUNT_NAME;
import static com.vmware.photon.controller.model.adapters.azureadapter.AzureConstants.AZURE_STORAGE_ACCOUNT_TYPE;
import static com.vmware.photon.controller.model.adapters.azureadapter.AzureConstants.COMPUTE_NAMESPACE;
import static com.vmware.photon.controller.model.adapters.azureadapter.AzureConstants.MISSING_SUBSCRIPTION_CODE;
import static com.vmware.photon.controller.model.adapters.azureadapter.AzureConstants.NETWORK_NAMESPACE;
import static com.vmware.photon.controller.model.adapters.azureadapter.AzureConstants.PROVIDER_REGISTRED_STATE;
import static com.vmware.photon.controller.model.adapters.azureadapter.AzureConstants.STORAGE_NAMESPACE;
import static com.vmware.photon.controller.model.adapters.azureadapter.AzureUtils.awaitTermination;
import static com.vmware.photon.controller.model.adapters.azureadapter.AzureUtils.cleanUpHttpClient;
import static com.vmware.photon.controller.model.adapters.azureadapter.AzureUtils.getAzureConfig;

import java.io.File;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import com.microsoft.azure.CloudError;
import com.microsoft.azure.CloudException;
import com.microsoft.azure.credentials.ApplicationTokenCredentials;
import com.microsoft.azure.management.compute.ComputeManagementClient;
import com.microsoft.azure.management.compute.ComputeManagementClientImpl;
import com.microsoft.azure.management.compute.models.HardwareProfile;
import com.microsoft.azure.management.compute.models.ImageReference;
import com.microsoft.azure.management.compute.models.NetworkInterfaceReference;
import com.microsoft.azure.management.compute.models.NetworkProfile;
import com.microsoft.azure.management.compute.models.OSDisk;
import com.microsoft.azure.management.compute.models.OSProfile;
import com.microsoft.azure.management.compute.models.StorageProfile;
import com.microsoft.azure.management.compute.models.VirtualHardDisk;
import com.microsoft.azure.management.compute.models.VirtualMachine;
import com.microsoft.azure.management.network.NetworkManagementClient;
import com.microsoft.azure.management.network.NetworkManagementClientImpl;
import com.microsoft.azure.management.network.models.AddressSpace;
import com.microsoft.azure.management.network.models.NetworkInterface;
import com.microsoft.azure.management.network.models.NetworkInterfaceIPConfiguration;
import com.microsoft.azure.management.network.models.NetworkSecurityGroup;
import com.microsoft.azure.management.network.models.PublicIPAddress;
import com.microsoft.azure.management.network.models.SecurityRule;
import com.microsoft.azure.management.network.models.Subnet;
import com.microsoft.azure.management.network.models.VirtualNetwork;
import com.microsoft.azure.management.resources.ResourceManagementClient;
import com.microsoft.azure.management.resources.ResourceManagementClientImpl;
import com.microsoft.azure.management.resources.SubscriptionClient;
import com.microsoft.azure.management.resources.SubscriptionClientImpl;
import com.microsoft.azure.management.resources.models.Provider;
import com.microsoft.azure.management.resources.models.ResourceGroup;
import com.microsoft.azure.management.resources.models.Subscription;
import com.microsoft.azure.management.storage.StorageManagementClient;
import com.microsoft.azure.management.storage.StorageManagementClientImpl;
import com.microsoft.azure.management.storage.models.AccountType;
import com.microsoft.azure.management.storage.models.StorageAccount;
import com.microsoft.azure.management.storage.models.StorageAccountCreateParameters;
import com.microsoft.azure.management.storage.models.StorageAccountKeys;
import com.microsoft.rest.ServiceCallback;
import com.microsoft.rest.ServiceResponse;
import okhttp3.OkHttpClient;
import retrofit2.Retrofit;

import com.vmware.photon.controller.model.adapterapi.ComputeInstanceRequest;
import com.vmware.photon.controller.model.adapterapi.ComputeInstanceRequest.InstanceRequestType;
import com.vmware.photon.controller.model.adapters.azureadapter.diagnostic.settings.models.AzureDiagnosticSettings;
import com.vmware.photon.controller.model.adapters.util.AdapterUtils;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService;
import com.vmware.photon.controller.model.resources.ComputeService;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeStateWithDescription;
import com.vmware.photon.controller.model.resources.DiskService.DiskState;
import com.vmware.xenon.common.FileUtils;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Operation.CompletionHandler;
import com.vmware.xenon.common.OperationContext;
import com.vmware.xenon.common.OperationJoin;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.AuthCredentialsService;
import com.vmware.xenon.services.common.AuthCredentialsService.AuthCredentialsServiceState;

/**
 * Adapter to create/delete a VM instance on Azure.
 */
public class AzureInstanceService extends StatelessService {

    public static final String SELF_LINK = AzureUriPaths.AZURE_INSTANCE_ADAPTER;

    // TODO VSYM-322: Remove unused default properties from AzureInstanceService
    // Name prefixes
    private static final String NICCONFIG_NAME_PREFIX = "nicconfig";

    private static final String SUBNET_NAME = "default";
    private static final String NETWORK_ADDRESS_PREFIX = "10.0.0.0/16";
    private static final String SUBNET_ADDRESS_PREFIX = "10.0.0.0/24";
    private static final String PUBLIC_IP_ALLOCATION_METHOD = "Dynamic";
    private static final String PRIVATE_IP_ALLOCATION_METHOD = "Dynamic";

    private static final String DEFAULT_VM_SIZE = "Basic_A0";
    private static final String OS_DISK_CREATION_OPTION = "fromImage";

    private static final AccountType DEFAULT_STORAGE_ACCOUNT_TYPE = AccountType.STANDARD_LRS;
    private static final String VHD_URI_FORMAT = "https://%s.blob.core.windows.net/vhds/%s.vhd";
    private static final String BOOT_DISK_SUFFIX = "-boot-disk";

    private static final long DEFAULT_EXPIRATION_INTERVAL_MICROS = TimeUnit.MINUTES.toMicros(5);
    private static final int RETRY_INTERVAL_SECONDS = 30;

    private ExecutorService executorService;

    @Override
    public void handleStart(Operation startPost) {
        executorService = getHost().allocateExecutor(this);

        super.handleStart(startPost);
    }

    @Override
    public void handleStop(Operation delete) {
        executorService.shutdown();
        awaitTermination(this, executorService);
        super.handleStop(delete);
    }

    @Override
    public void handlePatch(Operation op) {
        if (!op.hasBody()) {
            op.fail(new IllegalArgumentException("body is required"));
            return;
        }
        AzureAllocationContext ctx = new AzureAllocationContext(
                op.getBody(ComputeInstanceRequest.class));
        switch (ctx.computeRequest.requestType) {
        case VALIDATE_CREDENTIALS:
            ctx.stage = AzureStages.PARENTAUTH;
            ctx.operation = op;
            handleAllocation(ctx);
            break;
        default:
            op.complete();
            if (ctx.computeRequest.isMockRequest && ctx.computeRequest.requestType
                    == ComputeInstanceRequest.InstanceRequestType.CREATE) {
                AdapterUtils.sendPatchToProvisioningTask(this,
                        ctx.computeRequest.provisioningTaskReference);
                return;
            }
            try {
                handleAllocation(ctx);
            } catch (Exception e) {
                logSevere(e);
                if (ctx.computeRequest.provisioningTaskReference != null) {
                    AdapterUtils.sendFailurePatchToProvisioningTask(this,
                            ctx.computeRequest.provisioningTaskReference, e);
                }
            }
        }
    }

    /**
     * State machine to handle different stages of VM creation/deletion.
     */
    private void handleAllocation(AzureAllocationContext ctx) {
        switch (ctx.stage) {
        case VMDESC:
            getVMDescription(ctx, AzureStages.PARENTDESC);
            break;
        case PARENTDESC:
            getParentDescription(ctx, AzureStages.PARENTAUTH);
            break;
        case PARENTAUTH:
            getParentAuth(ctx, AzureStages.CLIENT);
            break;
        case CLIENT:
            if (ctx.credentials == null) {
                try {
                    ctx.credentials = getAzureConfig(ctx.parentAuth);
                } catch (Throwable e) {
                    logSevere(e);
                    ctx.error = e;
                    ctx.stage = AzureStages.ERROR;
                    handleAllocation(ctx);
                    return;
                }
            }

            if (ctx.httpClient == null) {
                ctx.httpClient = new OkHttpClient();
                ctx.clientBuilder = ctx.httpClient.newBuilder();
            }
            // now that we have a client lets move onto the next step
            switch (ctx.computeRequest.requestType) {
            case CREATE:
                ctx.stage = AzureStages.CHILDAUTH;
                handleAllocation(ctx);
                break;
            case VALIDATE_CREDENTIALS:
                validateAzureCredentials(ctx);
                break;
            case DELETE:
            case DELETE_DOCUMENTS_ONLY:
                ctx.stage = AzureStages.DELETE;
                handleAllocation(ctx);
                break;
            default:
                ctx.error = new IllegalStateException(
                        "Unknown compute request type: " + ctx.computeRequest.requestType);
                ctx.stage = AzureStages.ERROR;
                handleAllocation(ctx);
            }
            break;
        case CHILDAUTH:
            getChildAuth(ctx, AzureStages.VMDISKS);
            break;
        case VMDISKS:
            getVMDisks(ctx);
            break;
        case INIT_RES_GROUP:
            initResourceGroup(ctx);
            break;
        case INIT_STORAGE:
            initStorageAccount(ctx);
            break;
        case INIT_NETWORK:
            initNetwork(ctx);
            break;
        case INIT_PUBLIC_IP:
            initPublicIP(ctx);
            break;
        case INIT_SEC_GROUP:
            initSecurityGroup(ctx);
            break;
        case INIT_NIC:
            initNIC(ctx);
            break;
        case CREATE:
            createVM(ctx);
            break;
        case ENABLE_MONITORING:
            try {
                enableMonitoring(ctx);
            } catch (Throwable e) {
                this.handleError(ctx, e);
                return;
            }
            break;
        case DELETE:
            deleteVM(ctx);
            break;
        case GET_STORAGE_KEYS:
            getStorageKeys(ctx);
            break;
        case ERROR:
            if (ctx.computeRequest.provisioningTaskReference != null) {
                AdapterUtils.sendFailurePatchToProvisioningTask(this,
                        ctx.computeRequest.provisioningTaskReference, ctx.error);
            }
            cleanUpHttpClient(this, ctx.httpClient);
            break;
        case FINISHED:
            AdapterUtils.sendPatchToProvisioningTask(
                    AzureInstanceService.this,
                    ctx.computeRequest.provisioningTaskReference);
            cleanUpHttpClient(this, ctx.httpClient);
            break;
        default:
            logSevere("Unhandled stage: %s", ctx.stage.toString());
            cleanUpHttpClient(this, ctx.httpClient);
            break;
        }
    }

    /**
     * Validates azure credential by making an API call.
     */
    private void validateAzureCredentials(final AzureAllocationContext ctx) {
        if (ctx.computeRequest.isMockRequest) {
            ctx.operation.complete();
            return;
        }

        SubscriptionClient subscriptionClient = new SubscriptionClientImpl(
                AzureConstants.BASE_URI, ctx.credentials, ctx.clientBuilder,
                getRetrofitBuilder());

        subscriptionClient.getSubscriptionsOperations().getAsync(
                ctx.parentAuth.userLink, new ServiceCallback<Subscription>() {
                    @Override
                    public void failure(Throwable e) {
                        ctx.operation.fail(e);
                    }

                    @Override
                    public void success(ServiceResponse<Subscription> result) {
                        Subscription subscription = result.getBody();
                        logFine("Got subscription %s with id %s", subscription.getDisplayName(),
                                subscription.getId());
                        ctx.operation.complete();
                    }
                });
    }

    private void deleteVM(AzureAllocationContext ctx) {
        if (ctx.computeRequest.isMockRequest
                || ctx.computeRequest.requestType == InstanceRequestType.DELETE_DOCUMENTS_ONLY) {
            deleteComputeResource(ctx);
            return;
        }

        String resourceGroupName = ctx.vmName;

        if (resourceGroupName == null || resourceGroupName.isEmpty()) {
            throw new IllegalArgumentException("Resource group name is required");
        }

        logInfo("Deleting resource group with name [%s]", resourceGroupName);

        ResourceManagementClient client = getResourceManagementClient(ctx);

        client.getResourceGroupsOperations().beginDeleteAsync(resourceGroupName,
                new AzureAsyncCallback<Void>() {
                    @Override
                    public void onError(Throwable e) {
                        logSevere(e);
                        ctx.error = e;
                        ctx.stage = AzureStages.ERROR;
                        handleAllocation(ctx);
                    }

                    @Override
                    public void onSuccess(ServiceResponse<Void> result) {
                        logInfo("Successfully deleted resource group [%s]", resourceGroupName);
                        deleteComputeResource(ctx);
                    }
                }
        );
    }

    private void deleteComputeResource(AzureAllocationContext ctx) {
        ComputeStateWithDescription computeDesc = ctx.child;
        ComputeInstanceRequest computeReq = ctx.computeRequest;

        List<String> resourcesToDelete = new ArrayList<>();
        resourcesToDelete.add(computeDesc.documentSelfLink);
        if (computeDesc.diskLinks != null) {
            resourcesToDelete.addAll(computeDesc.diskLinks);
        }
        AtomicInteger deleteCallbackCount = new AtomicInteger(0);
        CompletionHandler deletionKickoffCompletion = (sendDeleteOp, sendDeleteEx) -> {
            if (sendDeleteEx != null) {
                handleError(ctx, sendDeleteEx);
                return;
            }
            if (deleteCallbackCount.incrementAndGet() == resourcesToDelete.size()) {
                AdapterUtils
                        .sendPatchToProvisioningTask(this, computeReq.provisioningTaskReference);
                ctx.stage = AzureStages.FINISHED;
                handleAllocation(ctx);
            }
        };
        for (String resourcetoDelete : resourcesToDelete) {
            sendRequest(Operation.createDelete(UriUtils.buildUri(getHost(), resourcetoDelete))
                    .setBody(new ServiceDocument())
                    .setCompletion(deletionKickoffCompletion));
        }
    }

    private void initResourceGroup(AzureAllocationContext ctx) {
        String resourceGroupName = ctx.vmName;

        if (resourceGroupName == null || resourceGroupName.isEmpty()) {
            throw new IllegalArgumentException("Resource group name is required");
        }

        logInfo("Creating resource group with name [%s]", resourceGroupName);

        ResourceGroup group = new ResourceGroup();
        group.setLocation(ctx.child.description.regionId);

        ResourceManagementClient client = getResourceManagementClient(ctx);

        client.getResourceGroupsOperations().createOrUpdateAsync(resourceGroupName, group,
                new AzureAsyncCallback<ResourceGroup>() {
                    @Override
                    public void onError(Throwable e) {
                        logSevere(e);
                        ctx.error = e;
                        ctx.stage = AzureStages.ERROR;
                        handleAllocation(ctx);
                    }

                    @Override
                    public void onSuccess(ServiceResponse<ResourceGroup> result) {
                        ctx.stage = AzureStages.INIT_STORAGE;
                        ctx.resourceGroup = result.getBody();
                        logInfo("Successfully created resource group [%s]",
                                result.getBody().getName());
                        handleAllocation(ctx);
                    }
                }
        );
    }

    private void initStorageAccount(AzureAllocationContext ctx) {
        StorageAccountCreateParameters storageParameters = new StorageAccountCreateParameters();
        storageParameters.setLocation(ctx.resourceGroup.getLocation());

        if (ctx.bootDisk.customProperties == null) {
            ctx.error = new IllegalArgumentException("Custom properties for boot disk is required");
            ctx.stage = AzureStages.ERROR;
            handleAllocation(ctx);
            return;
        }

        ctx.storageAccountName = ctx.bootDisk.customProperties.get(AZURE_STORAGE_ACCOUNT_NAME);

        if (ctx.storageAccountName == null) {
            ctx.error = new IllegalStateException("Storage account name is required");
            ctx.stage = AzureStages.ERROR;
            handleAllocation(ctx);
            return;
        }

        String accountType = ctx.bootDisk.customProperties
                .getOrDefault(AZURE_STORAGE_ACCOUNT_TYPE, DEFAULT_STORAGE_ACCOUNT_TYPE.toValue());
        storageParameters.setAccountType(AccountType.fromValue(accountType));

        logInfo("Creating storage account with name [%s]", ctx.storageAccountName);

        StorageManagementClient client = getStorageManagementClient(ctx);

        client.getStorageAccountsOperations().beginCreateAsync(ctx.resourceGroup.getName(),
                ctx.storageAccountName, storageParameters,
                new AzureAsyncCallback<StorageAccount>() {
                    @Override
                    public void onError(Throwable e) {
                        handleSubscriptionError(ctx, STORAGE_NAMESPACE, e);
                    }

                    @Override
                    public void onSuccess(ServiceResponse<StorageAccount> result) {
                        ctx.stage = AzureStages.INIT_NETWORK;
                        ctx.storage = result.getBody();
                        logInfo("Successfully created storage account [%s]",
                                ctx.storageAccountName);
                        handleAllocation(ctx);
                    }
                });
    }

    private void initNetwork(AzureAllocationContext ctx) {
        VirtualNetwork vnet = new VirtualNetwork();
        vnet.setLocation(ctx.resourceGroup.getLocation());
        vnet.setAddressSpace(new AddressSpace());
        vnet.getAddressSpace().setAddressPrefixes(new ArrayList<>());
        vnet.getAddressSpace().getAddressPrefixes().add(NETWORK_ADDRESS_PREFIX);

        vnet.setSubnets(new ArrayList<>());
        Subnet subnet = new Subnet();
        subnet.setName(SUBNET_NAME);
        subnet.setAddressPrefix(SUBNET_ADDRESS_PREFIX);
        vnet.getSubnets().add(subnet);

        String vNetName = ctx.vmName;

        logInfo("Creating virtual network [%s]", vNetName);

        NetworkManagementClient client = getNetworkManagementClient(ctx);

        client.getVirtualNetworksOperations().beginCreateOrUpdateAsync(
                ctx.resourceGroup.getName(), vNetName, vnet,
                new AzureAsyncCallback<VirtualNetwork>() {
                    @Override
                    public void onError(Throwable e) {
                        handleSubscriptionError(ctx, NETWORK_NAMESPACE, e);
                    }

                    @Override
                    public void onSuccess(ServiceResponse<VirtualNetwork> result) {
                        ctx.stage = AzureStages.INIT_PUBLIC_IP;
                        ctx.network = result.getBody();
                        logInfo("Successfully created virtual network [%s]",
                                result.getBody().getName());
                        handleAllocation(ctx);
                    }
                });
    }

    private void initPublicIP(AzureAllocationContext ctx) {
        PublicIPAddress publicIPAddress = new PublicIPAddress();
        publicIPAddress.setLocation(ctx.resourceGroup.getLocation());
        publicIPAddress.setPublicIPAllocationMethod(PUBLIC_IP_ALLOCATION_METHOD);

        String publicIPName = ctx.vmName;

        logInfo("Creating public IP with name [%s]", publicIPName);

        NetworkManagementClient client = getNetworkManagementClient(ctx);

        client.getPublicIPAddressesOperations().beginCreateOrUpdateAsync(
                ctx.resourceGroup.getName(), publicIPName, publicIPAddress,
                new AzureAsyncCallback<PublicIPAddress>() {
                    @Override
                    public void onError(Throwable e) {
                        logSevere(e);
                        ctx.error = e;
                        ctx.stage = AzureStages.ERROR;
                        handleAllocation(ctx);
                    }

                    @Override
                    public void onSuccess(ServiceResponse<PublicIPAddress> result) {
                        ctx.stage = AzureStages.INIT_SEC_GROUP;
                        ctx.publicIP = result.getBody();
                        logInfo("Successfully created public IP address with name [%s]",
                                result.getBody().getName());
                        handleAllocation(ctx);
                    }
                });
    }

    private void initSecurityGroup(AzureAllocationContext ctx) {
        NetworkSecurityGroup group = new NetworkSecurityGroup();
        group.setLocation(ctx.resourceGroup.getLocation());

        // Set the linux security rule to allow SSH traffic
        SecurityRule linuxSecurityRule = new SecurityRule();
        linuxSecurityRule.setPriority(AzureConstants.AZURE_LINUX_SECURITY_GROUP_PRIORITY);
        linuxSecurityRule.setName(AzureConstants.AZURE_LINUX_SECURITY_GROUP_NAME);
        linuxSecurityRule.setDescription(AzureConstants.AZURE_LINUX_SECURITY_GROUP_DESCRIPTION);
        linuxSecurityRule.setAccess(AzureConstants.AZURE_LINUX_SECURITY_GROUP_ACCESS);
        linuxSecurityRule.setProtocol(AzureConstants.AZURE_LINUX_SECURITY_GROUP_PROTOCOL);
        linuxSecurityRule.setDirection(AzureConstants.AZURE_LINUX_SECURITY_GROUP_DIRECTION);
        linuxSecurityRule.setSourceAddressPrefix(
                AzureConstants.AZURE_LINUX_SECURITY_GROUP_SOURCE_ADDRESS_PREFIX);
        linuxSecurityRule.setDestinationAddressPrefix(
                AzureConstants.AZURE_LINUX_SECURITY_GROUP_DESTINATION_ADDRESS_PREFIX);
        linuxSecurityRule
                .setSourcePortRange(AzureConstants.AZURE_LINUX_SECURITY_GROUP_SOURCE_PORT_RANGE);
        linuxSecurityRule.setDestinationPortRange(
                AzureConstants.AZURE_LINUX_SECURITY_GROUP_DESTINATION_PORT_RANGE);

        // Set the windows security rule to allow SSH traffic
        SecurityRule windowsSecurityRule = new SecurityRule();
        windowsSecurityRule.setPriority(AzureConstants.AZURE_WINDOWS_SECURITY_GROUP_PRIORITY);
        windowsSecurityRule.setName(AzureConstants.AZURE_WINDOWS_SECURITY_GROUP_NAME);
        windowsSecurityRule.setDescription(AzureConstants.AZURE_WINDOWS_SECURITY_GROUP_DESCRIPTION);
        windowsSecurityRule.setAccess(AzureConstants.AZURE_WINDOWS_SECURITY_GROUP_ACCESS);
        windowsSecurityRule.setProtocol(AzureConstants.AZURE_WINDOWS_SECURITY_GROUP_PROTOCOL);
        windowsSecurityRule.setDirection(AzureConstants.AZURE_WINDOWS_SECURITY_GROUP_DIRECTION);
        windowsSecurityRule.setSourceAddressPrefix(
                AzureConstants.AZURE_WINDOWS_SECURITY_GROUP_SOURCE_ADDRESS_PREFIX);
        windowsSecurityRule.setDestinationAddressPrefix(
                AzureConstants.AZURE_WINDOWS_SECURITY_GROUP_DESTINATION_ADDRESS_PREFIX);
        windowsSecurityRule
                .setSourcePortRange(AzureConstants.AZURE_WINDOWS_SECURITY_GROUP_SOURCE_PORT_RANGE);
        windowsSecurityRule.setDestinationPortRange(
                AzureConstants.AZURE_WINDOWS_SECURITY_GROUP_DESTINATION_PORT_RANGE);

        List<SecurityRule> securityRules = new ArrayList<>();
        securityRules.add(linuxSecurityRule);
        securityRules.add(windowsSecurityRule);
        group.setSecurityRules(securityRules);

        String secGroupName = ctx.vmName;

        logInfo("Creating security group with name [%s]", secGroupName);

        NetworkManagementClient client = getNetworkManagementClient(ctx);

        client.getNetworkSecurityGroupsOperations().createOrUpdateAsync(
                ctx.resourceGroup.getName(), secGroupName, group,
                new AzureAsyncCallback<NetworkSecurityGroup>() {
                    @Override
                    public void onError(Throwable e) {
                        logSevere(e);
                        ctx.error = e;
                        ctx.stage = AzureStages.ERROR;
                        handleAllocation(ctx);
                    }

                    @Override
                    public void onSuccess(ServiceResponse<NetworkSecurityGroup> result) {
                        ctx.stage = AzureStages.INIT_NIC;
                        ctx.securityGroup = result.getBody();
                        logInfo("Successfully created security group with name [%s]",
                                result.getBody().getName());
                        handleAllocation(ctx);
                    }
                });
    }

    private void initNIC(AzureAllocationContext ctx) {
        NetworkInterface nic = new NetworkInterface();
        nic.setLocation(ctx.resourceGroup.getLocation());
        nic.setIpConfigurations(new ArrayList<>());
        NetworkInterfaceIPConfiguration configuration = new NetworkInterfaceIPConfiguration();
        configuration.setName(generateName(NICCONFIG_NAME_PREFIX));
        configuration.setPrivateIPAllocationMethod(PRIVATE_IP_ALLOCATION_METHOD);
        configuration.setSubnet(ctx.network.getSubnets().get(0));
        configuration.setPublicIPAddress(ctx.publicIP);
        nic.getIpConfigurations().add(configuration);
        nic.setNetworkSecurityGroup(ctx.securityGroup);

        String nicName = generateName(ctx.vmName);

        NetworkManagementClient client = getNetworkManagementClient(ctx);

        client.getNetworkInterfacesOperations().beginCreateOrUpdateAsync(
                ctx.resourceGroup.getName(), nicName, nic,
                new AzureAsyncCallback<NetworkInterface>() {
                    @Override
                    public void onError(Throwable e) {
                        logSevere(e);
                        ctx.error = e;
                        ctx.stage = AzureStages.ERROR;
                        handleAllocation(ctx);
                    }

                    @Override
                    public void onSuccess(ServiceResponse<NetworkInterface> result) {
                        ctx.stage = AzureStages.CREATE;
                        ctx.nic = result.getBody();
                        logInfo("Successfully created NIC with name [%s]",
                                result.getBody().getName());
                        handleAllocation(ctx);
                    }
                });
    }

    private void createVM(AzureAllocationContext ctx) {
        ComputeDescriptionService.ComputeDescription description = ctx.child.description;

        Map<String, String> customProperties = description.customProperties;
        if (customProperties == null) {
            handleError(ctx, new IllegalStateException("Custom properties not specified"));
            return;
        }

        DiskState bootDisk = ctx.bootDisk;
        if (bootDisk == null) {
            handleError(ctx, new IllegalStateException("Azure bootDisk not specified"));
            return;
        }

        URI imageId = bootDisk.sourceImageReference;
        if (imageId == null) {
            handleError(ctx, new IllegalStateException("Azure image reference not specified"));
            return;
        }

        VirtualMachine request = new VirtualMachine();
        request.setLocation(ctx.resourceGroup.getLocation());

        // Set OS profile.
        OSProfile osProfile = new OSProfile();
        String vmName = ctx.vmName;
        osProfile.setComputerName(vmName);
        osProfile.setAdminUsername(ctx.childAuth.userEmail);
        osProfile.setAdminPassword(ctx.childAuth.privateKey);
        request.setOsProfile(osProfile);

        // Set hardware profile.
        HardwareProfile hardwareProfile = new HardwareProfile();
        hardwareProfile.setVmSize(
                description.instanceType != null ? description.instanceType : DEFAULT_VM_SIZE);
        request.setHardwareProfile(hardwareProfile);

        // Set storage profile.
        VirtualHardDisk vhd = new VirtualHardDisk();
        String vhdName = getVHDName(vmName);
        vhd.setUri(String.format(VHD_URI_FORMAT, ctx.storageAccountName, vhdName));

        OSDisk osDisk = new OSDisk();
        osDisk.setName(vmName);
        osDisk.setVhd(vhd);
        osDisk.setCaching(bootDisk.customProperties.get(AZURE_OSDISK_CACHING));
        // We don't support Attach option which allows to use a specialized disk to create the
        // virtual machine.
        osDisk.setCreateOption(OS_DISK_CREATION_OPTION);

        StorageProfile storageProfile = new StorageProfile();
        // Currently we only support platform images.
        ImageReference imageReference = getImageReference(imageId.toString());
        storageProfile.setImageReference(imageReference);
        storageProfile.setOsDisk(osDisk);
        request.setStorageProfile(storageProfile);

        // Set network profile
        NetworkInterfaceReference nir = new NetworkInterfaceReference();
        nir.setPrimary(true);
        nir.setId(ctx.nic.getId());

        NetworkProfile networkProfile = new NetworkProfile();
        networkProfile.setNetworkInterfaces(Collections.singletonList(nir));
        request.setNetworkProfile(networkProfile);

        logInfo("Creating virtual machine with name [%s]", vmName);

        ComputeManagementClient client = new ComputeManagementClientImpl(AzureConstants.BASE_URI,
                ctx.credentials, ctx.clientBuilder, getRetrofitBuilder());
        client.setSubscriptionId(ctx.parentAuth.userLink);

        client.getVirtualMachinesOperations().createOrUpdateAsync(
                ctx.resourceGroup.getName(), vmName, request,
                new AzureAsyncCallback<VirtualMachine>() {
                    @Override
                    public void onError(Throwable e) {
                        handleSubscriptionError(ctx, COMPUTE_NAMESPACE, e);
                    }

                    @Override
                    public void onSuccess(ServiceResponse<VirtualMachine> result) {
                        VirtualMachine vm = result.getBody();
                        logInfo("Successfully created vm [%s]", vm.getName());

                        ComputeService.ComputeStateWithDescription resultDesc = new
                                ComputeService.ComputeStateWithDescription();
                        if (ctx.child.customProperties == null) {
                            resultDesc.customProperties = new HashMap<>();
                        } else {
                            resultDesc.customProperties = ctx.child.customProperties;
                        }
                        // Azure for some case changes the case of the vm id.
                        ctx.vmId = vm.getId().toLowerCase();
                        resultDesc.id = ctx.vmId;
                        resultDesc.customProperties
                                .put(AzureConstants.AZURE_RESOURCE_GROUP_NAME,
                                        ctx.resourceGroup.getName());

                        Operation.CompletionHandler completionHandler = (ox,
                                exc) -> {
                            if (exc != null) {
                                logSevere(exc);
                                ctx.stage = AzureStages.ERROR;
                                ctx.error = exc;
                                handleAllocation(ctx);
                                return;
                            }
                            ctx.stage = AzureStages.ENABLE_MONITORING;
                            handleAllocation(ctx);
                        };

                        sendRequest(
                                Operation.createPatch(ctx.computeRequest.computeReference)
                                        .setBody(resultDesc).setCompletion(completionHandler)
                                        .setReferer(getHost().getUri()));
                    }
                });
    }

    /**
     * Gets the storage keys from azure and patches the credential state.
     */
    private void getStorageKeys(AzureAllocationContext ctx) {
        StorageManagementClient client = getStorageManagementClient(ctx);

        client.getStorageAccountsOperations().listKeysAsync(ctx.resourceGroup.getName(),
                ctx.storageAccountName, new AzureAsyncCallback<StorageAccountKeys>() {
                    @Override
                    public void onError(Throwable e) {
                        handleError(ctx, e);
                    }

                    @Override
                    public void onSuccess(ServiceResponse<StorageAccountKeys> result) {
                        StorageAccountKeys keys = result.getBody();
                        String key1 = keys.getKey1();
                        String key2 = keys.getKey2();
                        String authCredentialsLink = ctx.bootDisk.authCredentialsLink;

                        AuthCredentialsServiceState diskAuth = new AuthCredentialsServiceState();
                        Operation operation;
                        URI authUri;
                        String authLink;
                        // PATCH or POST depending whether credentials exists or not.
                        if (authCredentialsLink == null) {
                            diskAuth.documentSelfLink = UUID.randomUUID().toString();
                            authUri = UriUtils
                                    .buildUri(getHost(), AuthCredentialsService.FACTORY_LINK);
                            authLink = UriUtils.buildUriPath(AuthCredentialsService.FACTORY_LINK,
                                    diskAuth.documentSelfLink);
                            operation = Operation.createPost(authUri);
                        } else {
                            authUri = UriUtils.buildUri(getHost(), authCredentialsLink);
                            authLink = diskAuth.documentSelfLink;
                            operation = Operation.createPatch(authUri);
                        }

                        diskAuth.customProperties = new HashMap<>();
                        diskAuth.customProperties.put(AZURE_STORAGE_ACCOUNT_KEY1, key1);
                        diskAuth.customProperties.put(AZURE_STORAGE_ACCOUNT_KEY2, key2);

                        sendRequest(operation.setBody(diskAuth)
                                .setCompletion((o, e) -> {
                                    if (e != null) {
                                        handleError(ctx, e);
                                        return;
                                    }
                                    logInfo("Successfully retrieved keys for storage account [%s]",
                                            ctx.storageAccountName);

                                    // link the auth state with disk, if not linked.
                                    if (authCredentialsLink == null) {
                                        ctx.bootDisk.authCredentialsLink = authLink;
                                        Operation patch = Operation
                                                .createPatch(UriUtils.buildUri(getHost(),
                                                        ctx.bootDisk.documentSelfLink))
                                                .setBody(ctx.bootDisk)
                                                .setCompletion(((completedOp, failure) -> {
                                                    if (failure != null) {
                                                        handleError(ctx, failure);
                                                        return;
                                                    }

                                                    ctx.stage = AzureStages.FINISHED;
                                                    handleAllocation(ctx);
                                                }));
                                        sendRequest(patch);
                                        return;
                                    }

                                    ctx.stage = AzureStages.FINISHED;
                                    handleAllocation(ctx);
                                }));

                    }
                });
    }

    private String getVHDName(String vmName) {
        return vmName + BOOT_DISK_SUFFIX;
    }

    private ImageReference getImageReference(String imageId) {
        String[] imageIdParts = imageId.split(":");
        if (imageIdParts.length != 4) {
            throw new IllegalArgumentException(
                    "Azure image id should be of the format <publisher>:<offer>:<sku>:<version>");
        }

        ImageReference imageReference = new ImageReference();
        imageReference.setPublisher(imageIdParts[0]);
        imageReference.setOffer(imageIdParts[1]);
        imageReference.setSku(imageIdParts[2]);
        imageReference.setVersion(imageIdParts[3]);

        return imageReference;
    }

    /**
     * This method tries to detect a subscription registration error and register subscription for
     * given namespace. Otherwise the fallback is to transition to error state.
     */
    private void handleSubscriptionError(AzureAllocationContext ctx, String namespace,
            Throwable e) {
        if (e instanceof CloudException) {
            CloudException ce = (CloudException) e;
            CloudError body = ce.getBody();
            if (body != null) {
                String code = body.getCode();
                if (MISSING_SUBSCRIPTION_CODE.equals(code)) {
                    registerSubscription(ctx, namespace);
                    return;
                }
            }
        }
        handleError(ctx, e);
    }

    private void handleError(AzureAllocationContext ctx, Throwable e) {
        logSevere(e);
        ctx.error = e;
        ctx.stage = AzureStages.ERROR;
        handleAllocation(ctx);
    }

    private void registerSubscription(AzureAllocationContext ctx, String namespace) {
        ResourceManagementClient client = getResourceManagementClient(ctx);
        client.getProvidersOperations().registerAsync(namespace, new AzureAsyncCallback<Provider>() {
            @Override
            public void onError(Throwable e) {
                logSevere(e);
                ctx.error = e;
                ctx.stage = AzureStages.ERROR;
                handleAllocation(ctx);
            }

            @Override
            public void onSuccess(ServiceResponse<Provider> result) {
                Provider provider = result.getBody();
                String registrationState = provider.getRegistrationState();
                if (!PROVIDER_REGISTRED_STATE.equalsIgnoreCase(registrationState)) {
                    logInfo("%s namespace registration in %s state", namespace, registrationState);
                    long retryExpiration =
                            Utils.getNowMicrosUtc() + DEFAULT_EXPIRATION_INTERVAL_MICROS;
                    getSubscriptionState(ctx, namespace, retryExpiration);
                    return;
                }
                logInfo("Successfully registered namespace [%s]", provider.getNamespace());
                handleAllocation(ctx);
            }
        });
    }

    private void getSubscriptionState(AzureAllocationContext ctx,
            String namespace, long retryExpiration) {
        if (Utils.getNowMicrosUtc() > retryExpiration) {
            String msg = String
                    .format("Subscription for %s namespace did not reach %s state", namespace,
                            PROVIDER_REGISTRED_STATE);
            logSevere(msg);
            ctx.error = new RuntimeException(msg);
            ctx.stage = AzureStages.ERROR;
            handleAllocation(ctx);
            return;
        }

        ResourceManagementClient client = getResourceManagementClient(ctx);

        getHost().schedule(
                () -> client.getProvidersOperations().getAsync(namespace,
                        new AzureAsyncCallback<Provider>() {
                            @Override
                            public void onError(Throwable e) {
                                logSevere(e);
                                ctx.error = e;
                                ctx.stage = AzureStages.ERROR;
                                handleAllocation(ctx);
                            }

                            @Override
                            public void onSuccess(ServiceResponse<Provider> result) {
                                Provider provider = result.getBody();
                                String registrationState = provider.getRegistrationState();
                                if (!PROVIDER_REGISTRED_STATE.equalsIgnoreCase(registrationState)) {
                                    logInfo("%s namespace registration in %s state",
                                            namespace, registrationState);
                                    getSubscriptionState(ctx, namespace, retryExpiration);
                                    return;
                                }
                                logInfo("Successfully registered namespace [%s]",
                                        provider.getNamespace());
                                handleAllocation(ctx);
                            }
                        }), RETRY_INTERVAL_SECONDS, TimeUnit.SECONDS);
    }

    private void getChildAuth(AzureAllocationContext ctx, AzureStages next) {
        if (ctx.child.description.authCredentialsLink == null) {
            handleError(ctx, new IllegalStateException("Auth information for compute is required"));
            return;
        }

        String childAuthLink = ctx.child.description.authCredentialsLink;
        Consumer<Operation> onSuccess = (op) -> {
            ctx.childAuth = op.getBody(AuthCredentialsService.AuthCredentialsServiceState.class);
            ctx.stage = next;
            handleAllocation(ctx);
        };
        AdapterUtils.getServiceState(this, childAuthLink, onSuccess, getFailureConsumer(ctx));
    }

    private void getParentAuth(AzureAllocationContext ctx, AzureStages next) {
        String parentAuthLink;
        if (ctx.computeRequest.requestType
                == ComputeInstanceRequest.InstanceRequestType.VALIDATE_CREDENTIALS) {
            parentAuthLink = ctx.computeRequest.authCredentialsLink;
        } else {
            parentAuthLink = ctx.parent.description.authCredentialsLink;
        }
        Consumer<Operation> onSuccess = (op) -> {
            ctx.parentAuth = op.getBody(AuthCredentialsService.AuthCredentialsServiceState.class);
            ctx.stage = next;
            handleAllocation(ctx);
        };
        AdapterUtils.getServiceState(this, parentAuthLink, onSuccess, getFailureConsumer(ctx));
    }

    /*
     * method will be responsible for getting the compute description for the
     * requested resource and then passing to the next step
     */
    private void getVMDescription(AzureAllocationContext ctx, AzureStages next) {
        Consumer<Operation> onSuccess = (op) -> {
            ctx.child = op.getBody(ComputeService.ComputeStateWithDescription.class);
            ctx.vmName = ctx.child.id;
            if (ctx.child.customProperties != null) {
                ctx.vmName = ctx.child.customProperties
                        .getOrDefault(CUSTOM_DISPLAY_NAME, ctx.vmName);
            }
            ctx.stage = next;
            logInfo(ctx.child.id);
            handleAllocation(ctx);
        };
        URI computeUri = UriUtils.extendUriWithQuery(
                ctx.computeRequest.computeReference, UriUtils.URI_PARAM_ODATA_EXPAND,
                Boolean.TRUE.toString());
        AdapterUtils.getServiceState(this, computeUri, onSuccess, getFailureConsumer(ctx));
    }

    /*
     * Method will get the service for the identified link
     */
    private void getParentDescription(AzureAllocationContext ctx, AzureStages next) {
        Consumer<Operation> onSuccess = (op) -> {
            ctx.parent = op.getBody(ComputeService.ComputeStateWithDescription.class);
            ctx.stage = next;
            handleAllocation(ctx);
        };
        URI parentURI = UriUtils.buildExpandLinksQueryUri
                (UriUtils.buildUri(this.getHost(), ctx.child.parentLink));
        AdapterUtils.getServiceState(this, parentURI, onSuccess, getFailureConsumer(ctx));
    }

    private Consumer<Throwable> getFailureConsumer(AzureAllocationContext ctx) {
        return (t) -> {
            ctx.stage = AzureStages.ERROR;
            ctx.error = t;
            handleAllocation(ctx);
        };
    }

    private Retrofit.Builder getRetrofitBuilder() {
        Retrofit.Builder builder = new Retrofit.Builder();
        builder.callbackExecutor(executorService);
        return builder;
    }

    private String generateName(String prefix) {
        return prefix + randomString(5);
    }

    private String randomString(int length) {
        Random random = new Random();
        StringBuilder stringBuilder = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            stringBuilder.append((char) ('a' + random.nextInt(26)));
        }
        return stringBuilder.toString();
    }

    private ResourceManagementClient getResourceManagementClient(AzureAllocationContext ctx) {
        if (ctx.resourceManagementClient == null) {
            ResourceManagementClient client = new ResourceManagementClientImpl(
                    AzureConstants.BASE_URI, ctx.credentials, ctx.clientBuilder,
                    getRetrofitBuilder());
            client.setSubscriptionId(ctx.parentAuth.userLink);
            ctx.resourceManagementClient = client;
        }
        return ctx.resourceManagementClient;
    }

    private NetworkManagementClient getNetworkManagementClient(AzureAllocationContext ctx) {
        if (ctx.networkManagementClient == null) {
            NetworkManagementClient client = new NetworkManagementClientImpl(
                    AzureConstants.BASE_URI, ctx.credentials, ctx.clientBuilder,
                    getRetrofitBuilder());
            client.setSubscriptionId(ctx.parentAuth.userLink);
            ctx.networkManagementClient = client;
        }
        return ctx.networkManagementClient;
    }

    private StorageManagementClient getStorageManagementClient(AzureAllocationContext ctx) {
        if (ctx.storageManagementClient == null) {
            StorageManagementClient client = new StorageManagementClientImpl(
                    AzureConstants.BASE_URI, ctx.credentials, ctx.clientBuilder,
                    getRetrofitBuilder());
            client.setSubscriptionId(ctx.parentAuth.userLink);
            ctx.storageManagementClient = client;
        }
        return ctx.storageManagementClient;
    }

    /**
     * Method will retrieve disks for targeted image
     */
    private void getVMDisks(AzureAllocationContext ctx) {
        if (ctx.child.diskLinks == null || ctx.child.diskLinks.size() == 0) {
            ctx.error = new IllegalStateException("a minimum of 1 disk is required");
            ctx.stage = AzureStages.ERROR;
            handleAllocation(ctx);
            return;
        }
        Collection<Operation> operations = new ArrayList<>();
        // iterate thru disks and create operations
        operations.addAll(ctx.child.diskLinks.stream()
                .map(disk -> Operation.createGet(UriUtils.buildUri(this.getHost(), disk)))
                .collect(Collectors.toList()));

        OperationJoin operationJoin = OperationJoin.create(operations)
                .setCompletion(
                        (ops, exc) -> {
                            if (exc != null) {
                                ctx.error = new IllegalStateException(
                                        "Error getting disk information");
                                ctx.stage = AzureStages.ERROR;
                                handleAllocation(ctx);
                                return;
                            }

                            ctx.childDisks = new ArrayList<>();
                            for (Operation op : ops.values()) {
                                DiskState disk = op.getBody(DiskState.class);

                                // We treat the first disk in the boot order as the boot disk.
                                if (disk.bootOrder == 1) {
                                    if (ctx.bootDisk != null) {
                                        ctx.error = new IllegalStateException(
                                                "Only 1 boot disk is allowed");
                                        ctx.stage = AzureStages.ERROR;
                                        handleAllocation(ctx);
                                        return;
                                    }

                                    ctx.bootDisk = disk;
                                } else {
                                    ctx.childDisks.add(disk);
                                }
                            }

                            if (ctx.bootDisk == null) {
                                ctx.error = new IllegalStateException("Boot disk is required");
                                ctx.stage = AzureStages.ERROR;
                                handleAllocation(ctx);
                                return;
                            }

                            ctx.stage = AzureStages.INIT_RES_GROUP;
                            handleAllocation(ctx);
                        });
        operationJoin.sendWith(this);
    }

    private void enableMonitoring(AzureAllocationContext ctx) {
        Operation readFile = Operation.createGet(null).setCompletion((o, e) -> {
            if (e != null) {
                handleError(ctx, e);
                return;
            }
            AzureDiagnosticSettings azureDiagnosticSettings = o
                    .getBody(AzureDiagnosticSettings.class);
            String vmName = ctx.vmName;
            String azureInstanceId = ctx.vmId;
            String storageAccountName = ctx.storageAccountName;

            // Replace the resourceId and storageAccount keys with correct values
            azureDiagnosticSettings.getProperties()
                    .getPublicConfiguration()
                    .getDiagnosticMonitorConfiguration()
                    .getMetrics()
                    .setResourceId(azureInstanceId);
            azureDiagnosticSettings.getProperties()
                    .getPublicConfiguration()
                    .setStorageAccount(storageAccountName);

            ApplicationTokenCredentials credentials = ctx.credentials;

            URI uri = UriUtils.extendUriWithQuery(
                    UriUtils.buildUri(UriUtils.buildUri(AzureConstants.BASE_URI_FOR_REST),
                            azureInstanceId, AzureConstants.DIAGNOSTIC_SETTING_ENDPOINT,
                            AzureConstants.DIAGNOSTIC_SETTING_AGENT),
                    AzureConstants.QUERY_PARAM_API_VERSION,
                    AzureConstants.DIAGNOSTIC_SETTING_API_VERSION);

            Operation operation = Operation.createPut(uri);
            operation.setBody(azureDiagnosticSettings);
            operation.addRequestHeader(Operation.ACCEPT_HEADER, Operation.MEDIA_TYPE_APPLICATION_JSON);
            operation.addRequestHeader(Operation.CONTENT_TYPE_HEADER, Operation.MEDIA_TYPE_APPLICATION_JSON);
            try {
                operation.addRequestHeader(Operation.AUTHORIZATION_HEADER,
                        AzureConstants.AUTH_HEADER_BEARER_PREFIX + credentials.getToken());
            } catch (Exception ex) {
                this.handleError(ctx, ex);
            }

            logInfo("Enabling monitoring on the VM [%s]", vmName);
            operation.setCompletion((op, er) -> {
                if (er != null) {
                    handleError(ctx, er);
                    return;
                }

                logInfo("Successfully enabled monitoring on the VM [%s]", vmName);
                ctx.stage = AzureStages.GET_STORAGE_KEYS;
                handleAllocation(ctx);
            });
            sendRequest(operation);
        });

        String fileUri = getClass().getResource(AzureConstants.DIAGNOSTIC_SETTINGS_JSON_FILE_NAME)
                .getFile();
        File jsonPayloadFile = new File(fileUri);
        try {
            FileUtils.readFileAndComplete(readFile, jsonPayloadFile);
        } catch (Exception e) {
            handleError(ctx, e);
        }
    }

    /**
     * Operation context aware service callback handler.
     */
    private abstract class AzureAsyncCallback<T> extends ServiceCallback<T> {
        OperationContext opContext;

        public AzureAsyncCallback() {
            opContext = OperationContext.getOperationContext();
        }

        /**
         * Invoked when a failure happens during service call.
         */
        abstract void onError(Throwable e);

        /**
         * Invoked when a service call is successful.
         */
        abstract void onSuccess(ServiceResponse<T> result);

        @Override
        public void failure(Throwable t) {
            OperationContext.restoreOperationContext(opContext);
            onError(t);
        }

        @Override
        public void success(ServiceResponse<T> result) {
            OperationContext.restoreOperationContext(opContext);
            onSuccess(result);
        }
    }
}
