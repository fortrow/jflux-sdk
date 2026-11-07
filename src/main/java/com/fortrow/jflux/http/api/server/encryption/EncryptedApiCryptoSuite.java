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

package com.fortrow.jflux.http.api.server.encryption;

import com.fortrow.jflux.crypto.Cryptography;
import com.fortrow.jflux.crypto.KemEnvelope;
import com.fortrow.jflux.crypto.builtin.RSA;
import com.fortrow.jflux.encoding.BaseX;
import com.fortrow.jflux.exceptions.ExceptionHandling;

import java.nio.charset.StandardCharsets;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.Arrays;

/** Explicit encrypted API key-envelope policy. Payloads continue to use AES-256-GCM. */
public enum EncryptedApiCryptoSuite {
    RSA_2048("rsa-2048", 0, 0),
    ML_KEM_512("ml-kem-512", 800, 1632),
    ML_KEM_768("ml-kem-768", 1184, 2400),
    ML_KEM_1024("ml-kem-1024", 1568, 3168);

    public static final String SUITE_HEADER = "X-JFlux-Crypto-Suite";
    public static final String CLIENT_KEY_HEADER = "X-JFlux-Client-Public-Key";
    private final String id;
    private final int publicBytes;
    private final int privateBytes;

    EncryptedApiCryptoSuite(String id, int publicBytes, int privateBytes) {
        this.id = id;
        this.publicBytes = publicBytes;
        this.privateBytes = privateBytes;
    }

    public String id() { return id; }
    public boolean isPostQuantum() { return this != RSA_2048; }

    /** Fails immediately when the optional provider is missing. */
    public void requireAvailable() {
        if (isPostQuantum() && !com.fortrow.jflux.crypto.CryptographyRegister.fromDefaults().keyEncapsulationAlgorithms().containsKey(id))
            throw ExceptionHandling.invalid("Post-quantum API crypto requires the Bouncy Castle ML-KEM provider: " + id);
    }

    public static EncryptedApiCryptoSuite fromId(String id) {
        for (EncryptedApiCryptoSuite suite : values()) if (suite.id.equals(id)) return suite;
        throw ExceptionHandling.invalid("Unsupported encrypted API crypto suite: " + id);
    }

    /** Generates a local pair, public encoding first; erase the private bytes after import. */
    public byte[][] generateKeyPair() {
        requireAvailable();
        return Cryptography.defaults().generateKeyPair(id);
    }

    /** Decodes the raw ML-KEM public key or the legacy RSA X.509 encoding. */
    public PublicKey publicKey(byte[] encoded) {
        if (!isPostQuantum()) {
            if (encoded == null || encoded.length > 512) throw ExceptionHandling.invalid("Invalid RSA public key length.");
            PublicKey key = RSA.publicKeyFromBytes(encoded);
            if (!(key instanceof java.security.interfaces.RSAPublicKey rsa) || rsa.getModulus().bitLength() != 2048)
                throw ExceptionHandling.invalid("The encrypted API requires an RSA-2048 public key.");
            return key;
        }
        if (encoded == null || encoded.length != publicBytes) throw ExceptionHandling.invalid("Invalid ML-KEM public key length.");
        return new KemPublicKey(id, encoded);
    }

    /** Decodes the raw ML-KEM private key or the legacy RSA PKCS#8 encoding. */
    public PrivateKey privateKey(byte[] encoded) {
        if (!isPostQuantum()) return RSA.privateKeyFromBytes(encoded);
        if (encoded == null || encoded.length != privateBytes) throw ExceptionHandling.invalid("Invalid ML-KEM private key length.");
        return new KemPrivateKey(id, encoded);
    }

    public static String seal(byte[] plaintext, PublicKey recipient) {
        if (recipient instanceof KemPublicKey key)
            return BaseX.encode64(KemEnvelope.seal(key.algorithm, key.encoded, plaintext, context(key.algorithm)));
        return RSA.encrypt(plaintext, recipient);
    }

    public static byte[] open(String ciphertext, PrivateKey recipient) {
        if (ciphertext == null || ciphertext.length() > 8000)
            throw ExceptionHandling.invalid("Invalid encrypted API key-envelope size.");
        if (recipient instanceof KemPrivateKey key) {
            if (key.destroyed) throw ExceptionHandling.invalid("The ML-KEM private key has been destroyed.");
            return KemEnvelope.open(key.algorithm, key.encoded, BaseX.bytesFrom64(ciphertext), context(key.algorithm));
        }
        return BaseX.bytesFrom64(RSA.decrypt(BaseX.bytesFrom64(ciphertext), recipient));
    }

    private static byte[] context(String algorithm) {
        return ("JFlux-Encrypted-API-v2/" + algorithm).getBytes(StandardCharsets.US_ASCII);
    }

    private static final class KemPublicKey implements PublicKey {
        private final String algorithm;
        private final byte[] encoded;
        private KemPublicKey(String algorithm, byte[] encoded) { this.algorithm = algorithm; this.encoded = encoded.clone(); }
        public String getAlgorithm() { return algorithm; }
        public String getFormat() { return "RAW"; }
        public byte[] getEncoded() { return encoded.clone(); }
    }

    private static final class KemPrivateKey implements PrivateKey {
        private final String algorithm;
        private final byte[] encoded;
        private boolean destroyed;
        private KemPrivateKey(String algorithm, byte[] encoded) { this.algorithm = algorithm; this.encoded = encoded.clone(); }
        public String getAlgorithm() { return algorithm; }
        public String getFormat() { return "RAW"; }
        public byte[] getEncoded() {
            if (destroyed) throw ExceptionHandling.invalid("The ML-KEM private key has been destroyed.");
            return encoded.clone();
        }
        public void destroy() { Arrays.fill(encoded, (byte) 0); destroyed = true; }
        public boolean isDestroyed() { return destroyed; }
    }
}
