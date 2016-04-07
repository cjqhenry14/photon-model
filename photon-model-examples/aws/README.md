# Overview
The examples in this directory can be run directly against an AWS account.

## Instructions
* To create a VM
  * Setup AWS account
    * ./aws/aws-setup.sh -a _ACCESS KEY_ -s _SECRET KEY_
  * Create a VM
    * ./aws/aws-create-vm.sh -c _VM COUNT_

* To remove a VM
  * Use the computeLink obtained from the VM creation step.
    * ./aws/aws-remove-vm.sh _COMPUTE STATE LINK_