package com.fortrow.jflux.exceptions.reporting.types;

import com.fortrow.jflux.exceptions.types.ReportedException;

import java.net.URI;

final class ReporterSupport {

    private ReporterSupport() { }

    static boolean usable(String value) {
        if (value == null || value.isBlank()) return false;
        String lower = value.trim().toLowerCase();
        return !lower.contains("example.invalid") && !lower.contains("placeholder") && !lower.equals("change-me");
    }

    static String payload(ReportedException exception) {
        return exception.reportJsonString();
    }

    static URI uri(String value) {
        try {
            return URI.create(value);
        } catch (RuntimeException e) {
            return null;
        }
    }
}
