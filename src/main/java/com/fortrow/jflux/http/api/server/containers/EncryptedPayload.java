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
        this.aad = aad;
    }

    public EncryptedPayload(String body, String key, String nonce) {
        this(body, key, nonce, null);
    }

    public EncryptedPayload(String body, String key, String nonce, byte[] aad) {
        this.body = body;
        this.payloadKey = key;
        this.nonce = nonce;
        this.aad = aad;
    }

    public String decrypt() {
        byte[] plaintext;
        if(__.isset(keys)) {
            byte[] key = Hex.fromHex(keys.getClientPayloadKey());
            byte[] iv = Hex.fromHex(keys.getClientIv());
            plaintext = AES.gcm(BaseX.bytesFrom64(this.body), key, iv, aad, false);
        } else {
            plaintext = AES.gcm(BaseX.bytesFrom64(this.body), Hex.fromHex(payloadKey), Hex.fromHex(nonce), aad, false);
        }
        if (plaintext == null) {
            throw new SecurityException("Encrypted payload authentication failed.");
        }
        return new String(plaintext, StandardCharsets.UTF_8);
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
