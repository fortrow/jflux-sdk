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

package com.fortrow.jflux.email;

import com.fortrow.jflux.exceptions.ExceptionHandling;
import com.fortrow.jflux.exceptions.types.CommonIOException;
import com.fortrow.jflux.todash.__;

import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/**
 * Bounded, dependency-free SMTP client with TLS, MIME text/HTML messages, and
 * streaming file attachments. Use {@link Gmail} or {@link Outlook} for their
 * provider-safe STARTTLS defaults.
 */
public final class SMTP {

    /** Limits raw message bodies and attachment files before they are sent. */
    public static final long DEFAULT_MAX_MESSAGE_BYTES = 25L * 1024L * 1024L;
    public static final Duration DEFAULT_CONNECT_TIMEOUT = Duration.ofSeconds(10);
    public static final Duration DEFAULT_READ_TIMEOUT = Duration.ofSeconds(30);

    private static final int MAX_RESPONSE_LINES = 64;
    private static final int MAX_RESPONSE_LINE_LENGTH = 8 * 1024;
    private static final char[] BASE64_TABLE = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/".toCharArray();

    private final Configuration configuration;

    /** Creates an SMTP client from a validated immutable configuration. */
    public SMTP(Configuration configuration) {
        if (configuration == null) throw ExceptionHandling.invalid("SMTP configuration is required.");
        this.configuration = configuration;
    }

    /** Returns the immutable connection configuration. */
    public Configuration configuration() {
        return configuration;
    }

    /** Starts a developer-friendly immutable message builder. */
    public static Message.Builder message() {
        return new Message.Builder();
    }

    /** Sends a simple UTF-8 text message. */
    public Delivery send(String from, String recipient, String subject, String text) throws CommonIOException {
        return send(message().from(from).to(recipient).subject(subject).text(text).build());
    }

    /** Sends a message and converts transport failures into a JFlux I/O exception. */
    public Delivery send(Message message) throws CommonIOException {
        try {
            return deliver(message);
        } catch (IOException failure) {
            throw new CommonIOException("SMTP delivery failed: " + safeFailureMessage(failure), failure);
        }
    }

    /**
     * Attempts delivery without creating a JFlux exception. This is intended
     * for infrastructure such as the exception email reporter, where a second
     * failed report must not recursively report itself.
     */
    public Attempt trySend(Message message) {
        try {
            return new Attempt(deliver(message), "");
        } catch (IOException failure) {
            return new Attempt(null, safeFailureMessage(failure));
        }
    }

