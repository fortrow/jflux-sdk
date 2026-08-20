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
 * File created by Ryan Fitzgerald (ryan@fortrow.com) on 2026-05-11 14:44:42 EDT
 * 
 * -------------------------------------------------------------------------

 */
package com.fortrow.jflux.threading;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ThreadFanoutTest {

    @Test
    void runsAllTasksAndWaitsForCompletion() throws InterruptedException {
        ThreadFanout fanout = new ThreadFanout();
        AtomicInteger counter = new AtomicInteger();

        fanout.append(counter::incrementAndGet);
        fanout.append(counter::incrementAndGet);
        fanout.append(counter::incrementAndGet);

        fanout.startAndWait();

        assertEquals(3, counter.get());
        assertFalse(fanout.failed());
    }

    @Test
    void capturesFirstTaskFailureAndStillWaitsForOtherTasks() throws InterruptedException {
        ThreadFanout fanout = new ThreadFanout();
        AtomicInteger counter = new AtomicInteger();

        fanout.append(() -> { throw new IllegalStateException("boom"); });
        fanout.append(counter::incrementAndGet);

        fanout.startAndWait();

        assertEquals(1, counter.get());
        assertTrue(fanout.failed());
        assertEquals("boom", fanout.getFailure().getMessage());
    }

    @Test
    void rejectsAppendingAfterStart() throws InterruptedException {
        ThreadFanout fanout = new ThreadFanout();

        fanout.startAndWait();

        assertThrows(IllegalStateException.class, () -> fanout.append(() -> { }));
    }
}
