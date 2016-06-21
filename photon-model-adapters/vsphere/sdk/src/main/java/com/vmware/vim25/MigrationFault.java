
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlSeeAlso;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for MigrationFault complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="MigrationFault">
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
@XmlType(name = "MigrationFault")
@XmlSeeAlso({
    SnapshotRevertIssue.class,
    MismatchedVMotionNetworkNames.class,
    FaultToleranceNotSameBuild.class,
    ToolsInstallationInProgress.class,
    WillLoseHAProtection.class,
    MigrationDisabled.class,
    LargeRDMConversionNotSupported.class,
    MaintenanceModeFileMove.class,
    CannotMoveVmWithNativeDeltaDisk.class,
    DiskMoveTypeNotSupported.class,
    CannotModifyConfigCpuRequirements.class,
    FaultToleranceAntiAffinityViolated.class,
    UncommittedUndoableDisk.class,
    RDMNotPreserved.class,
    TooManyDisksOnLegacyHost.class,
    NetworksMayNotBeTheSame.class,
    AffinityConfigured.class,
    ReadOnlyDisksWithLegacyDestination.class,
    HAErrorsAtDest.class,
    WillModifyConfigCpuRequirements.class,
    CannotMoveVmWithDeltaDisk.class,
    SuspendedRelocateNotSupported.class,
    RDMConversionNotSupported.class,
    DatacenterMismatch.class,
    WillResetSnapshotDirectory.class,
    MigrationNotReady.class,
    FaultToleranceNeedsThickDisk.class,
    NoGuestHeartbeat.class,
    SnapshotCopyNotSupported.class,
    MismatchedNetworkPolicies.class,
    MigrationFeatureNotSupported.class,
    IncompatibleDefaultDevice.class,
    DisallowedMigrationDeviceAttached.class,
    CloneFromSnapshotNotSupported.class,
    VMotionProtocolIncompatible.class,
    VMotionInterfaceIssue.class
})
public class MigrationFault
    extends VimFault
{


}
