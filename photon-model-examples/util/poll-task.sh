#!/bin/bash -e

# Load helper functions.
source $(dirname $0)/util.sh

if [ "$#" -eq 0 ]
then
  read taskLink
else
  taskLink=$1
fi

started=0

while true; do
  state=$(xenonc get $taskLink)
  stage=$(jq -r .taskInfo.stage <<<"$state")

  case "$stage" in
    null)
      photon-model-error "invalid task link"
      ;;
    CREATED)
      echo "${stage}"
      ;;
    STARTED)
      if [ "$started" -eq "0" ]
      then
        started=1
        echo -n "STARTED."
      else
        echo -n "."
      fi
      sleep 1
      ;;
    FINISHED|CANCELLED)
      echo "${stage}"
      break
      ;;
    FAILED)
      echo "${stage}"
      message=$(jq -r .taskInfo.failure.message <<<"$state")
      photon-model-error "$message"
      exit 1
      ;;
  esac
done
