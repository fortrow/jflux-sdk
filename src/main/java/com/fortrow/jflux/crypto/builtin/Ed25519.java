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
 * File created by Ryan Fitzgerald (ryan@fortrow.com) on 2026-05-10 20:55:15 EDT
 * 
 * -------------------------------------------------------------------------

 */
package com.fortrow.jflux.crypto.builtin;

import com.fortrow.jflux.exceptions.ExceptionHandling;

import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;

public final class Ed25519 {

    private Ed25519() {
    }

    public static KeyPair generateKeyPair() {
        try {
            return KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        } catch (Exception e) {
            throw ExceptionHandling.failure("Unable to generate Ed25519 key pair", e);
        }
    }

    public static byte[] sign(byte[] input, PrivateKey privateKey) {
        try {
            Signature signature = Signature.getInstance("Ed25519");
            signature.initSign(privateKey);
            signature.update(input);
            return signature.sign();
        } catch (Exception e) {
            throw ExceptionHandling.failure("Unable to sign Ed25519 payload", e);
        }
    }

    public static boolean verify(byte[] input, byte[] signatureBytes, PublicKey publicKey) {
        try {
            Signature signature = Signature.getInstance("Ed25519");
            signature.initVerify(publicKey);
            signature.update(input);
            return signature.verify(signatureBytes);
        } catch (Exception e) {
            ExceptionHandling.report(e);
            return false;
        }
    }

    public static PublicKey publicKeyFromBytes(byte[] input) {
        try {
            return KeyFactory.getInstance("Ed25519").generatePublic(new X509EncodedKeySpec(input));
        } catch (Exception e) {
            throw ExceptionHandling.invalid("Invalid Ed25519 public key", e);
        }
    }

    public static PrivateKey privateKeyFromBytes(byte[] input) {
        try {
            return KeyFactory.getInstance("Ed25519").generatePrivate(new PKCS8EncodedKeySpec(input));
        } catch (Exception e) {
            throw ExceptionHandling.invalid("Invalid Ed25519 private key", e);
        }
    }
}
