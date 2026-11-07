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

package com.fortrow.jflux.files;

import com.fortrow.jflux.exceptions.ExceptionHandling;
import com.fortrow.jflux.exceptions.types.CommonIOException;

import java.io.File;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/** Lightweight timestamped append-only log helper. */
public class LogFile {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private final File file;
    private final PrintStream stderr;

    public LogFile() {
        this(System.err);
    }

    /** Creates a stderr-backed logger. The stream is injectable for tests and hosts. */
    public LogFile(PrintStream stderr) {
        if (stderr == null) throw ExceptionHandling.invalid("stderr logger is required.");
        this.file = null;
        this.stderr = stderr;
    }

    public LogFile(String filepath) {
        this(filepath, System.err);
    }

    /** Creates a file-backed logger; the supplied stream is retained for future fallback use. */
    public LogFile(String filepath, PrintStream stderr) {
        if (stderr == null) throw ExceptionHandling.invalid("stderr logger is required.");
        try {
            this.file = Files.open(filepath).getFile();
            this.stderr = stderr;
        } catch (CommonIOException e) {
            throw ExceptionHandling.failure("Unable to open log file '" + filepath + "'.", e);
        }
    }

    public synchronized void write(String line) {
        String message = "[" + LocalDateTime.now().format(FORMATTER) + "] :: " + String.valueOf(line) + System.lineSeparator();
        if (file == null) {
            stderr.print(message);
            stderr.flush();
            return;
        }
        try {
            java.nio.file.Files.writeString(
                    file.toPath(),
                    message,
                    StandardCharsets.UTF_8,
                    java.nio.file.StandardOpenOption.CREATE,
                    java.nio.file.StandardOpenOption.APPEND
            );
        } catch (java.io.IOException e) {
            throw ExceptionHandling.failure("Unable to append log file '" + file + "'.", e);
        }
    }
}
