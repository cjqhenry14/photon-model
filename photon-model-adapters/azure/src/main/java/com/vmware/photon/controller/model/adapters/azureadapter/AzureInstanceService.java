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

import static com.vmware.photon.controller.model.adapters.azureadapter.AzureConstants.AZURE_IMAGE_OFFER;
import static com.vmware.photon.controller.model.adapters.azureadapter.AzureConstants.AZURE_IMAGE_PUBLISHER;
import static com.vmware.photon.controller.model.adapters.azureadapter.AzureConstants.AZURE_IMAGE_SKU;
import static com.vmware.photon.controller.model.adapters.azureadapter.AzureConstants.AZURE_IMAGE_VERSION;
import static com.vmware.photon.controller.model.adapters.azureadapter.AzureConstants.AZURE_RESOURCE_GROUP_NAME;
import static com.vmware.photon.controller.model.adapters.azureadapter.AzureConstants.AZURE_VM_ADMIN_PASSWORD;
import static com.vmware.photon.controller.model.adapters.azureadapter.AzureConstants.AZURE_VM_ADMIN_USERNAME;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.function.Consumer;

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
import com.microsoft.azure.management.network.models.PublicIPAddressDnsSettings;
import com.microsoft.azure.management.network.models.Subnet;
import com.microsoft.azure.management.network.models.VirtualNetwork;
import com.microsoft.azure.management.resources.ResourceManagementClient;
import com.microsoft.azure.management.resources.ResourceManagementClientImpl;
import com.microsoft.azure.management.resources.models.ResourceGroup;
import com.microsoft.azure.management.storage.StorageManagementClient;
import com.microsoft.azure.management.storage.StorageManagementClientImpl;
import com.microsoft.azure.management.storage.models.AccountType;
import com.microsoft.azure.management.storage.models.StorageAccount;
import com.microsoft.azure.management.storage.models.StorageAccountCreateParameters;
import com.microsoft.rest.ServiceCallback;
import com.microsoft.rest.ServiceResponse;
import okhttp3.OkHttpClient;
import retrofit2.Retrofit;

import com.vmware.photon.controller.model.adapterapi.ComputeInstanceRequest;
import com.vmware.photon.controller.model.adapters.util.AdapterUtils;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService;
import com.vmware.photon.controller.model.resources.ComputeService;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.services.common.AuthCredentialsService;

/**
 * Adapter to create/delete a VM instance on Azure.
 */
public class AzureInstanceService extends StatelessService {

    public static final String SELF_LINK = AzureUriPaths.AZURE_INSTANCE_SERVICE;

    // TODO: https://jira-hzn.eng.vmware.com/browse/VSYM-322
    // Name prefixes
    public static final String STORAGE_NAME_PREFIX = "storage";
    public static final String SUBNET_NAME_PREFIX = "subnet";
    public static final String NETWORK_NAME_PREFIX = "network";
    public static final String DOMAIN_NAME_PREFIX = "domain";
    public static final String PUBLICIP_NAME_PREFIX = "publicip";
    public static final String NICCONFIG_NAME_PREFIX = "nicconfig";
    public static final String SECGROUP_NAME_PREFIX = "secgroup";
    public static final String NIC_NAME_PREFIX = "nic";
    public static final String COMPUTER_NAME_PREFIX = "azure";
    public static final String OSDISK_NAME_PREFIX = "osdisk";
    public static final String VM_NAME_PREFIX = "vm";

    public static final String NETWORK_ADDRESS_PREFIX = "10.0.0.0/16";
    public static final String DNS_SERVER = "10.1.1.1";
    public static final String SUBNET_ADDRESS_PREFIX = "10.0.0.0/24";
    public static final String PUBLIC_IP_ALLOCATION_METHOD = "Dynamic";
    public static final String PRIVATE_IP_ALLOCATION_METHOD = "Dynamic";

    public static final String DEFAULT_VM_SIZE = "Basic_A0";
    public static final String OS_DISK_CACHING = "None";
    public static final String OS_DISK_CREATION_OPTION = "fromImage";

    public static final String VHD_URI_FORMAT = "https://%s.blob.core.windows.net/javacontainer/osjavawindows.vhd";

