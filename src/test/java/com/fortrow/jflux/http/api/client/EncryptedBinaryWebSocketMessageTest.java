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

import com.fortrow.jflux.http.api.websocket.EncryptedBinaryWebSocketMessage;
import com.fortrow.jflux.http.api.websocket.EncryptedBinaryWebSocketMessage.Message;
import java.nio.ByteBuffer;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class EncryptedBinaryWebSocketMessageTest {
    private static final UUID ID = UUID.fromString("00112233-4455-6677-8899-aabbccddeeff");
    private Message request(Map<String, String> headers, byte[] body) {
        return new Message(ID, false, 0, "POST", "/streams/batch", headers, body);
    }
    @Test void binaryApiOptInRequiresExplicitSecurityPrerequisites() {
        var config = new com.fortrow.jflux.http.api.server.config.APIServerConfig("127.0.0.1", 0, "/api")
                .setBinaryWebSocketEnabled(true).useWebSocket(0);
        assertFalse(config.validate());
    }
    @Test void preservesArbitraryCiphertextWithoutBase64AndDoesNotMoveInput() {
        byte[] ciphertext = {0, -1, -128, 10, 13};
        Message original = request(Map.of("X-Api-Key", "key", "Content-Type", "application/octet-stream"), ciphertext);
        byte[] encoded = EncryptedBinaryWebSocketMessage.encode(original);
        assertEquals(0x4a464231, ByteBuffer.wrap(encoded).getInt());
        ByteBuffer wrapped = ByteBuffer.allocate(encoded.length + 4).putInt(42).put(encoded); wrapped.flip(); wrapped.position(4);
        Message decoded = EncryptedBinaryWebSocketMessage.decode(wrapped);
        assertEquals(4, wrapped.position());
        assertEquals(ID, decoded.requestId()); assertEquals("/streams/batch", decoded.path());
        assertEquals("key", decoded.headers().get("x-api-key"));
        assertArrayEquals(ciphertext, decoded.ciphertext());
        ciphertext[0] = 1;
        byte[] returned = decoded.ciphertext(); returned[0] = 2;
        assertEquals(0, decoded.ciphertext()[0]); assertEquals(0, original.ciphertext()[0]);
        assertThrows(UnsupportedOperationException.class, () -> decoded.headers().put("x", "y"));
    }
    @Test void canonicalizesHeadersAndRoundTripsResponse() {
        Map<String, String> first = new LinkedHashMap<>(); first.put("Z", "z"); first.put("A", "a");
        assertArrayEquals(EncryptedBinaryWebSocketMessage.encode(request(first, new byte[0])),
                EncryptedBinaryWebSocketMessage.encode(request(Map.of("a", "a", "z", "z"), new byte[0])));
        Message response = new Message(ID, true, 200, "", "", Map.of(), new byte[] {1});
        Message decoded = EncryptedBinaryWebSocketMessage.decode(ByteBuffer.wrap(EncryptedBinaryWebSocketMessage.encode(response)));
        assertTrue(decoded.response()); assertEquals(200, decoded.status());
    }
    @Test void rejectsForgedLengthsFlagsTrailingDataAndTruncation() {
        byte[] valid = EncryptedBinaryWebSocketMessage.encode(request(Map.of(), new byte[] {1}));
        for (int length = 0; length < valid.length; length++) {
            int truncated = length;
            assertThrows(IllegalArgumentException.class, () -> EncryptedBinaryWebSocketMessage.decode(ByteBuffer.wrap(valid, 0, truncated)));
        }
        byte[] flags = valid.clone(); flags[7] = 1;
        assertThrows(IllegalArgumentException.class, () -> EncryptedBinaryWebSocketMessage.decode(ByteBuffer.wrap(flags)));
        byte[] lengths = valid.clone(); ByteBuffer.wrap(lengths).putInt(32, Integer.MAX_VALUE);
        assertThrows(IllegalArgumentException.class, () -> EncryptedBinaryWebSocketMessage.decode(ByteBuffer.wrap(lengths)));
        byte[] trailing = java.util.Arrays.copyOf(valid, valid.length + 1);
        assertThrows(IllegalArgumentException.class, () -> EncryptedBinaryWebSocketMessage.decode(ByteBuffer.wrap(trailing)));
    }
    @Test void rejectsDuplicateHeadersInvalidUtf8AndInvalidRouting() {
        assertThrows(IllegalArgumentException.class, () -> request(Map.of("X-Key", "a", "x-key", "b"), new byte[0]));
        assertThrows(IllegalArgumentException.class, () -> request(Map.of("x", "bad\r\nvalue"), new byte[0]));
        assertThrows(IllegalArgumentException.class, () -> request(Map.of("x", "\uD800"), new byte[0]).ciphertext());
        byte[] invalid = EncryptedBinaryWebSocketMessage.encode(request(Map.of(), new byte[0]));
        invalid[38] = (byte) 0xff;
        assertThrows(IllegalArgumentException.class, () -> EncryptedBinaryWebSocketMessage.decode(ByteBuffer.wrap(invalid)));
        assertThrows(IllegalArgumentException.class, () -> new Message(ID, false, 0, "POST", "/../secret", Map.of(), new byte[0]));
    }
}
