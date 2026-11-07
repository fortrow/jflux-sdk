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
 * File created by Ryan Fitzgerald (ryan@fortrow.com) on 2026-05-11 14:43:54 EDT
 */
package com.fortrow.jflux.threading;

import com.fortrow.jflux.exceptions.ExceptionHandling;
import com.fortrow.jflux.threading.types.DetachedThread;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

public final class ThreadFanout {

    private final List<Task> tasks = new ArrayList<>();
    private final ArrayList<DetachedThread> workers = new ArrayList<>();
    private final AtomicReference<Throwable> failure = new AtomicReference<>();
    private volatile boolean started;

    public synchronized void append(Task task) {
        if(started) throw ExceptionHandling.failure("Cannot append tasks after ThreadFanout has started.", null);
        if(task != null) tasks.add(task);
    }

    public int size() {
        return tasks.size();
    }

    public synchronized void startAndWait() throws InterruptedException {
        if(started) throw ExceptionHandling.failure("ThreadFanout can only be started once.", null);
        started = true;
        if(tasks.isEmpty()) return;

        List<Task> snapshot = List.copyOf(tasks);
        CountDownLatch latch = new CountDownLatch(snapshot.size());
        for(Task task : snapshot) {
            DetachedThread worker = new DetachedThread() {
                @Override
                protected boolean execute() {
                    try {
                        task.run();
                    } catch (Throwable e) {
                        failure.compareAndSet(null, e);
                        ExceptionHandling.report(e);
                    } finally {
                        latch.countDown();
                    }
                    return false;
                }
            };
            workers.add(worker);
            worker.start();
        }
        try {
            latch.await();
        } catch (InterruptedException interrupted) {
            stop();
            Thread.currentThread().interrupt();
            throw interrupted;
        } finally {
            workers.clear();
        }
    }

    public void stop() {
        for(DetachedThread worker : workers) {
            worker.stop();
        }
    }

    public boolean failed() {
        return failure.get() != null;
    }

    public Throwable getFailure() {
        return failure.get();
    }

    @FunctionalInterface
    public interface Task {
        void run() throws Exception;
    }
}
