
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlSeeAlso;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for VsanFault complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="VsanFault">
 *   &lt;complexContent>
 *     &lt;extension base="{urn:vim25}VimFault">
 *       &lt;sequence>
 *       &lt;/sequence>
 *     &lt;/extension>
 *   &lt;/complexContent>
 * &lt;/complexType>
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "VsanFault")
@XmlSeeAlso({
    DuplicateVsanNetworkInterface.class,
    CannotChangeVsanClusterUuid.class,
    CannotReconfigureVsanWhenHaEnabled.class,
    CannotMoveVsanEnabledHost.class,
    CannotChangeVsanNodeUuid.class,
    VsanDiskFault.class
})
public class VsanFault
    extends VimFault
{


}
