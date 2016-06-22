
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for VirtualMachineVMCIDevice complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="VirtualMachineVMCIDevice">
 *   &lt;complexContent>
 *     &lt;extension base="{urn:vim25}VirtualDevice">
 *       &lt;sequence>
 *         &lt;element name="id" type="{http://www.w3.org/2001/XMLSchema}long" minOccurs="0"/>
 *         &lt;element name="allowUnrestrictedCommunication" type="{http://www.w3.org/2001/XMLSchema}boolean" minOccurs="0"/>
 *         &lt;element name="filterEnable" type="{http://www.w3.org/2001/XMLSchema}boolean" minOccurs="0"/>
 *         &lt;element name="filterInfo" type="{urn:vim25}VirtualMachineVMCIDeviceFilterInfo" minOccurs="0"/>
 *       &lt;/sequence>
 *     &lt;/extension>
 *   &lt;/complexContent>
 * &lt;/complexType>
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "VirtualMachineVMCIDevice", propOrder = {
    "id",
    "allowUnrestrictedCommunication",
    "filterEnable",
    "filterInfo"
})
public class VirtualMachineVMCIDevice
    extends VirtualDevice
{

    protected Long id;
    protected Boolean allowUnrestrictedCommunication;
    protected Boolean filterEnable;
    protected VirtualMachineVMCIDeviceFilterInfo filterInfo;

    /**
     * Gets the value of the id property.
     * 
     * @return
     *     possible object is
     *     {@link Long }
     *     
     */
    public Long getId() {
        return id;
    }

    /**
     * Sets the value of the id property.
     * 
     * @param value
     *     allowed object is
     *     {@link Long }
     *     
     */
    public void setId(Long value) {
        this.id = value;
    }

    /**
     * Gets the value of the allowUnrestrictedCommunication property.
     * 
     * @return
     *     possible object is
     *     {@link Boolean }
     *     
     */
    public Boolean isAllowUnrestrictedCommunication() {
        return allowUnrestrictedCommunication;
    }

    /**
     * Sets the value of the allowUnrestrictedCommunication property.
     * 
     * @param value
     *     allowed object is
     *     {@link Boolean }
     *     
     */
    public void setAllowUnrestrictedCommunication(Boolean value) {
        this.allowUnrestrictedCommunication = value;
    }

    /**
     * Gets the value of the filterEnable property.
     * 
     * @return
     *     possible object is
     *     {@link Boolean }
     *     
     */
    public Boolean isFilterEnable() {
        return filterEnable;
    }

    /**
     * Sets the value of the filterEnable property.
     * 
     * @param value
     *     allowed object is
     *     {@link Boolean }
     *     
     */
    public void setFilterEnable(Boolean value) {
        this.filterEnable = value;
    }

    /**
     * Gets the value of the filterInfo property.
     * 
     * @return
     *     possible object is
     *     {@link VirtualMachineVMCIDeviceFilterInfo }
     *     
     */
    public VirtualMachineVMCIDeviceFilterInfo getFilterInfo() {
        return filterInfo;
    }

    /**
     * Sets the value of the filterInfo property.
     * 
     * @param value
     *     allowed object is
     *     {@link VirtualMachineVMCIDeviceFilterInfo }
     *     
     */
    public void setFilterInfo(VirtualMachineVMCIDeviceFilterInfo value) {
        this.filterInfo = value;
    }

}
