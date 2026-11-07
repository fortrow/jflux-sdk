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
package com.fortrow.jflux.stdout.tui;

import java.io.PrintStream;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Animated terminal spinner using Ryel brand colors and braille frames.
 */
public final class TerminalSpinner implements AutoCloseable {

    private static final String[] BRAILLE = {"⠋", "⠙", "⠹", "⠸", "⠼", "⠴", "⠦", "⠧", "⠇", "⠏"};
    private static final String[] PULSE = {"●", "◐", "○", "◑"};

    private final PrintStream out;
    private final ScheduledExecutorService scheduler;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private ScheduledFuture<?> handle;
    private volatile String message;
    private int frameIndex = 0;

    public TerminalSpinner() {
        this(System.out);
    }

    public TerminalSpinner(PrintStream out) {
        this.out = out != null ? out : System.out;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "jflux-tui-spinner");
            t.setDaemon(true);
            return t;
        });
    }

    public synchronized TerminalSpinner start(String message) {
        this.message = message == null ? "" : message;
        if (running.compareAndSet(false, true)) {
            handle = scheduler.scheduleAtFixedRate(this::tick, 0, 80, TimeUnit.MILLISECONDS);
        }
        return this;
    }

    public synchronized TerminalSpinner update(String message) {
        this.message = message == null ? "" : message;
        return this;
    }

    private void tick() {
        if (!running.get()) return;
        String glyph = AnsiStyle.amber(BRAILLE[frameIndex % BRAILLE.length]);
        frameIndex++;
        out.print("\r" + glyph + " " + message + "   ");
        out.flush();
    }

    public synchronized void success(String finalMessage) {
        stop();
        out.print("\r" + AnsiStyle.mint("✔") + " " + (finalMessage != null ? finalMessage : message) + "\n");
        out.flush();
    }

    public synchronized void fail(String errorMessage) {
        stop();
        out.print("\r" + AnsiStyle.coral("✖") + " " + (errorMessage != null ? errorMessage : message) + "\n");
        out.flush();
    }

    public synchronized void stop() {
        if (running.compareAndSet(true, false)) {
            if (handle != null) handle.cancel(false);
            out.print("\r\033[2K");
            out.flush();
        }
    }

    @Override
    public void close() {
        stop();
        scheduler.shutdownNow();
    }
}
