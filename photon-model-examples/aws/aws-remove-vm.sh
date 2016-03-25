#!/bin/bash -e

# Load helper functions.
source $(dirname $0)/../util/util.sh

photon-model-validate

if [ "$#" -ne "1" ]
then
  photon-model-error "Usage: $(basename $0) compute-link"
fi

computeLink=$1

photon-model-validate-document-link "$computeLink"

# Create resource removal task and output task link
xenonc post "/provisioning/resource-removal-tasks" \
     $(photon-model-remove-compute-resource "resourceQuerySpec" "$computeLink") \
  | \
  jq -r ".documentSelfLink"