    /** Sends a message on a virtual thread. */
    public CompletableFuture<Delivery> sendAsync(Message message) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return send(message);
            } catch (CommonIOException failure) {
                throw new CompletionException(failure);
            }
        }, command -> Thread.startVirtualThread(command));
    }

    private Delivery deliver(Message message) throws IOException {
        if (message == null) throw ExceptionHandling.invalid("SMTP message is required.");
        try (PreparedMessage prepared = PreparedMessage.prepare(message, configuration.maxMessageBytes());
             Session session = open(configuration)) {
            Response greeting = session.readResponse();
            greeting.require(220);
            List<String> capabilities = session.ehlo(configuration.clientName(), configuration.requiresEhlo());
            if (configuration.security() == Security.STARTTLS) {
                if (!supports(capabilities, "STARTTLS")) {
                    throw new IOException("SMTP server does not advertise STARTTLS.");
                }
                session.command("STARTTLS").require(220);
                session.upgradeToTls(configuration);
                capabilities = session.ehlo(configuration.clientName(), true);
            }
            authenticate(session, capabilities, configuration);

            session.command("MAIL FROM:<" + message.from() + ">").require(250);
            List<String> recipients = message.allRecipients();
            for (String recipient : recipients) session.command("RCPT TO:<" + recipient + ">").require(250, 251);
            session.command("DATA").require(354);

            String messageId = '<' + __.id() + '@' + messageIdDomain(configuration.clientName()) + '>';
            writeMimeMessage(session, message, prepared, messageId);
            session.endData().require(250);
            try {
                session.command("QUIT").require(221, 250);
            } catch (IOException ignored) {
                // The message has already been accepted. Connection teardown is best-effort.
            }
            return new Delivery(messageId, recipients, Instant.now());
        }
    }

    private static Session open(Configuration configuration) throws IOException {
        int connectTimeout = timeoutMillis(configuration.connectTimeout());
        int readTimeout = timeoutMillis(configuration.readTimeout());
        if (configuration.security() == Security.IMPLICIT_TLS) {
            Socket raw = new Socket();
            raw.connect(new InetSocketAddress(configuration.host(), configuration.port()), connectTimeout);
            raw.setSoTimeout(readTimeout);
            SSLSocketFactory factory = (SSLSocketFactory) SSLSocketFactory.getDefault();
            SSLSocket secure = (SSLSocket) factory.createSocket(raw, configuration.host(), configuration.port(), true);
            secure.setSoTimeout(readTimeout);
            configureTls(secure);
            secure.startHandshake();
            return new Session(secure);
        }
        Socket socket = new Socket();
        socket.connect(new InetSocketAddress(configuration.host(), configuration.port()), connectTimeout);
        socket.setSoTimeout(readTimeout);
        return new Session(socket);
    }

    private static void authenticate(Session session, List<String> capabilities, Configuration configuration)
            throws IOException {
        Authentication authentication = configuration.resolvedAuthentication();
        if (authentication == Authentication.NONE) return;
        if (!session.tls()) throw new IOException("SMTP authentication requires TLS.");

        Authentication selected = authentication == Authentication.AUTO
                ? automaticAuthentication(capabilities) : authentication;
        if (selected == Authentication.NONE) throw new IOException("SMTP server does not advertise a supported authentication method.");
        if (selected == Authentication.PLAIN) {
            if (!supportsAuthentication(capabilities, "PLAIN")) {
                throw new IOException("SMTP server does not advertise AUTH PLAIN.");
            }
            session.command("AUTH PLAIN " + plainCredentials(configuration)).require(235);
            return;
        }
        if (!supportsAuthentication(capabilities, "LOGIN")) {
            throw new IOException("SMTP server does not advertise AUTH LOGIN.");
        }
        session.command("AUTH LOGIN").require(334);
        session.command(base64(configuration.username().getBytes(StandardCharsets.UTF_8))).require(334);
        session.command(passwordBase64(configuration)).require(235);
    }

    private static Authentication automaticAuthentication(List<String> capabilities) {
        if (supportsAuthentication(capabilities, "PLAIN")) return Authentication.PLAIN;
        if (supportsAuthentication(capabilities, "LOGIN")) return Authentication.LOGIN;
        return Authentication.NONE;
    }

    private static String plainCredentials(Configuration configuration) {
        char[] password = configuration.password();
        byte[] bytes = null;
        try {
            String combined = "\0" + configuration.username() + "\0" + new String(password);
            bytes = combined.getBytes(StandardCharsets.UTF_8);
            return base64(bytes);
        } finally {
            Arrays.fill(password, '\0');
            if (bytes != null) Arrays.fill(bytes, (byte) 0);
        }
    }

    private static String passwordBase64(Configuration configuration) {
        char[] password = configuration.password();
        byte[] bytes = null;
        try {
            bytes = new String(password).getBytes(StandardCharsets.UTF_8);
            return base64(bytes);
        } finally {
            Arrays.fill(password, '\0');
            if (bytes != null) Arrays.fill(bytes, (byte) 0);
        }
    }

    private static void writeMimeMessage(Session session, Message message, PreparedMessage prepared, String messageId)
            throws IOException {
        session.dataLine("Date: " + DateTimeFormatter.RFC_1123_DATE_TIME.format(ZonedDateTime.now(ZoneOffset.UTC)));
        session.dataLine("Message-ID: " + messageId);
        session.dataLine("From: " + message.from());
        if (!message.to().isEmpty()) session.dataLine("To: " + __.join(message.to(), ", "));
        if (!message.cc().isEmpty()) session.dataLine("Cc: " + __.join(message.cc(), ", "));
        if (message.replyTo() != null) session.dataLine("Reply-To: " + message.replyTo());
        session.dataLine("Subject: " + encodeHeader(message.subject()));
        session.dataLine("MIME-Version: 1.0");

        if (prepared.attachments().isEmpty()) {
            writeBodyOrAlternative(session, prepared, "jflux-alt-" + __.id());
            return;
        }

        String mixedBoundary = "jflux-mixed-" + __.id();
        session.dataLine("Content-Type: multipart/mixed; boundary=\"" + mixedBoundary + "\"");
        session.dataLine("");
        session.dataLine("--" + mixedBoundary);
        writeBodyOrAlternative(session, prepared, "jflux-alt-" + __.id());
        for (ResolvedAttachment attachment : prepared.attachments()) {
            session.dataLine("--" + mixedBoundary);
            session.dataLine("Content-Type: " + attachment.contentType() + "; name=\"" + attachment.filename() + "\"");
            session.dataLine("Content-Transfer-Encoding: base64");
            session.dataLine("Content-Disposition: attachment; filename=\"" + attachment.filename() + "\"");
            session.dataLine("");
            writeBase64(session.writer(), attachment.file());
        }
        session.dataLine("--" + mixedBoundary + "--");
    }

    private static void writeBodyOrAlternative(Session session, PreparedMessage prepared, String boundary) throws IOException {
        if (prepared.text().length == 0 || prepared.html().length == 0) {
            if (prepared.text().length > 0) writeBodyPart(session, "text/plain", prepared.text());
            else writeBodyPart(session, "text/html", prepared.html());
            return;
        }
        session.dataLine("Content-Type: multipart/alternative; boundary=\"" + boundary + "\"");
        session.dataLine("");
        session.dataLine("--" + boundary);
        writeBodyPart(session, "text/plain", prepared.text());
        session.dataLine("--" + boundary);
        writeBodyPart(session, "text/html", prepared.html());
        session.dataLine("--" + boundary + "--");
    }

    private static void writeBodyPart(Session session, String contentType, byte[] body) throws IOException {
        session.dataLine("Content-Type: " + contentType + "; charset=UTF-8");
        session.dataLine("Content-Transfer-Encoding: base64");
        session.dataLine("");
        writeBase64(session.writer(), body);
    }

    /** Writes 76-character MIME Base64 lines without retaining an encoded file in memory. */
    private static void writeBase64(BufferedWriter writer, Path file) throws IOException {
        byte[] buffer = new byte[57];
        char[] output = new char[76];
        try (InputStream input = Files.newInputStream(file)) {
            int count;
            while ((count = readChunk(input, buffer)) > 0) writeBase64Line(writer, buffer, 0, count, output);
        } finally {
            Arrays.fill(buffer, (byte) 0);
            Arrays.fill(output, '\0');
        }
    }

    private static void writeBase64(BufferedWriter writer, byte[] bytes) throws IOException {
        char[] output = new char[76];
        for (int offset = 0; offset < bytes.length; offset += 57) {
            writeBase64Line(writer, bytes, offset, Math.min(57, bytes.length - offset), output);
        }
        Arrays.fill(output, '\0');
    }

    private static void writeBase64Line(BufferedWriter writer, byte[] source, int offset, int length, char[] output)
            throws IOException {
        int sourceIndex = offset;
        int outputIndex = 0;
        int end = offset + length;
        while (sourceIndex < end) {
            int first = source[sourceIndex++] & 0xff;
            int second = sourceIndex < end ? source[sourceIndex++] & 0xff : -1;
            int third = sourceIndex < end ? source[sourceIndex++] & 0xff : -1;
            output[outputIndex++] = BASE64_TABLE[first >>> 2];
            output[outputIndex++] = BASE64_TABLE[((first & 0x03) << 4) | (second < 0 ? 0 : second >>> 4)];
            output[outputIndex++] = second < 0 ? '=' : BASE64_TABLE[((second & 0x0f) << 2) | (third < 0 ? 0 : third >>> 6)];
            output[outputIndex++] = third < 0 ? '=' : BASE64_TABLE[third & 0x3f];
        }
        writer.write(output, 0, outputIndex);
        writer.write("\r\n");
    }

    private static int readChunk(InputStream input, byte[] buffer) throws IOException {
        int offset = 0;
        while (offset < buffer.length) {
            int read = input.read(buffer, offset, buffer.length - offset);
            if (read == -1) break;
            offset += read;
        }
        return offset;
    }

    private static boolean supports(List<String> capabilities, String capability) {
        String expected = capability.toUpperCase(Locale.ROOT);
        return capabilities.stream().map(value -> value.toUpperCase(Locale.ROOT))
                .anyMatch(value -> value.equals(expected) || value.startsWith(expected + " "));
    }

    private static boolean supportsAuthentication(List<String> capabilities, String mechanism) {
        String upperMechanism = mechanism.toUpperCase(Locale.ROOT);
        return capabilities.stream().map(value -> value.toUpperCase(Locale.ROOT)).anyMatch(value ->
                (value.startsWith("AUTH ") || value.startsWith("AUTH="))
                        && List.of(value.replaceFirst("^AUTH[ =]", "").split("\\s+")).contains(upperMechanism));
    }

    private static String base64(byte[] value) {
        return Base64.getEncoder().encodeToString(value);
    }

    private static String encodeHeader(String value) {
        if (value.chars().allMatch(character -> character >= 32 && character <= 126)) return value;
        return "=?UTF-8?B?" + base64(value.getBytes(StandardCharsets.UTF_8)) + "?=";
    }

    private static String messageIdDomain(String clientName) {
        return clientName.indexOf('.') >= 0 ? clientName : "localhost";
    }

    private static void configureTls(SSLSocket socket) {
        SSLParameters parameters = socket.getSSLParameters();
        parameters.setEndpointIdentificationAlgorithm("HTTPS");
        socket.setSSLParameters(parameters);
    }

    private static int timeoutMillis(Duration timeout) {
        return (int) Math.min(Integer.MAX_VALUE, Math.max(1L, timeout.toMillis()));
    }

    private static String safeFailureMessage(IOException failure) {
        String message = failure.getMessage();
        return message == null || message.isBlank() ? "SMTP server connection failed." : message;
    }

    /** TLS policy. STARTTLS and implicit TLS authenticate the remote SMTP server. */
    public enum Security {
        STARTTLS,
        IMPLICIT_TLS,
        PLAINTEXT
    }

    /** SMTP AUTH selection. {@link #AUTO} chooses PLAIN then LOGIN after TLS. */
    public enum Authentication {
        AUTO,
        PLAIN,
        LOGIN,
        NONE
    }

    /** Immutable SMTP connection and resource-limit settings. */
    public record Configuration(
            String host,
            int port,
            String username,
            char[] password,
            Security security,
            Authentication authentication,
            String clientName,
            Duration connectTimeout,
            Duration readTimeout,
            long maxMessageBytes) {

        public Configuration {
            host = validHost(host);
            if (port < 1 || port > 65535) throw ExceptionHandling.invalid("SMTP port must be between 1 and 65535.");
            username = username == null || username.isBlank() ? "" : validSingleLine(username, "SMTP username");
            password = password == null ? new char[0] : password.clone();
            security = __.defaultTo(security, Security.STARTTLS);
            authentication = __.defaultTo(authentication, Authentication.AUTO);
            clientName = validClientName(__.defaultTo(clientName, "localhost"));
            connectTimeout = validTimeout(__.defaultTo(connectTimeout, DEFAULT_CONNECT_TIMEOUT), "SMTP connect timeout");
            readTimeout = validTimeout(__.defaultTo(readTimeout, DEFAULT_READ_TIMEOUT), "SMTP read timeout");
            if (maxMessageBytes <= 0) throw ExceptionHandling.invalid("SMTP maximum message size must be positive.");
            boolean hasUsername = !username.isEmpty();
            boolean hasPassword = password.length > 0;
            if (hasUsername != hasPassword) throw ExceptionHandling.invalid("SMTP username and password must be supplied together.");
            if (authentication != Authentication.NONE && !hasUsername && authentication != Authentication.AUTO) {
                throw ExceptionHandling.invalid("Selected SMTP authentication requires a username and password.");
            }
            if (security == Security.PLAINTEXT && (hasUsername || authentication != Authentication.NONE && authentication != Authentication.AUTO)) {
                throw ExceptionHandling.invalid("SMTP credentials cannot be sent over a plaintext connection.");
            }
        }

        /** Common authenticated STARTTLS constructor. Prefer a {@code char[]} password. */
        public Configuration(String host, int port, String username, char[] password) {
            this(host, port, username, password, Security.STARTTLS, Authentication.AUTO,
                    "localhost", DEFAULT_CONNECT_TIMEOUT, DEFAULT_READ_TIMEOUT, DEFAULT_MAX_MESSAGE_BYTES);
        }

        /** Common unauthenticated local-relay constructor. */
        public Configuration(String host, int port) {
            this(host, port, "", new char[0], Security.PLAINTEXT, Authentication.NONE,
                    "localhost", DEFAULT_CONNECT_TIMEOUT, DEFAULT_READ_TIMEOUT, DEFAULT_MAX_MESSAGE_BYTES);
        }

        @Override
        public char[] password() {
            return password.clone();
        }

        /** Resolves AUTO to NONE when the configuration has no credentials. */
        public Authentication resolvedAuthentication() {
            return authentication == Authentication.AUTO && username.isEmpty() ? Authentication.NONE : authentication;
        }

        /** STARTTLS needs ESMTP capability negotiation even without authentication. */
        public boolean requiresEhlo() {
            return security == Security.STARTTLS || resolvedAuthentication() != Authentication.NONE;
        }

        @Override
        public String toString() {
            return "Configuration[host=" + host + ", port=" + port + ", username=" + username
                    + ", password=<redacted>, security=" + security + ", authentication=" + authentication
                    + ", clientName=" + clientName + ", maxMessageBytes=" + maxMessageBytes + ']';
        }
    }

    /** Immutable outbound message. BCC recipients are used for SMTP delivery but omitted from headers. */
    public record Message(
            String from,
            List<String> to,
            List<String> cc,
            List<String> bcc,
            String replyTo,
            String subject,
            String textBody,
            String htmlBody,
            List<Attachment> attachments) {

        public Message {
            from = validAddress(from, "Sender address");
            to = addresses(to, "To recipient");
            cc = addresses(cc, "CC recipient");
            bcc = addresses(bcc, "BCC recipient");
            if (to.isEmpty() && cc.isEmpty() && bcc.isEmpty()) {
                throw ExceptionHandling.invalid("An SMTP message needs at least one recipient.");
            }
            replyTo = replyTo == null || replyTo.isBlank() ? null : validAddress(replyTo, "Reply-to address");
            subject = validOptionalHeader(__.defaultTo(subject, ""), "SMTP subject", 512);
            if (subject.getBytes(StandardCharsets.UTF_8).length > 512) {
                throw ExceptionHandling.invalid("SMTP subject exceeds 512 UTF-8 bytes.");
            }
            textBody = __.defaultTo(textBody, "");
            htmlBody = __.defaultTo(htmlBody, "");
            attachments = attachments == null ? List.of() : List.copyOf(__.compact(attachments));
            if (textBody.isEmpty() && htmlBody.isEmpty() && attachments.isEmpty()) {
                throw ExceptionHandling.invalid("SMTP message needs text, HTML, or an attachment.");
            }
        }

        /** Returns every envelope recipient in deterministic To, CC, BCC order. */
        public List<String> allRecipients() {
            return List.copyOf(__.compact(List.of(to, cc, bcc)).stream().flatMap(Collection::stream).distinct().toList());
        }

        /** Starts a message builder without creating an SMTP client. */
        public static Builder builder() {
            return new Builder();
        }

        /** Fluent builder that avoids a long positional message constructor. */
        public static final class Builder {
            private String from;
            private final List<String> to = new ArrayList<>();
            private final List<String> cc = new ArrayList<>();
            private final List<String> bcc = new ArrayList<>();
            private String replyTo;
            private String subject = "";
            private String text = "";
            private String html = "";
            private final List<Attachment> attachments = new ArrayList<>();

            public Builder from(String address) {
                from = address;
                return this;
            }

            public Builder to(String... addresses) {
                addAddresses(to, addresses, "To recipient");
                return this;
            }

            public Builder cc(String... addresses) {
                addAddresses(cc, addresses, "CC recipient");
                return this;
            }

            public Builder bcc(String... addresses) {
                addAddresses(bcc, addresses, "BCC recipient");
                return this;
            }

            public Builder replyTo(String address) {
                replyTo = address;
                return this;
            }

            public Builder subject(String value) {
                subject = value;
                return this;
            }

            public Builder text(String value) {
                text = value;
                return this;
            }

            public Builder html(String value) {
                html = value;
                return this;
            }

            public Builder attach(Path file) {
                attachments.add(Attachment.of(file));
                return this;
            }

            public Builder attach(Path file, String filename, String contentType) {
                attachments.add(new Attachment(file, filename, contentType));
                return this;
            }

            public Message build() {
                return new Message(from, to, cc, bcc, replyTo, subject, text, html, attachments);
            }
        }
    }

    /** File attachment metadata. File bytes are streamed only during delivery. */
    public record Attachment(Path file, String filename, String contentType) {

        public Attachment {
            if (file == null) throw ExceptionHandling.invalid("SMTP attachment file is required.");
            file = file.toAbsolutePath().normalize();
            if (filename == null || filename.isBlank()) {
                Path fileName = file.getFileName();
                if (fileName == null) throw ExceptionHandling.invalid("SMTP attachment needs a filename.");
                filename = fileName.toString();
            }
            filename = validFilename(filename);
            contentType = contentType == null || contentType.isBlank() ? "" : validContentType(contentType);
        }

        /** Creates metadata using the source file name and a later content-type probe. */
        public static Attachment of(Path file) {
            return new Attachment(file, null, null);
        }
    }

    /** Successful SMTP acceptance by the remote server. */
    public record Delivery(String messageId, List<String> recipients, Instant deliveredAt) {
        public Delivery {
            messageId = validHeader(messageId, "SMTP message identifier", 512);
            recipients = List.copyOf(recipients == null ? List.of() : recipients);
            deliveredAt = deliveredAt == null ? Instant.now() : deliveredAt;
        }
    }

    /** Non-throwing transport result for reporters and best-effort notification paths. */
    public record Attempt(Delivery delivery, String failure) {
        public Attempt {
            failure = __.defaultTo(failure, "");
        }

        public boolean success() {
            return delivery != null;
        }
    }

    private static final class PreparedMessage implements AutoCloseable {
        private final byte[] text;
        private final byte[] html;
        private final List<ResolvedAttachment> attachments;

        private PreparedMessage(byte[] text, byte[] html, List<ResolvedAttachment> attachments) {
            this.text = text;
            this.html = html;
            this.attachments = attachments;
        }

        private static PreparedMessage prepare(Message message, long maxBytes) throws IOException {
            byte[] text = message.textBody().getBytes(StandardCharsets.UTF_8);
            byte[] html = message.htmlBody().getBytes(StandardCharsets.UTF_8);
            long size = text.length + html.length;
            List<ResolvedAttachment> attachments = new ArrayList<>(message.attachments().size());
            try {
                if (size > maxBytes) {
                    throw new IOException("SMTP message exceeds the configured size limit of " + maxBytes + " bytes.");
                }
                for (Attachment attachment : message.attachments()) {
                    if (!Files.isRegularFile(attachment.file())) {
                        throw new IOException("SMTP attachment does not exist or is not a regular file: " + attachment.filename());
                    }
                    long attachmentSize = Files.size(attachment.file());
                    if (attachmentSize > maxBytes - size) {
                        throw new IOException("SMTP message exceeds the configured size limit of " + maxBytes + " bytes.");
                    }
                    size += attachmentSize;
                    String contentType = attachment.contentType().isEmpty() ? Files.probeContentType(attachment.file()) : attachment.contentType();
                    attachments.add(new ResolvedAttachment(attachment.file(), attachment.filename(),
                            contentType == null ? "application/octet-stream" : validContentType(contentType)));
                }
                if (size > maxBytes) throw new IOException("SMTP message exceeds the configured size limit of " + maxBytes + " bytes.");
                return new PreparedMessage(text, html, List.copyOf(attachments));
            } catch (IOException | RuntimeException failure) {
                Arrays.fill(text, (byte) 0);
                Arrays.fill(html, (byte) 0);
                throw failure;
            }
        }

        private byte[] text() {
            return text;
        }

        private byte[] html() {
            return html;
        }

        private List<ResolvedAttachment> attachments() {
            return attachments;
        }

        @Override
        public void close() {
            Arrays.fill(text, (byte) 0);
            Arrays.fill(html, (byte) 0);
        }
    }

    private record ResolvedAttachment(Path file, String filename, String contentType) { }

    private static final class Session implements AutoCloseable {
        private Socket socket;
        private BufferedReader reader;
        private BufferedWriter writer;

        private Session(Socket socket) throws IOException {
            this.socket = socket;
            reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.US_ASCII));
            writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.US_ASCII));
        }

        private boolean tls() {
            return socket instanceof SSLSocket;
        }

        private BufferedWriter writer() {
            return writer;
        }

        private List<String> ehlo(String clientName, boolean required) throws IOException {
            Response response = command("EHLO " + clientName);
            if (response.code() == 250) return response.capabilities();
            if (required) throw response.failure("SMTP server rejected EHLO");
            command("HELO " + clientName).require(250);
            return List.of();
        }

        private void upgradeToTls(Configuration configuration) throws IOException {
            SSLSocketFactory factory = (SSLSocketFactory) SSLSocketFactory.getDefault();
            SSLSocket secure = (SSLSocket) factory.createSocket(
                    socket, configuration.host(), configuration.port(), true);
            secure.setSoTimeout(timeoutMillis(configuration.readTimeout()));
            configureTls(secure);
            secure.startHandshake();
            socket = secure;
            reader = new BufferedReader(new InputStreamReader(secure.getInputStream(), StandardCharsets.US_ASCII));
            writer = new BufferedWriter(new OutputStreamWriter(secure.getOutputStream(), StandardCharsets.US_ASCII));
        }

        private Response command(String command) throws IOException {
            writer.write(command);
            writer.write("\r\n");
            writer.flush();
            return readResponse();
        }

        private void dataLine(String value) throws IOException {
            writer.write(value);
            writer.write("\r\n");
        }

        private Response endData() throws IOException {
            writer.write(".\r\n");
            writer.flush();
            return readResponse();
        }

        private Response readResponse() throws IOException {
            List<String> lines = new ArrayList<>();
            int code = -1;
            for (int index = 0; index < MAX_RESPONSE_LINES; index++) {
                String line = reader.readLine();
                if (line == null) throw new IOException("SMTP server closed the connection.");
                if (line.length() > MAX_RESPONSE_LINE_LENGTH) throw new IOException("SMTP response line exceeds the safety limit.");
                if (line.length() < 3 || !Character.isDigit(line.charAt(0)) || !Character.isDigit(line.charAt(1)) || !Character.isDigit(line.charAt(2))) {
                    throw new IOException("SMTP server returned a malformed response.");
                }
                int responseCode = Integer.parseInt(line.substring(0, 3));
                if (code == -1) code = responseCode;
                else if (code != responseCode) throw new IOException("SMTP response codes changed within one response.");
                lines.add(line.length() > 4 ? line.substring(4) : "");
                if (line.length() == 3 || line.charAt(3) != '-') return new Response(code, List.copyOf(lines));
            }
            throw new IOException("SMTP response exceeded " + MAX_RESPONSE_LINES + " lines.");
        }

        @Override
        public void close() throws IOException {
            socket.close();
        }
    }

    private record Response(int code, List<String> lines) {
        private Response require(int... expected) throws IOException {
            for (int candidate : expected) if (candidate == code) return this;
            throw failure("Unexpected SMTP response");
        }

        private List<String> capabilities() {
            return lines;
        }

        private IOException failure(String prefix) {
            return new IOException(prefix + " (SMTP " + code + ").");
        }
    }

    private static List<String> addresses(List<String> values, String label) {
        if (values == null) return List.of();
        return List.copyOf(__.compact(values).stream().map(value -> validAddress(value, label)).distinct().toList());
    }

    private static void addAddresses(List<String> target, String[] values, String label) {
        if (values == null || values.length == 0) throw ExceptionHandling.invalid(label + " is required.");
        for (String value : values) target.add(validAddress(value, label));
    }

    private static String validHost(String value) {
        String host = validSingleLine(value, "SMTP host");
        if (host.length() > 253 || host.contains(" ") || host.contains("@")) {
            throw ExceptionHandling.invalid("SMTP host is invalid.");
        }
        return host;
    }

    private static String validClientName(String value) {
        String client = validSingleLine(value, "SMTP client name");
        if (!client.matches("[A-Za-z0-9][A-Za-z0-9.-]{0,252}")) {
            throw ExceptionHandling.invalid("SMTP client name must be a hostname-safe value.");
        }
        return client;
    }

    private static String validAddress(String value, String label) {
        String address = validSingleLine(value, label);
        int at = address.lastIndexOf('@');
        if (at <= 0 || at == address.length() - 1 || address.length() > 320 || address.contains(" ")
                || address.contains("<") || address.contains(">") || !isAsciiPrintable(address)) {
            throw ExceptionHandling.invalid(label + " must be a plain email address.");
        }
        return address;
    }

    private static String validFilename(String value) {
        String filename = validHeader(value, "SMTP attachment filename", 255);
        if (filename.contains("/") || filename.contains("\\") || filename.contains("\"") || filename.contains(";")
                || !isAsciiPrintable(filename)) {
            throw ExceptionHandling.invalid("SMTP attachment filename contains unsupported characters.");
        }
        return filename;
    }

    private static String validContentType(String value) {
        String type = validHeader(value, "SMTP attachment content type", 128).toLowerCase(Locale.ROOT);
        if (!type.matches("[a-z0-9!#$&^_.+-]+/[a-z0-9!#$&^_.+-]+")) {
            throw ExceptionHandling.invalid("SMTP attachment content type is invalid.");
        }
        return type;
    }

    private static String validHeader(String value, String label, int maxLength) {
        String header = validSingleLine(value, label);
        if (header.length() > maxLength) throw ExceptionHandling.invalid(label + " exceeds " + maxLength + " characters.");
        return header;
    }

    private static String validOptionalHeader(String value, String label, int maxLength) {
        String header = value == null ? "" : value.strip();
        if (header.indexOf('\r') >= 0 || header.indexOf('\n') >= 0 || header.indexOf('\0') >= 0) {
            throw ExceptionHandling.invalid(label + " cannot contain line breaks or null characters.");
        }
        if (header.length() > maxLength) throw ExceptionHandling.invalid(label + " exceeds " + maxLength + " characters.");
        return header;
    }

    private static String validSingleLine(String value, String label) {
        if (value == null || value.isBlank()) throw ExceptionHandling.invalid(label + " is required.");
        if (value.indexOf('\r') >= 0 || value.indexOf('\n') >= 0 || value.indexOf('\0') >= 0) {
            throw ExceptionHandling.invalid(label + " cannot contain line breaks or null characters.");
        }
        return value.strip();
    }

    private static Duration validTimeout(Duration value, String label) {
        if (value.isNegative() || value.isZero()) throw ExceptionHandling.invalid(label + " must be positive.");
        return value;
    }

    private static boolean isAsciiPrintable(String value) {
        return value.chars().allMatch(character -> character >= 33 && character <= 126);
    }
}
