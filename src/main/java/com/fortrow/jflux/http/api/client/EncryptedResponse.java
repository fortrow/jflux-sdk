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
package com.fortrow.jflux.http.api.client;

import com.fortrow.jflux.crypto.Rand;
import com.fortrow.jflux.crypto.builtin.AES;
import com.fortrow.jflux.crypto.builtin.SHA;
import com.fortrow.jflux.crypto.builtin.SecureCompare;
import com.fortrow.jflux.encoding.BaseX;
import com.fortrow.jflux.encoding.Hex;
import com.fortrow.jflux.http.api.server.containers.EncryptedPayload;
import com.fortrow.jflux.http.api.server.encryption.APIKeys;
import com.fortrow.jflux.http.api.server.encryption.EncryptedApiCryptoSuite;
import com.fortrow.jflux.http.api.server.exceptions.APIException;
import com.fortrow.jflux.http.api.types.EncryptedResponseType;
import com.fortrow.jflux.output.json.JSON;
import org.json.simple.JSONObject;

import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.Arrays;

public class EncryptedResponse {

    private int status;
    private ResponseHeaders headers;
    private String ciphertext;
    private String body;
    private byte[] binaryBody;
    private JSONObject data;
    private EncryptedResponseType responseType;
    private String nextAuthorization;
    private String nextSecretKey;
    private String nextIv;
    private String currentSecretKey;
    private String currentIv;
    private boolean verified = false;
    private byte[] aad;

    public EncryptedResponse(int status, ResponseHeaders headers, String body, String apiKey, String secret, String activeSecret, String random, PublicKey serverPublic, PrivateKey clientPrivate, EncryptedResponseType responseType, boolean parseJson) {
        this(status, headers, body, apiKey, secret, activeSecret, random, serverPublic, clientPrivate, responseType, parseJson, null);
    }

    public EncryptedResponse(int status, ResponseHeaders headers, String body, String apiKey, String secret, String activeSecret, String random, PublicKey serverPublic, PrivateKey clientPrivate, EncryptedResponseType responseType, boolean parseJson, byte[] aad) {
        this.status = status;
        this.headers = headers;
        this.body = body;
        this.aad = aad;
        if(parseJson) this.data = parseObject(body);
        this.responseType = responseType;
        if(!isBootstrapResponse(responseType)) this.verified = verify(apiKey, secret, activeSecret, random, clientPrivate, serverPublic);
    }

    public EncryptedResponse(int status, ResponseHeaders headers, String body, String apiKey, String secret, String activeSecret, String random, PublicKey serverPublic, PrivateKey clientPrivate, EncryptedResponseType responseType) {
        this(status, headers, body, apiKey, secret, activeSecret, random, serverPublic, clientPrivate, responseType, null);
    }

    public EncryptedResponse(int status, ResponseHeaders headers, String body, String apiKey, String secret, String activeSecret, String random, PublicKey serverPublic, PrivateKey clientPrivate, EncryptedResponseType responseType, byte[] aad) {
        this.status = status;
        this.headers = headers;
        this.body = body;
        this.aad = aad;
        this.data = parseObject(body);
        Object responseValue = data.get("response");
        this.ciphertext = responseValue instanceof String ? (String) responseValue : null;
        this.responseType = responseType;
        if(!isBootstrapResponse(responseType)) {
            this.verified = verify(apiKey, secret, activeSecret, random, clientPrivate, serverPublic);
            if (this.verified) {
                try {
                    EncryptedPayload payload = new EncryptedPayload(ciphertext, currentSecretKey, currentIv, aad);
                    this.body = payload.decrypt();
                } catch (RuntimeException ex) {
                    this.verified = false;
                    new APIException("Encrypted response payload is invalid.", ex).tryReport();
                }
            }
        }
    }

    /** Verifies ratchet metadata and authenticates raw ciphertext before exposing binary plaintext. */
    public static EncryptedResponse binary(int status, ResponseHeaders headers, byte[] ciphertext,
            String apiKey, String secret, String activeSecret, String random, PublicKey serverPublic,
            PrivateKey clientPrivate, byte[] aad) {
        EncryptedResponse response = new EncryptedResponse(status, headers, "", apiKey, secret,
                activeSecret, random, serverPublic, clientPrivate, EncryptedResponseType.EXECUTE_RESOURCE, false, aad);
        response.data = new JSONObject();
        response.data.put("request_status", status >= 200 && status < 300 ? "success" : "error");
        if (!"1".equals(headers.getString("X-JFlux-Binary-Encrypted"))) response.verified = false;
        if (response.verified) {
            try {
                response.binaryBody = new EncryptedPayload("", response.currentSecretKey, response.currentIv, aad).decryptBytes(ciphertext);
            } catch (RuntimeException error) {
                response.verified = false;
                response.binaryBody = null;
                new APIException("Binary encrypted response authentication failed.", error).tryReport();
            }
        }
        return response;
    }

    /** Returns a defensive copy only after complete response authentication. */
    public byte[] getBodyBytes() { return verified && binaryBody != null ? binaryBody.clone() : new byte[0]; }

