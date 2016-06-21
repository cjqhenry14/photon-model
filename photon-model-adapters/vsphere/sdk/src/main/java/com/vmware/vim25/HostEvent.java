
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlSeeAlso;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for HostEvent complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="HostEvent">
 *   &lt;complexContent>
 *     &lt;extension base="{urn:vim25}Event">
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
@XmlType(name = "HostEvent")
@XmlSeeAlso({
    HostWwnConflictEvent.class,
    GhostDvsProxySwitchRemovedEvent.class,
    VcAgentUninstalledEvent.class,
    HostCompliantEvent.class,
    VMFSDatastoreExtendedEvent.class,
    HostCnxFailedBadCcagentEvent.class,
    HostDisconnectedEvent.class,
    NoDatastoresConfiguredEvent.class,
    DatastoreRenamedOnHostEvent.class,
    HostDasEnablingEvent.class,
    DrsResourceConfigureFailedEvent.class,
    HostCnxFailedNoLicenseEvent.class,
    DuplicateIpDetectedEvent.class,
    HostAdminEnableEvent.class,
    RemoteTSMEnabledEvent.class,
    NASDatastoreCreatedEvent.class,
    HostIpChangedEvent.class,
    HostCnxFailedAccountFailedEvent.class,
    HostCnxFailedBadUsernameEvent.class,
    UpdatedAgentBeingRestartedEvent.class,
    HostSyncFailedEvent.class,
    HostInAuditModeEvent.class,
    HostUpgradeFailedEvent.class,
    UserUnassignedFromGroup.class,
    EnteringMaintenanceModeEvent.class,
    HostIpToShortNameFailedEvent.class,
    DatastoreRemovedOnHostEvent.class,
    GhostDvsProxySwitchDetectedEvent.class,
    VcAgentUninstallFailedEvent.class,
    HostDasErrorEvent.class,
    HostCnxFailedNoConnectionEvent.class,
    VimAccountPasswordChangedEvent.class,
    HostCnxFailedEvent.class,
    LocalTSMEnabledEvent.class,
    AccountRemovedEvent.class,
    HostDasOkEvent.class,
    HostComplianceCheckedEvent.class,
    EnteringStandbyModeEvent.class,
    AdminPasswordNotChangedEvent.class,
    LocalDatastoreCreatedEvent.class,
    HostCnxFailedNoAccessEvent.class,
    ExitMaintenanceModeEvent.class,
    HostAdminDisableEvent.class,
    HostDasDisabledEvent.class,
    AccountCreatedEvent.class,
    HostCnxFailedNetworkErrorEvent.class,
    HostConnectedEvent.class,
    ExitStandbyModeFailedEvent.class,
    HostShortNameToIpFailedEvent.class,
    EnteredMaintenanceModeEvent.class,
    HostDasDisablingEvent.class,
    AccountUpdatedEvent.class,
    EnteredStandbyModeEvent.class,
    HostEnableAdminFailedEvent.class,
    DrsResourceConfigureSyncedEvent.class,
    HostNonCompliantEvent.class,
    HostCnxFailedTimeoutEvent.class,
    HostUserWorldSwapNotEnabledEvent.class,
    HostCnxFailedAlreadyManagedEvent.class,
    VcAgentUpgradeFailedEvent.class,
    HostCnxFailedBadVersionEvent.class,
    UserAssignedToGroup.class,
    DatastorePrincipalConfigured.class,
    HostReconnectionFailedEvent.class,
    DatastoreDiscoveredEvent.class,
    HostCnxFailedNotFoundEvent.class,
    ExitedStandbyModeEvent.class,
    HostConfigAppliedEvent.class,
    VcAgentUpgradedEvent.class,
    VMFSDatastoreCreatedEvent.class,
    DvsHealthStatusChangeEvent.class,
    HostWwnChangedEvent.class,
    HostCnxFailedCcagentUpgradeEvent.class,
    HostRemovedEvent.class,
    HostConnectionLostEvent.class,
    IScsiBootFailureEvent.class,
    HostGetShortNameFailedEvent.class,
    UserPasswordChanged.class,
    HostVnicConnectedToCustomizedDVPortEvent.class,
    HostAddedEvent.class,
    CanceledHostOperationEvent.class,
    HostProfileAppliedEvent.class,
    TimedOutHostOperationEvent.class,
    ExitingStandbyModeEvent.class,
    HostShutdownEvent.class,
    VMFSDatastoreExpandedEvent.class,
    HostDasEnabledEvent.class,
    HostIpInconsistentEvent.class,
    HostDasEvent.class,
    HostAddFailedEvent.class
})
public class HostEvent
    extends Event
{


}
