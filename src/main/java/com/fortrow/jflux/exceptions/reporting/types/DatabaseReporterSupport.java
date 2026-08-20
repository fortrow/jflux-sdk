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
