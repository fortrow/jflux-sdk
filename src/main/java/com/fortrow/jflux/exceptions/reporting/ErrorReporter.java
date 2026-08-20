package com.fortrow.jflux.exceptions.reporting;

import com.fortrow.jflux.exceptions.ExceptionHandling;
import com.fortrow.jflux.exceptions.reporting.types.EmailReporter;
import com.fortrow.jflux.exceptions.reporting.types.HttpEndpointReporter;
import com.fortrow.jflux.exceptions.reporting.types.LogFileReporter;
import com.fortrow.jflux.exceptions.reporting.types.McpReporter;
import com.fortrow.jflux.exceptions.reporting.types.MySQLReporter;
import com.fortrow.jflux.exceptions.reporting.types.PushoverReporter;
import com.fortrow.jflux.exceptions.reporting.types.SQLiteReporter;
import com.fortrow.jflux.exceptions.types.CriticalException;
import com.fortrow.jflux.exceptions.types.ExceptionAdapter;
import com.fortrow.jflux.exceptions.types.FatalException;
import com.fortrow.jflux.exceptions.types.ReportedException;
import com.fortrow.jflux.files.LogFile;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Central automatic logging and configured error-reporting policy. */
public final class ErrorReporter {

    private static final Set<Class<? extends ExceptionAdapter>> AUTO_REPORT_TYPES = ConcurrentHashMap.newKeySet();

    private static volatile boolean emailEnabled;
    private static volatile boolean httpEndpointEnabled;
    private static volatile boolean logfileEnabled;
    private static volatile boolean mcpEnabled;
    private static volatile boolean mysqlEnabled;
    private static volatile boolean sqliteEnabled;
    private static volatile boolean pushoverEnabled;

    /** Enables automatic logging to the configured application error logger. */
    public static volatile boolean alwaysLog = true;

    /** Applications may replace this with their own file-backed or stderr logger. */
    public static volatile LogFile errorLogger = new LogFile();

    static {
        AUTO_REPORT_TYPES.add(CriticalException.class);
        AUTO_REPORT_TYPES.add(FatalException.class);
        AUTO_REPORT_TYPES.add(ReportedException.class);
    }

    private ErrorReporter() { }

    /** Logs an exception once through the application's automatic logger. */
    public static boolean autolog(ExceptionAdapter exception) {
        if (!alwaysLog || exception == null) return false;
        LogFile logger = errorLogger;
        if (logger == null) return false;
        if (!exception.markAutomaticallyLogged()) return false;
        try {
            logger.write(exception.reportText());
            return true;
        } catch (RuntimeException failure) {
            System.err.println("jflux automatic error logging failed: " + failure.getMessage());
            return false;
        }
    }

    public static boolean autolog(ReportedException exception) {
        return autolog((ExceptionAdapter) exception);
    }

    /** Reports through each configured and enabled sink. */
    public static boolean report(ReportedException exception) {
        if (exception == null) return false;
        boolean delivered = autolog(exception);
        refreshConfiguration();
        boolean attempted = false;

        if (emailEnabled) {
            attempted = true;
            delivered |= EmailReporter.report(exception);
        }
        if (httpEndpointEnabled) {
            attempted = true;
            delivered |= HttpEndpointReporter.report(exception);
        }
        if (logfileEnabled) {
            attempted = true;
            delivered |= LogFileReporter.report(exception);
        }
        if (mcpEnabled) {
            attempted = true;
            delivered |= McpReporter.report(exception);
        }
        if (mysqlEnabled) {
            attempted = true;
            delivered |= MySQLReporter.report(exception);
        }
        if (sqliteEnabled) {
            attempted = true;
            delivered |= SQLiteReporter.report(exception);
        }
        if (pushoverEnabled) {
            attempted = true;
            delivered |= PushoverReporter.report(exception);
        }

        return attempted ? delivered : alwaysLog && exception.isAutomaticallyLogged();
    }

    public static void initialize() {
        refreshConfiguration();
    }

    public static void setErrorLogger(LogFile logger) {
        errorLogger = logger;
    }

    public static LogFile getErrorLogger() {
        return errorLogger;
    }

    public static void setAlwaysLog(boolean enabled) {
        alwaysLog = enabled;
    }

    public static void setAutoReport(Class<? extends ExceptionAdapter> type, boolean enabled) {
        if (type == null) throw ExceptionHandling.invalid("Exception type is required.");
        if (enabled) AUTO_REPORT_TYPES.add(type);
        else AUTO_REPORT_TYPES.remove(type);
    }

    public static boolean shouldAutoReport(ExceptionAdapter exception) {
        if (exception == null) return false;
        for (Class<? extends ExceptionAdapter> type : AUTO_REPORT_TYPES) {
            if (type.isAssignableFrom(exception.getClass())) return true;
        }
        return false;
    }

    public static Set<Class<? extends ExceptionAdapter>> autoReportTypes() {
        return Collections.unmodifiableSet(AUTO_REPORT_TYPES);
    }

    /** Called by ExceptionAdapter after its report data has been initialized. */
    public static void handle(ExceptionAdapter exception) {
        autolog(exception);
        if (exception instanceof ReportedException) return;
        if (shouldAutoReport(exception)) new ReportedException(exception).tryReport();
    }

    private static void refreshConfiguration() {
        emailEnabled = EmailReporter.hasConfiguration();
        httpEndpointEnabled = HttpEndpointReporter.hasConfiguration();
        logfileEnabled = LogFileReporter.hasConfiguration();
        mcpEnabled = McpReporter.hasConfiguration();
        mysqlEnabled = MySQLReporter.hasConfiguration();
        sqliteEnabled = SQLiteReporter.hasConfiguration();
        pushoverEnabled = PushoverReporter.hasConfiguration();
    }
}
