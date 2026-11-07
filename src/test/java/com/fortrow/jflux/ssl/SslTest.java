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

package com.fortrow.jflux.ssl;

import com.fortrow.jflux.ssl.keystore.JavaKeyStore;
import com.fortrow.jflux.ssl.selfsigned.SelfSignedSSL;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.net.ssl.SSLContext;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class SslTest {

    @Test
    void selfSignedCertificateBuildsTlsContext(@TempDir Path directory) {
        if (!SelfSignedSSL.isAvailable()) return;

        SslCertKeyPair pair = SelfSignedSSL.create(directory.toString(), "test-key.pem",
                "US", "Virginia", "Richmond", "Fortrow", "IT", "localhost");

        assertTrue(pair.getKey().isFile());
        assertTrue(pair.getCert().isFile());
        JavaKeyStore keyStore = new JavaKeyStore(pair, "v1.3");
        assertNotNull(keyStore.getNative());
        assertEquals("TLSv1.3", keyStore.ssl().getProtocol());
    }

    @Test
    void tlsVersionAndInputFilesAreValidated(@TempDir Path directory) {
        SslCertKeyPair missing = new SslCertKeyPair(directory.resolve("missing-key.pem").toFile(),
                directory.resolve("missing-cert.pem").toFile());

        assertThrows(IllegalArgumentException.class, () -> new JavaKeyStore(missing, "v1.1"));
        assertThrows(IllegalArgumentException.class, () -> new JavaKeyStore(missing, "v1.3"));
    }

    @Test
    void compatibilityCaOverloadDoesNotPretendToSignWithOnlyACertificate(@TempDir Path directory) {
        assertThrows(UnsupportedOperationException.class, () -> SelfSignedSSL.createWithCA(
                directory.toString(), "server", directory.resolve("ca.pem").toFile(),
                "US", "Virginia", "Richmond", "Fortrow", "IT", "localhost"));
    }
}
