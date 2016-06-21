
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlSeeAlso;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for VirtualHardwareCompatibilityIssue complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="VirtualHardwareCompatibilityIssue">
 *   &lt;complexContent>
 *     &lt;extension base="{urn:vim25}VmConfigFault">
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
@XmlType(name = "VirtualHardwareCompatibilityIssue")
@XmlSeeAlso({
    WakeOnLanNotSupported.class,
    VirtualHardwareVersionNotSupported.class,
    StorageVmotionIncompatible.class,
    MemorySizeNotSupported.class,
    MemorySizeNotRecommended.class,
    NumVirtualCpusNotSupported.class,
    DrsVmotionIncompatibleFault.class,
    MemorySizeNotSupportedByDatastore.class,
    CpuIncompatible.class,
    FeatureRequirementsNotMet.class,
    NumVirtualCoresPerSocketNotSupported.class,
    NotEnoughCpus.class,
    DeviceNotSupported.class,
    DiskNotSupported.class
})
public class VirtualHardwareCompatibilityIssue
    extends VmConfigFault
{


}
