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

/**
 * Azure related constants.
 */
public class AzureConstants {
    public static final String BASE_URI = "https://management.azure.com/";
    public static final String AZURE_TENANT_ID = "azureTenantId";
    public static final String AZURE_RESOURCE_GROUP_NAME = "azureResourceGroupName";
    public static final String AZURE_VM_ADMIN_USERNAME = "azureVMAdminName";
    public static final String AZURE_VM_ADMIN_PASSWORD = "azureVMAdminPassword";
    public static final String AZURE_INSTANCE_ID = "azureInstanceId";
    public static final String AZURE_IMAGE_PUBLISHER = "azureImagePublisher";
    public static final String AZURE_IMAGE_OFFER = "azureImageOffer";
    public static final String AZURE_IMAGE_SKU = "azureImageSKU";
    public static final String AZURE_IMAGE_VERSION = "azureImageVersion";
    public static final String AZURE_OSDISK_CACHING = "azureOsDiskCaching";
    public static final String AZURE_STORAGE_ACCOUNT_TYPE = "azureStorageAccountType";
    public static final String AZURE_STORAGE_ACCOUNT_NAME = "azureStorageAccountName";
    public static final String AZURE_STORAGE_ACCOUNT_KEY1 = "azureStorageAccountKey1";
    public static final String AZURE_STORAGE_ACCOUNT_KEY2 = "azureStorageAccountKey2";
    public static final String AZURE_VM_SIZE = "azureVMSize";

    // Azure Namespace
    public static final String COMPUTE_NAMESPACE = "Microsoft.Compute";
    public static final String STORAGE_NAMESPACE = "Microsoft.Storage";
    public static final String NETWORK_NAMESPACE = "Microsoft.Network";

    // Azure error code
    public static final String MISSING_SUBSCRIPTION_CODE = "MissingSubscriptionRegistration";

    // Azure constants
    public static final String PROVIDER_REGISTRED_STATE = "REGISTERED";

}
