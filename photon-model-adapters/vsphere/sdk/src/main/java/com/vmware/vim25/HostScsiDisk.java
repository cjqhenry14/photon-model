
package com.vmware.vim25;

import java.util.ArrayList;
import java.util.List;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for HostScsiDisk complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="HostScsiDisk">
 *   &lt;complexContent>
 *     &lt;extension base="{urn:vim25}ScsiLun">
 *       &lt;sequence>
 *         &lt;element name="capacity" type="{urn:vim25}HostDiskDimensionsLba"/>
 *         &lt;element name="devicePath" type="{http://www.w3.org/2001/XMLSchema}string"/>
 *         &lt;element name="ssd" type="{http://www.w3.org/2001/XMLSchema}boolean" minOccurs="0"/>
 *         &lt;element name="localDisk" type="{http://www.w3.org/2001/XMLSchema}boolean" minOccurs="0"/>
 *         &lt;element name="physicalLocation" type="{http://www.w3.org/2001/XMLSchema}string" maxOccurs="unbounded" minOccurs="0"/>
 *         &lt;element name="emulatedDIXDIFEnabled" type="{http://www.w3.org/2001/XMLSchema}boolean" minOccurs="0"/>
 *         &lt;element name="vsanDiskInfo" type="{urn:vim25}VsanHostVsanDiskInfo" minOccurs="0"/>
 *       &lt;/sequence>
 *     &lt;/extension>
 *   &lt;/complexContent>
 * &lt;/complexType>
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "HostScsiDisk", propOrder = {
    "capacity",
    "devicePath",
    "ssd",
    "localDisk",
    "physicalLocation",
    "emulatedDIXDIFEnabled",
    "vsanDiskInfo"
})
public class HostScsiDisk
    extends ScsiLun
{

    @XmlElement(required = true)
    protected HostDiskDimensionsLba capacity;
    @XmlElement(required = true)
    protected String devicePath;
    protected Boolean ssd;
    protected Boolean localDisk;
    protected List<String> physicalLocation;
    protected Boolean emulatedDIXDIFEnabled;
    protected VsanHostVsanDiskInfo vsanDiskInfo;

    /**
     * Gets the value of the capacity property.
     * 
     * @return
     *     possible object is
     *     {@link HostDiskDimensionsLba }
     *     
     */
    public HostDiskDimensionsLba getCapacity() {
        return capacity;
    }

    /**
     * Sets the value of the capacity property.
     * 
     * @param value
     *     allowed object is
     *     {@link HostDiskDimensionsLba }
     *     
     */
    public void setCapacity(HostDiskDimensionsLba value) {
        this.capacity = value;
    }

    /**
     * Gets the value of the devicePath property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getDevicePath() {
        return devicePath;
    }

    /**
     * Sets the value of the devicePath property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setDevicePath(String value) {
        this.devicePath = value;
    }

    /**
     * Gets the value of the ssd property.
     * 
     * @return
     *     possible object is
     *     {@link Boolean }
     *     
     */
    public Boolean isSsd() {
        return ssd;
    }

    /**
     * Sets the value of the ssd property.
     * 
     * @param value
     *     allowed object is
     *     {@link Boolean }
     *     
     */
    public void setSsd(Boolean value) {
        this.ssd = value;
    }

    /**
     * Gets the value of the localDisk property.
     * 
     * @return
     *     possible object is
     *     {@link Boolean }
     *     
     */
    public Boolean isLocalDisk() {
        return localDisk;
    }

    /**
     * Sets the value of the localDisk property.
     * 
     * @param value
     *     allowed object is
     *     {@link Boolean }
     *     
     */
    public void setLocalDisk(Boolean value) {
        this.localDisk = value;
    }

    /**
     * Gets the value of the physicalLocation property.
     * 
     * <p>
     * This accessor method returns a reference to the live list,
     * not a snapshot. Therefore any modification you make to the
     * returned list will be present inside the JAXB object.
     * This is why there is not a <CODE>set</CODE> method for the physicalLocation property.
     * 
     * <p>
     * For example, to add a new item, do as follows:
     * <pre>
     *    getPhysicalLocation().add(newItem);
     * </pre>
     * 
     * 
     * <p>
     * Objects of the following type(s) are allowed in the list
     * {@link String }
     * 
     * 
     */
    public List<String> getPhysicalLocation() {
        if (physicalLocation == null) {
            physicalLocation = new ArrayList<String>();
        }
        return this.physicalLocation;
    }

    /**
     * Gets the value of the emulatedDIXDIFEnabled property.
     * 
     * @return
     *     possible object is
     *     {@link Boolean }
     *     
     */
    public Boolean isEmulatedDIXDIFEnabled() {
        return emulatedDIXDIFEnabled;
    }

    /**
     * Sets the value of the emulatedDIXDIFEnabled property.
     * 
     * @param value
     *     allowed object is
     *     {@link Boolean }
     *     
     */
    public void setEmulatedDIXDIFEnabled(Boolean value) {
        this.emulatedDIXDIFEnabled = value;
    }

    /**
     * Gets the value of the vsanDiskInfo property.
     * 
     * @return
     *     possible object is
     *     {@link VsanHostVsanDiskInfo }
     *     
     */
    public VsanHostVsanDiskInfo getVsanDiskInfo() {
        return vsanDiskInfo;
    }

    /**
     * Sets the value of the vsanDiskInfo property.
     * 
     * @param value
     *     allowed object is
     *     {@link VsanHostVsanDiskInfo }
     *     
     */
    public void setVsanDiskInfo(VsanHostVsanDiskInfo value) {
        this.vsanDiskInfo = value;
    }

}
