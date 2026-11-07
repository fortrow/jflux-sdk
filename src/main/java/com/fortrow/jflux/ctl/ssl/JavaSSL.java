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
package com.fortrow.jflux.ctl.ssl;

import com.fortrow.jflux.exceptions.ExceptionHandling;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.List;

/** Small Java TLS baseline helper for control-plane and system integrations. */
public final class JavaSSL {

    private static final List<String> SECURE_PROTOCOLS = List.of("TLSv1.3", "TLSv1.2");

    private JavaSSL() { }

    /** Creates an initialized TLS context using the JVM trust and key managers. */
    public static SSLContext context() {
        return context("TLS");
    }

    /** Creates an initialized TLS context for TLS, TLSv1.2, or TLSv1.3. */
    public static SSLContext context(String protocol) {
        String value = protocol == null || protocol.isBlank() ? "TLS" : protocol.trim();
        if (!value.equalsIgnoreCase("TLS") && !value.equalsIgnoreCase("TLSv1.2")
                && !value.equalsIgnoreCase("TLSv1.3")) {
            throw ExceptionHandling.invalid("Only TLS, TLSv1.2, and TLSv1.3 are supported.");
        }
        try {
            SSLContext context = SSLContext.getInstance(value);
            context.init(null, null, new SecureRandom());
            return context;
        } catch (GeneralSecurityException e) {
            throw ExceptionHandling.failure("Unable to initialize the Java TLS context.", e);
        }
    }

    /** Returns client parameters restricted to TLS 1.2/1.3 with HTTPS hostname verification. */
    public static SSLParameters clientParameters() {
        SSLParameters parameters = secureParameters();
        parameters.setEndpointIdentificationAlgorithm("HTTPS");
        return parameters;
    }

    /** Returns server parameters restricted to TLS 1.2/1.3. */
    public static SSLParameters serverParameters() {
        return secureParameters();
    }

    private static SSLParameters secureParameters() {
        SSLParameters parameters = context().getDefaultSSLParameters();
        parameters.setProtocols(SECURE_PROTOCOLS.toArray(String[]::new));
        return parameters;
    }
}
