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
 * File created by Ryan Fitzgerald (ryan@fortrow.com) on 2026-05-10 20:55:29 EDT
 * 
 * -------------------------------------------------------------------------

 */
package com.fortrow.jflux.crypto.builtin;

import com.fortrow.jflux.exceptions.ExceptionHandling;

import javax.crypto.KeyAgreement;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;

public final class X25519 {

    private X25519() {
    }

    public static KeyPair generateKeyPair() {
        try {
            return KeyPairGenerator.getInstance("X25519").generateKeyPair();
        } catch (Exception e) {
            throw ExceptionHandling.failure("Unable to generate X25519 key pair", e);
        }
    }

    public static byte[] sharedSecret(PrivateKey privateKey, PublicKey publicKey) {
        try {
            KeyAgreement keyAgreement = KeyAgreement.getInstance("X25519");
            keyAgreement.init(privateKey);
            keyAgreement.doPhase(publicKey, true);
            return keyAgreement.generateSecret();
        } catch (Exception e) {
            throw ExceptionHandling.failure("Unable to derive X25519 shared secret", e);
        }
    }

    public static PublicKey publicKeyFromBytes(byte[] input) {
        try {
            return KeyFactory.getInstance("X25519").generatePublic(new X509EncodedKeySpec(input));
        } catch (Exception e) {
            throw ExceptionHandling.invalid("Invalid X25519 public key", e);
        }
    }

    public static PrivateKey privateKeyFromBytes(byte[] input) {
        try {
            return KeyFactory.getInstance("X25519").generatePrivate(new PKCS8EncodedKeySpec(input));
        } catch (Exception e) {
            throw ExceptionHandling.invalid("Invalid X25519 private key", e);
        }
    }
}
