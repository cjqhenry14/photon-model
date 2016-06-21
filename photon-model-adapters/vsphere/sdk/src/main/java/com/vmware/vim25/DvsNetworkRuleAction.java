
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlSeeAlso;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for DvsNetworkRuleAction complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="DvsNetworkRuleAction">
 *   &lt;complexContent>
 *     &lt;extension base="{urn:vim25}DynamicData">
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
@XmlType(name = "DvsNetworkRuleAction")
@XmlSeeAlso({
    DvsLogNetworkRuleAction.class,
    DvsAcceptNetworkRuleAction.class,
    DvsPuntNetworkRuleAction.class,
    DvsDropNetworkRuleAction.class,
    DvsMacRewriteNetworkRuleAction.class,
    DvsUpdateTagNetworkRuleAction.class,
    DvsGreEncapNetworkRuleAction.class,
    DvsCopyNetworkRuleAction.class,
    DvsRateLimitNetworkRuleAction.class
})
public class DvsNetworkRuleAction
    extends DynamicData
{


}
