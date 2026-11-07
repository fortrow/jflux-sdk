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
 * File created by Ryan Fitzgerald (ryan@fortrow.com) on 2026-05-11 14:25:08 EDT
 */
package com.fortrow.jflux.db.supporting;

import com.fortrow.jflux.db.Db;
import com.fortrow.jflux.db.connectors.details.DbConnectionSqlLiteDetails;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SchemaMigrationTest {

    @Test
    void recreatesEmptySqliteTableWhenRequiredColumnIsMissing() throws Exception {
        Path file = Files.createTempFile("jflux-schema-migration", ".db");
        try(Connection connection = DriverManager.getConnection("jdbc:sqlite:" + file)) {
            createOldLayoutsTable(connection);
        }

        Db db = new Db(new DbConnectionSqlLiteDetails(file.toString()));
        DbConnection connection = db.getRawDriver().conn();
        TableSchema[] expected = layoutsSchema();

        assertFalse(Schema.verifySqliteTable(connection, "layouts", expected));
        assertEquals(1, Schema.migrateSqliteTable(connection, "layouts", expected));
        assertTrue(Schema.verifySqliteTable(connection, "layouts", expected));
    }

    @Test
    void refusesRequiredColumnMigrationOnNonEmptySqliteTable() throws Exception {
        Path file = Files.createTempFile("jflux-schema-migration-non-empty", ".db");
        try(Connection connection = DriverManager.getConnection("jdbc:sqlite:" + file)) {
            createOldLayoutsTable(connection);
            try(Statement statement = connection.createStatement()) {
                statement.executeUpdate("INSERT INTO layouts (id, secondary_id, filepath, hash, version, lang) VALUES ('1', 'abc', '/layout.py', 'hash', '1', 'python')");
            }
        }

        Db db = new Db(new DbConnectionSqlLiteDetails(file.toString()));
        SQLException ex = assertThrows(SQLException.class, () -> Schema.migrateSqliteTable(db.getRawDriver().conn(), "layouts", layoutsSchema()));
        assertTrue(ex.getMessage().contains("risking data loss"));
    }

    private void createOldLayoutsTable(Connection connection) throws SQLException {
        try(Statement statement = connection.createStatement()) {
            statement.executeUpdate("CREATE TABLE layouts (`id` varchar(40) PRIMARY KEY NOT NULL, `secondary_id` text NOT NULL, `filepath` text NOT NULL, `hash` text NOT NULL, `version` text NOT NULL, `lang` text NOT NULL)");
        }
    }

    private TableSchema[] layoutsSchema() {
        return new TableSchema[]{
                TableSchema.id(),
                TableSchema.col("secondary_id", "text", false),
                TableSchema.col("collection_id", "text", false),
                TableSchema.col("filepath", "text", false),
                TableSchema.col("hash", "text", false),
                TableSchema.col("version", "text", false),
                TableSchema.col("lang", "text", false)
        };
    }
}
