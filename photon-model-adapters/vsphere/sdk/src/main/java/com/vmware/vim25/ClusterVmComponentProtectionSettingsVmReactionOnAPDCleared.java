
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlEnum;
import javax.xml.bind.annotation.XmlEnumValue;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for ClusterVmComponentProtectionSettingsVmReactionOnAPDCleared.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * <p>
 * <pre>
 * &lt;simpleType name="ClusterVmComponentProtectionSettingsVmReactionOnAPDCleared">
 *   &lt;restriction base="{http://www.w3.org/2001/XMLSchema}string">
 *     &lt;enumeration value="none"/>
 *     &lt;enumeration value="reset"/>
 *     &lt;enumeration value="useClusterDefault"/>
 *   &lt;/restriction>
 * &lt;/simpleType>
 * </pre>
 * 
 */
@XmlType(name = "ClusterVmComponentProtectionSettingsVmReactionOnAPDCleared")
@XmlEnum
public enum ClusterVmComponentProtectionSettingsVmReactionOnAPDCleared {

    @XmlEnumValue("none")
    NONE("none"),
    @XmlEnumValue("reset")
    RESET("reset"),
    @XmlEnumValue("useClusterDefault")
    USE_CLUSTER_DEFAULT("useClusterDefault");
    private final String value;

    ClusterVmComponentProtectionSettingsVmReactionOnAPDCleared(String v) {
        value = v;
    }

    public String value() {
        return value;
    }

    public static ClusterVmComponentProtectionSettingsVmReactionOnAPDCleared fromValue(String v) {
        for (ClusterVmComponentProtectionSettingsVmReactionOnAPDCleared c: ClusterVmComponentProtectionSettingsVmReactionOnAPDCleared.values()) {
            if (c.value.equals(v)) {
                return c;
            }
        }
        throw new IllegalArgumentException(v);
    }

}
