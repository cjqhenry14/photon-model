# Overview
The examples in this directory can be run directly against an AWS account.

## Instructions
* To create a VM
  * Setup AWS account
    * ./aws/aws-setup.sh -a <ACCESS_KEY> -s <SECRET_KEY>
  * Create a VM
    * ./aws/aws-create-vm.sh -c <VM_COUNT>

* To remove a VM
  * Use the computeLink obtained from the VM creation step.
  	* ./aws/aws-remove-vm.sh <COMPUTE_STATE_LINK>