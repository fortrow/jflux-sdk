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

import com.fortrow.jflux.crypto.adapters.EncapsulatedSecret;
import com.fortrow.jflux.crypto.adapters.SymmetricAlgorithm;
import com.fortrow.jflux.crypto.builtin.ChaCha20Poly1305;
import com.fortrow.jflux.crypto.builtin.MLDSA;
import com.fortrow.jflux.crypto.builtin.MLKEM;
import com.fortrow.jflux.crypto.builtin.SLHDSA;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CryptographyAdapterTest {

    @Test
    void chachaPoly1305RoundTripsThroughRegister() {
        CryptographyRegister register = CryptographyRegister.fromDefaults();
        Cryptography crypto = new Cryptography(register);
        byte[] plaintext = "adapter-selected encryption".getBytes(StandardCharsets.UTF_8);
        byte[] key = ChaCha20Poly1305.generateKey();
        byte[] nonce = ChaCha20Poly1305.generateNonce();
        byte[] aad = "metadata".getBytes(StandardCharsets.UTF_8);

        byte[] ciphertext = crypto.encrypt("ChaCha20-Poly1305", plaintext, key, nonce, aad);

        assertArrayEquals(plaintext, crypto.decrypt("chacha20-poly1305", ciphertext, key, nonce, aad));
        assertNotEquals(new String(plaintext, StandardCharsets.UTF_8),
                new String(ciphertext, StandardCharsets.ISO_8859_1));
    }

    @Test
    void replacingAnAlgorithmDoesNotChangeCallerCode() {
        CryptographyRegister register = CryptographyRegister.fromDefaults();
        byte[] expected = {9, 8, 7};
        SymmetricAlgorithm replacement = new SymmetricAlgorithm(
                "legacy-encryption",
                0,
                0,
                (input, key, aad) -> expected,
                (input, key, aad) -> input);
        register.register("aes-gcm-256", replacement);

        Cryptography crypto = new Cryptography(register);

        assertArrayEquals(expected, crypto.encrypt("aes-gcm-256", new byte[]{1}, new byte[]{2}));
        assertArrayEquals(new byte[]{1}, crypto.decrypt("AES-GCM-256", new byte[]{1}, new byte[]{2}));
    }

    @Test
    void mlKem768EncapsulatesAndDecapsulates() {
        byte[][] keys = MLKEM.generateKeyPair("ml-kem-768");

        EncapsulatedSecret result = MLKEM.encapsulate(keys[0], "ml-kem-768");
        byte[] recovered = MLKEM.decapsulate(result.encapsulation(), keys[1], "ml-kem-768");

        assertArrayEquals(result.secret(), recovered);
    }

    @Test
    void mlDsa65SignsAndRejectsModifiedMessages() {
        byte[][] keys = MLDSA.generateKeyPair("ml-dsa-65");
        byte[] message = "signed by an adapter".getBytes(StandardCharsets.UTF_8);
        byte[] signature = MLDSA.sign(message, keys[1], "ml-dsa-65");

        assertTrue(MLDSA.verify(message, signature, keys[0], "ml-dsa-65"));
        message[0] ^= 1;
        assertFalse(MLDSA.verify(message, signature, keys[0], "ml-dsa-65"));
    }

    @Test
    void slhDsaSha2SignsAndVerifies() {
        byte[][] keys = SLHDSA.generateKeyPair("slh-dsa-sha2-128s");
        byte[] message = "hash based signature".getBytes(StandardCharsets.UTF_8);
        byte[] signature = SLHDSA.sign(message, keys[1], "slh-dsa-sha2-128s");

        assertTrue(SLHDSA.verify(message, signature, keys[0], "slh-dsa-sha2-128s"));
    }
}
