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
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/** In-process change source useful for adapters, tests, and application webhooks. */
public final class QueueXqlChangeSource implements XqlChangeSource {
    private final BlockingQueue<XqlChangeEvent> events = new LinkedBlockingQueue<>();
    private volatile boolean closed;
    public void publish(XqlChangeEvent event) { if (closed) throw new IllegalStateException("Change source is closed."); events.add(event); }
    @Override public XqlChangeEvent next() throws InterruptedException { return closed && events.isEmpty() ? null : events.take(); }
    @Override public void close() { closed = true; events.offer(new XqlChangeEvent("__closed__", XqlChangeEvent.Operation.DELETE, java.util.Map.of())); }
}
