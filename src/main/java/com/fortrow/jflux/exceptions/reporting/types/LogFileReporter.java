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
