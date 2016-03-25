#!/bin/bash -e

# Load helper functions.
source ./util/util.sh

function usage() {
  photon-model-error "Usage: $0 [-c VM_COUNT]"
}

while getopts "c:" flag
do
  case $flag in
    c)
      vmCount="$OPTARG"
      ;;
    *)
    usage
    ;;
  esac
done
