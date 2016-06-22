
package com.vmware.vim25;

import java.util.ArrayList;
import java.util.List;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for RemoveDiskRequestType complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="RemoveDiskRequestType">
 *   &lt;complexContent>
 *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType">
 *       &lt;sequence>
 *         &lt;element name="_this" type="{urn:vim25}ManagedObjectReference"/>
 *         &lt;element name="disk" type="{urn:vim25}HostScsiDisk" maxOccurs="unbounded"/>
 *         &lt;element name="maintenanceSpec" type="{urn:vim25}HostMaintenanceSpec" minOccurs="0"/>
 *         &lt;element name="timeout" type="{http://www.w3.org/2001/XMLSchema}int" minOccurs="0"/>
 *       &lt;/sequence>
 *     &lt;/restriction>
 *   &lt;/complexContent>
 * &lt;/complexType>
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "RemoveDiskRequestType", propOrder = {
    "_this",
    "disk",
    "maintenanceSpec",
    "timeout"
})
public class RemoveDiskRequestType {

    @XmlElement(required = true)
    protected ManagedObjectReference _this;
    @XmlElement(required = true)
    protected List<HostScsiDisk> disk;
    protected HostMaintenanceSpec maintenanceSpec;
    protected Integer timeout;

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
     * Gets the value of the disk property.
     * 
     * <p>
     * This accessor method returns a reference to the live list,
     * not a snapshot. Therefore any modification you make to the
     * returned list will be present inside the JAXB object.
     * This is why there is not a <CODE>set</CODE> method for the disk property.
     * 
     * <p>
     * For example, to add a new item, do as follows:
     * <pre>
     *    getDisk().add(newItem);
     * </pre>
     * 
     * 
     * <p>
     * Objects of the following type(s) are allowed in the list
     * {@link HostScsiDisk }
     * 
     * 
     */
    public List<HostScsiDisk> getDisk() {
        if (disk == null) {
            disk = new ArrayList<HostScsiDisk>();
        }
        return this.disk;
    }

    /**
     * Gets the value of the maintenanceSpec property.
     * 
     * @return
     *     possible object is
     *     {@link HostMaintenanceSpec }
     *     
     */
    public HostMaintenanceSpec getMaintenanceSpec() {
        return maintenanceSpec;
    }

    /**
     * Sets the value of the maintenanceSpec property.
     * 
     * @param value
     *     allowed object is
     *     {@link HostMaintenanceSpec }
     *     
     */
    public void setMaintenanceSpec(HostMaintenanceSpec value) {
        this.maintenanceSpec = value;
    }

    /**
     * Gets the value of the timeout property.
     * 
     * @return
     *     possible object is
     *     {@link Integer }
     *     
     */
    public Integer getTimeout() {
        return timeout;
    }

    /**
     * Sets the value of the timeout property.
     * 
     * @param value
     *     allowed object is
     *     {@link Integer }
     *     
     */
    public void setTimeout(Integer value) {
        this.timeout = value;
    }

}