    public static final String DEFAULT_IMAGE_PUBLISHER = "Canonical";
    public static final String DEFAULT_IMAGE_OFFER = "UbuntuServer";
    public static final String DEFAULT_IMAGE_SKU = "14.04.3-LTS";
    public static final String DEFAULT_IMAGE_VERSION = "latest";

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
            AdapterUtils.sendPatchToTask(this, ctx.computeRequest.provisioningTaskReference);
            return;
        }
        handleAllocation(ctx);
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
                }
            }
            // now that we have a client lets move onto the next step
            switch (ctx.computeRequest.requestType) {
            case CREATE:
                ctx.stage = AzureStages.INIT_RES_GROUP;
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
        case ERROR:
            if (ctx.computeRequest.provisioningTaskReference != null) {
                AdapterUtils.sendFailurePatchToTask(this,
                        ctx.computeRequest.provisioningTaskReference, ctx.error);
            }
            break;
        default:
            logSevere("Unhandled stage: %s", ctx.stage.toString());
            break;
        }
    }

    private void deleteVM(AzureAllocationContext ctx) {
        if (ctx.computeRequest.isMockRequest) {
            deleteComputeResource(ctx);
            return;
        }

        ResourceManagementClient client = new ResourceManagementClientImpl(AzureConstants.BASE_URI,
                ctx.credentials, new OkHttpClient.Builder(), getRetrofitBuilder());

        client.setSubscriptionId(ctx.parentAuth.userLink);

        String resourceGroupName = ctx.child.description.customProperties
                .get(AZURE_RESOURCE_GROUP_NAME);

        logInfo("Deleting resource group with name [%s]", resourceGroupName);

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
        Operation.CompletionHandler completionHandler = (ox, exc) -> {
            if (exc != null) {
                AdapterUtils.sendFailurePatchToTask(AzureInstanceService.this,
                        ctx.computeRequest.provisioningTaskReference, exc);
                return;
            }
            AdapterUtils.sendPatchToTask(AzureInstanceService.this,
                    ctx.computeRequest.provisioningTaskReference);
        };
        sendRequest(Operation.createDelete(
                UriUtils.buildUri(getHost(), ctx.child.documentSelfLink))
                .setBody(new ServiceDocument())
                .setCompletion(completionHandler)
                .setReferer(getHost().getUri()));
    }

    private void initResourceGroup(AzureAllocationContext ctx) {
        ResourceManagementClient client = new ResourceManagementClientImpl(AzureConstants.BASE_URI,
                ctx.credentials, new OkHttpClient.Builder(), getRetrofitBuilder());

        client.setSubscriptionId(ctx.parentAuth.userLink);

        String resourceGroupName = ctx.child.description.customProperties
                .get(AZURE_RESOURCE_GROUP_NAME);

        logInfo("Creating resource group with name [%s]", resourceGroupName);

        ResourceGroup group = new ResourceGroup();

        group.setLocation(ctx.child.description.zoneId);
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
        storageParameters.setAccountType(AccountType.STANDARD_LRS);

        StorageManagementClient client = new StorageManagementClientImpl(AzureConstants.BASE_URI,
                ctx.credentials, new OkHttpClient.Builder(), getRetrofitBuilder());
        client.setSubscriptionId(ctx.parentAuth.userLink);

        final String storageAccountName = generateName(STORAGE_NAME_PREFIX);

        logInfo("Creating storage account with name [%s]", storageAccountName)
        ;
        client.getStorageAccountsOperations().createAsync(ctx.resourceGroup.getName(),
                storageAccountName, storageParameters,
                new ServiceCallback<StorageAccount>() {
                    @Override
                    public void failure(Throwable e) {
                        logSevere(e);
                        ctx.error = e;
                        ctx.stage = AzureStages.ERROR;
                        handleAllocation(ctx);
                    }

                    @Override
                    public void success(ServiceResponse<StorageAccount> result) {
                        ctx.stage = AzureStages.INIT_NETWORK;
                        ctx.storage = result.getBody();
                        // Storing the account name since the API isn't returning one.
                        ctx.storageAccountName = storageAccountName;
                        logInfo("Successfully created storage account [%s]", storageAccountName);
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
        subnet.setName(generateName(SUBNET_NAME_PREFIX));
        subnet.setAddressPrefix(SUBNET_ADDRESS_PREFIX);
        vnet.getSubnets().add(subnet);

        NetworkManagementClient client = new NetworkManagementClientImpl(AzureConstants.BASE_URI,
                ctx.credentials, new OkHttpClient.Builder(), getRetrofitBuilder());
        client.setSubscriptionId(ctx.parentAuth.userLink);

        String vNetName = generateName(NETWORK_NAME_PREFIX);

        logInfo("Creating virtual network [%s]", vNetName);

        client.getVirtualNetworksOperations().createOrUpdateAsync(
                ctx.resourceGroup.getName(), vNetName, vnet,
                new ServiceCallback<VirtualNetwork>() {
                    @Override
                    public void failure(Throwable e) {
                        logSevere(e);
                        ctx.error = e;
                        ctx.stage = AzureStages.ERROR;
                        handleAllocation(ctx);
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
        publicIPAddress.setDnsSettings(new PublicIPAddressDnsSettings());
        publicIPAddress.getDnsSettings().setDomainNameLabel(generateName(DOMAIN_NAME_PREFIX));

        NetworkManagementClient client = new NetworkManagementClientImpl(AzureConstants.BASE_URI,
                ctx.credentials, new OkHttpClient.Builder(), getRetrofitBuilder());
        client.setSubscriptionId(ctx.parentAuth.userLink);

        String publicIPName = generateName(PUBLICIP_NAME_PREFIX);

        logInfo("Creating public IP with name [%s]", publicIPName);

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
                        logInfo("Successfully created public IP address with name [%s] and FQDN "
                                        + "[%s]",
                                result.getBody().getName(),
                                result.getBody().getDnsSettings().getFqdn());
                        handleAllocation(ctx);
                    }
                });
    }

    private void initSecurityGroup(AzureAllocationContext ctx) {
        NetworkSecurityGroup group = new NetworkSecurityGroup();
        group.setLocation(ctx.resourceGroup.getLocation());

        NetworkManagementClient client = new NetworkManagementClientImpl(AzureConstants.BASE_URI,
                ctx.credentials, new OkHttpClient.Builder(), getRetrofitBuilder());
        client.setSubscriptionId(ctx.parentAuth.userLink);

        String secGroupName = generateName(SECGROUP_NAME_PREFIX);

        logInfo("Creating security group with name [%s]", secGroupName);

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

        NetworkManagementClient client = new NetworkManagementClientImpl(AzureConstants.BASE_URI,
                ctx.credentials, new OkHttpClient.Builder(), getRetrofitBuilder());
        client.setSubscriptionId(ctx.parentAuth.userLink);

        String nicName = generateName(NIC_NAME_PREFIX);

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

        ImageReference imageReference = new ImageReference();
        imageReference.setPublisher(customProperties.getOrDefault(AZURE_IMAGE_PUBLISHER,
                DEFAULT_IMAGE_PUBLISHER));
        imageReference
                .setOffer(customProperties.getOrDefault(AZURE_IMAGE_OFFER, DEFAULT_IMAGE_OFFER));
        imageReference.setSku(customProperties.getOrDefault(AZURE_IMAGE_SKU, DEFAULT_IMAGE_SKU));
        imageReference.setVersion(
                customProperties.getOrDefault(AZURE_IMAGE_VERSION, DEFAULT_IMAGE_VERSION));

        VirtualMachine request = new VirtualMachine();
        request.setLocation(ctx.resourceGroup.getLocation());
        request.setOsProfile(new OSProfile());
        request.getOsProfile().setComputerName(generateName(COMPUTER_NAME_PREFIX));
        request.getOsProfile().setAdminUsername(customProperties.get(AZURE_VM_ADMIN_USERNAME));
        request.getOsProfile().setAdminPassword(customProperties.get(AZURE_VM_ADMIN_PASSWORD));
        request.setHardwareProfile(new HardwareProfile());
        request.getHardwareProfile().setVmSize(DEFAULT_VM_SIZE);
        request.setStorageProfile(new StorageProfile());
        request.getStorageProfile().setImageReference(imageReference);
        request.getStorageProfile().setDataDisks(null);
        request.getStorageProfile().setOsDisk(new OSDisk());
        request.getStorageProfile().getOsDisk().setCaching(OS_DISK_CACHING);
        request.getStorageProfile().getOsDisk().setCreateOption(OS_DISK_CREATION_OPTION);
        request.getStorageProfile().getOsDisk().setName(generateName(OSDISK_NAME_PREFIX));
        request.getStorageProfile().getOsDisk().setVhd(new VirtualHardDisk());
        request.getStorageProfile().getOsDisk().getVhd()
                .setUri(String.format(VHD_URI_FORMAT, ctx.storageAccountName));
        request.setNetworkProfile(new NetworkProfile());
        request.getNetworkProfile().setNetworkInterfaces(new ArrayList<>());
        NetworkInterfaceReference nir = new NetworkInterfaceReference();
        nir.setPrimary(true);
        nir.setId(ctx.nic.getId());
        request.getNetworkProfile().getNetworkInterfaces().add(nir);

        ComputeManagementClient client = new ComputeManagementClientImpl(AzureConstants.BASE_URI,
                ctx.credentials, new OkHttpClient.Builder(), getRetrofitBuilder());
        client.setSubscriptionId(ctx.parentAuth.userLink);

        String vmName = generateName(VM_NAME_PREFIX);

        logInfo("Creating virtual machine with name [%s]", vmName);

        client.getVirtualMachinesOperations().createOrUpdateAsync(
                ctx.resourceGroup.getName(), vmName, request,
                new ServiceCallback<VirtualMachine>() {
                    @Override
                    public void failure(Throwable e) {
                        logSevere(e);
                        ctx.error = e;
                        ctx.stage = AzureStages.ERROR;
                        handleAllocation(ctx);
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
                                AdapterUtils.sendFailurePatchToTask(AzureInstanceService.this,
                                        ctx.computeRequest.provisioningTaskReference,
                                        new IllegalStateException(
                                                "Error updating VM state"));
                                return;
                            }
                            AdapterUtils.sendPatchToTask(AzureInstanceService.this,
                                    ctx.computeRequest.provisioningTaskReference);
                        };

                        sendRequest(
                                Operation.createPatch(ctx.computeRequest.computeReference)
                                        .setBody(resultDesc).setCompletion(completionHandler)
                                        .setReferer(getHost().getUri()));
                    }
                });
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
        builder.callbackExecutor(getHost().allocateExecutor(this));
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
}
