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
import com.fortrow.jflux.exceptions.ExceptionHandling;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/** Versioned ML-KEM + HKDF-SHA256 + AES-256-GCM envelopes for small key material. */
public final class KemEnvelope {
    private static final int MAX_PLAINTEXT = 4096;
    private static final int NONCE_BYTES = 12;
    private KemEnvelope() { }

    /** Encrypts key material; context must be identical at the recipient. */
    public static byte[] seal(String algorithm, byte[] publicKey, byte[] plaintext, byte[] context) {
        int kemLength = encapsulationLength(algorithm);
        if (plaintext == null || plaintext.length > MAX_PLAINTEXT)
            throw ExceptionHandling.invalid("KEM envelope plaintext exceeds 4096 bytes.");
        Cryptography crypto = Cryptography.defaults();
        byte[] aad = context == null ? new byte[0] : context;
        byte[] secret = null;
        byte[] key = null;
        try (EncapsulatedSecret result = crypto.encapsulate(algorithm, publicKey)) {
            byte[] encapsulation = result.encapsulation();
            if (encapsulation.length != kemLength) throw ExceptionHandling.invalid("Invalid KEM encapsulation length.");
            secret = result.secret();
            key = crypto.deriveKey(secret, encapsulation, info(algorithm), 32);
            byte[] nonce = crypto.randomBytes(NONCE_BYTES);
            byte[] ciphertext = crypto.encrypt("aes-gcm-256", plaintext, key, nonce, aad);
            return ByteBuffer.allocate(1 + kemLength + NONCE_BYTES + ciphertext.length)
                    .put((byte) 1).put(encapsulation).put(nonce).put(ciphertext).array();
        } finally {
            erase(secret);
            erase(key);
        }
    }

    /** Authenticates and decrypts an envelope, rejecting malformed or modified data. */
    public static byte[] open(String algorithm, byte[] privateKey, byte[] envelope, byte[] context) {
        int kemLength = encapsulationLength(algorithm);
        int overhead = 1 + kemLength + NONCE_BYTES + 16;
        if (envelope == null || envelope.length < overhead || envelope.length > overhead + MAX_PLAINTEXT
                || envelope[0] != 1) throw ExceptionHandling.invalid("Invalid KEM envelope.");
        Cryptography crypto = Cryptography.defaults();
        byte[] encapsulation = Arrays.copyOfRange(envelope, 1, 1 + kemLength);
        byte[] nonce = Arrays.copyOfRange(envelope, 1 + kemLength, 1 + kemLength + NONCE_BYTES);
        byte[] ciphertext = Arrays.copyOfRange(envelope, 1 + kemLength + NONCE_BYTES, envelope.length);
        byte[] secret = null;
        byte[] key = null;
        try {
            secret = crypto.decapsulate(algorithm, encapsulation, privateKey);
            key = crypto.deriveKey(secret, encapsulation, info(algorithm), 32);
            byte[] plaintext = crypto.decrypt("aes-gcm-256", ciphertext, key, nonce, context == null ? new byte[0] : context);
            if (plaintext == null) throw ExceptionHandling.invalid("KEM envelope authentication failed.");
            return plaintext;
        } finally {
            erase(secret);
            erase(key);
        }
    }

    private static byte[] info(String algorithm) {
        return ("JFlux-KEM-Envelope-v1/" + algorithm).getBytes(StandardCharsets.US_ASCII);
    }

    private static int encapsulationLength(String algorithm) {
        if (algorithm == null) throw ExceptionHandling.invalid("KEM algorithm is required.");
        return switch (algorithm) {
            case "ml-kem-512" -> 768;
            case "ml-kem-768" -> 1088;
            case "ml-kem-1024" -> 1568;
            default -> throw ExceptionHandling.invalid("Unsupported KEM envelope algorithm: " + algorithm);
        };
    }

    private static void erase(byte[] bytes) {
        if (bytes != null) Arrays.fill(bytes, (byte) 0);
    }
}
