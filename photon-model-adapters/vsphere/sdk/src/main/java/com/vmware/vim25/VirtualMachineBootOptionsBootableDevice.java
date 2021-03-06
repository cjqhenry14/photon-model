
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlSeeAlso;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for VirtualMachineBootOptionsBootableDevice complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="VirtualMachineBootOptionsBootableDevice">
 *   &lt;complexContent>
 *     &lt;extension base="{urn:vim25}DynamicData">
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
@XmlType(name = "VirtualMachineBootOptionsBootableDevice")
@XmlSeeAlso({
    VirtualMachineBootOptionsBootableFloppyDevice.class,
    VirtualMachineBootOptionsBootableCdromDevice.class,
    VirtualMachineBootOptionsBootableEthernetDevice.class,
    VirtualMachineBootOptionsBootableDiskDevice.class
})
public class VirtualMachineBootOptionsBootableDevice
    extends DynamicData
{


}
