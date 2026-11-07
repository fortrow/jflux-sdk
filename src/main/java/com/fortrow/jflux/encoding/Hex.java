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
package com.fortrow.jflux.encoding;

import com.fortrow.jflux.exceptions.ExceptionHandling;

public final class Hex {

    private static final char[] DIGITS = "0123456789abcdef".toCharArray();

    private Hex() {
    }

    /**
     * Encodes bytes as lowercase hexadecimal.
     *
     * @deprecated since 1.1.0; use {@link #encode(byte[])} for a descriptive API.
     *
     * @param data bytes to encode
     * @return a string containing two hexadecimal characters per byte
     */
    @Deprecated(since = "1.1.0", forRemoval = false)
    public static String getHex(byte[] data) {
        return encode(data);
    }

    /**
     * Encodes bytes as lowercase hexadecimal without an intermediate byte array.
     *
     * @param data bytes to encode
     * @return a string containing two hexadecimal characters per byte
     */
    public static String encode(byte[] data) {
        if (data == null) throw ExceptionHandling.invalid("Hex input must not be null");
        char[] output = new char[data.length * 2];
        for (int i = 0; i < data.length; i++) {
            int value = data[i] & 0xff;
            int offset = i * 2;
            output[offset] = DIGITS[value >>> 4];
            output[offset + 1] = DIGITS[value & 0x0f];
        }
        return new String(output);
    }

    /**
     * Decodes hexadecimal, returning {@code null} for malformed input.
     * This is the compatibility API retained from jflux 1.0.x.
     *
     * @deprecated since 1.1.0; use {@link #decode(String)} when malformed input
     * should be reported explicitly. Use this method only when the legacy
     * {@code null} failure contract is required.
     *
     * @param hex hexadecimal input
     * @return decoded bytes, or {@code null} when the input is malformed
     */
    @Deprecated(since = "1.1.0", forRemoval = false)
    public static byte[] fromHex(String hex) {
        if (!isValid(hex)) {
            return null;
        }
        return decode(hex);
    }

    /**
     * Decodes hexadecimal with an explicit failure for malformed input.
     *
     * @param hex hexadecimal input
     * @return decoded bytes
     * @throws IllegalArgumentException if the input is null, has an odd length,
     *                                  or contains a non-hexadecimal character
     */
    public static byte[] decode(String hex) {
        if (hex == null) {
            throw ExceptionHandling.invalid("Hex input must not be null");
        }
        if ((hex.length() & 1) != 0) {
            throw ExceptionHandling.invalid("Hex input must contain an even number of characters");
        }

        byte[] output = new byte[hex.length() / 2];
        for (int i = 0; i < output.length; i++) {
            int offset = i * 2;
            int high = Character.digit(hex.charAt(offset), 16);
            int low = Character.digit(hex.charAt(offset + 1), 16);
            if (high < 0 || low < 0) {
                throw ExceptionHandling.invalid("Invalid hexadecimal character at index "
                        + (high < 0 ? offset : offset + 1));
            }
            output[i] = (byte) ((high << 4) | low);
        }
        return output;
    }

    /**
     * Checks whether a string is valid hexadecimal without allocating decoded bytes.
     *
     * @param hex input to check
     * @return true when the input is non-null, even-length, and hexadecimal
     */
    public static boolean isValid(String hex) {
        if (hex == null || (hex.length() & 1) != 0) {
            return false;
        }
        for (int i = 0; i < hex.length(); i++) {
            if (Character.digit(hex.charAt(i), 16) < 0) {
                return false;
            }
        }
        return true;
    }
}
