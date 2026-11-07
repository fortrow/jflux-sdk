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

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;

final class DatabaseReporterSupport {

    private DatabaseReporterSupport() { }

    static boolean validTable(String table) {
        return table != null && table.matches("[A-Za-z_][A-Za-z0-9_]*");
    }

    static boolean insert(Connection connection, String table, ReportedException exception) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS " + table + " ("
                    + "id VARCHAR(36) PRIMARY KEY, created_at VARCHAR(64) NOT NULL, "
                    + "exception_type VARCHAR(512) NOT NULL, message TEXT, payload TEXT NOT NULL"
                    + ")");
        }
        String sql = "INSERT INTO " + table + " (id, created_at, exception_type, message, payload) VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, java.util.UUID.randomUUID().toString());
            statement.setString(2, java.time.Instant.now().toString());
            statement.setString(3, exception.getClass().getName());
            statement.setString(4, exception.getMessage());
            statement.setString(5, exception.reportJsonString());
            statement.executeUpdate();
        }
        return true;
    }
}
