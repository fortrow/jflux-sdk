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

import com.fortrow.jflux.exceptions.ExceptionHandling;
import com.fortrow.jflux.exceptions.types.ReportedException;

import java.sql.Connection;
import java.sql.DriverManager;

/** SQLite error reporter using the standard jflux SQL storage shape. */
public final class SQLiteReporter {

    private static final Configuration PLACEHOLDER = new Configuration("errors.example.invalid/jflux.sqlite", "jflux_errors", false);
    private static volatile Configuration configuration = PLACEHOLDER;

    private SQLiteReporter() { }

    public record Configuration(String filepath, String table, boolean enabled) {
        public Configuration {
            if (!DatabaseReporterSupport.validTable(table)) throw ExceptionHandling.invalid("Invalid SQLite reporter table name.");
        }
    }

    public static void setConfiguration(String filepath) {
        setConfiguration(filepath, "jflux_errors");
    }

    public static void setConfiguration(String filepath, String table) {
        configuration = new Configuration(filepath, table, true);
    }

    public static void disable() {
        configuration = PLACEHOLDER;
    }

    public static boolean hasConfiguration() {
        return configuration.enabled && ReporterSupport.usable(configuration.filepath);
    }

    public static boolean report(ReportedException exception) {
        if (!hasConfiguration() || exception == null) return false;
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + configuration.filepath)) {
            return DatabaseReporterSupport.insert(connection, configuration.table, exception);
        } catch (Exception ignored) {
            return false;
        }
    }
}
