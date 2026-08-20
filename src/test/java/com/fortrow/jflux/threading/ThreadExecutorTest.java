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
 * File created by Ryan Fitzgerald (ryan@fortrow.com) on 2026-05-11 15:14:17 EDT
 * 
 * -------------------------------------------------------------------------

 */
package com.fortrow.jflux.threading;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ThreadExecutorTest {

    @Test
    void executesQueuedTasksOnWorkers() throws InterruptedException {
        ThreadExecutor executor = new ThreadExecutor(2);
        CountDownLatch latch = new CountDownLatch(3);
        AtomicInteger count = new AtomicInteger();

        try {
            executor.execute(() -> { count.incrementAndGet(); latch.countDown(); });
            executor.execute(() -> { count.incrementAndGet(); latch.countDown(); });
            executor.execute(() -> { count.incrementAndGet(); latch.countDown(); });

            assertTrue(latch.await(2, TimeUnit.SECONDS));
            assertEquals(3, count.get());
        } finally {
            executor.close();
        }
    }

    @Test
    void rejectsTasksAfterClose() {
        ThreadExecutor executor = new ThreadExecutor(1);
        executor.close();

        assertThrows(IllegalStateException.class, () -> executor.execute(() -> { }));
    }
}
