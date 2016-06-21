
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlSeeAlso;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for ClusterEvent complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="ClusterEvent">
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
@XmlType(name = "ClusterEvent")
@XmlSeeAlso({
    DasEnabledEvent.class,
    DasAdmissionControlEnabledEvent.class,
    ClusterOvercommittedEvent.class,
    HostMonitoringStateChangedEvent.class,
    ClusterComplianceCheckedEvent.class,
    ClusterCreatedEvent.class,
    VmHealthMonitoringStateChangedEvent.class,
    DrsDisabledEvent.class,
    DasDisabledEvent.class,
    DrsEnabledEvent.class,
    DasAgentUnavailableEvent.class,
    ClusterReconfiguredEvent.class,
    DasClusterIsolatedEvent.class,
    FailoverLevelRestored.class,
    DasAdmissionControlDisabledEvent.class,
    InsufficientFailoverResourcesEvent.class,
    DasHostIsolatedEvent.class,
    ClusterDestroyedEvent.class,
    DrsRecoveredFromFailureEvent.class,
    DrsInvocationFailedEvent.class,
    ClusterStatusChangedEvent.class,
    DasAgentFoundEvent.class,
    DasHostFailedEvent.class
})
public class ClusterEvent
    extends Event
{


}
