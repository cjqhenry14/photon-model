
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlSeeAlso;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for VirtualDeviceDeviceBackingOption complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="VirtualDeviceDeviceBackingOption">
 *   &lt;complexContent>
 *     &lt;extension base="{urn:vim25}VirtualDeviceBackingOption">
 *       &lt;sequence>
 *         &lt;element name="autoDetectAvailable" type="{urn:vim25}BoolOption"/>
 *       &lt;/sequence>
 *     &lt;/extension>
 *   &lt;/complexContent>
 * &lt;/complexType>
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "VirtualDeviceDeviceBackingOption", propOrder = {
    "autoDetectAvailable"
})
@XmlSeeAlso({
    VirtualParallelPortDeviceBackingOption.class,
    VirtualCdromRemoteAtapiBackingOption.class,
    VirtualCdromAtapiBackingOption.class,
    VirtualPointingDeviceBackingOption.class,
    VirtualDiskRawDiskVer2BackingOption.class,
    VirtualEthernetCardNetworkBackingOption.class,
    VirtualDiskRawDiskMappingVer1BackingOption.class,
    VirtualEthernetCardLegacyNetworkBackingOption.class,
    VirtualSerialPortDeviceBackingOption.class,
    VirtualSCSIPassthroughDeviceBackingOption.class,
    VirtualUSBRemoteHostBackingOption.class,
    VirtualUSBUSBBackingOption.class,
    VirtualSoundCardDeviceBackingOption.class,
    VirtualCdromPassthroughBackingOption.class,
    VirtualFloppyDeviceBackingOption.class,
    VirtualPCIPassthroughDeviceBackingOption.class
})
public class VirtualDeviceDeviceBackingOption
    extends VirtualDeviceBackingOption
{

    @XmlElement(required = true)
    protected BoolOption autoDetectAvailable;

    /**
     * Gets the value of the autoDetectAvailable property.
     * 
     * @return
     *     possible object is
     *     {@link BoolOption }
     *     
     */
    public BoolOption getAutoDetectAvailable() {
        return autoDetectAvailable;
    }

    /**
     * Sets the value of the autoDetectAvailable property.
     * 
     * @param value
     *     allowed object is
     *     {@link BoolOption }
     *     
     */
    public void setAutoDetectAvailable(BoolOption value) {
        this.autoDetectAvailable = value;
    }

}
