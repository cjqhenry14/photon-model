#!/usr/bin/env bash
[ -n "$DEBUG" ] && set -x

export XENONC=${XENONC:-"http://localhost:8000"}
export PHOTON_MODEL_PREFIX=${PHOTON_MODEL_PREFIX:-"examples"}

function photon-model-error() {
  echo "$1" 1>&2
  exit 1
}

# Validate we have the proper environment to run the example scripts
function photon-model-validate() {
  # Test that XENONC env is properly configured.
  if ! xenonc get /resources/compute 1>/dev/null
  then
       photon-model-error "Error validating XENONC at ${XENONC}"
  fi
}

function photon-model-validate-document-link() {
  xenonc get "$1" 1>/dev/null 2>/dev/null || photon-model-error "invalid document link: $1"
}

# Build a set of xenonc QuerySpecification flags to query for compute resources.
function photon-model-query-compute-resource() {
  if [ "$#" -ne "3" ] && [ "$#" -ne "2" ]
  then
    photon-model-error "Usage: $FUNCNAME option resource-pool-link [compute-description-link]"
  fi

  option="$1"
  resourcePoolLink="$2"
  descriptionLink="$3"

  cat <<EOF
     --${option}.query.occurance=MUST_OCCUR
     --${option}.query.booleanClauses[0].occurance=MUST_OCCUR
     --${option}.query.booleanClauses[0].term.propertyName=documentKind
     --${option}.query.booleanClauses[0].term.matchValue=com:vmware:photon:controller:model:resources:ComputeService:ComputeState
     --${option}.query.booleanClauses[1].occurance=MUST_OCCUR
     --${option}.query.booleanClauses[1].term.propertyName=resourcePoolLink
     --${option}.query.booleanClauses[1].term.matchValue=${resourcePoolLink}
EOF
  if [ "${descriptionLink}" !=  "" ]
  then
    cat <<EOF
       --${option}.query.booleanClauses[2].occurance=MUST_OCCUR
       --${option}.query.booleanClauses[2].term.propertyName=descriptionLink
       --${option}.query.booleanClauses[2].term.matchValue=${descriptionLink}
EOF
  fi
}

# Build a xenonc resourceQuerySpec to remove a compute resource
function photon-model-remove-compute-resource() {
  if [ "$#" -ne "2" ]
  then
    photon-model-error "Usage: $FUNCNAME option compute-link"
  fi

  option="$1"
  computeLink="$2"

  cat <<EOF
     --${option}.query.occurance=MUST_OCCUR
     --${option}.query.booleanClauses[0].term.propertyName=documentSelfLink
     --${option}.query.booleanClauses[0].term.matchValue=${computeLink}
EOF
}