    public int getStatus() { return this.status; }
    public ResponseHeaders getHeaders() { return this.headers; }
    public JSONObject getData() { return this.data; }
    public String getBody() { return body; }
    public JSONObject getBodyObject() { return body == null || body.isBlank() ? new JSONObject() : JSON.getObject(body); }
    public String getRequestStatus() {
        Object value = data == null ? null : data.get("request_status");
        return value == null ? "" : value.toString();
    }
    public boolean isRedirect() { return "redirect".equals(getRequestStatus()); }
    public boolean isTooManyRequests() { return "too_many_requests".equals(getRequestStatus()); }
    public boolean isVerified() { return this.verified; }

    public String sessionAuthorization() { return headers == null ? null : headers.getString("X-Api-Session-Authorization"); }
    public String nextSessionAuthorization(String sessionId) {
        String currentAuthorization = sessionAuthorization();
        if (currentAuthorization == null || sessionId == null || sessionId.isBlank()) return null;
        return SHA.getHmac384Hex(currentAuthorization, sessionId);
    }
    public String authorization() { return headers == null ? null : headers.getString("X-Api-Authorization"); }
    public String nextAuthorization() { return this.nextAuthorization; }
    public String nextRandom() { return this.nextIv; }
    public String nextSecretKey() { return this.nextSecretKey; }
    public String currentSecretKey() { return this.currentSecretKey; }

    private static boolean isBootstrapResponse(EncryptedResponseType type) {
        return type == EncryptedResponseType.SESSION_INIT || type == EncryptedResponseType.SESSION_RESUME;
    }

    private static JSONObject parseObject(String body) {
        if (body == null || body.isBlank()) return new JSONObject();
        try {
            return JSON.getObject(body);
        } catch (RuntimeException ignored) {
            return new JSONObject();
        }
    }

    private boolean verify(String apiKey, String secretKey, String activeSecret, String random, PrivateKey privateKey, PublicKey publicKey) {
        return verifyTransfer(apiKey, secretKey, activeSecret, random, privateKey, publicKey);
    }

    public boolean verifyLate(String apiKey, String secretKey, String random, PrivateKey privateKey, PublicKey publicKey) {
        return verifyTransfer(apiKey, secretKey, secretKey, random, privateKey, publicKey);
    }

    private boolean verifyTransfer(String apiKey, String secretKey, String hashInput, String random, PrivateKey privateKey, PublicKey publicKey) {
        if(headers == null || apiKey == null || secretKey == null || hashInput == null || random == null
                || privateKey == null || publicKey == null) return false;
        if(!headers.contains("X-Api-Authorization") || !headers.contains("X-Api-Random-Bytes")) return false;

        String received = headers.getString("X-Api-Authorization");
        String iv = headers.getString("X-Api-Random-Bytes");
        if(received == null || iv == null || iv.length() != 24) return false;

        try {
            this.currentIv = iv;
            byte[] decrypted = EncryptedApiCryptoSuite.open(received, privateKey);
            byte[][] parts = splitProofEnvelope(decrypted);
            if (parts == null) return false;

            byte[] calculatedHash = Hex.fromHex(SHA.getHmac256(hashInput, apiKey));
            if (!SecureCompare.equals(parts[1], calculatedHash)) return false;

            byte[] decryptedSecretEnvelopeBytes = AES.gcm(
                    parts[0],
                    SHA.getHmac256(Hex.fromHex(random), Hex.fromHex(SHA.get256(secretKey))),
                    Hex.fromHex(iv),
                    calculatedHash,
                    false
            );
            byte[] decryptedSecretKeyBytes = APIKeys.extractKeyFromPaddedEnvelope(decryptedSecretEnvelopeBytes);
            if (decryptedSecretKeyBytes == null) return false;

            String decryptedSecretKey = Hex.getHex(decryptedSecretKeyBytes);
            String nextHash = SHA.getHmac256(decryptedSecretKey, apiKey);
            byte[] nextHashBytes = Hex.fromHex(nextHash);
            byte[] clientPayloadKey = Rand.generateKey(256);
            byte[] newIv = Rand.generateIv(96);
            byte[] nextSecretCipherText = AES.gcm(
                    APIKeys.paddedKeyEnvelope(clientPayloadKey),
                    SHA.getHmac256(Hex.fromHex(random), Hex.fromHex(SHA.get256(secretKey))),
                    newIv,
                    nextHashBytes,
                    true
            );
            if(nextSecretCipherText == null) return false;

            this.currentSecretKey = decryptedSecretKey;
            this.nextAuthorization = EncryptedApiCryptoSuite.seal(appendProof(nextSecretCipherText, nextHashBytes), publicKey);
            this.nextIv = Hex.getHex(newIv);
            this.nextSecretKey = Hex.getHex(clientPayloadKey);
            return true;
        } catch (RuntimeException ex) {
            new APIException("Encrypted response verification failed.", ex).tryReport();
            return false;
        }
    }

    private byte[][] splitProofEnvelope(byte[] input) {
        if (input == null || input.length <= 32) return null;
        return new byte[][] {
                Arrays.copyOfRange(input, 0, input.length - 32),
                Arrays.copyOfRange(input, input.length - 32, input.length)
        };
    }

    private byte[] appendProof(byte[] ciphertext, byte[] proof) {
        if (ciphertext == null || proof == null) return null;
        byte[] out = new byte[ciphertext.length + proof.length];
        System.arraycopy(ciphertext, 0, out, 0, ciphertext.length);
        System.arraycopy(proof, 0, out, ciphertext.length, proof.length);
        return out;
    }
}
