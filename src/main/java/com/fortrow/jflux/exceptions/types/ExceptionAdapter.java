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
 */

package com.fortrow.jflux.exceptions.types;

import com.fortrow.jflux.exceptions.reporting.ErrorReporter;
import com.fortrow.jflux.output.json.JSON;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Common base for jflux exceptions, automatic logging, and structured reports. */
public abstract class ExceptionAdapter extends Throwable {

    protected String message;
    protected ArrayList<String> stacktrace;
    protected ArrayList<String[]> suppressed;
    protected int code;
    protected String userMessage;
    private volatile boolean automaticallyLogged;

    public ExceptionAdapter() {
        super();
        initialize(null);
    }

    public ExceptionAdapter(String message) {
        super(message);
        initialize(message);
    }

    public ExceptionAdapter(String message, Throwable cause) {
        super(message, cause);
        initialize(message);
    }

    public ExceptionAdapter(Throwable cause) {
        super(cause);
        initialize(cause == null ? null : cause.getMessage());
    }

    protected ExceptionAdapter(String message, Throwable cause,
                               boolean enableSuppression,
                               boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
        initialize(message);
    }

    @Override
    public String getMessage() {
        return message == null ? super.getMessage() : message;
    }

    public String getUserMessage() {
        return userMessage == null ? getMessage() : userMessage;
    }

    public ExceptionAdapter setUserMessage(String userMessage) {
        this.userMessage = userMessage;
        return this;
    }

    public int getCode() {
        return code;
    }

    public ExceptionAdapter setCode(int code) {
        this.code = code;
        return this;
    }

    public List<String> getStacktraceEntries() {
        return Collections.unmodifiableList(stacktrace);
    }

    public List<String[]> getSuppressedEntries() {
        ArrayList<String[]> copy = new ArrayList<>();
        for (String[] entry : suppressed) copy.add(entry.clone());
        return Collections.unmodifiableList(copy);
    }

    /** Returns the structured JSON payload used by configured reporters. */
    @SuppressWarnings("unchecked")
    public JSONObject reportJson() {
        JSONObject result = new JSONObject();
        result.put("type", getClass().getName());
        result.put("message", getMessage());
        result.put("user_message", getUserMessage());
        result.put("code", code);
        JSONArray stack = new JSONArray();
        stack.addAll(stacktrace);
        result.put("stacktrace", stack);

        Throwable cause = getCause();
        if (cause != null) {
            result.put("cause_type", cause.getClass().getName());
            result.put("cause_message", cause.getMessage());
        }

        JSONArray suppressedErrors = new JSONArray();
        for (String[] entry : suppressed) {
            JSONObject item = new JSONObject();
            item.put("type", entry[0]);
            item.put("message", entry[1]);
            suppressedErrors.add(item);
        }
        result.put("suppressed", suppressedErrors);
        return result;
    }

    public String reportJsonString() {
        return JSON.getString(reportJson());
    }

    public String reportText() {
        StringBuilder result = new StringBuilder(getClass().getName());
        if (getMessage() != null && !getMessage().isBlank()) result.append(": ").append(getMessage());
        result.append(System.lineSeparator());
        for (String entry : stacktrace) result.append("  at ").append(entry).append(System.lineSeparator());
        return result.toString();
    }

    /** Explicitly reports this exception, converting non-reported types as needed. */
    public void report() {
        if (this instanceof ReportedException reported) {
            reported.tryReport();
            return;
        }
        new ReportedException(this).tryReport();
    }

    public boolean isAutomaticallyLogged() {
        return automaticallyLogged;
    }

    /** Used by ErrorReporter to ensure one automatic log entry per exception. */
    public boolean markAutomaticallyLogged() {
        if (automaticallyLogged) return false;
        automaticallyLogged = true;
        return true;
    }

    /** Kept as an extension hook for existing exception subclasses. */
    protected void onError() { }

    /** Raw stderr printing is replaced by the configured automatic logger. */
    protected boolean printErrors() {
        return false;
    }

    private void initialize(String initialMessage) {
        message = initialMessage;
        userMessage = initialMessage;
        stacktrace = new ArrayList<>();
        suppressed = new ArrayList<>();
        parseStacktrace();
        for (Throwable error : getSuppressed()) {
            suppressed.add(new String[]{error.getClass().getName(), String.valueOf(error.getMessage())});
        }
        onError();
        ErrorReporter.handle(this);
    }

    private void parseStacktrace() {
        for (StackTraceElement entry : getStackTrace()) stacktrace.add(entry.toString());
    }
}
