
package com.vmware.vim25;

import java.util.ArrayList;
import java.util.List;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for InsufficientNetworkResourcePoolCapacity complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="InsufficientNetworkResourcePoolCapacity">
 *   &lt;complexContent>
 *     &lt;extension base="{urn:vim25}InsufficientResourcesFault">
 *       &lt;sequence>
 *         &lt;element name="dvsName" type="{http://www.w3.org/2001/XMLSchema}string"/>
 *         &lt;element name="dvsUuid" type="{http://www.w3.org/2001/XMLSchema}string"/>
 *         &lt;element name="resourcePoolKey" type="{http://www.w3.org/2001/XMLSchema}string"/>
 *         &lt;element name="available" type="{http://www.w3.org/2001/XMLSchema}long"/>
 *         &lt;element name="requested" type="{http://www.w3.org/2001/XMLSchema}long"/>
 *         &lt;element name="device" type="{http://www.w3.org/2001/XMLSchema}string" maxOccurs="unbounded"/>
 *       &lt;/sequence>
 *     &lt;/extension>
 *   &lt;/complexContent>
 * &lt;/complexType>
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "InsufficientNetworkResourcePoolCapacity", propOrder = {
    "dvsName",
    "dvsUuid",
    "resourcePoolKey",
    "available",
    "requested",
    "device"
})
public class InsufficientNetworkResourcePoolCapacity
    extends InsufficientResourcesFault
{

    @XmlElement(required = true)
    protected String dvsName;
    @XmlElement(required = true)
    protected String dvsUuid;
    @XmlElement(required = true)
    protected String resourcePoolKey;
    protected long available;
    protected long requested;
    @XmlElement(required = true)
    protected List<String> device;

    /**
     * Gets the value of the dvsName property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getDvsName() {
        return dvsName;
    }

    /**
     * Sets the value of the dvsName property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setDvsName(String value) {
        this.dvsName = value;
    }

    /**
     * Gets the value of the dvsUuid property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getDvsUuid() {
        return dvsUuid;
    }

    /**
     * Sets the value of the dvsUuid property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setDvsUuid(String value) {
        this.dvsUuid = value;
    }

    /**
     * Gets the value of the resourcePoolKey property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getResourcePoolKey() {
        return resourcePoolKey;
    }

    /**
     * Sets the value of the resourcePoolKey property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setResourcePoolKey(String value) {
        this.resourcePoolKey = value;
    }

    /**
     * Gets the value of the available property.
     * 
     */
    public long getAvailable() {
        return available;
    }

    /**
     * Sets the value of the available property.
     * 
     */
    public void setAvailable(long value) {
        this.available = value;
    }

    /**
     * Gets the value of the requested property.
     * 
     */
    public long getRequested() {
        return requested;
    }

    /**
     * Sets the value of the requested property.
     * 
     */
    public void setRequested(long value) {
        this.requested = value;
    }

    /**
     * Gets the value of the device property.
     * 
     * <p>
     * This accessor method returns a reference to the live list,
     * not a snapshot. Therefore any modification you make to the
     * returned list will be present inside the JAXB object.
     * This is why there is not a <CODE>set</CODE> method for the device property.
     * 
     * <p>
     * For example, to add a new item, do as follows:
     * <pre>
     *    getDevice().add(newItem);
     * </pre>
     * 
     * 
     * <p>
     * Objects of the following type(s) are allowed in the list
     * {@link String }
     * 
     * 
     */
    public List<String> getDevice() {
        if (device == null) {
            device = new ArrayList<String>();
        }
        return this.device;
    }

}
