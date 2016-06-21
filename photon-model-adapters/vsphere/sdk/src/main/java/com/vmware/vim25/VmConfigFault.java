
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlSeeAlso;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for VmConfigFault complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="VmConfigFault">
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
@XmlType(name = "VmConfigFault")
@XmlSeeAlso({
    VAppNotRunning.class,
    OvfConsumerValidationFault.class,
    VmConfigIncompatibleForRecordReplay.class,
    NumVirtualCpusIncompatible.class,
    NoCompatibleSoftAffinityHost.class,
    FaultToleranceCannotEditMem.class,
    DeltaDiskFormatNotSupported.class,
    VmConfigIncompatibleForFaultTolerance.class,
    LargeRDMNotSupportedOnDatastore.class,
    NoCompatibleHardAffinityHost.class,
    UnsupportedVmxLocation.class,
    CannotDisableSnapshot.class,
    InvalidFormat.class,
    RDMNotSupportedOnDatastore.class,
    GenericVmConfigFault.class,
    VmHostAffinityRuleViolation.class,
    VFlashModuleNotSupported.class,
    MemoryHotPlugNotSupported.class,
    SoftRuleVioCorrectionDisallowed.class,
    RuleViolation.class,
    VFlashCacheHotConfigNotSupported.class,
    CannotAccessVmComponent.class,
    VAppPropertyFault.class,
    CpuHotPlugNotSupported.class,
    InvalidVmConfig.class,
    UnsupportedDatastore.class,
    SoftRuleVioCorrectionImpact.class,
    EightHostLimitViolated.class,
    VirtualHardwareCompatibilityIssue.class,
    CannotUseNetwork.class
})
public class VmConfigFault
    extends VimFault
{


}
