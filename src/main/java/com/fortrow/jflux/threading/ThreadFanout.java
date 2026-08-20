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
 * File created by Ryan Fitzgerald (ryan@fortrow.com) on 2026-05-11 14:43:54 EDT
 * 
 * -------------------------------------------------------------------------

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
