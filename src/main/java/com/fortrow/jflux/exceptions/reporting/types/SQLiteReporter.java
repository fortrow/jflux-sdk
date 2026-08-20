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
