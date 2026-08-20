/*
 * -------------------------------------------------------------------------
 * 
 * Copyright (c) 2026 Fortrow LLC. All rights reserved.
 *  
 * NOTICE: All information contained herein is, and remains the property of 
 * Fortrow, LLC. The intellectual and technical concepts contained herein are 
 * proprietary to [Company Name] and may be covered by U.S. and Foreign Patents, 
 * patents in process, and are protected by trade secret or copyright law.
 * 
 * Dissemination of this information or reproduction of this material is 
 * strictly forbidden unless prior written permission is obtained from 
 * Fortrow, LLC.
 * 
 * -------------------------------------------------------------------------
 * 
 * File created by Ryan Fitzgerald (ryan@fortrow.com) on 2026-05-11 14:25:08 EDT
 * 
 * -------------------------------------------------------------------------

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
