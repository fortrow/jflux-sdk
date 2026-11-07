/*
 * Copyright (c) 2026 Fortrow LLC.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Fortrow-Commercial
 *
 * JFlux is free software: you can redistribute it and/or modify it under the
 * terms of the GNU Affero General Public License, version 3, as published by
 * the Free Software Foundation.
 *
 * JFlux is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Affero General Public License for
 * more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with JFlux. If not, see <https://www.gnu.org/licenses/>.
 *
 * Alternatively, a separate commercial license is available from Fortrow LLC.
 * For terms permitting use without the AGPL copyleft requirements, contact
 * ryan@fortrow.com. Commercial rights require a separate written agreement.
 * See LICENSE and COMMERCIAL-LICENSE.md.
 *
 * File created by Ryan Fitzgerald (ryan@fortrow.com) on 2026-05-11 15:09:04 EDT
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
