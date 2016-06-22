
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for VirtualDiskSeSparseBackingInfo complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="VirtualDiskSeSparseBackingInfo">
 *   &lt;complexContent>
 *     &lt;extension base="{urn:vim25}VirtualDeviceFileBackingInfo">
 *       &lt;sequence>
 *         &lt;element name="diskMode" type="{http://www.w3.org/2001/XMLSchema}string"/>
 *         &lt;element name="writeThrough" type="{http://www.w3.org/2001/XMLSchema}boolean" minOccurs="0"/>
 *         &lt;element name="uuid" type="{http://www.w3.org/2001/XMLSchema}string" minOccurs="0"/>
 *         &lt;element name="contentId" type="{http://www.w3.org/2001/XMLSchema}string" minOccurs="0"/>
 *         &lt;element name="changeId" type="{http://www.w3.org/2001/XMLSchema}string" minOccurs="0"/>
 *         &lt;element name="parent" type="{urn:vim25}VirtualDiskSeSparseBackingInfo" minOccurs="0"/>
 *         &lt;element name="deltaDiskFormat" type="{http://www.w3.org/2001/XMLSchema}string" minOccurs="0"/>
 *         &lt;element name="digestEnabled" type="{http://www.w3.org/2001/XMLSchema}boolean" minOccurs="0"/>
 *         &lt;element name="grainSize" type="{http://www.w3.org/2001/XMLSchema}int" minOccurs="0"/>
 *       &lt;/sequence>
 *     &lt;/extension>
 *   &lt;/complexContent>
 * &lt;/complexType>
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "VirtualDiskSeSparseBackingInfo", propOrder = {
    "diskMode",
    "writeThrough",
    "uuid",
    "contentId",
    "changeId",
    "parent",
    "deltaDiskFormat",
    "digestEnabled",
    "grainSize"
})
public class VirtualDiskSeSparseBackingInfo
    extends VirtualDeviceFileBackingInfo
{

    @XmlElement(required = true)
    protected String diskMode;
    protected Boolean writeThrough;
    protected String uuid;
    protected String contentId;
    protected String changeId;
    protected VirtualDiskSeSparseBackingInfo parent;
    protected String deltaDiskFormat;
    protected Boolean digestEnabled;
    protected Integer grainSize;

    /**
     * Gets the value of the diskMode property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getDiskMode() {
        return diskMode;
    }

    /**
     * Sets the value of the diskMode property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setDiskMode(String value) {
        this.diskMode = value;
    }

    /**
     * Gets the value of the writeThrough property.
     * 
     * @return
     *     possible object is
     *     {@link Boolean }
     *     
     */
    public Boolean isWriteThrough() {
        return writeThrough;
    }

    /**
     * Sets the value of the writeThrough property.
     * 
     * @param value
     *     allowed object is
     *     {@link Boolean }
     *     
     */
    public void setWriteThrough(Boolean value) {
        this.writeThrough = value;
    }

    /**
     * Gets the value of the uuid property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getUuid() {
        return uuid;
    }

    /**
     * Sets the value of the uuid property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setUuid(String value) {
        this.uuid = value;
    }

    /**
     * Gets the value of the contentId property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getContentId() {
        return contentId;
    }

    /**
     * Sets the value of the contentId property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setContentId(String value) {
        this.contentId = value;
    }

    /**
     * Gets the value of the changeId property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getChangeId() {
        return changeId;
    }

    /**
     * Sets the value of the changeId property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setChangeId(String value) {
        this.changeId = value;
    }

    /**
     * Gets the value of the parent property.
     * 
     * @return
     *     possible object is
     *     {@link VirtualDiskSeSparseBackingInfo }
     *     
     */
    public VirtualDiskSeSparseBackingInfo getParent() {
        return parent;
    }

    /**
     * Sets the value of the parent property.
     * 
     * @param value
     *     allowed object is
     *     {@link VirtualDiskSeSparseBackingInfo }
     *     
     */
    public void setParent(VirtualDiskSeSparseBackingInfo value) {
        this.parent = value;
    }

    /**
     * Gets the value of the deltaDiskFormat property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getDeltaDiskFormat() {
        return deltaDiskFormat;
    }

    /**
     * Sets the value of the deltaDiskFormat property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setDeltaDiskFormat(String value) {
        this.deltaDiskFormat = value;
    }

    /**
     * Gets the value of the digestEnabled property.
     * 
     * @return
     *     possible object is
     *     {@link Boolean }
     *     
     */
    public Boolean isDigestEnabled() {
        return digestEnabled;
    }

    /**
     * Sets the value of the digestEnabled property.
     * 
     * @param value
     *     allowed object is
     *     {@link Boolean }
     *     
     */
    public void setDigestEnabled(Boolean value) {
        this.digestEnabled = value;
    }

    /**
     * Gets the value of the grainSize property.
     * 
     * @return
     *     possible object is
     *     {@link Integer }
     *     
     */
    public Integer getGrainSize() {
        return grainSize;
    }

    /**
     * Sets the value of the grainSize property.
     * 
     * @param value
     *     allowed object is
     *     {@link Integer }
     *     
     */
    public void setGrainSize(Integer value) {
        this.grainSize = value;
    }

}
