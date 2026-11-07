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
 * File created by Ryan Fitzgerald (ryan@fortrow.com) on 2026-05-11 15:14:17 EDT
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
