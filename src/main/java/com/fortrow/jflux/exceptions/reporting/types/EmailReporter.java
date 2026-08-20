package com.fortrow.jflux.exceptions.reporting.types;

import com.fortrow.jflux.exceptions.ExceptionHandling;
import com.fortrow.jflux.exceptions.types.ReportedException;

import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/** SMTP reporter with ready-to-fill Gmail and Outlook configurations. */
public final class EmailReporter {

    private static final Configuration PLACEHOLDER = new Configuration(
            "smtp.example.invalid", 587, "placeholder@example.invalid", "placeholder-password",
            "errors@example.invalid", "recipient@example.invalid", true, false, false
    );
    private static volatile Configuration configuration = PLACEHOLDER;

    private EmailReporter() { }

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
        }
    }

    public static Configuration placeholderConfiguration() {
        return PLACEHOLDER;
    }

    public static void setConfiguration(Configuration value) {
        configuration = value == null ? PLACEHOLDER : value;
    }

    public static void setGmailConfiguration(String username, String appPassword, String recipient) {
        setConfiguration(new Configuration("smtp.gmail.com", 587, username, appPassword,
                username, recipient, true, false, true));
    }

    public static void setOutlookConfiguration(String username, String password, String recipient) {
        setConfiguration(new Configuration("smtp.office365.com", 587, username, password,
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
                && ReporterSupport.usable(value.host)
                && ReporterSupport.usable(value.username)
                && ReporterSupport.usable(value.password)
                && ReporterSupport.usable(value.from)
                && ReporterSupport.usable(value.recipient);
    }

    public static boolean report(ReportedException exception) {
        if (!hasConfiguration() || exception == null) return false;
        Configuration value = configuration;
        Socket socket = null;
        try {
            socket = open(value);
            socket.setSoTimeout(10_000);
            BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.US_ASCII));
            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.US_ASCII));

            expect(reader, 220);
            command(reader, writer, "EHLO localhost", 250);
            if (value.startTls) {
                command(reader, writer, "STARTTLS", 220);
                SSLSocket secure = (SSLSocket) ((SSLSocketFactory) SSLSocketFactory.getDefault()).createSocket(
                        socket, value.host, value.port, false);
                secure.startHandshake();
                socket = secure;
                reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.US_ASCII));
                writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.US_ASCII));
                command(reader, writer, "EHLO localhost", 250);
            }
            if (ReporterSupport.usable(value.username) && ReporterSupport.usable(value.password)) {
                command(reader, writer, "AUTH LOGIN", 334);
                command(reader, writer, Base64.getEncoder().encodeToString(value.username.getBytes(StandardCharsets.UTF_8)), 334);
                command(reader, writer, Base64.getEncoder().encodeToString(value.password.getBytes(StandardCharsets.UTF_8)), 235);
            }
            command(reader, writer, "MAIL FROM:<" + value.from + ">", 250);
            command(reader, writer, "RCPT TO:<" + value.recipient + ">", 250, 251);
            command(reader, writer, "DATA", 354);
            writeMessage(reader, writer, value, exception);
            command(reader, writer, "QUIT", 221, 250);
            return true;
        } catch (IOException | RuntimeException ignored) {
            return false;
        } finally {
            if (socket != null) {
                try { socket.close(); } catch (IOException ignored) { }
            }
        }
    }

    private static Socket open(Configuration value) throws IOException {
        if (value.ssl) return SSLSocketFactory.getDefault().createSocket(value.host, value.port);
        Socket socket = new Socket();
        socket.connect(new InetSocketAddress(value.host, value.port), 10_000);
        return socket;
    }

    private static void writeMessage(BufferedReader reader, BufferedWriter writer,
                                     Configuration value, ReportedException exception) throws IOException {
        writer.write("From: " + value.from + "\r\n");
        writer.write("To: " + value.recipient + "\r\n");
        writer.write("Subject: jflux error report\r\n");
        writer.write("Content-Type: text/plain; charset=UTF-8\r\n");
        writer.write("\r\n");
        for (String line : exception.reportText().split("\\R", -1)) {
            writer.write(line.startsWith(".") ? "." + line : line);
            writer.write("\r\n");
        }
        writer.write(".\r\n");
        writer.flush();
        expect(reader, 250);
    }

    private static void command(BufferedReader reader, BufferedWriter writer, String command, int... expected) throws IOException {
        writer.write(command);
        writer.write("\r\n");
        writer.flush();
        expect(reader, expected);
    }

    private static void expect(BufferedReader reader, int... expected) throws IOException {
        String response = reader.readLine();
        if (response == null || response.length() < 3) throw new IOException("SMTP server closed the connection.");
        int code = Integer.parseInt(response.substring(0, 3));
        while (response.length() > 3 && response.charAt(3) == '-') {
            response = reader.readLine();
            if (response == null) throw new IOException("SMTP response was incomplete.");
        }
        for (int candidate : expected) if (candidate == code) return;
        throw new IOException("Unexpected SMTP response: " + code);
    }
}
