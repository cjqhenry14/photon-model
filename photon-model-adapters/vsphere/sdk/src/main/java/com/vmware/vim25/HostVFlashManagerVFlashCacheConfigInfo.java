
package com.vmware.vim25;

import java.util.ArrayList;
import java.util.List;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for HostVFlashManagerVFlashCacheConfigInfo complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="HostVFlashManagerVFlashCacheConfigInfo">
 *   &lt;complexContent>
 *     &lt;extension base="{urn:vim25}DynamicData">
 *       &lt;sequence>
 *         &lt;element name="vFlashModuleConfigOption" type="{urn:vim25}HostVFlashManagerVFlashCacheConfigInfoVFlashModuleConfigOption" maxOccurs="unbounded" minOccurs="0"/>
 *         &lt;element name="defaultVFlashModule" type="{http://www.w3.org/2001/XMLSchema}string" minOccurs="0"/>
 *         &lt;element name="swapCacheReservationInGB" type="{http://www.w3.org/2001/XMLSchema}long" minOccurs="0"/>
 *       &lt;/sequence>
 *     &lt;/extension>
 *   &lt;/complexContent>
 * &lt;/complexType>
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "HostVFlashManagerVFlashCacheConfigInfo", propOrder = {
    "vFlashModuleConfigOption",
    "defaultVFlashModule",
    "swapCacheReservationInGB"
})
public class HostVFlashManagerVFlashCacheConfigInfo
    extends DynamicData
{

    protected List<HostVFlashManagerVFlashCacheConfigInfoVFlashModuleConfigOption> vFlashModuleConfigOption;
    protected String defaultVFlashModule;
    protected Long swapCacheReservationInGB;

    /**
     * Gets the value of the vFlashModuleConfigOption property.
     * 
     * <p>
     * This accessor method returns a reference to the live list,
     * not a snapshot. Therefore any modification you make to the
     * returned list will be present inside the JAXB object.
     * This is why there is not a <CODE>set</CODE> method for the vFlashModuleConfigOption property.
     * 
     * <p>
     * For example, to add a new item, do as follows:
     * <pre>
     *    getVFlashModuleConfigOption().add(newItem);
     * </pre>
     * 
     * 
     * <p>
     * Objects of the following type(s) are allowed in the list
     * {@link HostVFlashManagerVFlashCacheConfigInfoVFlashModuleConfigOption }
     * 
     * 
     */
    public List<HostVFlashManagerVFlashCacheConfigInfoVFlashModuleConfigOption> getVFlashModuleConfigOption() {
        if (vFlashModuleConfigOption == null) {
            vFlashModuleConfigOption = new ArrayList<HostVFlashManagerVFlashCacheConfigInfoVFlashModuleConfigOption>();
        }
        return this.vFlashModuleConfigOption;
    }

    /**
     * Gets the value of the defaultVFlashModule property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getDefaultVFlashModule() {
        return defaultVFlashModule;
    }

    /**
     * Sets the value of the defaultVFlashModule property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setDefaultVFlashModule(String value) {
        this.defaultVFlashModule = value;
    }

    /**
     * Gets the value of the swapCacheReservationInGB property.
     * 
     * @return
     *     possible object is
     *     {@link Long }
     *     
     */
    public Long getSwapCacheReservationInGB() {
        return swapCacheReservationInGB;
    }

    /**
     * Sets the value of the swapCacheReservationInGB property.
     * 
     * @param value
     *     allowed object is
     *     {@link Long }
     *     
     */
    public void setSwapCacheReservationInGB(Long value) {
        this.swapCacheReservationInGB = value;
    }

}
