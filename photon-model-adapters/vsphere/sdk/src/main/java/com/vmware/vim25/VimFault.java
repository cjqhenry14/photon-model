
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlSeeAlso;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for VimFault complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="VimFault">
 *   &lt;complexContent>
 *     &lt;extension base="{urn:vim25}MethodFault">
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
@XmlType(name = "VimFault")
@XmlSeeAlso({
    GenericDrsFault.class,
    ConcurrentAccess.class,
    StorageDrsIolbDisabledInternally.class,
    MissingBmcSupport.class,
    LimitExceeded.class,
    IORMNotSupportedHostOnDatastore.class,
    NamespaceFull.class,
    StorageDrsCannotMoveFTVm.class,
    InvalidEvent.class,
    DasConfigFault.class,
    InvalidName.class,
    NoConnectedDatastore.class,
    CannotDisconnectHostWithFaultToleranceVm.class,
    VFlashModuleVersionIncompatible.class,
    CannotMoveHostWithFaultToleranceVm.class,
    InvalidFolder.class,
    StorageDrsCannotMoveVmWithMountedCDROM.class,
    AlreadyExists.class,
    VmValidateMaxDevice.class,
    PatchBinariesNotFound.class,
    StorageDrsHmsUnreachable.class,
    StorageDrsCannotMoveTemplate.class,
    InaccessibleVFlashSource.class,
    SsdDiskNotAvailable.class,
    AnswerFileUpdateFailed.class,
    InvalidLogin.class,
    ShrinkDiskFault.class,
    CannotMoveFaultToleranceVm.class,
    RebootRequired.class,
    StorageDrsStaleHmsCollection.class,
    AuthMinimumAdminPermission.class,
    OutOfBounds.class,
    DuplicateName.class,
    Timedout.class,
    WipeDiskFault.class,
    LicenseServerUnavailable.class,
    InsufficientStorageIops.class,
    ToolsUnavailable.class,
    HostPowerOpFailed.class,
    TaskInProgress.class,
    NoClientCertificate.class,
    StorageDrsCannotMoveDiskInMultiWriterMode.class,
    InvalidAffinitySettingFault.class,
    InvalidIpmiLoginInfo.class,
    FcoeFault.class,
    StorageDrsCannotMoveIndependentDisk.class,
    LogBundlingFailed.class,
    StorageDrsUnableToMoveFiles.class,
    StorageDrsRelocateDisabled.class,
    NoDiskFound.class,
    VAppConfigFault.class,
    StorageDrsCannotMoveManuallyPlacedSwapFile.class,
    ExtendedFault.class,
    StorageDrsDatacentersCannotShareDatastore.class,
    SSPIChallenge.class,
    DrsDisabledOnVm.class,
    InvalidLicense.class,
    StorageDrsHmsMoveInProgress.class,
    LicenseEntityNotFound.class,
    NetworkDisruptedAndConfigRolledBack.class,
    CannotEnableVmcpForCluster.class,
    StorageDrsDisabledOnVm.class,
    InvalidLocale.class,
    SwapDatastoreUnset.class,
    ResourceNotAvailable.class,
    NotFound.class,
    FaultToleranceVmNotDasProtected.class,
    VmMonitorIncompatibleForFaultTolerance.class,
    RecordReplayDisabled.class,
    ProfileUpdateFailed.class,
    VmToolsUpgradeFault.class,
    NotSupportedHostForChecksum.class,
    PatchNotApplicable.class,
    VmMetadataManagerFault.class,
    UserNotFound.class,
    CannotAccessLocalSource.class,
    UnsupportedVimApiVersion.class,
    StorageDrsCannotMoveManuallyPlacedVm.class,
    AlreadyUpgraded.class,
    NamespaceWriteProtected.class,
    HostIncompatibleForRecordReplay.class,
    HostHasComponentFailure.class,
    InvalidIpmiMacAddress.class,
    ActiveDirectoryFault.class,
    HostConfigFault.class,
    CannotPlaceWithoutPrerequisiteMoves.class,
    InvalidBmcRole.class,
    StorageDrsCannotMoveVmInUserFolder.class,
    NoCompatibleDatastore.class,
    RemoveFailed.class,
    InvalidPrivilege.class,
    SnapshotFault.class,
    CustomizationFault.class,
    ResourceInUse.class,
    InsufficientResourcesFault.class,
    StorageDrsHbrDiskNotMovable.class,
    InvalidDatastore.class,
    MismatchedBundle.class,
    NamespaceLimitReached.class,
    TooManyConsecutiveOverrides.class,
    VsanFault.class,
    StorageDrsCannotMoveVmWithNoFilesInLayout.class,
    OvfFault.class,
    PatchMetadataInvalid.class,
    FileFault.class,
    InvalidState.class,
    DvsFault.class,
    VmFaultToleranceIssue.class,
    NoSubjectName.class,
    StorageDrsCannotMoveSharedDisk.class,
    ReplicationFault.class,
    MigrationFault.class,
    UnrecognizedHost.class,
    EVCConfigFault.class,
    IscsiFault.class,
    NoCompatibleHost.class,
    GuestOperationsFault.class,
    VmConfigFault.class,
    HostConnectFault.class
})
public class VimFault
    extends MethodFault
{


}
