
package com.vmware.vim25;

import java.util.ArrayList;
import java.util.List;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for VsanUpgradeSystemRogueHostsInClusterIssue complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="VsanUpgradeSystemRogueHostsInClusterIssue">
 *   &lt;complexContent>
 *     &lt;extension base="{urn:vim25}VsanUpgradeSystemPreflightCheckIssue">
 *       &lt;sequence>
 *         &lt;element name="uuids" type="{http://www.w3.org/2001/XMLSchema}string" maxOccurs="unbounded"/>
 *       &lt;/sequence>
 *     &lt;/extension>
 *   &lt;/complexContent>
 * &lt;/complexType>
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "VsanUpgradeSystemRogueHostsInClusterIssue", propOrder = {
    "uuids"
})
public class VsanUpgradeSystemRogueHostsInClusterIssue
    extends VsanUpgradeSystemPreflightCheckIssue
{

    @XmlElement(required = true)
    protected List<String> uuids;

    /**
     * Gets the value of the uuids property.
     * 
     * <p>
     * This accessor method returns a reference to the live list,
     * not a snapshot. Therefore any modification you make to the
     * returned list will be present inside the JAXB object.
     * This is why there is not a <CODE>set</CODE> method for the uuids property.
     * 
     * <p>
     * For example, to add a new item, do as follows:
     * <pre>
     *    getUuids().add(newItem);
     * </pre>
     * 
     * 
     * <p>
     * Objects of the following type(s) are allowed in the list
     * {@link String }
     * 
     * 
     */
    public List<String> getUuids() {
        if (uuids == null) {
            uuids = new ArrayList<String>();
        }
        return this.uuids;
    }

}
