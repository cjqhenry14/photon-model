
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for ClusterVmComponentProtectionSettings complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="ClusterVmComponentProtectionSettings">
 *   &lt;complexContent>
 *     &lt;extension base="{urn:vim25}DynamicData">
 *       &lt;sequence>
 *         &lt;element name="vmStorageProtectionForAPD" type="{http://www.w3.org/2001/XMLSchema}string" minOccurs="0"/>
 *         &lt;element name="enableAPDTimeoutForHosts" type="{http://www.w3.org/2001/XMLSchema}boolean" minOccurs="0"/>
 *         &lt;element name="vmTerminateDelayForAPDSec" type="{http://www.w3.org/2001/XMLSchema}int" minOccurs="0"/>
 *         &lt;element name="vmReactionOnAPDCleared" type="{http://www.w3.org/2001/XMLSchema}string" minOccurs="0"/>
 *         &lt;element name="vmStorageProtectionForPDL" type="{http://www.w3.org/2001/XMLSchema}string" minOccurs="0"/>
 *       &lt;/sequence>
 *     &lt;/extension>
 *   &lt;/complexContent>
 * &lt;/complexType>
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "ClusterVmComponentProtectionSettings", propOrder = {
    "vmStorageProtectionForAPD",
    "enableAPDTimeoutForHosts",
    "vmTerminateDelayForAPDSec",
    "vmReactionOnAPDCleared",
    "vmStorageProtectionForPDL"
})
public class ClusterVmComponentProtectionSettings
    extends DynamicData
{

    protected String vmStorageProtectionForAPD;
    protected Boolean enableAPDTimeoutForHosts;
    protected Integer vmTerminateDelayForAPDSec;
    protected String vmReactionOnAPDCleared;
    protected String vmStorageProtectionForPDL;

    /**
     * Gets the value of the vmStorageProtectionForAPD property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getVmStorageProtectionForAPD() {
        return vmStorageProtectionForAPD;
    }

    /**
     * Sets the value of the vmStorageProtectionForAPD property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setVmStorageProtectionForAPD(String value) {
        this.vmStorageProtectionForAPD = value;
    }

    /**
     * Gets the value of the enableAPDTimeoutForHosts property.
     * 
     * @return
     *     possible object is
     *     {@link Boolean }
     *     
     */
    public Boolean isEnableAPDTimeoutForHosts() {
        return enableAPDTimeoutForHosts;
    }

    /**
     * Sets the value of the enableAPDTimeoutForHosts property.
     * 
     * @param value
     *     allowed object is
     *     {@link Boolean }
     *     
     */
    public void setEnableAPDTimeoutForHosts(Boolean value) {
        this.enableAPDTimeoutForHosts = value;
    }

    /**
     * Gets the value of the vmTerminateDelayForAPDSec property.
     * 
     * @return
     *     possible object is
     *     {@link Integer }
     *     
     */
    public Integer getVmTerminateDelayForAPDSec() {
        return vmTerminateDelayForAPDSec;
    }

    /**
     * Sets the value of the vmTerminateDelayForAPDSec property.
     * 
     * @param value
     *     allowed object is
     *     {@link Integer }
     *     
     */
    public void setVmTerminateDelayForAPDSec(Integer value) {
        this.vmTerminateDelayForAPDSec = value;
    }

    /**
     * Gets the value of the vmReactionOnAPDCleared property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getVmReactionOnAPDCleared() {
        return vmReactionOnAPDCleared;
    }

    /**
     * Sets the value of the vmReactionOnAPDCleared property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setVmReactionOnAPDCleared(String value) {
        this.vmReactionOnAPDCleared = value;
    }

    /**
     * Gets the value of the vmStorageProtectionForPDL property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getVmStorageProtectionForPDL() {
        return vmStorageProtectionForPDL;
    }

    /**
     * Sets the value of the vmStorageProtectionForPDL property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setVmStorageProtectionForPDL(String value) {
        this.vmStorageProtectionForPDL = value;
    }

}
