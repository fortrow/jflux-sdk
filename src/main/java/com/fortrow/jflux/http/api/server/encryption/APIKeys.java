/*
 * -------------------------------------------------------------------------
 * 
 * Copyright (c) 2026 Fortrow LLC. All rights reserved.
 *  
 * NOTICE: All information contained herein is, and remains the property of 
 * Fortrow, LLC. The intellectual and technical concepts contained herein are 
 * proprietary to [Company Name] and may be covered by U.S. and Foreign Patents, 
 * patents in process, and are protected by trade secret or copyright law.
 * 
 * Dissemination of this information or reproduction of this material is 
 * strictly forbidden unless prior written permission is obtained from 
 * Fortrow, LLC.
 * 
 * -------------------------------------------------------------------------
 * 
 * File created by Ryan Fitzgerald (ryan@fortrow.com) on 2026-05-10 23:15:31 EDT
 * 
 * -------------------------------------------------------------------------

 */
package com.fortrow.jflux.http.api.server.encryption;

import com.fortrow.jflux.crypto.Rand;
import com.fortrow.jflux.crypto.builtin.AES;
import com.fortrow.jflux.crypto.builtin.HKDF;
import com.fortrow.jflux.crypto.builtin.RSA;
import com.fortrow.jflux.crypto.builtin.SHA;
import com.fortrow.jflux.crypto.builtin.SecureCompare;
import com.fortrow.jflux.encoding.BaseX;
import com.fortrow.jflux.encoding.Hex;
import com.fortrow.jflux.http.api.server.containers.ClientSession;
import com.fortrow.jflux.http.api.server.containers.IpAddress;
import com.fortrow.jflux.http.api.server.exceptions.APIException;
import com.fortrow.jflux.todash.__;

