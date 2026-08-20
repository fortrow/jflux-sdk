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
package com.fortrow.jflux.sockets;

import com.fortrow.jflux.exceptions.ExceptionHandling;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.concurrent.atomic.AtomicBoolean;

public abstract class CallbackBufferedReader extends BufferedReader {

    private final AtomicBoolean closed = new AtomicBoolean();

    public CallbackBufferedReader(Reader in) {
        super(in);
    }

    @Override
    public void close() throws IOException {
        if (!closed.compareAndSet(false, true)) return;
        IOException failure = null;
        try {
            super.close();
        } catch (IOException e) {
            failure = e;
            ExceptionHandling.report(e);
        } finally {
            try {
                onDestroy();
            } catch (Throwable throwable) {
                ExceptionHandling.report(throwable);
            }
        }
        if (failure != null) throw failure;
    }

    public abstract void onDestroy();

}
