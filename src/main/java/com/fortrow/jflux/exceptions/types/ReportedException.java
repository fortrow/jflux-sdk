package com.fortrow.jflux.exceptions.types;

import com.fortrow.jflux.exceptions.reporting.ErrorReporter;

/** Exception type intended for explicit or policy-driven external reporting. */
public class ReportedException extends ExceptionAdapter {

    private boolean reported;
    private boolean failed;

    public ReportedException() {
        super();
        finishConstruction();
    }

    public ReportedException(String message) {
        super(message);
        finishConstruction();
    }

    public ReportedException(String message, Throwable cause) {
        super(message, cause);
        finishConstruction();
    }

    public ReportedException(Throwable cause) {
        super(cause);
        finishConstruction();
    }

    protected ReportedException(String message, Throwable cause,
                                boolean enableSuppression,
                                boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
        finishConstruction();
    }

    public synchronized boolean tryReport() {
        if (reported) return !failed;
        reported = true;
        failed = !ErrorReporter.report(this);
        return !failed;
    }

    public boolean wasReported() {
        return reported;
    }

    public boolean reportFailed() {
        return failed;
    }

    private void finishConstruction() {
        if (ErrorReporter.shouldAutoReport(this)) tryReport();
    }
}
