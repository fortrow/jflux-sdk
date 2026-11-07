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

import java.util.Arrays;
import java.util.concurrent.CompletableFuture;

/**
 * Microsoft 365/Outlook SMTP client preset using the Office 365 STARTTLS
 * endpoint. Tenant policy can disable password-based SMTP AUTH; applications
 * should enable it only when their Microsoft 365 policy permits it.
 */
public final class Outlook {

    public static final String SMTP_HOST = "smtp.office365.com";
    public static final int SMTP_PORT = 587;

    private final String address;
    private final SMTP smtp;

    /** Creates an Outlook/Microsoft 365 STARTTLS client. */
    public Outlook(String address, char[] password) {
        this.address = validAddress(address);
        this.smtp = new SMTP(configuration(this.address, password));
    }

    /** Convenience constructor for existing string-based configuration sources. */
    public Outlook(String address, String password) {
        this.address = validAddress(address);
        char[] value = password == null ? null : password.toCharArray();
        try {
            this.smtp = new SMTP(configuration(this.address, value));
        } finally {
            if (value != null) Arrays.fill(value, '\0');
        }
    }

    /** Returns Office 365's authenticated STARTTLS configuration without opening a connection. */
    public static SMTP.Configuration configuration(String address, char[] password) {
        String account = validAddress(address);
        return new SMTP.Configuration(SMTP_HOST, SMTP_PORT, account, password,
                SMTP.Security.STARTTLS, SMTP.Authentication.AUTO, "localhost",
                SMTP.DEFAULT_CONNECT_TIMEOUT, SMTP.DEFAULT_READ_TIMEOUT, SMTP.DEFAULT_MAX_MESSAGE_BYTES);
    }

    /** Returns the sender address associated with this client. */
    public String address() {
        return address;
    }

    /** Starts a message with the configured Outlook address as its sender. */
    public SMTP.Message.Builder message() {
        return SMTP.message().from(address);
    }

    /** Sends an already-built message. */
    public SMTP.Delivery send(SMTP.Message message) throws CommonIOException {
        return smtp.send(message);
    }

    /** Sends a text email from this Outlook account. */
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
            throw ExceptionHandling.invalid("Outlook address must be a plain email address.");
        }
        return address.strip();
    }
}
