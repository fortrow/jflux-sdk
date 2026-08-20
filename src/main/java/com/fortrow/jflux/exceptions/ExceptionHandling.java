package com.fortrow.jflux.exceptions;

import com.fortrow.jflux.exceptions.types.CommonException;
import com.fortrow.jflux.exceptions.types.ExceptionAdapter;
import com.fortrow.jflux.exceptions.types.ReportedException;

/** Compatibility bridge for APIs that predate the jflux exception hierarchy. */
public final class ExceptionHandling {

    private ExceptionHandling() { }

    /** Converts and reports any failure through the configured jflux reporters. */
    public static boolean report(Throwable failure) {
        if (failure == null) return false;
        if (failure instanceof ReportedException reported) return reported.tryReport();
        if (failure instanceof ExceptionAdapter adapter) return new ReportedException(adapter).tryReport();
        return new ReportedException(failure).tryReport();
    }

    /** Reports invalid caller input while preserving a legacy runtime contract. */
    public static IllegalArgumentException invalid(String message) {
        new CommonException(message);
        return new IllegalArgumentException(message);
    }

    public static IllegalArgumentException invalid(String message, Throwable cause) {
        new CommonException(message, cause);
        return new IllegalArgumentException(message, cause);
    }

    /** Reports an operational failure while preserving a legacy runtime contract. */
    public static IllegalStateException failure(String message, Throwable cause) {
        new ReportedException(message, cause).tryReport();
        return new IllegalStateException(message, cause);
    }

    public static UnsupportedOperationException unsupported(String message) {
        new CommonException(message);
        return new UnsupportedOperationException(message);
    }
}
