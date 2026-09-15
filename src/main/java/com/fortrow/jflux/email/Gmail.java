package com.fortrow.jflux.email;

import com.fortrow.jflux.exceptions.ExceptionHandling;
import com.fortrow.jflux.exceptions.types.CommonIOException;

import java.util.Arrays;
import java.util.concurrent.CompletableFuture;

/**
 * Gmail SMTP client preset. Gmail generally requires a Google app password;
 * ordinary account passwords and OAuth browser flows are intentionally not
 * handled by this low-level SMTP package.
 */
public final class Gmail {

    public static final String SMTP_HOST = "smtp.gmail.com";
    public static final int SMTP_PORT = 587;

    private final String address;
    private final SMTP smtp;

    /** Creates a Gmail STARTTLS client from an address and Google app password. */
    public Gmail(String address, char[] appPassword) {
        this.address = validAddress(address);
        this.smtp = new SMTP(configuration(this.address, appPassword));
    }

    /** Convenience constructor for existing string-based configuration sources. */
    public Gmail(String address, String appPassword) {
        this.address = validAddress(address);
        char[] password = appPassword == null ? null : appPassword.toCharArray();
        try {
            this.smtp = new SMTP(configuration(this.address, password));
        } finally {
            if (password != null) Arrays.fill(password, '\0');
        }
    }

    /** Returns Gmail's authenticated STARTTLS configuration without opening a connection. */
    public static SMTP.Configuration configuration(String address, char[] appPassword) {
        String account = validAddress(address);
        return new SMTP.Configuration(SMTP_HOST, SMTP_PORT, account, appPassword,
                SMTP.Security.STARTTLS, SMTP.Authentication.AUTO, "localhost",
                SMTP.DEFAULT_CONNECT_TIMEOUT, SMTP.DEFAULT_READ_TIMEOUT, SMTP.DEFAULT_MAX_MESSAGE_BYTES);
    }

    /** Returns the sender address associated with this client. */
    public String address() {
        return address;
    }

    /** Starts a message with the configured Gmail address as its sender. */
    public SMTP.Message.Builder message() {
        return SMTP.message().from(address);
    }

    /** Sends an already-built message. Its sender must match the desired envelope sender. */
    public SMTP.Delivery send(SMTP.Message message) throws CommonIOException {
        return smtp.send(message);
    }

    /** Sends a text email from this Gmail account. */
    public SMTP.Delivery send(String recipient, String subject, String text) throws CommonIOException {
        return smtp.send(message().to(recipient).subject(subject).text(text).build());
    }

    /** Attempts delivery without throwing, appropriate for optional notifications. */
    public SMTP.Attempt trySend(SMTP.Message message) {
        return smtp.trySend(message);
    }

    /** Sends an email on a virtual thread. */
    public CompletableFuture<SMTP.Delivery> sendAsync(SMTP.Message message) {
        return smtp.sendAsync(message);
    }

    /** Exposes the reusable transport for advanced SMTP configuration inspection. */
    public SMTP smtp() {
        return smtp;
    }

    private static String validAddress(String address) {
        if (address == null || address.isBlank() || address.indexOf('@') <= 0 || address.indexOf('\r') >= 0
                || address.indexOf('\n') >= 0 || address.contains(" ")) {
            throw ExceptionHandling.invalid("Gmail address must be a plain email address.");
        }
        return address.strip();
    }
}
