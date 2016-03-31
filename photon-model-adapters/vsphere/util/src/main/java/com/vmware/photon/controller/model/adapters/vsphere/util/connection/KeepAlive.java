/*
 * Copyright (c) 2015-2016 VMware, Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License.  You may obtain a copy of
 * the License at http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed
 * under the License is distributed on an "AS IS" BASIS, without warranties or
 * conditions of any kind, EITHER EXPRESS OR IMPLIED.  See the License for the
 * specific language governing permissions and limitations under the License.
 */

package com.vmware.photon.controller.model.adapters.vsphere.util.connection;

import com.vmware.vim25.ManagedObjectReference;
import com.vmware.vim25.RuntimeFaultFaultMsg;
import com.vmware.vim25.VimPortType;

/**
 * This is a keep-alive utility class. It will keep an instance of a connection alive by polling the "currentTime"
 * method on the remote Host or vCenter that the supplied connection and VimPortType were talking to.
 * @see VimPortType
 */
public class KeepAlive implements Runnable {
    public static final Long DEFAULT_INTERVAL = 300000L;
    private boolean verbose = Boolean
            .parseBoolean(System.getProperty("keep-alive.verbose", "false"));
    private Boolean running;
    private final Long interval;
    private final VimPortType vimPort;
    private final ManagedObjectReference serviceInstanceReference;

    /**
     * this class is immutable and acts on the supplied vimPort and serviceInstanceReference the default
     * interval is set to 300000 milliseconds
     * @param vimPort
     * @param serviceInstanceReference
     */
    public KeepAlive(final VimPortType vimPort,
            final ManagedObjectReference serviceInstanceReference) {
        this(vimPort, serviceInstanceReference, DEFAULT_INTERVAL);
    }

    /**
     * builds an instance of this object
     * @param vimPort
     * @param serviceInstanceReference
     * @param interval
     */
    public KeepAlive(final VimPortType vimPort,
            final ManagedObjectReference serviceInstanceReference, final Long interval) {
        this.vimPort = vimPort;
        this.serviceInstanceReference = serviceInstanceReference;
        this.interval = interval;
        this.running = Boolean.TRUE;
    }

    /**
     * kicks off a thread that will call the "keep alive" method on the connection instance
     */
    public void keepAlive() {
        try {
            run(this.vimPort, this.serviceInstanceReference);
        } catch (RuntimeFaultFaultMsg runtimeFaultFaultMsg) {
            runtimeFaultFaultMsg.printStackTrace();
        } catch (Exception e) {
            stop();
        }
    }

    /**
     * calls "currentTime" against the supplied objects
     * @param vimPort
     * @param serviceInstanceRef
     * @throws RuntimeFaultFaultMsg
     */
    public static void run(final VimPortType vimPort,
            final ManagedObjectReference serviceInstanceRef) throws RuntimeFaultFaultMsg {
        vimPort.currentTime(serviceInstanceRef);
    }

    /**
     * @return true if the embedded thread is running
     */
    public boolean isRunning() {
        final Boolean val;
        synchronized (this.running) {
            val = this.running;
        }
        return val;
    }

    /**
     * signals the embedded thread to stop
     */
    public void stop() {
        synchronized (this.running) {
            if (this.verbose) {
                System.out.println("keep alive stopped");
            }
            this.running = false;
        }
    }

    /**
     * starts a keep-alive thread which will call keepAlive then sleep for the interval
     */
    @Override
    public void run() {
        synchronized (this.running) {
            this.running = true;
        }
        try {
            while (isRunning()) {
                if (this.verbose) {
                    System.out.println("keep alive");
                }
                keepAlive();
                Thread.sleep(this.interval);
            }
        } catch (Throwable t) {
            stop();
        }
    }

    /**
     * Returns a thread you can start to run a keep alive on your connection. You supply it with your copy of
     * the vimPort and serviceInstanceRef to ping. Call start on the thread when you need to start the keep-alive.
     *
     * @param vimPort
     * @param serviceInstanceRef
     * @return
     */
    public static Thread keepAlive(VimPortType vimPort, ManagedObjectReference serviceInstanceRef) {
        return keepAlive(vimPort, serviceInstanceRef, DEFAULT_INTERVAL);
    }

    /**
     * constructs a new embedded thread to keep alive
     * @param vimPort
     * @param serviceInstanceRef
     * @param interval
     * @return
     */
    public static Thread keepAlive(VimPortType vimPort, ManagedObjectReference serviceInstanceRef,
            Long interval) {
        Thread thread = new Thread(new KeepAlive(vimPort, serviceInstanceRef, interval));
        return thread;
    }
}
