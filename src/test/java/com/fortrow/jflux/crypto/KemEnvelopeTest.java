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

package com.fortrow.jflux.crypto;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;

class KemEnvelopeTest {
    @ParameterizedTest
    @ValueSource(strings = {"ml-kem-512", "ml-kem-768", "ml-kem-1024"})
    void authenticatesKeyMaterialAndRejectsTampering(String algorithm) {
        var crypto = Cryptography.defaults();
        byte[][] keys = crypto.generateKeyPair(algorithm);
        byte[] message = crypto.randomBytes(96);
        byte[] context = "api/session".getBytes(StandardCharsets.UTF_8);
        byte[] sealed = KemEnvelope.seal(algorithm, keys[0], message, context);
        assertArrayEquals(message, KemEnvelope.open(algorithm, keys[1], sealed, context));
        assertFalse(java.util.Arrays.equals(sealed, KemEnvelope.seal(algorithm, keys[0], message, context)));
        assertThrows(RuntimeException.class, () -> KemEnvelope.open(algorithm, keys[1], sealed, new byte[]{1}));
        byte[][] other = crypto.generateKeyPair(algorithm);
        assertThrows(RuntimeException.class, () -> KemEnvelope.open(algorithm, other[1], sealed, context));
        sealed[sealed.length - 1] ^= 1;
        assertThrows(RuntimeException.class, () -> KemEnvelope.open(algorithm, keys[1], sealed, context));
        sealed[0] = 2;
        assertThrows(RuntimeException.class, () -> KemEnvelope.open(algorithm, keys[1], sealed, context));
        assertThrows(RuntimeException.class, () -> KemEnvelope.open(algorithm, keys[1], new byte[10], context));
        assertThrows(RuntimeException.class, () -> KemEnvelope.seal(algorithm, keys[0], new byte[4097], context));
        try (var result = crypto.encapsulate(algorithm, keys[0])) {
            result.close();
            assertArrayEquals(new byte[32], result.secret());
        }
    }
}
