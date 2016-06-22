
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for QueryIPAllocationsRequestType complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="QueryIPAllocationsRequestType">
 *   &lt;complexContent>
 *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType">
 *       &lt;sequence>
 *         &lt;element name="_this" type="{urn:vim25}ManagedObjectReference"/>
 *         &lt;element name="dc" type="{urn:vim25}ManagedObjectReference"/>
 *         &lt;element name="poolId" type="{http://www.w3.org/2001/XMLSchema}int"/>
 *         &lt;element name="extensionKey" type="{http://www.w3.org/2001/XMLSchema}string"/>
 *       &lt;/sequence>
 *     &lt;/restriction>
 *   &lt;/complexContent>
 * &lt;/complexType>
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "QueryIPAllocationsRequestType", propOrder = {
    "_this",
    "dc",
    "poolId",
    "extensionKey"
})
public class QueryIPAllocationsRequestType {

    @XmlElement(required = true)
    protected ManagedObjectReference _this;
    @XmlElement(required = true)
    protected ManagedObjectReference dc;
    protected int poolId;
    @XmlElement(required = true)
    protected String extensionKey;

    /**
     * Gets the value of the this property.
     * 
     * @return
     *     possible object is
     *     {@link ManagedObjectReference }
     *     
     */
    public ManagedObjectReference getThis() {
        return _this;
    }

    /**
     * Sets the value of the this property.
     * 
     * @param value
     *     allowed object is
     *     {@link ManagedObjectReference }
     *     
     */
    public void setThis(ManagedObjectReference value) {
        this._this = value;
    }

    /**
     * Gets the value of the dc property.
     * 
     * @return
     *     possible object is
     *     {@link ManagedObjectReference }
     *     
     */
    public ManagedObjectReference getDc() {
        return dc;
    }

    /**
     * Sets the value of the dc property.
     * 
     * @param value
     *     allowed object is
     *     {@link ManagedObjectReference }
     *     
     */
    public void setDc(ManagedObjectReference value) {
        this.dc = value;
    }

    /**
     * Gets the value of the poolId property.
     * 
     */
    public int getPoolId() {
        return poolId;
    }

    /**
     * Sets the value of the poolId property.
     * 
     */
    public void setPoolId(int value) {
        this.poolId = value;
    }

    /**
     * Gets the value of the extensionKey property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getExtensionKey() {
        return extensionKey;
    }

    /**
     * Sets the value of the extensionKey property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setExtensionKey(String value) {
        this.extensionKey = value;
    }

}
