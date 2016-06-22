
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for anonymous complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType>
 *   &lt;complexContent>
 *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType">
 *       &lt;sequence>
 *         &lt;element name="returnval" type="{urn:vim25}VirtualDiskVFlashCacheConfigInfo"/>
 *       &lt;/sequence>
 *     &lt;/restriction>
 *   &lt;/complexContent>
 * &lt;/complexType>
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "", propOrder = {
    "returnval"
})
@XmlRootElement(name = "HostGetVFlashModuleDefaultConfigResponse")
public class HostGetVFlashModuleDefaultConfigResponse {

    @XmlElement(required = true)
    protected VirtualDiskVFlashCacheConfigInfo returnval;

    /**
     * Gets the value of the returnval property.
     * 
     * @return
     *     possible object is
     *     {@link VirtualDiskVFlashCacheConfigInfo }
     *     
     */
    public VirtualDiskVFlashCacheConfigInfo getReturnval() {
        return returnval;
    }

    /**
     * Sets the value of the returnval property.
     * 
     * @param value
     *     allowed object is
     *     {@link VirtualDiskVFlashCacheConfigInfo }
     *     
     */
    public void setReturnval(VirtualDiskVFlashCacheConfigInfo value) {
        this.returnval = value;
    }

}
