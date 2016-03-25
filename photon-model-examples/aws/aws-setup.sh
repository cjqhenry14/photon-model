#!/bin/bash -e

cd "$(dirname "$0")/../"

# invoke the aws validate to validate inputs
source aws/validate-setup.sh

# Create resource pool
resourcePoolLink=$(xenonc -i resource/pool.yml -- --id "AWS")

# Create credentials
awsCredentials=$(xenonc -i aws/host_credentials.yml -- \
     --accessKey "${accessKey}" \
     --secretKey "${secretKey}" )

# Create description for the aws host (parent)
parentDesc=$(xenonc -i aws/parent-description.yml -- --credentialsLink "${awsCredentials}")

# Create the aws parent resource
xenonc -i aws/parent-compute.yml -- --descLink "${parentDesc}" --resourcePoolLink "${resourcePoolLink}" 1>/dev/null

userCredentialsLink=$(xenonc -i aws/user_credentials.yml)

# Create boot (AMI) disk description
rootDiskLink=$(xenonc -i aws/boot-disk.yml)

# Create description for the aws instances
computeDescriptionLink=$(xenonc -i aws/description.yml -- --credentialsLink "${userCredentialsLink}")

# Create a Resource description
resourceDescriptionLink=$(xenonc -i aws/resource-description.yml -- \
                          --rootDiskLink "${rootDiskLink}" \
                          --computeDescriptionLink "${computeDescriptionLink}")

rm aws/.temp-export-data 2> /dev/null
touch aws/.temp-export-data
echo "export resourcePoolLink="${resourcePoolLink}"" >> aws/.temp-export-data
echo "export computeDescriptionLink="${computeDescriptionLink}"" >> aws/.temp-export-data
echo "export resourceDescriptionLink="${resourceDescriptionLink}"" >> aws/.temp-export-data
