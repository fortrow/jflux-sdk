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

package com.fortrow.jflux.exceptions.reporting.types;

import com.fortrow.jflux.exceptions.types.ReportedException;
import com.fortrow.jflux.files.LogFile;

/** Optional dedicated report log, separate from the application's automatic logger. */
public final class LogFileReporter {

    private static volatile String filepath;
    private static volatile LogFile logger;

    private LogFileReporter() { }

    public static void setConfiguration(String path) {
        if (path == null || path.isBlank()) {
            filepath = null;
            logger = null;
            return;
        }
        filepath = path;
        logger = new LogFile(path);
    }

    /** Compatibility overload; the first value is treated as a log path. */
    @Deprecated(since = "1.1.0", forRemoval = false)
    public static void setConfiguration(String path, String ignored) {
        setConfiguration(path);
    }

    public static boolean hasConfiguration() {
        return ReporterSupport.usable(filepath) && logger != null;
    }

    public static boolean report(ReportedException exception) {
        if (!hasConfiguration() || exception == null) return false;
        try {
            logger.write(exception.reportText());
            return true;
        } catch (RuntimeException ignored) {
            return false;
        }
    }
}