import java.nio.charset.StandardCharsets;
import java.security.PrivateKey;
import java.text.ParseException;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public abstract class APIKeys {
    private static final byte KEY_ENVELOPE_MAGIC_0 = 0x4a;
    private static final byte KEY_ENVELOPE_MAGIC_1 = 0x46;
    private static final byte KEY_ENVELOPE_VERSION = 0x01;
    private static final int KEY_ENVELOPE_HEADER_LENGTH = 5;
    private static final int KEY_ENVELOPE_KEY_LENGTH = 32;
    private static final int KEY_ENVELOPE_MIN_PADDING = 16;
    private static final int KEY_ENVELOPE_MAX_PADDING = 80;

    private String key;
    private String secret;
    private String sessionSecret;
    private String psk;

    private String clientPayloadKey;
    private String clientIv;
    private String clientEncrypted;

    private String serverPayloadKey;
    private String serverIv;
    private String serverEncrypted;

    private byte[] randomBytes;

    private List<IpAddress> allowedIps = null;

    public record State(
            String sessionSecret,
            String clientPayloadKey,
            String clientIv,
            String clientEncrypted,
            String serverPayloadKey,
            String serverIv,
            String serverEncrypted,
            byte[] randomBytes
    ) { }

    public static class Keys {
        private String key;
        private String secret;
        private String sessionSecret;
        private String psk;

        public Keys(String key, String secret, String sessionSecret, String psk) {
            this.key = key;
            this.secret = secret;
            this.sessionSecret = sessionSecret;
            this.psk = psk;
        }

        public String getKey() { return key; }
        public String getSecret() { return secret; }
        public String getSessionSecret() { return sessionSecret; }
        public String getPsk() { return psk; }
    }

    public APIKeys(String key, String secret, String sessionSecret, String psk) {
        this.key = key;
        this.secret = secret;
        this.sessionSecret = sessionSecret;
        this.psk = psk;
    }

    public APIKeys(Keys keys) {
        this.key = keys.getKey();
        this.secret = keys.getSecret();
        this.sessionSecret = keys.getSessionSecret();
        this.psk = keys.getPsk();
    }

    public APIKeys(Keys keys, List<IpAddress> allowedIps) {
        this.key = keys.getKey();
        this.secret = keys.getSecret();
        this.sessionSecret = keys.getSessionSecret();
        this.psk = keys.getPsk();
        this.allowedIps = allowedIps;
    }

    public APIKeys(String key, String secret, String sessionSecret, String psk, List<IpAddress> allowedIps) {
        this.key = key;
        this.secret = secret;
        this.sessionSecret = sessionSecret;
        this.psk = psk;
        this.allowedIps = allowedIps;
    }

    public static Keys generateKeys() {
        String apiKey = secureToken("apikey", 32);
        String secret = secureHex(32);
        String sessionSecret = secureHex(32);
        String psk = Hex.getHex(Rand.generateKey(256));
        return new Keys(apiKey, secret, sessionSecret, psk);
    }

    private static String secureToken(String prefix, int bytes) {
        return prefix + "_" + Hex.getHex(Rand.secure(bytes));
    }

    private static String secureHex(int bytes) {
        return Hex.getHex(Rand.secure(bytes));
    }

    public abstract void updateSessionSecret(APIKeys keys, String sessionSecret) throws APIException;

    private void rotate() {
        this.sessionSecret = this.serverPayloadKey;
    }

    public void commitSessionSecret() throws APIException {
        updateSessionSecret(this, this.sessionSecret);
    }

    public State snapshot() {
        return new State(
                sessionSecret,
                clientPayloadKey,
                clientIv,
                clientEncrypted,
                serverPayloadKey,
                serverIv,
                serverEncrypted,
                randomBytes == null ? null : Arrays.copyOf(randomBytes, randomBytes.length)
        );
    }

    public void restore(State state) {
        if(state == null) return;
        this.sessionSecret = state.sessionSecret();
        this.clientPayloadKey = state.clientPayloadKey();
        this.clientIv = state.clientIv();
        this.clientEncrypted = state.clientEncrypted();
        this.serverPayloadKey = state.serverPayloadKey();
        this.serverIv = state.serverIv();
        this.serverEncrypted = state.serverEncrypted();
        this.randomBytes = state.randomBytes() == null ? null : Arrays.copyOf(state.randomBytes(), state.randomBytes().length);
    }

    public boolean ipIsAllowed(String ip) throws ParseException { return ipIsAllowed(new IpAddress(ip)); }

    public boolean matches(APIKeys other) {
        return other != null && SecureCompare.equals(this.key, other.key);
    }

    @Override
    public boolean equals(Object obj) {
        if(this == obj) return true;
        if(!(obj instanceof APIKeys)) return false;
        return matches((APIKeys) obj);
    }

    @Override
    public int hashCode() {
        return key == null ? 0 : key.hashCode();
    }

    public String getSessionSecret() { return SHA.get256(this.sessionSecret); }
    public String getRawSessionSecret() { return this.sessionSecret; }
    public String getKey() { return key; }
    public String getSecret() { return secret; }
    public String getPsk() { return psk; }
    public List<IpAddress> getAllowedIps() { return allowedIps; }
    public byte[] getRandomBytes() { return randomBytes; }
    public String getClientPayloadKey() { return clientPayloadKey; }
    public String getClientIv() { return clientIv; }
    public String getClientEncrypted() { return clientEncrypted; }
    public String getServerPayloadKey() { return serverPayloadKey; }
    public String getServerIv() { return serverIv; }
    public String getServerEncrypted() { return serverEncrypted; }

    public boolean ipIsAllowed(IpAddress ip) {
        if(!__.isset(allowedIps)) return true;
        for (IpAddress allowed : allowedIps) {
            if(__.same(allowed.getAddress(), ip.getAddress())) return true;
        }
        return false;
    }

    public String encryptWithPsk(byte[] data, byte[] iv) {
        byte[] key = Hex.fromHex(this.psk);
        return BaseX.encode64(Objects.requireNonNull(AES.gcm(data, key, iv, null, true)));
    }

    public boolean initialInbound(ClientSession session, String receivedRand) throws APIException {
        byte[][] splitRes = getInitialClientRand(Objects.requireNonNull(Hex.fromHex(receivedRand)));
        if (splitRes == null) {
            throw new APIException("Invalid initial random bytes.");
        }

        byte[] receivedKey = splitRes[0];
        this.randomBytes = receivedKey;

        byte[] calculatedHash = Hex.fromHex(SHA.getHmac256(secret, key));
        byte[] serverPayloadKey = Rand.generateKey(256);
        byte[] serverIv = Rand.generateIv(96);
        byte[] wrappingKey = SHA.getHmac256(randomBytes, Hex.fromHex(SHA.get256(secret)));
        byte[] nextEncrypted = AES.gcm(paddedKeyEnvelope(serverPayloadKey), wrappingKey, serverIv, calculatedHash, true);
        byte[] finalRes = appendProof(nextEncrypted, calculatedHash);
        String finalResBase64 = RSA.encrypt(finalRes, session.getSession().getClientPublicKey().getPublicKey());

        this.serverEncrypted = finalResBase64;
        this.serverPayloadKey = Hex.getHex(serverPayloadKey);
        this.serverIv = Hex.getHex(serverIv);

        return true;
    }

    public boolean inbound(ClientSession session, String received, String iv) throws APIException {
        PrivateKey serverPrivateKey = session.getSession().getServerPrivateKey().getPrivateKey();
        byte[] decrypted = BaseX.bytesFrom64(RSA.decrypt(BaseX.bytesFrom64(received), serverPrivateKey));
        byte[] expectedHash = Hex.fromHex(SHA.getHmac256(serverPayloadKey, key));
        byte[][] parts = splitProofEnvelope(decrypted);
        if (parts == null || !SecureCompare.equals(parts[1], expectedHash)) {
            return false;
        }

        byte[] payloadEnvelopeBytes = AES.gcm(
                parts[0],
                SHA.getHmac256(randomBytes, Hex.fromHex(SHA.get256(secret))),
                Hex.fromHex(iv),
                expectedHash,
                false
        );
        byte[] payloadKeyBytes = extractKeyFromPaddedEnvelope(payloadEnvelopeBytes);
        if (payloadKeyBytes == null) {
            return false;
        }

        String decryptedPayloadKey = Hex.getHex(payloadKeyBytes);
        String nextHash = SHA.getHmac256(decryptedPayloadKey, key);
        byte[] nextHashBytes = Hex.fromHex(nextHash);
        byte[] serverPayloadKey = Rand.generateKey(256);
        byte[] serverIv = Rand.generateIv(96);
        byte[] nextEncrypted = AES.gcm(
                paddedKeyEnvelope(serverPayloadKey),
                SHA.getHmac256(randomBytes, Hex.fromHex(SHA.get256(secret))),
                serverIv,
                nextHashBytes,
                true
        );
        byte[] finalRes = appendProof(nextEncrypted, nextHashBytes);
        String finalResBase64 = RSA.encrypt(finalRes, session.getSession().getClientPublicKey().getPublicKey());

        rotate();
        this.clientEncrypted = received;
        this.clientPayloadKey = decryptedPayloadKey;
        this.clientIv = iv;
        this.serverEncrypted = finalResBase64;
        this.serverPayloadKey = Hex.getHex(serverPayloadKey);
        this.serverIv = Hex.getHex(serverIv);

        return true;
    }

    public static byte[] requestAad(String method, String path, String timestamp, String sequence, String apiKey, String sessionId, String keySetId, String nonce, String contentType) {
        return requestAad(method, path, timestamp, sequence, apiKey, sessionId, keySetId, nonce, contentType, method);
    }

    public static byte[] requestAad(String method, String path, String timestamp, String sequence, String apiKey, String sessionId, String keySetId, String nonce, String contentType, String resourceMethod) {
        return requestAadString(method, path, timestamp, sequence, apiKey, sessionId, keySetId, nonce, contentType, resourceMethod).getBytes(StandardCharsets.UTF_8);
    }

    public static String requestSignature(String apiSecret, String requestSecret, String apiKey, String sessionId, String method, String path, String timestamp, String sequence, String keySetId, String nonce, String contentType, String body) {
        return requestSignature(apiSecret, requestSecret, apiKey, sessionId, method, path, timestamp, sequence, keySetId, nonce, contentType, body, method);
    }

    public static String requestSignature(String apiSecret, String requestSecret, String apiKey, String sessionId, String method, String path, String timestamp, String sequence, String keySetId, String nonce, String contentType, String body, String resourceMethod) {
        byte[] macKey = deriveRequestMacKey(apiSecret, requestSecret, apiKey, sessionId);
        return SHA.getHmac384Hex(canonicalRequest(method, path, timestamp, sequence, apiKey, sessionId, keySetId, nonce, contentType, body, resourceMethod), macKey);
    }

    public static boolean verifyRequestSignature(String receivedSignature, String apiSecret, String requestSecret, String apiKey, String sessionId, String method, String path, String timestamp, String sequence, String keySetId, String nonce, String contentType, String body) {
        return verifyRequestSignature(receivedSignature, apiSecret, requestSecret, apiKey, sessionId, method, path, timestamp, sequence, keySetId, nonce, contentType, body, method);
    }

    public static boolean verifyRequestSignature(String receivedSignature, String apiSecret, String requestSecret, String apiKey, String sessionId, String method, String path, String timestamp, String sequence, String keySetId, String nonce, String contentType, String body, String resourceMethod) {
        String expected = requestSignature(apiSecret, requestSecret, apiKey, sessionId, method, path, timestamp, sequence, keySetId, nonce, contentType, body, resourceMethod);
        return SecureCompare.equals(expected, receivedSignature);
    }

    public static String sessionCloseAuthorization(String apiSecret, String requestSecret, String apiKey, String sessionId, String keySetId, String sequence) {
        byte[] macKey = deriveRequestMacKey(apiSecret, requestSecret, apiKey, sessionId);
        return SHA.getHmac384Hex(sessionCloseIntent(apiKey, sessionId, keySetId, sequence), macKey);
    }

    public static boolean verifySessionCloseAuthorization(String received, String apiSecret, String requestSecret, String apiKey, String sessionId, String keySetId, String sequence) {
        String expected = sessionCloseAuthorization(apiSecret, requestSecret, apiKey, sessionId, keySetId, sequence);
        return SecureCompare.equals(expected, received);
    }

    public static byte[] deriveRequestMacKey(String apiSecret, String requestSecret, String apiKey, String sessionId) {
        byte[] inputKeyMaterial = secretMaterial(requestSecret);
        byte[] salt = Hex.fromHex(SHA.get256(nullToEmpty(apiSecret) + ":" + nullToEmpty(apiKey) + ":" + nullToEmpty(sessionId)));
        byte[] info = ("jflux:http-api:v1:request-mac:" + nullToEmpty(apiKey)).getBytes(StandardCharsets.UTF_8);
        return HKDF.deriveKey(inputKeyMaterial, salt, info, 32);
    }

    private static String canonicalRequest(String method, String path, String timestamp, String sequence, String apiKey, String sessionId, String keySetId, String nonce, String contentType, String body, String resourceMethod) {
        return requestAadString(method, path, timestamp, sequence, apiKey, sessionId, keySetId, nonce, contentType, resourceMethod)
                + "\nbody-sha256:" + SHA.get256(nullToEmpty(body));
    }

    private static String sessionCloseIntent(String apiKey, String sessionId, String keySetId, String sequence) {
        return "JFLUX-HTTP-API/1"
                + "\naction:session-close"
                + "\napi-key:" + nullToEmpty(apiKey)
                + "\nsession-id:" + nullToEmpty(sessionId)
                + "\nkey-set-id:" + nullToEmpty(keySetId)
                + "\nsequence:" + nullToEmpty(sequence);
    }

    private static String requestAadString(String method, String path, String timestamp, String sequence, String apiKey, String sessionId, String keySetId, String nonce, String contentType, String resourceMethod) {
        return "JFLUX-HTTP-API/1"
                + "\nmethod:" + nullToEmpty(method).toUpperCase()
                + "\nresource-method:" + nullToEmpty(resourceMethod).toUpperCase()
                + "\npath:" + nullToEmpty(path)
                + "\ntimestamp:" + nullToEmpty(timestamp)
                + "\nsequence:" + nullToEmpty(sequence)
                + "\napi-key:" + nullToEmpty(apiKey)
                + "\nsession-id:" + nullToEmpty(sessionId)
                + "\nkey-set-id:" + nullToEmpty(keySetId)
                + "\nnonce:" + nullToEmpty(nonce)
                + "\ncontent-type:" + nullToEmpty(contentType);
    }

    private static byte[] secretMaterial(String value) {
        String safe = nullToEmpty(value);
        if (safe.matches("(?i)[0-9a-f]+") && safe.length() % 2 == 0) {
            return Hex.fromHex(safe);
        }
        return safe.getBytes(StandardCharsets.UTF_8);
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    public static byte[] paddedKeyEnvelope(byte[] payloadKey) {
        if (payloadKey == null || payloadKey.length != KEY_ENVELOPE_KEY_LENGTH) {
            return null;
        }

        int paddingLength = KEY_ENVELOPE_MIN_PADDING
                + ((Rand.secure(1)[0] & 0xff) % (KEY_ENVELOPE_MAX_PADDING - KEY_ENVELOPE_MIN_PADDING + 1));
        byte[] padding = Rand.secure(paddingLength);
        byte[] envelope = new byte[KEY_ENVELOPE_HEADER_LENGTH + payloadKey.length + padding.length];

        envelope[0] = KEY_ENVELOPE_MAGIC_0;
        envelope[1] = KEY_ENVELOPE_MAGIC_1;
        envelope[2] = KEY_ENVELOPE_VERSION;
        envelope[3] = (byte) payloadKey.length;
        envelope[4] = (byte) padding.length;
        System.arraycopy(payloadKey, 0, envelope, KEY_ENVELOPE_HEADER_LENGTH, payloadKey.length);
        System.arraycopy(padding, 0, envelope, KEY_ENVELOPE_HEADER_LENGTH + payloadKey.length, padding.length);
        return envelope;
    }

    public static byte[] extractKeyFromPaddedEnvelope(byte[] envelope) {
        if (envelope == null || envelope.length < KEY_ENVELOPE_HEADER_LENGTH) {
            return null;
        }
        if (envelope[0] != KEY_ENVELOPE_MAGIC_0
                || envelope[1] != KEY_ENVELOPE_MAGIC_1
                || envelope[2] != KEY_ENVELOPE_VERSION) {
            return null;
        }

        int keyLength = envelope[3] & 0xff;
        int paddingLength = envelope[4] & 0xff;
        if (keyLength != KEY_ENVELOPE_KEY_LENGTH
                || paddingLength < KEY_ENVELOPE_MIN_PADDING
                || paddingLength > KEY_ENVELOPE_MAX_PADDING
                || envelope.length != KEY_ENVELOPE_HEADER_LENGTH + keyLength + paddingLength) {
            return null;
        }
        return Arrays.copyOfRange(envelope, KEY_ENVELOPE_HEADER_LENGTH, KEY_ENVELOPE_HEADER_LENGTH + keyLength);
    }

    private byte[][] getInitialClientRand(byte[] input) {
        if(input.length == 44) {
            byte[] keyArr = Arrays.copyOfRange(input, 0, 32);
            byte[] nonceArr = Arrays.copyOfRange(input, 32, 44);
            return new byte[][] { keyArr, nonceArr };
        }
        return null;
    }

    private byte[][] splitProofEnvelope(byte[] input) {
        if (input == null || input.length <= 32) {
            return null;
        }
        return new byte[][] {
                Arrays.copyOfRange(input, 0, input.length - 32),
                Arrays.copyOfRange(input, input.length - 32, input.length)
        };
    }

    private static byte[] appendProof(byte[] ciphertext, byte[] proof) {
        if (ciphertext == null || proof == null) {
            return null;
        }
        byte[] out = new byte[ciphertext.length + proof.length];
        System.arraycopy(ciphertext, 0, out, 0, ciphertext.length);
        System.arraycopy(proof, 0, out, ciphertext.length, proof.length);
        return out;
    }
}
