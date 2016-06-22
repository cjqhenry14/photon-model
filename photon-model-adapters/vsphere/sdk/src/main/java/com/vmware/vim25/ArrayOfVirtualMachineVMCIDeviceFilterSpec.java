
package com.vmware.vim25;

import java.util.ArrayList;
import java.util.List;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for ArrayOfVirtualMachineVMCIDeviceFilterSpec complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="ArrayOfVirtualMachineVMCIDeviceFilterSpec">
 *   &lt;complexContent>
 *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType">
 *       &lt;sequence>
 *         &lt;element name="VirtualMachineVMCIDeviceFilterSpec" type="{urn:vim25}VirtualMachineVMCIDeviceFilterSpec" maxOccurs="unbounded" minOccurs="0"/>
 *       &lt;/sequence>
 *     &lt;/restriction>
 *   &lt;/complexContent>
 * &lt;/complexType>
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "ArrayOfVirtualMachineVMCIDeviceFilterSpec", propOrder = {
    "virtualMachineVMCIDeviceFilterSpec"
})
public class ArrayOfVirtualMachineVMCIDeviceFilterSpec {

    @XmlElement(name = "VirtualMachineVMCIDeviceFilterSpec")
    protected List<VirtualMachineVMCIDeviceFilterSpec> virtualMachineVMCIDeviceFilterSpec;

    /**
     * Gets the value of the virtualMachineVMCIDeviceFilterSpec property.
     * 
     * <p>
     * This accessor method returns a reference to the live list,
     * not a snapshot. Therefore any modification you make to the
     * returned list will be present inside the JAXB object.
     * This is why there is not a <CODE>set</CODE> method for the virtualMachineVMCIDeviceFilterSpec property.
     * 
     * <p>
     * For example, to add a new item, do as follows:
     * <pre>
     *    getVirtualMachineVMCIDeviceFilterSpec().add(newItem);
     * </pre>
     * 
     * 
     * <p>
     * Objects of the following type(s) are allowed in the list
     * {@link VirtualMachineVMCIDeviceFilterSpec }
     * 
     * 
     */
    public List<VirtualMachineVMCIDeviceFilterSpec> getVirtualMachineVMCIDeviceFilterSpec() {
        if (virtualMachineVMCIDeviceFilterSpec == null) {
            virtualMachineVMCIDeviceFilterSpec = new ArrayList<VirtualMachineVMCIDeviceFilterSpec>();
        }
        return this.virtualMachineVMCIDeviceFilterSpec;
    }

}
