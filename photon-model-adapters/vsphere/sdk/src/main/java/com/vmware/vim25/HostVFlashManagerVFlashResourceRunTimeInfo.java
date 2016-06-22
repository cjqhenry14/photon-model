
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for HostVFlashManagerVFlashResourceRunTimeInfo complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="HostVFlashManagerVFlashResourceRunTimeInfo">
 *   &lt;complexContent>
 *     &lt;extension base="{urn:vim25}DynamicData">
 *       &lt;sequence>
 *         &lt;element name="usage" type="{http://www.w3.org/2001/XMLSchema}long"/>
 *         &lt;element name="capacity" type="{http://www.w3.org/2001/XMLSchema}long"/>
 *         &lt;element name="accessible" type="{http://www.w3.org/2001/XMLSchema}boolean"/>
 *         &lt;element name="capacityForVmCache" type="{http://www.w3.org/2001/XMLSchema}long"/>
 *         &lt;element name="freeForVmCache" type="{http://www.w3.org/2001/XMLSchema}long"/>
 *       &lt;/sequence>
 *     &lt;/extension>
 *   &lt;/complexContent>
 * &lt;/complexType>
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "HostVFlashManagerVFlashResourceRunTimeInfo", propOrder = {
    "usage",
    "capacity",
    "accessible",
    "capacityForVmCache",
    "freeForVmCache"
})
public class HostVFlashManagerVFlashResourceRunTimeInfo
    extends DynamicData
{

    protected long usage;
    protected long capacity;
    protected boolean accessible;
    protected long capacityForVmCache;
    protected long freeForVmCache;

    /**
     * Gets the value of the usage property.
     * 
     */
    public long getUsage() {
        return usage;
    }

    /**
     * Sets the value of the usage property.
     * 
     */
    public void setUsage(long value) {
        this.usage = value;
    }

    /**
     * Gets the value of the capacity property.
     * 
     */
    public long getCapacity() {
        return capacity;
    }

    /**
     * Sets the value of the capacity property.
     * 
     */
    public void setCapacity(long value) {
        this.capacity = value;
    }

    /**
     * Gets the value of the accessible property.
     * 
     */
    public boolean isAccessible() {
        return accessible;
    }

    /**
     * Sets the value of the accessible property.
     * 
     */
    public void setAccessible(boolean value) {
        this.accessible = value;
    }

    /**
     * Gets the value of the capacityForVmCache property.
     * 
     */
    public long getCapacityForVmCache() {
        return capacityForVmCache;
    }

    /**
     * Sets the value of the capacityForVmCache property.
     * 
     */
    public void setCapacityForVmCache(long value) {
        this.capacityForVmCache = value;
    }

    /**
     * Gets the value of the freeForVmCache property.
     * 
     */
    public long getFreeForVmCache() {
        return freeForVmCache;
    }

    /**
     * Sets the value of the freeForVmCache property.
     * 
     */
    public void setFreeForVmCache(long value) {
        this.freeForVmCache = value;
    }

}
