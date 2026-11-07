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
 * File created by Ryan Fitzgerald (ryan@fortrow.com) on 2026-05-04 15:27:04 EDT
 */
package com.fortrow.jflux.threading.base;

import com.fortrow.jflux.exceptions.ExceptionHandling;
import com.fortrow.jflux.todash.__;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public abstract class ThreadImpl {

    public ThreadImpl() { }

    protected volatile int idleMillis = 0;
    protected volatile Thread thread;
    private final ConcurrentMap<String, Object> vars = new ConcurrentHashMap<>();
    private volatile boolean started;
    private volatile boolean active;
    private volatile Throwable failure;

    public boolean issetVar(String name) {
        return name != null && vars.containsKey(name) && __.isset(vars.get(name));
    }

    public void setVar(String name, Object value) {
        if (name == null || name.isBlank()) throw ExceptionHandling.invalid("Thread variable name is required.");
        if (value == null) vars.remove(name);
        else vars.put(name, value);
    }

    public Object getVar(String name) {
        return name == null ? null : vars.get(name);
    }

    public void removeVar(String name) {
        if (name != null) vars.remove(name);
    }

    public boolean isStarted() {
        return started;
    }

    public boolean isActive() {
        return active;
    }

    public boolean isVirtual() {
        Thread current = thread;
        return current != null && current.isVirtual();
    }

    public Throwable getFailure() {
        return failure;
    }

    public void clearFailure() {
        failure = null;
    }

    /** Waits for this thread to finish without changing the caller's interrupt status. */
    public boolean await(Duration timeout) throws InterruptedException {
        if (timeout == null || timeout.isNegative()) {
            throw ExceptionHandling.invalid("Thread wait timeout cannot be negative.");
        }
        Thread current = thread;
        if (current == null) return true;
        current.join(timeout.toMillis());
        return !current.isAlive();
    }

    protected final void runLoop() {
        active = true;
        try {
            onStart();
            boolean keepExecuting = true;
            while (keepExecuting && !Thread.currentThread().isInterrupted()) {
                try {
                    keepExecuting = execute();
                } catch (Throwable throwable) {
                    failure = throwable;
                    ExceptionHandling.report(throwable);
                    keepExecuting = false;
                }
                if (keepExecuting && idleMillis > 0) {
                    try {
                        Thread.sleep(idleMillis);
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                    }
                }
            }
        } catch (Throwable throwable) {
            failure = throwable;
            ExceptionHandling.report(throwable);
        } finally {
            try {
                onStop();
            } catch (Throwable throwable) {
                failure = throwable;
                ExceptionHandling.report(throwable);
            }
            active = false;
        }
    }

    protected final void markStarted() {
        if (started) throw ExceptionHandling.failure("Thread has already been started.", null);
        started = true;
    }

    public abstract boolean doesJoin();
    protected abstract boolean execute();
    protected abstract void onStart();
    protected abstract void onStop();
    public abstract void start();
    public abstract void stop();

}
