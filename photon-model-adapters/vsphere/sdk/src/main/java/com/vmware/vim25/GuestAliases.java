
package com.vmware.vim25;

import java.util.ArrayList;
import java.util.List;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for GuestAliases complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="GuestAliases">
 *   &lt;complexContent>
 *     &lt;extension base="{urn:vim25}DynamicData">
 *       &lt;sequence>
 *         &lt;element name="base64Cert" type="{http://www.w3.org/2001/XMLSchema}string"/>
 *         &lt;element name="aliases" type="{urn:vim25}GuestAuthAliasInfo" maxOccurs="unbounded"/>
 *       &lt;/sequence>
 *     &lt;/extension>
 *   &lt;/complexContent>
 * &lt;/complexType>
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "GuestAliases", propOrder = {
    "base64Cert",
    "aliases"
})
public class GuestAliases
    extends DynamicData
{

    @XmlElement(required = true)
    protected String base64Cert;
    @XmlElement(required = true)
    protected List<GuestAuthAliasInfo> aliases;

    /**
     * Gets the value of the base64Cert property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getBase64Cert() {
        return base64Cert;
    }

    /**
     * Sets the value of the base64Cert property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setBase64Cert(String value) {
        this.base64Cert = value;
    }

    /**
     * Gets the value of the aliases property.
     * 
     * <p>
     * This accessor method returns a reference to the live list,
     * not a snapshot. Therefore any modification you make to the
     * returned list will be present inside the JAXB object.
     * This is why there is not a <CODE>set</CODE> method for the aliases property.
     * 
     * <p>
     * For example, to add a new item, do as follows:
     * <pre>
     *    getAliases().add(newItem);
     * </pre>
     * 
     * 
     * <p>
     * Objects of the following type(s) are allowed in the list
     * {@link GuestAuthAliasInfo }
     * 
     * 
     */
    public List<GuestAuthAliasInfo> getAliases() {
        if (aliases == null) {
            aliases = new ArrayList<GuestAuthAliasInfo>();
        }
        return this.aliases;
    }

}
