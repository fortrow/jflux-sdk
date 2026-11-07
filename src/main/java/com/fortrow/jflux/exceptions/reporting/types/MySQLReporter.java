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

/** MySQL error reporter using the shared SQL error schema. */
public final class MySQLReporter {

    private static final Configuration PLACEHOLDER = new Configuration(
            "jdbc:mysql://db.example.invalid/jflux", "placeholder-user", "placeholder-password", "jflux_errors", false);
    private static volatile Configuration configuration = PLACEHOLDER;

    private MySQLReporter() { }

    public record Configuration(String jdbcUrl, String username, String password, String table, boolean enabled) {
        public Configuration {
            if (!DatabaseReporterSupport.validTable(table)) throw ExceptionHandling.invalid("Invalid MySQL reporter table name.");
        }
    }

    public static void setConfiguration(String host, int port, String database,
                                        String username, String password) {
        setConfiguration("jdbc:mysql://" + host + ":" + port + "/" + database,
                username, password, "jflux_errors");
    }

    public static void setConfiguration(String jdbcUrl, String username, String password, String table) {
        configuration = new Configuration(jdbcUrl, username, password, table, true);
    }

    /** Compatibility overload; use a full database configuration for a working reporter. */
    @Deprecated(since = "1.1.0", forRemoval = false)
    public static void setConfiguration(String jdbcUrl, String username) {
        configuration = new Configuration(jdbcUrl, username, "placeholder-password", "jflux_errors", false);
    }

    public static void disable() {
        configuration = PLACEHOLDER;
    }

    public static boolean hasConfiguration() {
        Configuration value = configuration;
        return value.enabled
                && ReporterSupport.usable(value.jdbcUrl)
                && ReporterSupport.usable(value.username)
                && ReporterSupport.usable(value.password);
    }

    public static boolean report(ReportedException exception) {
        if (!hasConfiguration() || exception == null) return false;
        Configuration value = configuration;
        try (Connection connection = DriverManager.getConnection(value.jdbcUrl, value.username, value.password)) {
            return DatabaseReporterSupport.insert(connection, value.table, exception);
        } catch (Exception ignored) {
            return false;
        }
    }
}
