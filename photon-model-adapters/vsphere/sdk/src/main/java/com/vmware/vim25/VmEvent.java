
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlSeeAlso;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for VmEvent complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="VmEvent">
 *   &lt;complexContent>
 *     &lt;extension base="{urn:vim25}Event">
 *       &lt;sequence>
 *         &lt;element name="template" type="{http://www.w3.org/2001/XMLSchema}boolean"/>
 *       &lt;/sequence>
 *     &lt;/extension>
 *   &lt;/complexContent>
 * &lt;/complexType>
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "VmEvent", propOrder = {
    "template"
})
@XmlSeeAlso({
    VmDateRolledBackEvent.class,
    VmBeingDeployedEvent.class,
    VmFailedToPowerOffEvent.class,
    VmBeingMigratedEvent.class,
    VmSecondaryEnabledEvent.class,
    VmDisconnectedEvent.class,
    VmRegisteredEvent.class,
    VmFailoverFailed.class,
    DrsRuleViolationEvent.class,
    VmStartingSecondaryEvent.class,
    VmRemovedEvent.class,
    VmDasUpdateOkEvent.class,
    VmFailedRelayoutEvent.class,
    VmAcquiredMksTicketEvent.class,
    VmMacAssignedEvent.class,
    VmUpgradeCompleteEvent.class,
    VmMaxFTRestartCountReached.class,
    VmCreatedEvent.class,
    VmPrimaryFailoverEvent.class,
    VmFailedToShutdownGuestEvent.class,
    VmInstanceUuidChangedEvent.class,
    VmUuidAssignedEvent.class,
    VmMessageEvent.class,
    VmDeployFailedEvent.class,
    VmFailedUpdatingSecondaryConfig.class,
    VmFailedToPowerOnEvent.class,
    VmDeployedEvent.class,
    VmReconfiguredEvent.class,
    VmRequirementsExceedCurrentEVCModeEvent.class,
    VmResettingEvent.class,
    VmBeingHotMigratedEvent.class,
    VmFailedToSuspendEvent.class,
    VmEndRecordingEvent.class,
    VmRelayoutSuccessfulEvent.class,
    VmEndReplayingEvent.class,
    VmEmigratingEvent.class,
    VmFailedMigrateEvent.class,
    VmRemoteConsoleConnectedEvent.class,
    VmAutoRenameEvent.class,
    VmInstanceUuidAssignedEvent.class,
    VmTimedoutStartingSecondaryEvent.class,
    VmDasResetFailedEvent.class,
    VmSuspendedEvent.class,
    NotEnoughResourcesToStartVmEvent.class,
    VmConnectedEvent.class,
    VmFaultToleranceVmTerminatedEvent.class,
    VmStoppingEvent.class,
    NoMaintenanceModeDrsRecommendationForVM.class,
    VmDiskFailedEvent.class,
    VmGuestStandbyEvent.class,
    VmOrphanedEvent.class,
    VmFailedToResetEvent.class,
    VmMessageWarningEvent.class,
    VmRelocateSpecEvent.class,
    VmFaultToleranceStateChangedEvent.class,
    VmBeingCreatedEvent.class,
    VmPoweredOffEvent.class,
    VmConfigMissingEvent.class,
    VmDasUpdateErrorEvent.class,
    VmRelayoutUpToDateEvent.class,
    DrsSoftRuleViolationEvent.class,
    VmWwnAssignedEvent.class,
    VmUuidConflictEvent.class,
    DrsRuleComplianceEvent.class,
    VmNoCompatibleHostForSecondaryEvent.class,
    VmRenamedEvent.class,
    VmMacChangedEvent.class,
    VmResumingEvent.class,
    VmGuestShutdownEvent.class,
    VmGuestRebootEvent.class,
    VmFaultToleranceTurnedOffEvent.class,
    VmMacConflictEvent.class,
    VmRemoteConsoleDisconnectedEvent.class,
    VmWwnConflictEvent.class,
    VmFailedStartingSecondaryEvent.class,
    VmReloadFromPathFailedEvent.class,
    VmReloadFromPathEvent.class,
    VmWwnChangedEvent.class,
    VmSecondaryAddedEvent.class,
    VmPoweredOnEvent.class,
    VmUpgradingEvent.class,
    VmStartRecordingEvent.class,
    VmSecondaryDisabledEvent.class,
    VmGuestOSCrashedEvent.class,
    VmStartReplayingEvent.class,
    VmCloneEvent.class,
    VmDasBeingResetEvent.class,
    VmUpgradeFailedEvent.class,
    VmDiscoveredEvent.class,
    VmFailedRelayoutOnVmfs2DatastoreEvent.class,
    VmInstanceUuidConflictEvent.class,
    MigrationEvent.class,
    VmMigratedEvent.class,
    VmFailedToRebootGuestEvent.class,
    VmMaxRestartCountReached.class,
    VmAcquiredTicketEvent.class,
    VmNoNetworkAccessEvent.class,
    VmPoweringOnWithCustomizedDVPortEvent.class,
    VmResourcePoolMovedEvent.class,
    VmStartingEvent.class,
    VmSecondaryStartedEvent.class,
    VmFailedToStandbyGuestEvent.class,
    VmUuidChangedEvent.class,
    VmSuspendingEvent.class,
    VmResourceReallocatedEvent.class,
    VmSecondaryDisabledBySystemEvent.class,
    VmMessageErrorEvent.class,
    VmStaticMacConflictEvent.class,
    CustomizationEvent.class
})
public class VmEvent
    extends Event
{

    protected boolean template;

    /**
     * Gets the value of the template property.
     * 
     */
    public boolean isTemplate() {
        return template;
    }

    /**
     * Sets the value of the template property.
     * 
     */
    public void setTemplate(boolean value) {
        this.template = value;
    }

}
