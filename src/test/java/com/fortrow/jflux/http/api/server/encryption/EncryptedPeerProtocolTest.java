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
 * File created by Ryan Fitzgerald (ryan@fortrow.com) on 2026-05-10 23:15:32 EDT
 */
package com.fortrow.jflux.http.api.server.encryption;

import com.fortrow.jflux.crypto.Rand;
import com.fortrow.jflux.crypto.builtin.AES;
import com.fortrow.jflux.crypto.builtin.HKDF;
import com.fortrow.jflux.crypto.builtin.SecureCompare;
import com.fortrow.jflux.encoding.BaseX;
import com.fortrow.jflux.encoding.Hex;
import com.fortrow.jflux.http.api.server.containers.APISession;
import com.fortrow.jflux.http.api.server.containers.ClientInfo;
import com.fortrow.jflux.http.api.server.containers.ClientSession;
import com.fortrow.jflux.http.api.server.containers.RequestHeaders;
import com.fortrow.jflux.http.api.server.exceptions.APIException;
import com.fortrow.jflux.http.api.server.types.APIServerType;
import com.sun.net.httpserver.Headers;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class EncryptedPeerProtocolTest {

    @Test
    void hkdfMatchesRfc5869Sha256TestVector() {
        byte[] ikm = Hex.fromHex("0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b");
        byte[] salt = Hex.fromHex("000102030405060708090a0b0c");
        byte[] info = Hex.fromHex("f0f1f2f3f4f5f6f7f8f9");

        byte[] prk = HKDF.extract(salt, ikm);
        byte[] okm = HKDF.expand(prk, info, 42);

        assertEquals("077709362c2e32df0ddc3f0dc47bba63" +
                "90b6c73bb50f9c3122ec844ad7c2b3e5", Hex.getHex(prk));
        assertEquals("3cb25f25faacd57a90434f64d0362f2a" +
                "2d2d0a90cf1a5a4c5db02d56ecc4c5bf" +
                "34007208d5b887185865", Hex.getHex(okm));
    }

    @Test
    void requestSignatureBindsBodyPathNonceAndSequence() {
        String apiSecret = "secret_" + Hex.getHex(Rand.secure(48));
        String requestSecret = Hex.getHex(Rand.secure(32));
        String apiKey = "apikey_" + Hex.getHex(Rand.secure(32));
        String sessionId = Hex.getHex(Rand.secure(20));
        String method = "POST";
        String path = "/records/insert";
        String timestamp = "1778455000";
        String sequence = "7";
        String keySetId = Hex.getHex(Rand.secure(20));
        String nonce = Hex.getHex(Rand.generateIv(96));
        String contentType = "text/plain";
        String body = BaseX.encode64(Rand.secure(64));

        String signature = APIKeys.requestSignature(apiSecret, requestSecret, apiKey, sessionId, method, path, timestamp, sequence, keySetId, nonce, contentType, body);

        assertTrue(APIKeys.verifyRequestSignature(signature, apiSecret, requestSecret, apiKey, sessionId, method, path, timestamp, sequence, keySetId, nonce, contentType, body));
        assertFalse(APIKeys.verifyRequestSignature(signature, apiSecret, requestSecret, apiKey, sessionId, method, path + "x", timestamp, sequence, keySetId, nonce, contentType, body));
        assertFalse(APIKeys.verifyRequestSignature(signature, apiSecret, requestSecret, apiKey, sessionId, method, path, timestamp, "8", keySetId, nonce, contentType, body));
        assertFalse(APIKeys.verifyRequestSignature(signature, apiSecret, requestSecret, apiKey, sessionId, method, path, timestamp, sequence, keySetId, Hex.getHex(Rand.generateIv(96)), contentType, body));
        assertFalse(APIKeys.verifyRequestSignature(signature, apiSecret, requestSecret, apiKey, sessionId, method, path, timestamp, sequence, keySetId, nonce, contentType, body + "A"));

        String logicalGetSignature = APIKeys.requestSignature(apiSecret, requestSecret, apiKey, sessionId, method, path, timestamp, sequence, keySetId, nonce, contentType, body, "GET");
        assertTrue(APIKeys.verifyRequestSignature(logicalGetSignature, apiSecret, requestSecret, apiKey, sessionId, method, path, timestamp, sequence, keySetId, nonce, contentType, body, "GET"));
        assertFalse(APIKeys.verifyRequestSignature(logicalGetSignature, apiSecret, requestSecret, apiKey, sessionId, method, path, timestamp, sequence, keySetId, nonce, contentType, body, "DELETE"));
    }

    @Test
    void sessionResetProofBindsApiKeyTimestampAndNonce() {
        String apiSecret = "secret_" + Hex.getHex(Rand.secure(32));
        String apiKey = "apikey_" + Hex.getHex(Rand.secure(32));
        String timestamp = "1778455000";
        String randomBytes = Hex.getHex(Rand.secure(44));
        String proof = APIKeys.sessionResetAuthorization(apiSecret, apiKey, timestamp, randomBytes);

        assertTrue(APIKeys.verifySessionResetAuthorization(proof, apiSecret, apiKey, timestamp, randomBytes));
        assertFalse(APIKeys.verifySessionResetAuthorization(proof, apiSecret, apiKey, "1778455001", randomBytes));
        assertFalse(APIKeys.verifySessionResetAuthorization(proof, apiSecret, apiKey, timestamp,
                Hex.getHex(Rand.secure(44))));
        assertFalse(APIKeys.verifySessionResetAuthorization(proof, apiSecret + "x", apiKey, timestamp, randomBytes));
    }

    @Test
    void gcmPayloadAuthenticationFailsWhenAadOrCiphertextChanges() {
        byte[] key = Rand.generateKey(256);
        byte[] nonce = Rand.generateIv(96);
        byte[] aad = APIKeys.requestAad("POST", "/class", "1778455000", "1", "apikey", "session", "keyset", Hex.getHex(nonce), "text/plain");
        byte[] plaintext = "{\"id\":1,\"name\":\"Ada\"}".getBytes(StandardCharsets.UTF_8);

        byte[] ciphertext = AES.gcm(plaintext, key, nonce, aad, true);

        assertArrayEquals(plaintext, AES.gcm(ciphertext, key, nonce, aad, false));
        assertNull(AES.gcm(ciphertext, key, nonce, APIKeys.requestAad("POST", "/class", "1778455000", "2", "apikey", "session", "keyset", Hex.getHex(nonce), "text/plain"), false));

        byte[] tamperedCiphertext = ciphertext.clone();
        tamperedCiphertext[0] ^= 1;
        assertNull(AES.gcm(tamperedCiphertext, key, nonce, aad, false));
    }

    @Test
    void sessionRejectsReplayedOrOutOfOrderSequences() throws APIException {
        APISession session = new APISession(testKeys(), "127.0.0.1", APIServerType.ENCRYPTED_SYNC, 30, false);

        assertTrue(session.acceptRequestSequence(1));
        assertFalse(session.acceptRequestSequence(1));
        assertFalse(session.acceptRequestSequence(0));
        assertTrue(session.acceptRequestSequence(2));
        assertFalse(session.acceptRequestSequence(2));
    }

    @Test
    void requestHeadersDetectDuplicateSecurityHeaders() {
        Headers headers = new Headers();
        headers.add("X-Api-Key", "one");
        headers.add("X-Api-Key", "two");
        headers.add("X-Request-Sequence", "1");

        RequestHeaders requestHeaders = new RequestHeaders(headers);

        assertTrue(requestHeaders.hasDuplicate("X-Api-Key"));
        assertTrue(requestHeaders.hasAnyDuplicate("X-Api-Key", "X-Request-Sequence"));
        assertFalse(requestHeaders.hasDuplicate("X-Request-Sequence"));
    }

    @Test
    void dynamicNonceLengthsUseTwelveByteGcmNonce() throws APIException {
        TestAPIKeys keys = testKeys();
        APISession apiSession = new APISession(keys, "127.0.0.1", APIServerType.ENCRYPTED_SYNC, 30, true);
        Headers rawHeaders = new Headers();
        rawHeaders.add("User-Agent", "junit");
        ClientInfo info = new ClientInfo(new RequestHeaders(rawHeaders), "127.0.0.1", "localhost");
        ClientSession clientSession = new ClientSession(info, apiSession);

        assertTrue(keys.initialInbound(clientSession, Hex.getHex(Rand.secure(44))));
        assertEquals(24, keys.getServerIv().length());
        assertThrows(APIException.class, () -> keys.initialInbound(clientSession, Hex.getHex(Rand.secure(48))));
    }

    @Test
    void keyTransferEnvelopeUsesRandomizedPadding() {
        byte[] key = Rand.generateKey(256);
        Set<Integer> lengths = new HashSet<>();

        for (int i = 0; i < 64; i++) {
            byte[] envelope = APIKeys.paddedKeyEnvelope(key);

            assertNotNull(envelope);
            assertTrue(envelope.length > key.length);
            assertArrayEquals(key, APIKeys.extractKeyFromPaddedEnvelope(envelope));
            lengths.add(envelope.length);
        }

        assertTrue(lengths.size() > 1);
    }

    @Test
    void keyTransferEnvelopeRejectsMalformedPadding() {
        byte[] key = Rand.generateKey(256);
        byte[] envelope = APIKeys.paddedKeyEnvelope(key);

        assertNotNull(envelope);
        envelope[0] ^= 1;

        assertNull(APIKeys.extractKeyFromPaddedEnvelope(envelope));
        assertNull(APIKeys.paddedKeyEnvelope(Rand.secure(31)));
    }

    @Test
    void secureCompareDoesNotAcceptLengthPrefixedMatches() {
        assertTrue(SecureCompare.equals("abcdef", "abcdef"));
        assertFalse(SecureCompare.equals("abcdef", "abcdeg"));
        assertFalse(SecureCompare.equals("abcdef", "abcdef00"));
    }

    private static TestAPIKeys testKeys() {
        return new TestAPIKeys(APIKeys.generateKeys());
    }

    private static final class TestAPIKeys extends APIKeys {
        private TestAPIKeys(Keys keys) {
            super(keys);
        }

        @Override
        public void updateSessionSecret(APIKeys keys, String sessionSecret) {
        }
    }
}
