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

package com.fortrow.jflux.exceptions;

import com.fortrow.jflux.exceptions.reporting.ErrorReporter;
import com.fortrow.jflux.exceptions.reporting.types.SQLiteReporter;
import com.fortrow.jflux.crypto.adapters.HashAlgorithm;
import com.fortrow.jflux.encoding.Hex;
import com.fortrow.jflux.exceptions.types.CommonException;
import com.fortrow.jflux.exceptions.types.ReportedException;
import com.fortrow.jflux.files.LogFile;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ExceptionReportingTest {

    @Test
    void automaticLoggerReceivesStructuredExceptionText() throws Throwable {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        LogFile previous = ErrorReporter.getErrorLogger();
        boolean previousAlwaysLog = ErrorReporter.alwaysLog;
        try {
            ErrorReporter.setErrorLogger(new LogFile(new PrintStream(output, true, StandardCharsets.UTF_8)));
            ErrorReporter.setAlwaysLog(true);
            CommonException exception = new CommonException("automatic log entry");

            String logged = output.toString(StandardCharsets.UTF_8);
            assertTrue(logged.contains("automatic log entry"));
            assertTrue(exception.isAutomaticallyLogged());
            assertTrue(exception.reportJsonString().contains("automatic log entry"));
        } finally {
            ErrorReporter.setErrorLogger(previous);
            ErrorReporter.setAlwaysLog(previousAlwaysLog);
        }
    }

    @Test
    void reportedExceptionCanBeExplicitWhenItsAutoPolicyIsDisabled() throws Throwable {
        boolean previousAlwaysLog = ErrorReporter.alwaysLog;
        ErrorReporter.setAutoReport(ReportedException.class, false);
        try {
            ErrorReporter.setAlwaysLog(false);
            ReportedException exception = new ReportedException("explicit report");

            assertFalse(exception.wasReported());
            assertFalse(exception.tryReport());
            assertTrue(exception.wasReported());
            assertTrue(exception.reportFailed());
        } finally {
            ErrorReporter.setAutoReport(ReportedException.class, true);
            ErrorReporter.setAlwaysLog(previousAlwaysLog);
        }
    }

    @Test
    void sqliteReporterStoresStructuredReports() throws Throwable {
        Path database = Files.createTempFile("jflux-errors-", ".sqlite");
        Files.deleteIfExists(database);
        try {
            SQLiteReporter.setConfiguration(database.toString(), "jflux_test_errors");
            ErrorReporter.setAutoReport(ReportedException.class, false);
            ReportedException exception = new ReportedException("database report");
            assertTrue(SQLiteReporter.report(exception));

            try (var connection = DriverManager.getConnection("jdbc:sqlite:" + database);
                 var statement = connection.createStatement();
                 var rows = statement.executeQuery("SELECT COUNT(*) FROM jflux_test_errors")) {
                assertTrue(rows.next());
                assertEquals(1, rows.getInt(1));
            }
        } finally {
            ErrorReporter.setAutoReport(ReportedException.class, true);
            SQLiteReporter.disable();
            Files.deleteIfExists(database);
        }
    }

    @Test
    void legacyPackageFailuresUseTheJfluxLogger() {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        LogFile previous = ErrorReporter.getErrorLogger();
        boolean previousAlwaysLog = ErrorReporter.alwaysLog;
        try {
            ErrorReporter.setErrorLogger(new LogFile(new PrintStream(output, true, StandardCharsets.UTF_8)));
            ErrorReporter.setAlwaysLog(true);

            assertThrows(IllegalArgumentException.class, () -> Hex.decode("odd"));
            assertTrue(output.toString(StandardCharsets.UTF_8).contains("CommonException"));

            HashAlgorithm unregistered = new HashAlgorithm("legacy-missing", 32);
            assertEquals(0, unregistered.hash(new byte[]{1}).length);
            assertTrue(output.toString(StandardCharsets.UTF_8).contains("No hash operation is registered"));
        } finally {
            ErrorReporter.setErrorLogger(previous);
            ErrorReporter.setAlwaysLog(previousAlwaysLog);
        }
    }
}
