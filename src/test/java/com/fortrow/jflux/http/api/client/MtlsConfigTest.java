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

package com.fortrow.jflux.http.api.client;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class MtlsConfigTest {
    @Test
    void keepsIdentityAndTrustStoreSettingsWithoutExposingPasswords() {
        char[] identityPassword = "identity-secret".toCharArray();
        char[] trustPassword = "trust-secret".toCharArray();
        MtlsConfig config = MtlsConfig.keyStores(Path.of("client.p12"), "PKCS12", identityPassword,
                Path.of("ca.p12"), "PKCS12", trustPassword);

        identityPassword[0] = 'x';
        trustPassword[0] = 'x';

        assertEquals(Path.of("client.p12"), config.identityStore());
        assertArrayEquals("identity-secret".toCharArray(), config.identityPassword());
        assertEquals(Path.of("ca.p12"), config.trustStore());
        assertArrayEquals("trust-secret".toCharArray(), config.trustStorePassword());
        assertFalse(config.toString().contains("secret"));
    }

    @Test
    void supportsAnIdentityWithoutAnExplicitTrustStore() {
        MtlsConfig config = MtlsConfig.pkcs12(Path.of("client.p12"), new char[0]);

        assertFalse(config.hasTrustStore());
        assertEquals("PKCS12", config.identityStoreType());
    }
}
