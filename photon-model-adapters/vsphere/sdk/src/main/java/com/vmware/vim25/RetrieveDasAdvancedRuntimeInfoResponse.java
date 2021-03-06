
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
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
 *         &lt;element name="returnval" type="{urn:vim25}ClusterDasAdvancedRuntimeInfo" minOccurs="0"/>
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
@XmlRootElement(name = "RetrieveDasAdvancedRuntimeInfoResponse")
public class RetrieveDasAdvancedRuntimeInfoResponse {

    protected ClusterDasAdvancedRuntimeInfo returnval;

    /**
     * Gets the value of the returnval property.
     * 
     * @return
     *     possible object is
     *     {@link ClusterDasAdvancedRuntimeInfo }
     *     
     */
    public ClusterDasAdvancedRuntimeInfo getReturnval() {
        return returnval;
    }

    /**
     * Sets the value of the returnval property.
     * 
     * @param value
     *     allowed object is
     *     {@link ClusterDasAdvancedRuntimeInfo }
     *     
     */
    public void setReturnval(ClusterDasAdvancedRuntimeInfo value) {
        this.returnval = value;
    }

}
