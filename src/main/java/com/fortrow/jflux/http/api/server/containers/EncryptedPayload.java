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
 * File created by Ryan Fitzgerald (ryan@fortrow.com) on 2026-05-10 22:51:05 EDT
 * 
 * -------------------------------------------------------------------------

 */
package com.fortrow.jflux.http.api.server.containers;

import com.fortrow.jflux.crypto.builtin.AES;
import com.fortrow.jflux.encoding.BaseX;
import com.fortrow.jflux.encoding.Hex;
import com.fortrow.jflux.http.api.server.encryption.APIKeys;
import com.fortrow.jflux.todash.__;

import java.nio.charset.StandardCharsets;

public class EncryptedPayload {

    private String body;
    private APIKeys keys = null;
    private String payloadKey = null;
    private String nonce = null;
    private byte[] aad = null;

    public EncryptedPayload(String body, APIKeys keys) {
        this(body, keys, null);
    }

    public EncryptedPayload(String body, APIKeys keys, byte[] aad) {
        this.body = body;
        this.keys = keys;
        this.aad = aad == null ? null : aad.clone();
    }

    public EncryptedPayload(String body, String key, String nonce) {
        this(body, key, nonce, null);
    }

    public EncryptedPayload(String body, String key, String nonce, byte[] aad) {
        this.body = body;
        this.payloadKey = key;
        this.nonce = nonce;
        this.aad = aad == null ? null : aad.clone();
    }

    /** Decrypts legacy Base64 ciphertext and decodes its UTF-8 plaintext. */
    public String decrypt() {
        byte[] key = Hex.fromHex(keys == null ? payloadKey : keys.getClientPayloadKey());
        byte[] iv = Hex.fromHex(keys == null ? nonce : keys.getClientIv());
        byte[] output = AES.gcm(BaseX.bytesFrom64(this.body), key, iv, aad, false);
        if (output == null) throw new SecurityException("Encrypted payload authentication failed.");
        return new String(output, StandardCharsets.UTF_8);
    }

    /** Authenticates raw ciphertext before returning arbitrary binary plaintext. */
    public byte[] decryptBytes(byte[] ciphertext) {
        if (ciphertext == null || ciphertext.length < 16 || ciphertext.length > MAX_BINARY_PLAINTEXT_BYTES + 16)
            throw com.fortrow.jflux.exceptions.ExceptionHandling.invalid("Binary ciphertext size is invalid.");
        byte[] key = Hex.fromHex(keys == null ? payloadKey : keys.getClientPayloadKey());
        byte[] iv = Hex.fromHex(keys == null ? nonce : keys.getClientIv());
        return cryptBytes(ciphertext, key, iv, false);
    }

    /** Encrypts arbitrary binary plaintext using the current outbound payload key/nonce. */
    public byte[] encryptBytes(byte[] plaintext) {
        if (plaintext == null || plaintext.length > MAX_BINARY_PLAINTEXT_BYTES)
            throw com.fortrow.jflux.exceptions.ExceptionHandling.invalid("Binary plaintext size is invalid.");
        byte[] key = Hex.fromHex(keys == null ? payloadKey : keys.getServerPayloadKey());
        byte[] iv = Hex.fromHex(keys == null ? nonce : keys.getServerIv());
        return cryptBytes(plaintext, key, iv, true);
    }

    public static final int MAX_BINARY_PLAINTEXT_BYTES = 4 * 1024 * 1024 - 16;

    private byte[] cryptBytes(byte[] input, byte[] key, byte[] iv, boolean encrypt) {
        try {
            if (key == null || (key.length != 16 && key.length != 24 && key.length != 32) || iv == null || iv.length != 12)
                throw com.fortrow.jflux.exceptions.ExceptionHandling.invalid("Binary payload key or nonce size is invalid.");
            byte[] output = AES.gcm(input, key, iv, aad, encrypt);
            if (output == null) throw new SecurityException("Encrypted payload authentication failed.");
            return output;
        } finally {
            if (key != null) java.util.Arrays.fill(key, (byte) 0);
            if (iv != null) java.util.Arrays.fill(iv, (byte) 0);
        }
    }

    public String encrypt(String plaintext) {
        this.body = plaintext;
        return encrypt();
    }

    public String encrypt() {
        byte[] key = Hex.fromHex(keys.getServerPayloadKey());
        byte[] iv = Hex.fromHex(keys.getServerIv());
        byte[] ciphertext = AES.gcm(this.body.getBytes(StandardCharsets.UTF_8), key, iv, aad, true);
        if (ciphertext == null) {
            throw new SecurityException("Encrypted payload authentication failed.");
        }
        return BaseX.encode64(ciphertext);
    }
}
