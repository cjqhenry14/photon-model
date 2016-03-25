#!/bin/bash -e

# Load helper functions.
source ./util/util.sh

photon-model-validate

function usage() {
  photon-model-error "Usage: $0 [-f CREDENTIALS_FILE] [-a ACCESS_KEY][-s SECRET_KEY]"
}

while getopts "s:f:a:" flag
do
  case $flag in
# the file containing the credentials body
    f)
      credentials_file="$OPTARG"
      ;;
# access key for the credentials
    a)
      accessKey="$OPTARG"
      ;;
# secret key for the credentials
    s)
      secretKey="$OPTARG"
      ;;
    *)
    usage
    ;;
  esac
done
