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
package com.fortrow.jflux.shell.unix;

import com.fortrow.jflux.shell.CommandImpl;
import com.fortrow.jflux.exceptions.ExceptionHandling;
import com.fortrow.jflux.threading.types.DetachedThread;
import com.fortrow.jflux.todash.__;

import java.nio.charset.StandardCharsets;

public abstract class UnixCommand implements CommandImpl {

    @Override
    public void noOut(String cmd) throws Exception {
        com.fortrow.jflux.shell.Shell.startDetached(cmd);
    }

    @Override
    public int runAndWait(String cmd) throws Exception {
        return com.fortrow.jflux.shell.Shell.execute(cmd).exitCode();
    }

    @Override
    public int execute(String cmd) throws Exception {
        com.fortrow.jflux.shell.CommandResult result = com.fortrow.jflux.shell.Shell.execute(cmd);
        for (String line : result.stdout().split("\\R")) {
            if (line.isBlank()) continue;
            lineRead(line, line.getBytes(StandardCharsets.UTF_8));
        }
        return result.exitCode();
    }

    @Override
    public int stream(String cmd, __.Console console) throws Exception {
        com.fortrow.jflux.shell.CommandResult result = com.fortrow.jflux.shell.Shell.execute(cmd);
        for (String line : result.stdout().split("\\R")) {
            if (line.isBlank()) continue;
            console.println(line);
        }
        return result.exitCode();
    }

    @Override
    public DetachedThread threaded(String cmd) throws Exception {
        DetachedThread thread = new DetachedThread() {
            @Override
            protected boolean execute() {
                try {
                    return runAndWait(cmd) == 0;
                } catch (Exception e) { ExceptionHandling.report(e); }
                return false;
            }
        };
        thread.start();
        return thread;
    }
}
