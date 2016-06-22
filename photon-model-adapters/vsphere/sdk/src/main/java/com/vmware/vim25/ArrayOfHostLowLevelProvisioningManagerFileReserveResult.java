
package com.vmware.vim25;

import java.util.ArrayList;
import java.util.List;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for ArrayOfHostLowLevelProvisioningManagerFileReserveResult complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="ArrayOfHostLowLevelProvisioningManagerFileReserveResult">
 *   &lt;complexContent>
 *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType">
 *       &lt;sequence>
 *         &lt;element name="HostLowLevelProvisioningManagerFileReserveResult" type="{urn:vim25}HostLowLevelProvisioningManagerFileReserveResult" maxOccurs="unbounded" minOccurs="0"/>
 *       &lt;/sequence>
 *     &lt;/restriction>
 *   &lt;/complexContent>
 * &lt;/complexType>
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "ArrayOfHostLowLevelProvisioningManagerFileReserveResult", propOrder = {
    "hostLowLevelProvisioningManagerFileReserveResult"
})
public class ArrayOfHostLowLevelProvisioningManagerFileReserveResult {

    @XmlElement(name = "HostLowLevelProvisioningManagerFileReserveResult")
    protected List<HostLowLevelProvisioningManagerFileReserveResult> hostLowLevelProvisioningManagerFileReserveResult;

    /**
     * Gets the value of the hostLowLevelProvisioningManagerFileReserveResult property.
     * 
     * <p>
     * This accessor method returns a reference to the live list,
     * not a snapshot. Therefore any modification you make to the
     * returned list will be present inside the JAXB object.
     * This is why there is not a <CODE>set</CODE> method for the hostLowLevelProvisioningManagerFileReserveResult property.
     * 
     * <p>
     * For example, to add a new item, do as follows:
     * <pre>
     *    getHostLowLevelProvisioningManagerFileReserveResult().add(newItem);
     * </pre>
     * 
     * 
     * <p>
     * Objects of the following type(s) are allowed in the list
     * {@link HostLowLevelProvisioningManagerFileReserveResult }
     * 
     * 
     */
    public List<HostLowLevelProvisioningManagerFileReserveResult> getHostLowLevelProvisioningManagerFileReserveResult() {
        if (hostLowLevelProvisioningManagerFileReserveResult == null) {
            hostLowLevelProvisioningManagerFileReserveResult = new ArrayList<HostLowLevelProvisioningManagerFileReserveResult>();
        }
        return this.hostLowLevelProvisioningManagerFileReserveResult;
    }

}
