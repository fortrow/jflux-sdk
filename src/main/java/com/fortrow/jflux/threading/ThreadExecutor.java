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
package com.fortrow.jflux.threading;

import com.fortrow.jflux.exceptions.ExceptionHandling;
import com.fortrow.jflux.threading.types.DetachedThread;

import java.util.ArrayList;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

public final class ThreadExecutor implements Executor, AutoCloseable {

    public static final int DEFAULT_QUEUE_CAPACITY = 1_024;

    private final BlockingQueue<Runnable> queue;
    private final ArrayList<DetachedThread> workers = new ArrayList<>();
    private final int workerCount;
    private volatile boolean closed;

    public ThreadExecutor(int workerCount) {
        this(workerCount, DEFAULT_QUEUE_CAPACITY);
    }

    public ThreadExecutor(int workerCount, int queueCapacity) {
        int count = Math.max(1, workerCount);
        if (queueCapacity < 1) throw ExceptionHandling.invalid("Queue capacity must be positive.");
        this.workerCount = count;
        this.queue = new ArrayBlockingQueue<>(queueCapacity);
        for(int i = 0; i < count; i++) {
            DetachedThread worker = new DetachedThread() {
                @Override
                protected boolean execute() {
                    if(closed) return false;
                    try {
                        Runnable task = queue.take();
                        try {
                            task.run();
                        } catch (Throwable throwable) {
                            ExceptionHandling.report(throwable);
                        }
                        return !closed;
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return false;
                    }
                }
            };
            workers.add(worker);
            worker.start();
        }
    }

    @Override
    public void execute(Runnable command) {
        if(command == null) return;
        if(closed) throw ExceptionHandling.failure("ThreadExecutor is closed.", null);
        if(!queue.offer(command)) throw ExceptionHandling.failure("ThreadExecutor queue is full.", null);
    }

    public int getWorkerCount() {
        return workerCount;
    }

    public int getQueueSize() {
        return queue.size();
    }

    public boolean isClosed() {
        return closed;
    }

    public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
        if (unit == null) throw ExceptionHandling.invalid("Time unit is required.");
        long deadline = System.nanoTime() + unit.toNanos(timeout);
        for (DetachedThread worker : workers) {
            long remaining = deadline - System.nanoTime();
            if (remaining <= 0) return false;
            if (!worker.await(java.time.Duration.ofNanos(remaining))) return false;
        }
        return true;
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        for(DetachedThread worker : workers) {
            worker.stop();
        }
        try {
            awaitTermination(1, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            ExceptionHandling.report(e);
        }
        workers.clear();
        queue.clear();
    }
}
