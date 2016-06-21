
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlSeeAlso;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for DvsFault complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="DvsFault">
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
@XmlType(name = "DvsFault")
@XmlSeeAlso({
    SwitchIpUnset.class,
    SwitchNotInUpgradeMode.class,
    DvsApplyOperationFault.class,
    VspanPortConflict.class,
    ConflictingConfiguration.class,
    VspanDestPortConflict.class,
    CollectorAddressUnset.class,
    VspanPortPromiscChangeFault.class,
    VspanSameSessionPortConflict.class,
    VspanPortgroupTypeChangeFault.class,
    InvalidIpfixConfig.class,
    DvsOperationBulkFault.class,
    VspanPortgroupPromiscChangeFault.class,
    BackupBlobReadFailure.class,
    DvsScopeViolated.class,
    ImportOperationBulkFault.class,
    VspanPortMoveFault.class,
    BackupBlobWriteFailure.class,
    ImportHostAddFailure.class,
    VspanPromiscuousPortNotSupported.class,
    DvsNotAuthorized.class,
    RollbackFailure.class
})
public class DvsFault
    extends VimFault
{


}
