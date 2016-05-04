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

    //Azure Linux Security Group constants
    public static final int AZURE_LINUX_SECURITY_GROUP_PRIORITY = 1000;
    public static final String AZURE_LINUX_SECURITY_GROUP_NAME = "default-allow-ssh";
    public static final String AZURE_LINUX_SECURITY_GROUP_PROTOCOL = "TCP";
    public static final String AZURE_LINUX_SECURITY_GROUP_DESCRIPTION = "Allow SSH (TCP/22)";
    public static final String AZURE_LINUX_SECURITY_GROUP_DIRECTION = "Inbound";
    public static final String AZURE_LINUX_SECURITY_GROUP_ACCESS = "Allow";
    public static final String AZURE_LINUX_SECURITY_GROUP_SOURCE_ADDRESS_PREFIX = "*";
    public static final String AZURE_LINUX_SECURITY_GROUP_SOURCE_PORT_RANGE = "*";
    public static final String AZURE_LINUX_SECURITY_GROUP_DESTINATION_ADDRESS_PREFIX = "*";
    public static final String AZURE_LINUX_SECURITY_GROUP_DESTINATION_PORT_RANGE = "22";

    //Azure Windows Security Group constants
    public static final int AZURE_WINDOWS_SECURITY_GROUP_PRIORITY = 1001;
    public static final String AZURE_WINDOWS_SECURITY_GROUP_NAME = "default-allow-rdp";
    public static final String AZURE_WINDOWS_SECURITY_GROUP_PROTOCOL = "TCP";
    public static final String AZURE_WINDOWS_SECURITY_GROUP_DESCRIPTION = "Allow RDP (TCP/3389)";
    public static final String AZURE_WINDOWS_SECURITY_GROUP_DIRECTION = "Inbound";
    public static final String AZURE_WINDOWS_SECURITY_GROUP_ACCESS = "Allow";
    public static final String AZURE_WINDOWS_SECURITY_GROUP_SOURCE_ADDRESS_PREFIX = "*";
    public static final String AZURE_WINDOWS_SECURITY_GROUP_SOURCE_PORT_RANGE = "*";
    public static final String AZURE_WINDOWS_SECURITY_GROUP_DESTINATION_ADDRESS_PREFIX = "*";
    public static final String AZURE_WINDOWS_SECURITY_GROUP_DESTINATION_PORT_RANGE = "3389";

    // Monitoring Constants
    public static final String DIAGNOSTIC_SETTINGS_JSON_FILE_NAME = "diagnosticSettings.json";
    public static final String BASE_URI_FOR_REST = "https://management.azure.com";
    public static final String DIAGNOSTIC_SETTING_AGENT = "agent";
    public static final String DIAGNOSTIC_SETTING_API_VERSION = "2014-04-01";
    public static final String DIAGNOSTIC_SETTING_ENDPOINT = "diagnosticSettings";

    // Stats Constants
    public static final String QUERY_PARAM_API_VERSION = "api-version";
    public static final String QUERY_PARAM_FILTER = "$filter";
    public static final String AUTH_HEADER_BEARER_PREFIX = "Bearer ";
    public static final String METRIC_DEFINITIONS_ENDPOINT = "metricDefinitions";
    public static final String METRIC_DEFINITIONS_MEMORY_FILTER = "name.value eq '\\Memory\\AvailableMemory'";
    public static final String METRIC_TIME_GRAIN_1_MINUTE = "PT1M";
    public static final int METRIC_COLLECTION_PERIOD = 5;
    public static final String METRIC_TIME_FORMAT = "yyyy-MM-dd'T'HH:mm:ssZ";
    public static final String METRIC_KEY_LAST = "Last";
    public static final String METRIC_KEY_MAXIMUM = "Maximum";
    public static final String METRIC_KEY_MINIMUM = "Minimum";
    public static final String METRIC_KEY_COUNTER_NAME = "CounterName";
    public static final String METRIC_KEY_TIMESTAMP = "TIMESTAMP";
    public static final String METRIC_KEY_TOTAL = "Total";
    public static final String METRIC_KEY_AVERAGE = "Average";
    public static final String METRIC_KEY_COUNT = "Count";
}
