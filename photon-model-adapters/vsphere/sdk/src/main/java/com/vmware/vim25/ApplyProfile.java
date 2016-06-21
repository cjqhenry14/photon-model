
package com.vmware.vim25;

import java.util.ArrayList;
import java.util.List;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlSeeAlso;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for ApplyProfile complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="ApplyProfile">
 *   &lt;complexContent>
 *     &lt;extension base="{urn:vim25}DynamicData">
 *       &lt;sequence>
 *         &lt;element name="enabled" type="{http://www.w3.org/2001/XMLSchema}boolean"/>
 *         &lt;element name="policy" type="{urn:vim25}ProfilePolicy" maxOccurs="unbounded" minOccurs="0"/>
 *         &lt;element name="profileTypeName" type="{http://www.w3.org/2001/XMLSchema}string" minOccurs="0"/>
 *         &lt;element name="profileVersion" type="{http://www.w3.org/2001/XMLSchema}string" minOccurs="0"/>
 *         &lt;element name="property" type="{urn:vim25}ProfileApplyProfileProperty" maxOccurs="unbounded" minOccurs="0"/>
 *       &lt;/sequence>
 *     &lt;/extension>
 *   &lt;/complexContent>
 * &lt;/complexType>
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "ApplyProfile", propOrder = {
    "enabled",
    "policy",
    "profileTypeName",
    "profileVersion",
    "property"
})
@XmlSeeAlso({
    NetworkPolicyProfile.class,
    HostMemoryProfile.class,
    UserGroupProfile.class,
    SecurityProfile.class,
    VirtualSwitchProfile.class,
    NetworkProfileDnsConfigProfile.class,
    ActiveDirectoryProfile.class,
    VlanProfile.class,
    StorageProfile.class,
    UserProfile.class,
    IpRouteProfile.class,
    NetworkProfile.class,
    FirewallProfile.class,
    PnicUplinkProfile.class,
    PhysicalNicProfile.class,
    NetStackInstanceProfile.class,
    AuthenticationProfile.class,
    OptionProfile.class,
    NasStorageProfile.class,
    IpAddressProfile.class,
    DvsProfile.class,
    FirewallProfileRulesetProfile.class,
    HostApplyProfile.class,
    ServiceProfile.class,
    ProfileApplyProfileElement.class,
    PermissionProfile.class,
    VirtualSwitchSelectionProfile.class,
    DvsVNicProfile.class,
    StaticRouteProfile.class,
    LinkProfile.class,
    NumPortsProfile.class,
    PortGroupProfile.class,
    DateTimeProfile.class
})
public class ApplyProfile
    extends DynamicData
{

    protected boolean enabled;
    protected List<ProfilePolicy> policy;
    protected String profileTypeName;
    protected String profileVersion;
    protected List<ProfileApplyProfileProperty> property;

    /**
     * Gets the value of the enabled property.
     * 
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Sets the value of the enabled property.
     * 
     */
    public void setEnabled(boolean value) {
        this.enabled = value;
    }

    /**
     * Gets the value of the policy property.
     * 
     * <p>
     * This accessor method returns a reference to the live list,
     * not a snapshot. Therefore any modification you make to the
     * returned list will be present inside the JAXB object.
     * This is why there is not a <CODE>set</CODE> method for the policy property.
     * 
     * <p>
     * For example, to add a new item, do as follows:
     * <pre>
     *    getPolicy().add(newItem);
     * </pre>
     * 
     * 
     * <p>
     * Objects of the following type(s) are allowed in the list
     * {@link ProfilePolicy }
     * 
     * 
     */
    public List<ProfilePolicy> getPolicy() {
        if (policy == null) {
            policy = new ArrayList<ProfilePolicy>();
        }
        return this.policy;
    }

    /**
     * Gets the value of the profileTypeName property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getProfileTypeName() {
        return profileTypeName;
    }

    /**
     * Sets the value of the profileTypeName property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setProfileTypeName(String value) {
        this.profileTypeName = value;
    }

    /**
     * Gets the value of the profileVersion property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getProfileVersion() {
        return profileVersion;
    }

    /**
     * Sets the value of the profileVersion property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setProfileVersion(String value) {
        this.profileVersion = value;
    }

    /**
     * Gets the value of the property property.
     * 
     * <p>
     * This accessor method returns a reference to the live list,
     * not a snapshot. Therefore any modification you make to the
     * returned list will be present inside the JAXB object.
     * This is why there is not a <CODE>set</CODE> method for the property property.
     * 
     * <p>
     * For example, to add a new item, do as follows:
     * <pre>
     *    getProperty().add(newItem);
     * </pre>
     * 
     * 
     * <p>
     * Objects of the following type(s) are allowed in the list
     * {@link ProfileApplyProfileProperty }
     * 
     * 
     */
    public List<ProfileApplyProfileProperty> getProperty() {
        if (property == null) {
            property = new ArrayList<ProfileApplyProfileProperty>();
        }
        return this.property;
    }

}
