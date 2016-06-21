
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlSeeAlso;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for InsufficientResourcesFault complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="InsufficientResourcesFault">
 *   &lt;complexContent>
 *     &lt;extension base="{urn:vim25}VimFault">
 *       &lt;sequence>
 *       &lt;/sequence>
 *     &lt;/extension>
 *   &lt;/complexContent>
 * &lt;/complexType>
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "InsufficientResourcesFault")
@XmlSeeAlso({
    InvalidResourcePoolStructureFault.class,
    InsufficientFailoverResourcesFault.class,
    InsufficientGraphicsResourcesFault.class,
    InsufficientStorageSpace.class,
    VmFaultToleranceTooManyVMsOnHost.class,
    VmFaultToleranceTooManyFtVcpusOnHost.class,
    VmSmpFaultToleranceTooManyVMsOnHost.class,
    InsufficientHostCapacityFault.class,
    InsufficientStandbyResource.class,
    InsufficientVFlashResourcesFault.class,
    InsufficientNetworkCapacity.class,
    NumVirtualCpusExceedsLimit.class,
    InsufficientAgentVmsDeployed.class,
    InsufficientCpuResourcesFault.class,
    InsufficientNetworkResourcePoolCapacity.class,
    InsufficientMemoryResourcesFault.class
})
public class InsufficientResourcesFault
    extends VimFault
{


}
