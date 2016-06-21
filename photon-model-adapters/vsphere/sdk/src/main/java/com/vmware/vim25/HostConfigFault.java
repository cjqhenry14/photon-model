
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlSeeAlso;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for HostConfigFault complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="HostConfigFault">
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
@XmlType(name = "HostConfigFault")
@XmlSeeAlso({
    DisableAdminNotSupported.class,
    HostConfigFailed.class,
    HostInDomain.class,
    BlockedByFirewall.class,
    AdminNotDisabled.class,
    NoGateway.class,
    InvalidHostName.class,
    ClockSkew.class,
    PlatformConfigFault.class,
    AdminDisabled.class,
    NoVirtualNic.class,
    VmfsMountFault.class,
    NasConfigFault.class
})
public class HostConfigFault
    extends VimFault
{


}
