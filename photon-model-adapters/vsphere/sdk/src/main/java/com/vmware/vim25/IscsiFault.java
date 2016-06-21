
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlSeeAlso;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for IscsiFault complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="IscsiFault">
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
@XmlType(name = "IscsiFault")
@XmlSeeAlso({
    IscsiFaultVnicNotBound.class,
    IscsiFaultVnicIsLastPath.class,
    IscsiFaultVnicNotFound.class,
    IscsiFaultPnicInUse.class,
    IscsiFaultVnicHasActivePaths.class,
    IscsiFaultVnicInUse.class,
    IscsiFaultInvalidVnic.class,
    IscsiFaultVnicHasNoUplinks.class,
    IscsiFaultVnicAlreadyBound.class,
    IscsiFaultVnicHasWrongUplink.class,
    IscsiFaultVnicHasMultipleUplinks.class
})
public class IscsiFault
    extends VimFault
{


}
