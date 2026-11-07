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
 *
 * File created by Ryan Fitzgerald (ryan@fortrow.com) on 2026-05-04 15:27:04 EDT
 */
package com.fortrow.jflux.ssl;


import java.io.File;
import java.util.Objects;

/** Files and credentials used to build a Java TLS context. */
public final class SslCertKeyPair {

    private final File key;
    private final File cert;
    private final String password;

    public SslCertKeyPair(File key, File cert) {
        this(key, cert, "");
    }

    public SslCertKeyPair(File key, File cert, String password) {
        this.key = Objects.requireNonNull(key, "Private key file is required.");
        this.cert = Objects.requireNonNull(cert, "Certificate file is required.");
        this.password = password == null ? "" : password;
    }

    public File getKey() {
        return key;
    }

    public File getCert() {
        return cert;
    }

    public String getPassword() {
        return password;
    }
}
