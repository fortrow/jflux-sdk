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
package com.fortrow.jflux.crypto;

import com.fortrow.jflux.encoding.Hex;
import com.fortrow.jflux.exceptions.ExceptionHandling;

import javax.crypto.KeyAgreement;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;
import java.util.UUID;
import java.util.zip.Adler32;

public class Cryptography {

    private static final Cryptography DEFAULT = new Cryptography(CryptographyRegister.fromDefaults());
    private final CryptographyRegister register;

    public Cryptography(CryptographyRegister register) {
        if (register == null) throw ExceptionHandling.invalid("Cryptography register is required");
        this.register = register;
    }

    /** Returns the process-wide register containing JFlux's standard algorithms. */
    public static Cryptography defaults() {
        return DEFAULT;
    }

    public byte[] encrypt(String algo, byte[] input, byte[] key, byte[]... aad) {
        return register.encrypt(algo, input, key, aad);
    }

    public byte[] decrypt(String algo, byte[] input, byte[] key, byte[]... aad) {
        return register.decrypt(algo, input, key, aad);
    }

    public byte[] hash(String algo, byte[] input) {
        return register.hash(algo, input);
    }

    public byte[] hmac(String algo, byte[] input, byte[] key) {
        return register.hmac(algo, input, key);
    }

    /** Returns cryptographically secure random bytes for application secrets and nonces. */
    public byte[] randomBytes(int length) {
        if (length < 0) throw ExceptionHandling.invalid("Random byte length must not be negative");
        return Rand.secure(length);
    }

    /** Creates a cryptographically random alphanumeric value with an optional prefix. */
    public String randomString(int length, String prefix) {
        if (length < 0) throw ExceptionHandling.invalid("Random string length must not be negative");
        String alphabet = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
        StringBuilder value = new StringBuilder(length);
        while (value.length() < length) {
            for (byte next : randomBytes(Math.max(1, length - value.length()))) {
                int unsigned = next & 0xff;
                if (unsigned >= 248) continue;
                value.append(alphabet.charAt(unsigned % alphabet.length()));
                if (value.length() == length) break;
            }
        }
        String actualPrefix = prefix == null ? "" : prefix;
        return actualPrefix.isEmpty() ? value.toString() : actualPrefix + "_" + value;
    }

