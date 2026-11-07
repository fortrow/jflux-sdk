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
 */

package com.fortrow.jflux.threading;

import com.fortrow.jflux.threading.types.DetachedThread;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

class ThreadLifecycleTest {

    @Test
    void detachedThreadsUseVirtualThreadsAndCanBeAwaited() throws InterruptedException {
        DetachedThread thread = new DetachedThread() {
            @Override
            protected boolean execute() {
                return false;
            }
        };

        thread.start();

        assertTrue(thread.await(Duration.ofSeconds(2)));
        assertTrue(thread.isStarted());
        assertTrue(thread.isVirtual());
        assertFalse(thread.isActive());
        assertNull(thread.getFailure());
    }

    @Test
    void threadFailuresAreRetainedAndDoNotEscapeTheWorker() throws InterruptedException {
        DetachedThread thread = new DetachedThread() {
            @Override
            protected boolean execute() {
                throw new IllegalStateException("expected failure");
            }
        };

        thread.start();

        assertTrue(thread.await(Duration.ofSeconds(2)));
        assertNotNull(thread.getFailure());
        assertEquals("expected failure", thread.getFailure().getMessage());
    }
}
