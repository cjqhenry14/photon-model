
package com.vmware.vim25;

import java.util.ArrayList;
import java.util.List;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for ArrayOfHostVsanInternalSystemVsanPhysicalDiskDiagnosticsResult complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="ArrayOfHostVsanInternalSystemVsanPhysicalDiskDiagnosticsResult">
 *   &lt;complexContent>
 *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType">
 *       &lt;sequence>
 *         &lt;element name="HostVsanInternalSystemVsanPhysicalDiskDiagnosticsResult" type="{urn:vim25}HostVsanInternalSystemVsanPhysicalDiskDiagnosticsResult" maxOccurs="unbounded" minOccurs="0"/>
 *       &lt;/sequence>
 *     &lt;/restriction>
 *   &lt;/complexContent>
 * &lt;/complexType>
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "ArrayOfHostVsanInternalSystemVsanPhysicalDiskDiagnosticsResult", propOrder = {
    "hostVsanInternalSystemVsanPhysicalDiskDiagnosticsResult"
})
public class ArrayOfHostVsanInternalSystemVsanPhysicalDiskDiagnosticsResult {

    @XmlElement(name = "HostVsanInternalSystemVsanPhysicalDiskDiagnosticsResult")
    protected List<HostVsanInternalSystemVsanPhysicalDiskDiagnosticsResult> hostVsanInternalSystemVsanPhysicalDiskDiagnosticsResult;

    /**
     * Gets the value of the hostVsanInternalSystemVsanPhysicalDiskDiagnosticsResult property.
     * 
     * <p>
     * This accessor method returns a reference to the live list,
     * not a snapshot. Therefore any modification you make to the
     * returned list will be present inside the JAXB object.
     * This is why there is not a <CODE>set</CODE> method for the hostVsanInternalSystemVsanPhysicalDiskDiagnosticsResult property.
     * 
     * <p>
     * For example, to add a new item, do as follows:
     * <pre>
     *    getHostVsanInternalSystemVsanPhysicalDiskDiagnosticsResult().add(newItem);
     * </pre>
     * 
     * 
     * <p>
     * Objects of the following type(s) are allowed in the list
     * {@link HostVsanInternalSystemVsanPhysicalDiskDiagnosticsResult }
     * 
     * 
     */
    public List<HostVsanInternalSystemVsanPhysicalDiskDiagnosticsResult> getHostVsanInternalSystemVsanPhysicalDiskDiagnosticsResult() {
        if (hostVsanInternalSystemVsanPhysicalDiskDiagnosticsResult == null) {
            hostVsanInternalSystemVsanPhysicalDiskDiagnosticsResult = new ArrayList<HostVsanInternalSystemVsanPhysicalDiskDiagnosticsResult>();
        }
        return this.hostVsanInternalSystemVsanPhysicalDiskDiagnosticsResult;
    }

}
