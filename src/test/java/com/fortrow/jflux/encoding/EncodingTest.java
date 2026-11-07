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

package com.fortrow.jflux.encoding;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EncodingTest {

    @Test
    void hexRoundTripSupportsBothCaseForms() {
        byte[] input = {(byte) 0x00, (byte) 0x7f, (byte) 0x80, (byte) 0xff};

        assertEquals("007f80ff", Hex.getHex(input));
        assertArrayEquals(input, Hex.fromHex("007F80ff"));
        assertArrayEquals(input, Hex.decode("007F80ff"));
    }

    @Test
    void hexValidationDoesNotDecodeMalformedValues() {
        assertFalse(Hex.isValid(null));
        assertFalse(Hex.isValid("abc"));
        assertFalse(Hex.isValid("gg"));
        assertTrue(Hex.isValid(""));
        assertNull(Hex.fromHex("abc"));
        assertNull(Hex.fromHex("gg"));
        assertThrows(IllegalArgumentException.class, () -> Hex.decode("abc"));
    }

    @Test
    void base64StringConversionsUseUtf8() {
        String input = "jflux π 🚀";

        String encoded = BaseX.encode64(input);

        assertEquals(input, BaseX.stringFrom64(encoded));
        assertArrayEquals(input.getBytes(StandardCharsets.UTF_8), BaseX.bytesFrom64(encoded));
        assertNull(BaseX.bytesFrom64("not base64!"));
    }
}
