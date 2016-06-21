
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlSeeAlso;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for VmFaultToleranceIssue complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="VmFaultToleranceIssue">
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
@XmlType(name = "VmFaultToleranceIssue")
@XmlSeeAlso({
    VmFaultToleranceConfigIssueWrapper.class,
    VmFaultToleranceInvalidFileBacking.class,
    NoHostSuitableForFtSecondary.class,
    InvalidOperationOnSecondaryVm.class,
    CannotChangeHaSettingsForFtSecondary.class,
    PowerOnFtSecondaryFailed.class,
    SecondaryVmAlreadyEnabled.class,
    SecondaryVmNotRegistered.class,
    SecondaryVmAlreadyDisabled.class,
    FtIssuesOnHost.class,
    CannotComputeFTCompatibleHosts.class,
    FaultToleranceNotLicensed.class,
    VmFaultToleranceConfigIssue.class,
    CannotChangeDrsBehaviorForFtSecondary.class,
    VmFaultToleranceOpIssuesList.class,
    HostIncompatibleForFaultTolerance.class,
    FaultTolerancePrimaryPowerOnNotAttempted.class,
    SecondaryVmAlreadyRegistered.class,
    IncompatibleHostForFtSecondary.class,
    NotSupportedDeviceForFT.class
})
public class VmFaultToleranceIssue
    extends VimFault
{


}
