
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlSeeAlso;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for DvsEvent complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="DvsEvent">
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
@XmlType(name = "DvsEvent")
@XmlSeeAlso({
    DvsCreatedEvent.class,
    VmVnicPoolReservationViolationClearEvent.class,
    DvsPortCreatedEvent.class,
    DvsUpgradeInProgressEvent.class,
    RecoveryEvent.class,
    DvsPortRuntimeChangeEvent.class,
    HostLocalPortCreatedEvent.class,
    DvsUpgradeAvailableEvent.class,
    DvsHostJoinedEvent.class,
    DvsUpgradeRejectedEvent.class,
    RollbackEvent.class,
    DvsHostLeftEvent.class,
    DvsPortLinkUpEvent.class,
    DvsRestoreEvent.class,
    DvsPortBlockedEvent.class,
    DvsPortReconfiguredEvent.class,
    DvsPortExitedPassthruEvent.class,
    DvsPortLinkDownEvent.class,
    DvsImportEvent.class,
    DvsHostWentOutOfSyncEvent.class,
    DvsPortUnblockedEvent.class,
    DvsPortLeavePortgroupEvent.class,
    DvsHostBackInSyncEvent.class,
    DvsPortDeletedEvent.class,
    DvsHostStatusUpdated.class,
    DvsMergedEvent.class,
    VmVnicPoolReservationViolationRaiseEvent.class,
    DvsPortVendorSpecificStateChangeEvent.class,
    DvsReconfiguredEvent.class,
    DvsDestroyedEvent.class,
    DvsUpgradedEvent.class,
    DvsPortEnteredPassthruEvent.class,
    DvsRenamedEvent.class,
    OutOfSyncDvsHost.class,
    DvsPortDisconnectedEvent.class,
    DvsPortConnectedEvent.class,
    DvsPortJoinPortgroupEvent.class
})
public class DvsEvent
    extends Event
{


}
