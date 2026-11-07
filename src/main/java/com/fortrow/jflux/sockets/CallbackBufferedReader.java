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
 * File created by Ryan Fitzgerald (ryan@fortrow.com) on 2026-05-04 15:27:04 EDT
 */
package com.fortrow.jflux.sockets;

import com.fortrow.jflux.exceptions.ExceptionHandling;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.concurrent.atomic.AtomicBoolean;

public abstract class CallbackBufferedReader extends BufferedReader {

    private final AtomicBoolean closed = new AtomicBoolean();

    public CallbackBufferedReader(Reader in) {
        super(in);
    }

    @Override
    public void close() throws IOException {
        if (!closed.compareAndSet(false, true)) return;
        IOException failure = null;
        try {
            super.close();
        } catch (IOException e) {
            failure = e;
            ExceptionHandling.report(e);
        } finally {
            try {
                onDestroy();
            } catch (Throwable throwable) {
                ExceptionHandling.report(throwable);
            }
        }
        if (failure != null) throw failure;
    }

    public abstract void onDestroy();

}
