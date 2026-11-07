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

package com.fortrow.jflux.http.api.websocket;

import com.fortrow.jflux.exceptions.ExceptionHandling;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/** Bounded binary envelope for encrypted API ciphertext; parsing does not authenticate it. */
public final class EncryptedBinaryWebSocketMessage {
    public static final int HEADER_BYTES = 36;
    public static final int MAX_METADATA_BYTES = 65_536;
    public static final int MAX_BODY_BYTES = 4 * 1024 * 1024;
    public static final int MAX_FRAME_BYTES = HEADER_BYTES + MAX_METADATA_BYTES + MAX_BODY_BYTES;
    private static final int MAGIC = 0x4a464231; // JFB1
    private static final int MAX_HEADERS = 64;
    private static final int MAX_TEXT_BYTES = 8192;
    private EncryptedBinaryWebSocketMessage() { }

    public record Message(UUID requestId, boolean response, int status, String method,
                          String path, Map<String, String> headers, byte[] ciphertext) {
        public Message {
            if (requestId == null || headers == null || ciphertext == null)
                throw invalid("Binary API envelope fields are required.");
            if (response ? status < 100 || status > 599 : status != 0)
                throw invalid("Binary API envelope status is invalid.");
            if (response) {
                if (!"".equals(method) || !"".equals(path)) throw invalid("Response routing fields must be empty.");
            } else {
                if (method == null || !method.matches("GET|POST|PUT|PATCH|DELETE|HEAD|OPTIONS|TRACE"))
                    throw invalid("Binary API request method is invalid.");
                if (path == null || !path.startsWith("/") || path.contains("..")
                        || path.contains("#") || path.contains("\\") || path.contains("\r") || path.contains("\n"))
                    throw invalid("Binary API request path is invalid.");
            }
            if (headers.size() > MAX_HEADERS || ciphertext.length > MAX_BODY_BYTES)
                throw invalid("Binary API envelope exceeds its bounds.");
            int metadataSize = textSize(method) + textSize(path);
            TreeMap<String, String> sorted = new TreeMap<>();
            for (var entry : headers.entrySet()) {
                String key = entry.getKey(), value = entry.getValue();
                if (key == null || !key.matches("[!#$%&'*+.^_`|~0-9A-Za-z-]+") || value == null
                        || value.contains("\r") || value.contains("\n") || value.indexOf('\0') >= 0)
                    throw invalid("Binary API header is invalid.");
                metadataSize += textSize(key) + textSize(value);
                if (metadataSize > MAX_METADATA_BYTES) throw invalid("Binary API metadata exceeds its bounds.");
                if (sorted.put(key.toLowerCase(Locale.ROOT), value) != null)
                    throw invalid("Duplicate binary API header.");
            }
            headers = Map.copyOf(sorted);
            ciphertext = ciphertext.clone();
        }
        @Override public byte[] ciphertext() { return ciphertext.clone(); }
        public int ciphertextLength() { return ciphertext.length; }
    }

    public static byte[] encode(Message message) {
        if (message == null) throw invalid("Binary API message is required.");
        var sorted = new TreeMap<>(message.headers());
        int metadataSize = textSize(message.method()) + textSize(message.path());
        for (var entry : sorted.entrySet()) metadataSize += textSize(entry.getKey()) + textSize(entry.getValue());
        if (metadataSize > MAX_METADATA_BYTES) throw invalid("Binary API metadata exceeds its bounds.");
        byte[] body = message.ciphertext;
        ByteBuffer output = ByteBuffer.allocate(HEADER_BYTES + metadataSize + body.length).order(ByteOrder.BIG_ENDIAN);
        output.putInt(MAGIC).put((byte) 1).put((byte) (message.response() ? 2 : 1)).putShort((short) 0);
        output.putLong(message.requestId().getMostSignificantBits()).putLong(message.requestId().getLeastSignificantBits());
        output.putShort((short) message.status()).putShort((short) sorted.size()).putInt(metadataSize).putInt(body.length);
        putText(output, message.method()); putText(output, message.path());
        sorted.forEach((key, value) -> { putText(output, key); putText(output, value); });
        output.put(body);
        return output.array();
    }

    public static Message decode(ByteBuffer frame) {
        if (frame == null || frame.remaining() < HEADER_BYTES || frame.remaining() > MAX_FRAME_BYTES)
            throw invalid("Binary API frame size is invalid.");
        ByteBuffer input = frame.asReadOnlyBuffer().order(ByteOrder.BIG_ENDIAN);
        if (input.getInt() != MAGIC || input.get() != 1) throw invalid("Unsupported binary API envelope.");
        int type = Byte.toUnsignedInt(input.get());
        if ((type != 1 && type != 2) || input.getShort() != 0) throw invalid("Binary API envelope flags are invalid.");
        UUID id = new UUID(input.getLong(), input.getLong());
        int status = Short.toUnsignedInt(input.getShort()), count = Short.toUnsignedInt(input.getShort());
        int metadataSize = input.getInt(), bodySize = input.getInt();
        if (count > MAX_HEADERS || metadataSize < 4 || metadataSize > MAX_METADATA_BYTES
                || bodySize < 0 || bodySize > MAX_BODY_BYTES || (long) metadataSize + bodySize != input.remaining())
            throw invalid("Binary API envelope lengths are invalid.");
        ByteBuffer metadata = input.slice().order(ByteOrder.BIG_ENDIAN); metadata.limit(metadataSize);
        String method = getText(metadata), path = getText(metadata);
        Map<String, String> headers = new LinkedHashMap<>();
        for (int index = 0; index < count; index++) {
            String key = getText(metadata), value = getText(metadata);
            if (headers.put(key.toLowerCase(Locale.ROOT), value) != null) throw invalid("Duplicate binary API header.");
        }
        if (metadata.hasRemaining()) throw invalid("Binary API metadata has trailing bytes.");
        input.position(input.position() + metadataSize);
        byte[] body = new byte[bodySize]; input.get(body);
        return new Message(id, type == 2, status, method, path, headers, body);
    }

    private static int textSize(String value) {
        byte[] encoded = utf8(value);
        return 2 + encoded.length;
    }
    private static byte[] utf8(String value) {
        if (value == null) throw invalid("Binary API text is required.");
        if (value.length() > MAX_TEXT_BYTES) throw invalid("Binary API text exceeds its bounds.");
        try {
            ByteBuffer encoded = StandardCharsets.UTF_8.newEncoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).encode(java.nio.CharBuffer.wrap(value));
            if (encoded.remaining() > MAX_TEXT_BYTES) throw invalid("Binary API text exceeds its bounds.");
            byte[] bytes = new byte[encoded.remaining()]; encoded.get(bytes); return bytes;
        } catch (CharacterCodingException error) { throw invalid("Binary API text is not valid Unicode."); }
    }
    private static void putText(ByteBuffer output, String value) {
        byte[] bytes = utf8(value); output.putShort((short) bytes.length).put(bytes);
    }
    private static String getText(ByteBuffer input) {
        if (input.remaining() < 2) throw invalid("Truncated binary API text.");
        int size = Short.toUnsignedInt(input.getShort());
        if (size > MAX_TEXT_BYTES || size > input.remaining()) throw invalid("Binary API text length is invalid.");
        ByteBuffer text = input.slice(); text.limit(size); input.position(input.position() + size);
        try { return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(text).toString(); }
        catch (CharacterCodingException error) { throw invalid("Binary API text is not valid UTF-8."); }
    }
    private static IllegalArgumentException invalid(String message) { return ExceptionHandling.invalid(message); }
}
