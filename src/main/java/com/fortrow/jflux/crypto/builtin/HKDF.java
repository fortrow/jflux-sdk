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
 * File created by Ryan Fitzgerald (ryan@fortrow.com) on 2026-05-10 22:51:05 EDT
 */
package com.fortrow.jflux.crypto.builtin;

import com.fortrow.jflux.encoding.Hex;
import com.fortrow.jflux.exceptions.ExceptionHandling;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public final class HKDF {

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final int HASH_LENGTH = 32;

    private HKDF() {}

    public static byte[] extract(byte[] salt, byte[] inputKeyMaterial) {
        byte[] actualSalt = salt == null ? new byte[HASH_LENGTH] : salt;
        return hmac(actualSalt, inputKeyMaterial);
    }

    public static byte[] expand(byte[] pseudorandomKey, byte[] info, int length) {
        if (length < 0 || length > 255 * HASH_LENGTH) {
            throw ExceptionHandling.invalid("Invalid HKDF output length");
        }
        byte[] actualInfo = info == null ? new byte[0] : info;
        byte[] output = new byte[length];
        byte[] previous = new byte[0];
        int generated = 0;
        int counter = 1;
        while (generated < length) {
            byte[] input = new byte[previous.length + actualInfo.length + 1];
            System.arraycopy(previous, 0, input, 0, previous.length);
            System.arraycopy(actualInfo, 0, input, previous.length, actualInfo.length);
            input[input.length - 1] = (byte) counter;
            previous = hmac(pseudorandomKey, input);
            int copy = Math.min(previous.length, length - generated);
            System.arraycopy(previous, 0, output, generated, copy);
            generated += copy;
            counter++;
        }
        return output;
    }

    public static byte[] deriveKey(byte[] inputKeyMaterial, byte[] salt, byte[] info, int length) {
        return expand(extract(salt, inputKeyMaterial), info, length);
    }

    public static String deriveKeyHex(String inputKeyMaterialHex, String saltHex, String info, int length) {
        byte[] salt = saltHex == null ? null : Hex.fromHex(saltHex);
        return Hex.getHex(deriveKey(
                Hex.fromHex(inputKeyMaterialHex),
                salt,
                info == null ? null : info.getBytes(StandardCharsets.UTF_8),
                length
        ));
    }

    private static byte[] hmac(byte[] key, byte[] input) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(key, HMAC_ALGORITHM));
            return mac.doFinal(input == null ? new byte[0] : input);
        } catch (Exception e) {
            throw ExceptionHandling.failure("HKDF failed", e);
        }
    }
}
