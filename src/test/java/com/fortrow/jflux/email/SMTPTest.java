package com.fortrow.jflux.email;

import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SMTPTest {

    @Test
    void sendsMultipartMessageAndStreamsAttachmentToLocalRelay() throws Throwable {
        Path attachment = Files.createTempFile("jflux-smtp-", ".txt");
        Files.writeString(attachment, "attachment", StandardCharsets.UTF_8);
        try (ServerSocket server = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            CompletableFuture<List<String>> conversation = CompletableFuture.supplyAsync(() -> runRelay(server),
                    command -> Thread.startVirtualThread(command));
            SMTP smtp = new SMTP(localConfiguration(server.getLocalPort()));
            SMTP.Message message = SMTP.message()
                    .from("sender@example.com")
                    .to("to@example.com")
                    .cc("copy@example.com")
                    .bcc("hidden@example.com")
                    .replyTo("replies@example.com")
                    .subject("Hello π")
                    .text("Plain text")
                    .html("<strong>HTML</strong>")
                    .attach(attachment)
                    .build();

            SMTP.Delivery delivery = smtp.send(message);
            List<String> lines = conversation.get(5, TimeUnit.SECONDS);

            assertTrue(delivery.messageId().startsWith("<"));
            assertEquals(List.of("to@example.com", "copy@example.com", "hidden@example.com"), delivery.recipients());
            assertTrue(lines.contains("MAIL FROM:<sender@example.com>"));
            assertTrue(lines.contains("RCPT TO:<hidden@example.com>"));
            assertTrue(lines.stream().anyMatch(line -> line.startsWith("Subject: =?UTF-8?B?")));
            assertTrue(lines.contains("Content-Disposition: attachment; filename=\"" + attachment.getFileName() + "\""));
            assertTrue(lines.contains("YXR0YWNobWVudA=="));
            assertFalse(lines.stream().anyMatch(line -> line.startsWith("Bcc:")));
        } finally {
            Files.deleteIfExists(attachment);
        }
    }

    @Test
    void providerPresetsUseSafeStartTlsEndpointsAndRedactPasswords() {
        SMTP.Configuration gmail = Gmail.configuration("alerts@gmail.example", "app-password".toCharArray());
        SMTP.Configuration outlook = Outlook.configuration("alerts@company.example", "password".toCharArray());

        assertEquals(Gmail.SMTP_HOST, gmail.host());
        assertEquals(SMTP.Security.STARTTLS, gmail.security());
        assertEquals(Outlook.SMTP_HOST, outlook.host());
        assertEquals(SMTP.Security.STARTTLS, outlook.security());
        assertFalse(gmail.toString().contains("app-password"));
    }

    @Test
    void rejectsHeaderInjectionAndPlaintextCredentials() {
        assertThrows(IllegalArgumentException.class, () -> SMTP.message()
                .from("sender@example.com")
                .to("to@example.com")
                .subject("valid\r\nBcc: injected@example.com")
                .text("body")
                .build());
        assertThrows(IllegalArgumentException.class, () -> new SMTP.Configuration(
                "localhost", 25, "user", "secret".toCharArray(), SMTP.Security.PLAINTEXT,
                SMTP.Authentication.AUTO, "localhost", Duration.ofSeconds(1), Duration.ofSeconds(1), 1024));
    }

    @Test
    void nonThrowingAttemptHandlesAnUnavailableRelay() throws Exception {
        int unusedPort;
        try (ServerSocket portReservation = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            unusedPort = portReservation.getLocalPort();
        }
        SMTP smtp = new SMTP(localConfiguration(unusedPort));
        SMTP.Attempt attempt = smtp.trySend(SMTP.message()
                .from("sender@example.com").to("to@example.com").text("body").build());

        assertFalse(attempt.success());
        assertTrue(attempt.failure().contains("Connection") || attempt.failure().contains("connect"));
    }

    private static SMTP.Configuration localConfiguration(int port) {
        return new SMTP.Configuration("127.0.0.1", port, "", new char[0], SMTP.Security.PLAINTEXT,
                SMTP.Authentication.NONE, "localhost", Duration.ofSeconds(2), Duration.ofSeconds(2), 1024 * 1024);
    }

    private static List<String> runRelay(ServerSocket server) {
        List<String> lines = new ArrayList<>();
        try (Socket socket = server.accept();
             BufferedReader input = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.US_ASCII));
             BufferedWriter output = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.US_ASCII))) {
            write(output, "220 jflux.test SMTP ready");
            boolean data = false;
            String line;
            while ((line = input.readLine()) != null) {
                lines.add(line);
                if (data) {
                    if (line.equals(".")) {
                        data = false;
                        write(output, "250 queued");
                    }
                    continue;
                }
                if (line.startsWith("EHLO ")) {
                    write(output, "250-jflux.test");
                    write(output, "250 SIZE 1048576");
                } else if (line.startsWith("HELO ") || line.startsWith("MAIL FROM:") || line.startsWith("RCPT TO:")) {
                    write(output, "250 accepted");
                } else if (line.equals("DATA")) {
                    data = true;
                    write(output, "354 end data with <CR><LF>.<CR><LF>");
                } else if (line.equals("QUIT")) {
                    write(output, "221 bye");
                    return lines;
                } else {
                    write(output, "500 unsupported");
                }
            }
            return lines;
        } catch (IOException failure) {
            throw new IllegalStateException(failure);
        }
    }

    private static void write(BufferedWriter output, String line) throws IOException {
        output.write(line);
        output.write("\r\n");
        output.flush();
    }
}
