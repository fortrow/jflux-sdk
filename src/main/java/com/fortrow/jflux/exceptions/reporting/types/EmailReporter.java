package com.fortrow.jflux.exceptions.reporting.types;

import com.fortrow.jflux.email.Gmail;
import com.fortrow.jflux.email.Outlook;
import com.fortrow.jflux.email.SMTP;
import com.fortrow.jflux.exceptions.ExceptionHandling;
import com.fortrow.jflux.exceptions.types.ReportedException;

import java.util.Arrays;

/**
 * SMTP error reporter backed by the reusable {@link SMTP} transport. Delivery
 * is deliberately non-throwing so an unavailable mail server cannot replace
 * or recursively report the application's original exception.
 */
public final class EmailReporter {

    private static final Configuration PLACEHOLDER = new Configuration(
            "smtp.example.invalid", 587, "placeholder@example.invalid", "placeholder-password",
            "errors@example.invalid", "recipient@example.invalid", true, false, false
    );
    private static volatile Configuration configuration = PLACEHOLDER;

    private EmailReporter() { }

    /** Legacy-compatible reporter settings. New application mail uses {@link SMTP.Configuration}. */
    public record Configuration(
            String host,
            int port,
            String username,
            String password,
            String from,
            String recipient,
            boolean startTls,
            boolean ssl,
            boolean enabled
    ) {
        public Configuration {
            if (port < 1 || port > 65535) throw ExceptionHandling.invalid("SMTP port must be between 1 and 65535.");
            if (host == null || host.isBlank()) throw ExceptionHandling.invalid("SMTP host is required.");
            if (from == null || from.isBlank()) throw ExceptionHandling.invalid("Sender address is required.");
            if (recipient == null || recipient.isBlank()) throw ExceptionHandling.invalid("Recipient address is required.");
            if (host.indexOf('\r') >= 0 || host.indexOf('\n') >= 0 || from.indexOf('\r') >= 0 || recipient.indexOf('\r') >= 0) {
                throw ExceptionHandling.invalid("SMTP reporter fields cannot contain line breaks.");
            }
        }

        @Override
        public String toString() {
            return "Configuration[host=" + host + ", port=" + port + ", username=" + username
                    + ", password=<redacted>, from=" + from + ", recipient=" + recipient
                    + ", startTls=" + startTls + ", ssl=" + ssl + ", enabled=" + enabled + ']';
        }
    }

    public static Configuration placeholderConfiguration() {
        return PLACEHOLDER;
    }

    public static void setConfiguration(Configuration value) {
        configuration = value == null ? PLACEHOLDER : value;
    }

    public static void setGmailConfiguration(String username, String appPassword, String recipient) {
        setConfiguration(new Configuration(Gmail.SMTP_HOST, Gmail.SMTP_PORT, username, appPassword,
                username, recipient, true, false, true));
    }

    public static void setOutlookConfiguration(String username, String password, String recipient) {
        setConfiguration(new Configuration(Outlook.SMTP_HOST, Outlook.SMTP_PORT, username, password,
                username, recipient, true, false, true));
    }

    /** Compatibility overload; use a full Configuration for a working reporter. */
    @Deprecated(since = "1.1.0", forRemoval = false)
    public static void setConfiguration(String host, String username) {
        setConfiguration(new Configuration(host, 587, username, "placeholder-password", username,
                "recipient@example.invalid", true, false, false));
    }

    public static boolean hasConfiguration() {
        Configuration value = configuration;
        return value.enabled
                && (value.startTls || value.ssl)
                && ReporterSupport.usable(value.host)
                && ReporterSupport.usable(value.username)
                && ReporterSupport.usable(value.password)
                && ReporterSupport.usable(value.from)
                && ReporterSupport.usable(value.recipient);
    }

    public static boolean report(ReportedException exception) {
        if (!hasConfiguration() || exception == null) return false;
        Configuration value = configuration;
        char[] password = value.password().toCharArray();
        try {
            SMTP.Security security = value.ssl() ? SMTP.Security.IMPLICIT_TLS : SMTP.Security.STARTTLS;
            SMTP.Configuration smtpConfiguration = new SMTP.Configuration(
                    value.host(), value.port(), value.username(), password, security, SMTP.Authentication.AUTO,
                    "localhost", SMTP.DEFAULT_CONNECT_TIMEOUT, SMTP.DEFAULT_READ_TIMEOUT, SMTP.DEFAULT_MAX_MESSAGE_BYTES);
            SMTP.Message message = SMTP.message()
                    .from(value.from())
                    .to(value.recipient())
                    .subject("jflux error report")
                    .text(exception.reportText())
                    .build();
            return new SMTP(smtpConfiguration).trySend(message).success();
        } catch (RuntimeException ignored) {
            return false;
        } finally {
            Arrays.fill(password, '\0');
        }
    }
}
