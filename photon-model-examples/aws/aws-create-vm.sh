#!/bin/bash -e

# invoke the aws validate to validate inputs
source aws/validate-create.sh

source aws/.temp-export-data
xenonc -i aws/resource-alloc.yml -- --pool "${resourcePoolLink}" \
                                    --resourceDescriptionLink "${resourceDescriptionLink}" \
                                    --count ${vmCount} | util/poll-task.sh

queryTask=$(xenonc post "/core/query-tasks" \
            $(photon-model-query-compute-resource "querySpec" "$resourcePoolLink" "$computeDescriptionLink") \
            | jq -r ".documentSelfLink")

# Waiting for the query to complete processing
sleep 1s

xenonc get $queryTask | jq -r ".results.documentLinks"
