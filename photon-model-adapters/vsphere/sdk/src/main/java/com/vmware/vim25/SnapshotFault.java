
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlSeeAlso;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for SnapshotFault complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="SnapshotFault">
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
@XmlType(name = "SnapshotFault")
@XmlSeeAlso({
    MemorySnapshotOnIndependentDisk.class,
    SnapshotIncompatibleDeviceInVm.class,
    SnapshotDisabled.class,
    FilesystemQuiesceFault.class,
    ApplicationQuiesceFault.class,
    TooManySnapshotLevels.class,
    MultipleSnapshotsNotSupported.class,
    SnapshotNoChange.class,
    SnapshotLocked.class
})
public class SnapshotFault
    extends VimFault
{


}
