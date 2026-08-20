/*
 * -------------------------------------------------------------------------
 * 
 * Copyright (c) 2026 Fortrow LLC. All rights reserved.
 *  
 * NOTICE: All information contained herein is, and remains the property of 
 * Fortrow, LLC. The intellectual and technical concepts contained herein are 
 * proprietary to [Company Name] and may be covered by U.S. and Foreign Patents, 
 * patents in process, and are protected by trade secret or copyright law.
 * 
 * Dissemination of this information or reproduction of this material is 
 * strictly forbidden unless prior written permission is obtained from 
 * Fortrow, LLC.
 * 
 * -------------------------------------------------------------------------
 * 
 * File created by Ryan Fitzgerald (ryan@fortrow.com) on 2026-05-04 15:27:04 EDT
 * 
 * -------------------------------------------------------------------------

 */
package com.fortrow.jflux.threading.types;


import com.fortrow.jflux.threading.base.DetachesThread;

public abstract class DetachedThread extends DetachesThread {

    protected abstract boolean execute();

    protected void onStart() {  }

    protected void onStop() { }

    public DetachedThread() { }

    public DetachedThread(int idleMiilis) {
        this.idleMillis = Math.max(0, idleMiilis);
    }

    @Override
    public synchronized void start() {
        markStarted();
        thread = Thread.ofVirtual().name("jflux-detached-").unstarted(this::runLoop);
        thread.start();
    }

    @Override
    public void stop() {
        Thread current = thread;
        if (current != null) current.interrupt();
    }

}
