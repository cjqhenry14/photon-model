
package com.vmware.vim25;

import java.util.ArrayList;
import java.util.List;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for DVSManagerImportEntityRequestType complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="DVSManagerImportEntityRequestType">
 *   &lt;complexContent>
 *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType">
 *       &lt;sequence>
 *         &lt;element name="_this" type="{urn:vim25}ManagedObjectReference"/>
 *         &lt;element name="entityBackup" type="{urn:vim25}EntityBackupConfig" maxOccurs="unbounded"/>
 *         &lt;element name="importType" type="{http://www.w3.org/2001/XMLSchema}string"/>
 *       &lt;/sequence>
 *     &lt;/restriction>
 *   &lt;/complexContent>
 * &lt;/complexType>
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "DVSManagerImportEntityRequestType", propOrder = {
    "_this",
    "entityBackup",
    "importType"
})
public class DVSManagerImportEntityRequestType {

    @XmlElement(required = true)
    protected ManagedObjectReference _this;
    @XmlElement(required = true)
    protected List<EntityBackupConfig> entityBackup;
    @XmlElement(required = true)
    protected String importType;

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
     * Gets the value of the entityBackup property.
     * 
     * <p>
     * This accessor method returns a reference to the live list,
     * not a snapshot. Therefore any modification you make to the
     * returned list will be present inside the JAXB object.
     * This is why there is not a <CODE>set</CODE> method for the entityBackup property.
     * 
     * <p>
     * For example, to add a new item, do as follows:
     * <pre>
     *    getEntityBackup().add(newItem);
     * </pre>
     * 
     * 
     * <p>
     * Objects of the following type(s) are allowed in the list
     * {@link EntityBackupConfig }
     * 
     * 
     */
    public List<EntityBackupConfig> getEntityBackup() {
        if (entityBackup == null) {
            entityBackup = new ArrayList<EntityBackupConfig>();
        }
        return this.entityBackup;
    }

    /**
     * Gets the value of the importType property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getImportType() {
        return importType;
    }

    /**
     * Sets the value of the importType property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setImportType(String value) {
        this.importType = value;
    }

}
