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

import static com.vmware.photon.controller.model.adapters.azureadapter.AzureConstants.AZURE_OSDISK_CACHING;
import static com.vmware.photon.controller.model.adapters.azureadapter.AzureConstants.AZURE_STORAGE_ACCOUNT_KEY1;
import static com.vmware.photon.controller.model.adapters.azureadapter.AzureConstants.AZURE_STORAGE_ACCOUNT_KEY2;
import static com.vmware.photon.controller.model.adapters.azureadapter.AzureConstants.AZURE_STORAGE_ACCOUNT_NAME;
import static com.vmware.photon.controller.model.adapters.azureadapter.AzureConstants.AZURE_STORAGE_ACCOUNT_TYPE;
import static com.vmware.photon.controller.model.adapters.azureadapter.AzureConstants.AZURE_VM_ADMIN_PASSWORD;
import static com.vmware.photon.controller.model.adapters.azureadapter.AzureConstants.AZURE_VM_ADMIN_USERNAME;
import static com.vmware.photon.controller.model.adapters.azureadapter.AzureConstants.AZURE_VM_SIZE;
import static com.vmware.photon.controller.model.adapters.azureadapter.AzureConstants.COMPUTE_NAMESPACE;
import static com.vmware.photon.controller.model.adapters.azureadapter.AzureConstants.MISSING_SUBSCRIPTION_CODE;
import static com.vmware.photon.controller.model.adapters.azureadapter.AzureConstants.NETWORK_NAMESPACE;
import static com.vmware.photon.controller.model.adapters.azureadapter.AzureConstants.PROVIDER_REGISTRED_STATE;
import static com.vmware.photon.controller.model.adapters.azureadapter.AzureConstants.STORAGE_NAMESPACE;

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
import com.microsoft.azure.credentials.AzureEnvironment;
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
import com.microsoft.azure.management.network.models.DhcpOptions;
import com.microsoft.azure.management.network.models.NetworkInterface;
import com.microsoft.azure.management.network.models.NetworkInterfaceIPConfiguration;
import com.microsoft.azure.management.network.models.NetworkSecurityGroup;
import com.microsoft.azure.management.network.models.PublicIPAddress;
import com.microsoft.azure.management.network.models.Subnet;
import com.microsoft.azure.management.network.models.VirtualNetwork;
import com.microsoft.azure.management.resources.ResourceManagementClient;
import com.microsoft.azure.management.resources.ResourceManagementClientImpl;
import com.microsoft.azure.management.resources.models.Provider;
import com.microsoft.azure.management.resources.models.ResourceGroup;
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
import com.vmware.photon.controller.model.adapters.util.AdapterUtils;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService;
import com.vmware.photon.controller.model.resources.ComputeService;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeStateWithDescription;
import com.vmware.photon.controller.model.resources.DiskService.DiskState;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Operation.CompletionHandler;
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

    public static final String SELF_LINK = AzureUriPaths.AZURE_INSTANCE_SERVICE;

    // TODO VSYM-322: Remove unused default properties from AzureInstanceService
    // Name prefixes
    private static final String NICCONFIG_NAME_PREFIX = "nicconfig";

    private static final String SUBNET_NAME = "default";
    private static final String NETWORK_ADDRESS_PREFIX = "10.0.0.0/16";
    private static final String DNS_SERVER = "10.1.1.1";
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
    private static final int EXECUTOR_SHUTDOWN_INTERVAL_MINUTES = 5;

    private ExecutorService executorService;

    @Override
    public void handleStart(Operation startPost) {
        executorService = getHost().allocateExecutor(this);

        super.handleStart(startPost);
    }

    @Override
    public void handleStop(Operation delete) {
        executorService.shutdown();
        awaitTermination(executorService);
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
                ctx.stage = AzureStages.VMDISKS;
                handleAllocation(ctx);
                break;
            case DELETE:
                ctx.stage = AzureStages.DELETE;
                handleAllocation(ctx);
                break;
            default:
                ctx.error = new Exception("Unknown Azure provisioning stage");
                ctx.stage = AzureStages.ERROR;
                handleAllocation(ctx);
            }
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
            cleanUpHttpClient(ctx);
            break;
        case FINISHED:
            cleanUpHttpClient(ctx);
            break;
        default:
            logSevere("Unhandled stage: %s", ctx.stage.toString());
            cleanUpHttpClient(ctx);
            break;
        }
    }

    private void deleteVM(AzureAllocationContext ctx) {
        if (ctx.computeRequest.isMockRequest) {
            deleteComputeResource(ctx);
            return;
        }

        String resourceGroupName = ctx.child.id;

        if (resourceGroupName == null || resourceGroupName.isEmpty()) {
            throw new IllegalArgumentException("Resource group name is required");
        }

        logInfo("Deleting resource group with name [%s]", resourceGroupName);

        ResourceManagementClient client = getResourceManagementClient(ctx);

        client.getResourceGroupsOperations().deleteAsync(resourceGroupName,
                new ServiceCallback<Void>() {
                    @Override
                    public void failure(Throwable e) {
                        logSevere(e);
                        ctx.error = e;
                        ctx.stage = AzureStages.ERROR;
                        handleAllocation(ctx);
                    }

                    @Override
                    public void success(ServiceResponse<Void> result) {
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
        String resourceGroupName = ctx.child.id;

        if (resourceGroupName == null || resourceGroupName.isEmpty()) {
            throw new IllegalArgumentException("Resource group name is required");
        }

        logInfo("Creating resource group with name [%s]", resourceGroupName);

        ResourceGroup group = new ResourceGroup();
        group.setLocation(ctx.child.description.regionId);

        ResourceManagementClient client = getResourceManagementClient(ctx);

        client.getResourceGroupsOperations().createOrUpdateAsync(resourceGroupName, group,
                new ServiceCallback<ResourceGroup>() {
                    @Override
                    public void failure(Throwable e) {
                        logSevere(e);
                        ctx.error = e;
                        ctx.stage = AzureStages.ERROR;
                        handleAllocation(ctx);
                    }

                    @Override
                    public void success(ServiceResponse<ResourceGroup> result) {
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

        client.getStorageAccountsOperations().createAsync(ctx.resourceGroup.getName(),
                ctx.storageAccountName, storageParameters,
                new ServiceCallback<StorageAccount>() {
                    @Override
                    public void failure(Throwable e) {
                        handleSubscriptionError(ctx, STORAGE_NAMESPACE, e);
                    }

                    @Override
                    public void success(ServiceResponse<StorageAccount> result) {
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
        vnet.setDhcpOptions(new DhcpOptions());
        vnet.getDhcpOptions().setDnsServers(new ArrayList<>());
        vnet.getDhcpOptions().getDnsServers().add(DNS_SERVER);
        vnet.setSubnets(new ArrayList<>());

        Subnet subnet = new Subnet();
        subnet.setName(SUBNET_NAME);
        subnet.setAddressPrefix(SUBNET_ADDRESS_PREFIX);
        vnet.getSubnets().add(subnet);

        String vNetName = ctx.child.id;

        logInfo("Creating virtual network [%s]", vNetName);

        NetworkManagementClient client = getNetworkManagementClient(ctx);

        client.getVirtualNetworksOperations().createOrUpdateAsync(
                ctx.resourceGroup.getName(), vNetName, vnet,
                new ServiceCallback<VirtualNetwork>() {
                    @Override
                    public void failure(Throwable e) {
                        handleSubscriptionError(ctx, NETWORK_NAMESPACE, e);
                    }

                    @Override
                    public void success(ServiceResponse<VirtualNetwork> result) {
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

        String publicIPName = ctx.child.id;

        logInfo("Creating public IP with name [%s]", publicIPName);

        NetworkManagementClient client = getNetworkManagementClient(ctx);

        client.getPublicIPAddressesOperations().createOrUpdateAsync(
                ctx.resourceGroup.getName(), publicIPName, publicIPAddress,
                new ServiceCallback<PublicIPAddress>() {
                    @Override
                    public void failure(Throwable e) {
                        logSevere(e);
                        ctx.error = e;
                        ctx.stage = AzureStages.ERROR;
                        handleAllocation(ctx);
                    }

                    @Override
                    public void success(ServiceResponse<PublicIPAddress> result) {
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

        String secGroupName = ctx.child.id;

        logInfo("Creating security group with name [%s]", secGroupName);

        NetworkManagementClient client = getNetworkManagementClient(ctx);

        client.getNetworkSecurityGroupsOperations().createOrUpdateAsync(
                ctx.resourceGroup.getName(), secGroupName, group,
                new ServiceCallback<NetworkSecurityGroup>() {
                    @Override
                    public void failure(Throwable e) {
                        logSevere(e);
                        ctx.error = e;
                        ctx.stage = AzureStages.ERROR;
                        handleAllocation(ctx);
                    }

                    @Override
                    public void success(ServiceResponse<NetworkSecurityGroup> result) {
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

        String nicName = generateName(ctx.child.id);

        NetworkManagementClient client = getNetworkManagementClient(ctx);

        client.getNetworkInterfacesOperations().createOrUpdateAsync(
                ctx.resourceGroup.getName(), nicName, nic,
                new ServiceCallback<NetworkInterface>() {
                    @Override
                    public void failure(Throwable e) {
                        logSevere(e);
                        ctx.error = e;
                        ctx.stage = AzureStages.ERROR;
                        handleAllocation(ctx);
                    }

                    @Override
                    public void success(ServiceResponse<NetworkInterface> result) {
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
        Map<String, String> stateCustomProperties = ctx.child.customProperties;

        DiskState bootDisk = ctx.bootDisk;
        if (bootDisk == null) {
            handleError(ctx, new IllegalStateException("Azure bootDisk not specified"));
            return;
        }

        URI imageId = bootDisk.sourceImageReference;
        if (imageId == null) {
            ctx.error = new IllegalStateException("Azure image reference not specified");
            ctx.stage = AzureStages.ERROR;
            handleAllocation(ctx);
            return;
        }

        VirtualMachine request = new VirtualMachine();
        request.setLocation(ctx.resourceGroup.getLocation());

        // Set OS profile.
        OSProfile osProfile = new OSProfile();
        String vmName = ctx.child.id;
        osProfile.setComputerName(vmName);
        osProfile.setAdminUsername(stateCustomProperties.get(AZURE_VM_ADMIN_USERNAME));
        osProfile.setAdminPassword(stateCustomProperties.get(AZURE_VM_ADMIN_PASSWORD));
        request.setOsProfile(osProfile);

        // Set hardware profile.
        HardwareProfile hardwareProfile = new HardwareProfile();
        hardwareProfile.setVmSize(customProperties.getOrDefault(AZURE_VM_SIZE, DEFAULT_VM_SIZE));
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
                new ServiceCallback<VirtualMachine>() {
                    @Override
                    public void failure(Throwable e) {
                        handleSubscriptionError(ctx, COMPUTE_NAMESPACE, e);
                    }

                    @Override
                    public void success(ServiceResponse<VirtualMachine> result) {
                        VirtualMachine vm = result.getBody();
                        logInfo("Successfully created vm [%s]", vm.getName());

                        ComputeService.ComputeStateWithDescription resultDesc = new
                                ComputeService.ComputeStateWithDescription();
                        if (ctx.child.customProperties == null) {
                            resultDesc.customProperties = new HashMap<>();
                        } else {
                            resultDesc.customProperties = ctx.child.customProperties;
                        }
                        resultDesc.customProperties
                                .put(AzureConstants.AZURE_INSTANCE_ID, vm.getId());

                        Operation.CompletionHandler completionHandler = (ox,
                                exc) -> {
                            if (exc != null) {
                                logSevere(exc);
                                ctx.stage = AzureStages.ERROR;
                                ctx.error = exc;
                                handleAllocation(ctx);
                                return;
                            }
                            ctx.stage = AzureStages.GET_STORAGE_KEYS;
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
                ctx.storageAccountName, new ServiceCallback<StorageAccountKeys>() {
                    @Override public void failure(Throwable e) {
                        handleError(ctx, e);
                    }

                    @Override
                    public void success(ServiceResponse<StorageAccountKeys> result) {
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

                                                    AdapterUtils.sendPatchToProvisioningTask(
                                                            AzureInstanceService.this,
                                                            ctx.computeRequest.provisioningTaskReference);
                                                    ctx.stage = AzureStages.FINISHED;
                                                    handleAllocation(ctx);
                                                    return;

                                                }));
                                        sendRequest(patch);
                                        return;
                                    }

                                    AdapterUtils
                                            .sendPatchToProvisioningTask(AzureInstanceService.this,
                                                    ctx.computeRequest.provisioningTaskReference);
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
        client.getProvidersOperations().registerAsync(namespace, new ServiceCallback<Provider>() {
            @Override
            public void failure(Throwable e) {
                logSevere(e);
                ctx.error = e;
                ctx.stage = AzureStages.ERROR;
                handleAllocation(ctx);
            }

            @Override
            public void success(ServiceResponse<Provider> result) {
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
                        new ServiceCallback<Provider>() {
                            @Override
                            public void failure(Throwable e) {
                                logSevere(e);
                                ctx.error = e;
                                ctx.stage = AzureStages.ERROR;
                                handleAllocation(ctx);
                            }

                            @Override
                            public void success(ServiceResponse<Provider> result) {
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

    private void getParentAuth(AzureAllocationContext ctx, AzureStages next) {
        String parentAuthLink;
        if (ctx.computeRequest.requestType
                == ComputeInstanceRequest.InstanceRequestType.VALIDATE_CREDENTIALS) {
            parentAuthLink = ctx.computeRequest.authCredentialsLink;
        } else {
            parentAuthLink = ctx.parent.description.authCredentialsLink;
        }
        URI authUri = UriUtils.buildUri(this.getHost(), parentAuthLink);
        Consumer<Operation> onSuccess = (op) -> {
            ctx.parentAuth = op.getBody(AuthCredentialsService.AuthCredentialsServiceState.class);
            ctx.stage = next;
            handleAllocation(ctx);
        };
        AdapterUtils.getServiceState(this, authUri, onSuccess, getFailureConsumer(ctx));
    }

    /*
     * method will be responsible for getting the compute description for the
     * requested resource and then passing to the next step
     */
    private void getVMDescription(AzureAllocationContext ctx, AzureStages next) {
        Consumer<Operation> onSuccess = (op) -> {
            ctx.child = op.getBody(ComputeService.ComputeStateWithDescription.class);
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

    /**
     * Configures authentication credential for Azure.
     */
    private ApplicationTokenCredentials getAzureConfig(
            AuthCredentialsService.AuthCredentialsServiceState parentAuth) throws Exception {

        String clientId = parentAuth.privateKeyId;
        String clientKey = parentAuth.privateKey;
        String tenantId = parentAuth.customProperties.get(AzureConstants.AZURE_TENANT_ID);

        return new ApplicationTokenCredentials(clientId, tenantId, clientKey,
                AzureEnvironment.AZURE);
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

    private void cleanUpHttpClient(AzureAllocationContext ctx) {
        if (ctx.httpClient == null) {
            return;
        }

        ctx.httpClient.connectionPool().evictAll();
        ExecutorService httpClientExecutor = ctx.httpClient.dispatcher().executorService();
        httpClientExecutor.shutdown();

        awaitTermination(httpClientExecutor);
    }

    private void awaitTermination(ExecutorService executor) {
        try {
            if (!executor.awaitTermination(EXECUTOR_SHUTDOWN_INTERVAL_MINUTES, TimeUnit.MINUTES)) {
                logWarning(
                        "Executor service can't be shutdown for Azure. Trying to shutdown now...");
                executor.shutdownNow();
            }
            logFine("Executor service shutdown for Azure");
        } catch (InterruptedException e) {
            logSevere(e);
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            logSevere(e);
        }
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
}
