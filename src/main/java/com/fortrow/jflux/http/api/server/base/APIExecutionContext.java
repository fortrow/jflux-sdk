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
 * File created by Ryan Fitzgerald (ryan@fortrow.com) on 2026-05-11 15:09:04 EDT
 * 
 * -------------------------------------------------------------------------

 */
package com.fortrow.jflux.http.api.server.base;

import com.fortrow.jflux.http.api.server.exceptions.APIExecutionCancelledException;

import java.util.concurrent.atomic.AtomicBoolean;

public final class APIExecutionContext {

    private final long startedAtMs = System.currentTimeMillis();
    private final int timeoutMs;
    private final AtomicBoolean cancelled = new AtomicBoolean(false);
    private volatile Thread thread;
    private volatile String cancelReason;

    public APIExecutionContext(int timeoutMs) {
        this.timeoutMs = Math.max(0, timeoutMs);
    }

    void attach(Thread thread) {
        this.thread = thread;
    }

    public long getStartedAtMs() {
        return startedAtMs;
    }

    public int getTimeoutMs() {
        return timeoutMs;
    }

    public boolean isCancelled() {
        return cancelled.get() || Thread.currentThread().isInterrupted() || isTimedOut();
    }

    public boolean isTimedOut() {
        return timeoutMs > 0 && System.currentTimeMillis() - startedAtMs >= timeoutMs;
    }

    public String getCancelReason() {
        if(cancelReason != null) return cancelReason;
        if(isTimedOut()) return "Request timed out.";
        if(Thread.currentThread().isInterrupted()) return "Request interrupted.";
        return "Request cancelled.";
    }

    public void cancel(String reason) {
        cancelReason = reason == null || reason.isBlank() ? "Request cancelled." : reason;
        cancelled.set(true);
        Thread current = thread;
        if(current != null) current.interrupt();
    }

    public void checkpoint() throws APIExecutionCancelledException {
        if(isCancelled()) throw new APIExecutionCancelledException(getCancelReason());
    }

    public void sleep(long millis) throws APIExecutionCancelledException {
        checkpoint();
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new APIExecutionCancelledException("Request interrupted.");
        }
        checkpoint();
    }
}
