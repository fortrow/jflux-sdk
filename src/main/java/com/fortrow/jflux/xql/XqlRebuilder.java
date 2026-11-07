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

package com.fortrow.jflux.xql;

import com.fortrow.jflux.exceptions.types.CommonIOException;
import java.util.concurrent.atomic.AtomicBoolean;

/** Durable queue worker. Failed jobs are returned to the queue before the worker stops. */
public final class XqlRebuilder implements AutoCloseable {
    private final XqlJobRepository jobs;
    private final XqlJobProcessor processor;
    private final AtomicBoolean running = new AtomicBoolean();
    private Thread worker;

    public XqlRebuilder(XqlJobRepository jobs, XqlJobProcessor processor) { this.jobs = jobs; this.processor = processor; }
    public synchronized void start() {
        if (!running.compareAndSet(false, true)) return;
        worker = Thread.ofVirtual().name("jflux-xql-rebuilder").start(this::run);
    }
    public boolean isRunning() { return running.get(); }
    public void await() throws InterruptedException { Thread thread = worker; if (thread != null) thread.join(); }
    private void run() {
        try {
            while (running.get()) {
                XqlJob job = jobs.claim();
                if (job == null) { Thread.sleep(25); continue; }
                try { processor.process(job); jobs.acknowledge(job); }
                catch (CommonIOException | XqlException e) { jobs.enqueue(job); e.report(); break; }
            }
        } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        catch (CommonIOException e) { e.report(); }
        finally { running.set(false); }
    }
    @Override public synchronized void close() { running.set(false); if (worker != null) worker.interrupt(); }
}
