
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlSeeAlso;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for RuntimeFault complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="RuntimeFault">
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
@XmlType(name = "RuntimeFault")
@XmlSeeAlso({
    ThirdPartyLicenseAssignmentFailed.class,
    ConflictingDatastoreFound.class,
    VAppOperationInProgress.class,
    DatabaseError.class,
    DisallowedChangeByService.class,
    LicenseAssignmentFailed.class,
    NotImplemented.class,
    DisallowedOperationOnFailoverHost.class,
    UnexpectedFault.class,
    ManagedObjectNotFound.class,
    NotSupported.class,
    CannotDisableDrsOnClusterManagedByVDC.class,
    MethodAlreadyDisabledFault.class,
    SystemError.class,
    MethodDisabled.class,
    FailToLockFaultToleranceVMs.class,
    SecurityError.class,
    RequestCanceled.class,
    InvalidArgument.class,
    NotEnoughLicenses.class,
    CannotDisableDrsOnClustersWithVApps.class,
    RestrictedByAdministrator.class,
    InvalidRequest.class,
    HostCommunication.class,
    OperationDisallowedOnHost.class,
    InvalidProfileReferenceHost.class
})
public class RuntimeFault
    extends MethodFault
{


}
