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
