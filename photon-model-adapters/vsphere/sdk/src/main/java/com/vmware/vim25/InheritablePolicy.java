
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlSeeAlso;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for InheritablePolicy complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="InheritablePolicy">
 *   &lt;complexContent>
 *     &lt;extension base="{urn:vim25}DynamicData">
 *       &lt;sequence>
 *         &lt;element name="inherited" type="{http://www.w3.org/2001/XMLSchema}boolean"/>
 *       &lt;/sequence>
 *     &lt;/extension>
 *   &lt;/complexContent>
 * &lt;/complexType>
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "InheritablePolicy", propOrder = {
    "inherited"
})
@XmlSeeAlso({
    DVSTrafficShapingPolicy.class,
    VMwareUplinkLacpPolicy.class,
    VmwareUplinkPortTeamingPolicy.class,
    IntPolicy.class,
    DVSVendorSpecificConfig.class,
    DvsFilterPolicy.class,
    DVSFailureCriteria.class,
    LongPolicy.class,
    VMwareUplinkPortOrderPolicy.class,
    VmwareDistributedVirtualSwitchVlanSpec.class,
    DVSSecurityPolicy.class,
    DvsFilterConfig.class,
    StringPolicy.class,
    BoolPolicy.class
})
public class InheritablePolicy
    extends DynamicData
{

    protected boolean inherited;

    /**
     * Gets the value of the inherited property.
     * 
     */
    public boolean isInherited() {
        return inherited;
    }

    /**
     * Sets the value of the inherited property.
     * 
     */
    public void setInherited(boolean value) {
        this.inherited = value;
    }

}