    /** Derives password material through the registered JFlux crypto facade. */
    public byte[] pbkdf2(String password, byte[] salt, int iterations, int bits) {
        if (password == null) throw ExceptionHandling.invalid("Password is required");
        if (salt == null) throw ExceptionHandling.invalid("Password salt is required");
        if (iterations < 1 || bits < 1) throw ExceptionHandling.invalid("PBKDF2 parameters are invalid");
        try {
            PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, iterations, bits);
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
        } catch (Exception failure) {
            throw ExceptionHandling.failure("Unable to derive PBKDF2 password material", failure);
        }
    }

    public byte[][] generateKeyPair(String algo) {
        if ("x25519".equalsIgnoreCase(algo)) return generateX25519KeyPair();
        return register.generateKeyPair(algo);
    }

    public com.fortrow.jflux.crypto.adapters.EncapsulatedSecret encapsulate(
            String algo, byte[] publicKey) {
        return register.encapsulate(algo, publicKey);
    }

    public byte[] decapsulate(String algo, byte[] encapsulation, byte[] privateKey) {
        return register.decapsulate(algo, encapsulation, privateKey);
    }

    public byte[] sign(String algo, byte[] input, byte[] privateKey) {
        return register.sign(algo, input, privateKey);
    }

    public boolean verify(String algo, byte[] input, byte[] signature,
                          byte[] publicKey) {
        return register.verify(algo, input, signature, publicKey);
    }

    /** Returns a lowercase hexadecimal digest using a registered hash algorithm. */
    public String hashHex(String algo, byte[] input) {
        return Hex.encode(hash(algo, input));
    }

    /** Returns a lowercase hexadecimal digest using UTF-8 input. */
    public String hashHex(String algo, String input) {
        if (input == null) throw ExceptionHandling.invalid("Hash input is required");
        return hashHex(algo, input.getBytes(StandardCharsets.UTF_8));
    }

    /** Returns a lowercase hexadecimal HMAC using a registered HMAC algorithm. */
    public String hmacHex(String algo, byte[] input, byte[] key) {
        return Hex.encode(hmac(algo, input, key));
    }

    /** Derives key material using HKDF with the registered SHA-256 HMAC. */
    public byte[] deriveKey(byte[] inputKeyMaterial, byte[] salt, byte[] info, int length) {
        if (inputKeyMaterial == null) throw ExceptionHandling.invalid("HKDF input key material is required");
        if (length < 1 || length > 255 * 32) throw ExceptionHandling.invalid("HKDF length is out of range");
        byte[] actualSalt = salt == null ? new byte[32] : salt;
        byte[] actualInfo = info == null ? new byte[0] : info;
        byte[] prk = hmac("hmac-sha3-256", inputKeyMaterial, actualSalt);
        byte[] result = new byte[length];
        byte[] previous = new byte[0];
        int offset = 0;
        try {
            for (int counter = 1; offset < length; counter++) {
                byte[] blockInput = new byte[previous.length + actualInfo.length + 1];
                System.arraycopy(previous, 0, blockInput, 0, previous.length);
                System.arraycopy(actualInfo, 0, blockInput, previous.length, actualInfo.length);
                blockInput[blockInput.length - 1] = (byte) counter;
                byte[] next;
                try {
                    next = hmac("hmac-sha3-256", blockInput, prk);
                } finally {
                    Arrays.fill(blockInput, (byte) 0);
                    Arrays.fill(previous, (byte) 0);
                }
                previous = next;
                int copy = Math.min(previous.length, length - offset);
                System.arraycopy(previous, 0, result, offset, copy);
                offset += copy;
            }
            return result;
        } finally {
            Arrays.fill(prk, (byte) 0);
            Arrays.fill(previous, (byte) 0);
        }
    }

    /** Creates the historical Meridian identifier through the registered SHA-1 algorithm. */
    public String id() {
        String material = Hex.encode(Rand.secure(32)) + "_" + System.currentTimeMillis()
                + "_" + Hex.encode(Rand.secure(32)) + "_" + UUID.randomUUID();
        return hashHex("sha1", material);
    }

    /** Returns the historical Adler-32 short identifier without platform-default encoding. */
    public String shortId(String input) {
        if (input == null) throw ExceptionHandling.invalid("Short identifier input is required");
        Adler32 checksum = new Adler32();
        byte[] bytes = input.getBytes(StandardCharsets.UTF_8);
        checksum.update(bytes, 0, bytes.length);
        return Long.toHexString(checksum.getValue());
    }

    /** Compares secrets in constant time, including unequal-length inputs. */
    public boolean secureEquals(byte[] left, byte[] right) {
        if (left == null || right == null) return false;
        int difference = left.length ^ right.length;
        int length = Math.max(left.length, right.length);
        for (int index = 0; index < length; index++) {
            byte leftByte = index < left.length ? left[index] : 0;
            byte rightByte = index < right.length ? right[index] : 0;
            difference |= leftByte ^ rightByte;
        }
        return difference == 0;
    }

    public boolean secureEquals(String left, String right) {
        if (left == null || right == null) return false;
        return secureEquals(left.getBytes(StandardCharsets.UTF_8), right.getBytes(StandardCharsets.UTF_8));
    }

    /** Decodes an encoded JCA public key for the requested algorithm. */
    public PublicKey publicKey(String algo, byte[] encoded) {
        try {
            return KeyFactory.getInstance(jcaAlgorithm(algo)).generatePublic(new X509EncodedKeySpec(encoded));
        } catch (Exception failure) {
            throw ExceptionHandling.invalid("Invalid " + algo + " public key", failure);
        }
    }

    /** Decodes an encoded JCA private key for the requested algorithm. */
    public PrivateKey privateKey(String algo, byte[] encoded) {
        try {
            return KeyFactory.getInstance(jcaAlgorithm(algo)).generatePrivate(new PKCS8EncodedKeySpec(encoded));
        } catch (Exception failure) {
            throw ExceptionHandling.invalid("Invalid " + algo + " private key", failure);
        }
    }

    /** Derives an X25519 shared secret from encoded keys. */
    public byte[] keyAgreement(String algo, byte[] privateKey, byte[] publicKey) {
        if (!"x25519".equalsIgnoreCase(algo)) {
            throw ExceptionHandling.invalid("Unsupported key-agreement algorithm: " + algo);
        }
        try {
            KeyAgreement agreement = KeyAgreement.getInstance("X25519");
            agreement.init(privateKey("x25519", privateKey));
            agreement.doPhase(publicKey("x25519", publicKey), true);
            return agreement.generateSecret();
        } catch (Exception failure) {
            throw ExceptionHandling.failure("Unable to derive " + algo + " shared secret", failure);
        }
    }

    private static byte[][] generateX25519KeyPair() {
        try {
            KeyPair pair = KeyPairGenerator.getInstance("X25519").generateKeyPair();
            return new byte[][]{pair.getPublic().getEncoded(), pair.getPrivate().getEncoded()};
        } catch (Exception failure) {
            throw ExceptionHandling.failure("Unable to generate X25519 key pair", failure);
        }
    }

    private static String jcaAlgorithm(String algo) {
        if (algo == null) throw ExceptionHandling.invalid("Key algorithm is required");
        return switch (algo.toLowerCase(java.util.Locale.ROOT)) {
            case "ed25519" -> "Ed25519";
            case "x25519" -> "X25519";
            case "rsa-2048", "rsa-4096", "rsa" -> "RSA";
            default -> throw ExceptionHandling.invalid("Unsupported key algorithm: " + algo);
        };
    }

}
