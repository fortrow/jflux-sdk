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

package com.fortrow.jflux.crypto.builtin;

import com.fortrow.jflux.exceptions.ExceptionHandling;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;

/** JCA ChaCha20-Poly1305 authenticated encryption. */
public final class ChaCha20Poly1305 {

    public static final int KEY_BYTES = 32;
    public static final int NONCE_BYTES = 12;

    private static final SecureRandom RANDOM = new SecureRandom();

    private ChaCha20Poly1305() {
    }

    public static byte[] encrypt(byte[] plaintext, byte[] key, byte[] nonce, byte[] aad) {
        return crypt(Cipher.ENCRYPT_MODE, plaintext, key, nonce, aad);
    }

    public static byte[] decrypt(byte[] ciphertext, byte[] key, byte[] nonce, byte[] aad) {
        return crypt(Cipher.DECRYPT_MODE, ciphertext, key, nonce, aad);
    }

    public static byte[] generateKey() {
        return randomBytes(KEY_BYTES);
    }

    public static byte[] generateNonce() {
        return randomBytes(NONCE_BYTES);
    }

    private static byte[] crypt(int mode, byte[] input, byte[] key, byte[] nonce, byte[] aad) {
        requireLength("key", key, KEY_BYTES);
        requireLength("nonce", nonce, NONCE_BYTES);
        try {
            Cipher cipher = Cipher.getInstance("ChaCha20-Poly1305");
            cipher.init(mode, new SecretKeySpec(key, "ChaCha20"), new IvParameterSpec(nonce));
            if (aad != null && aad.length > 0) cipher.updateAAD(aad);
            return cipher.doFinal(input);
        } catch (GeneralSecurityException e) {
            throw ExceptionHandling.failure("ChaCha20-Poly1305 is unavailable", e);
        }
    }

    private static byte[] randomBytes(int size) {
        byte[] output = new byte[size];
        RANDOM.nextBytes(output);
        return output;
    }

    private static void requireLength(String name, byte[] value, int expected) {
        if (value == null || value.length != expected) {
            throw ExceptionHandling.invalid(name + " must contain exactly " + expected + " bytes");
        }
    }
}
