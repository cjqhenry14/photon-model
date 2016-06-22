
package com.vmware.vim25;

import java.util.ArrayList;
import java.util.List;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for ClusterEVCManagerCheckResult complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="ClusterEVCManagerCheckResult">
 *   &lt;complexContent>
 *     &lt;extension base="{urn:vim25}DynamicData">
 *       &lt;sequence>
 *         &lt;element name="evcModeKey" type="{http://www.w3.org/2001/XMLSchema}string"/>
 *         &lt;element name="error" type="{urn:vim25}LocalizedMethodFault"/>
 *         &lt;element name="host" type="{urn:vim25}ManagedObjectReference" maxOccurs="unbounded" minOccurs="0"/>
 *       &lt;/sequence>
 *     &lt;/extension>
 *   &lt;/complexContent>
 * &lt;/complexType>
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "ClusterEVCManagerCheckResult", propOrder = {
    "evcModeKey",
    "error",
    "host"
})
public class ClusterEVCManagerCheckResult
    extends DynamicData
{

    @XmlElement(required = true)
    protected String evcModeKey;
    @XmlElement(required = true)
    protected LocalizedMethodFault error;
    protected List<ManagedObjectReference> host;

    /**
     * Gets the value of the evcModeKey property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getEvcModeKey() {
        return evcModeKey;
    }

    /**
     * Sets the value of the evcModeKey property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setEvcModeKey(String value) {
        this.evcModeKey = value;
    }

    /**
     * Gets the value of the error property.
     * 
     * @return
     *     possible object is
     *     {@link LocalizedMethodFault }
     *     
     */
    public LocalizedMethodFault getError() {
        return error;
    }

    /**
     * Sets the value of the error property.
     * 
     * @param value
     *     allowed object is
     *     {@link LocalizedMethodFault }
     *     
     */
    public void setError(LocalizedMethodFault value) {
        this.error = value;
    }

    /**
     * Gets the value of the host property.
     * 
     * <p>
     * This accessor method returns a reference to the live list,
     * not a snapshot. Therefore any modification you make to the
     * returned list will be present inside the JAXB object.
     * This is why there is not a <CODE>set</CODE> method for the host property.
     * 
     * <p>
     * For example, to add a new item, do as follows:
     * <pre>
     *    getHost().add(newItem);
     * </pre>
     * 
     * 
     * <p>
     * Objects of the following type(s) are allowed in the list
     * {@link ManagedObjectReference }
     * 
     * 
     */
    public List<ManagedObjectReference> getHost() {
        if (host == null) {
            host = new ArrayList<ManagedObjectReference>();
        }
        return this.host;
    }

}
