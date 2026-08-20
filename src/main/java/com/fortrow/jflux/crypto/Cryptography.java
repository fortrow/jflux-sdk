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
 * File created by Ryan Fitzgerald (ryan@fortrow.com) on 2026-05-04 15:27:04 EDT
 * 
 * -------------------------------------------------------------------------

 */
package com.fortrow.jflux.crypto;

public class Cryptography {

    private final CryptographyRegister register;

    public Cryptography(CryptographyRegister register) {
        this.register = register;
    }

    public byte[] encrypt(String algo, byte[] input, byte[] key, byte[]... aad) {
        return register.encrypt(algo, input, key, aad);
    }

    public byte[] decrypt(String algo, byte[] input, byte[] key, byte[]... aad) {
        return register.decrypt(algo, input, key, aad);
    }

    public byte[] hash(String algo, byte[] input) {
        return register.hash(algo, input);
    }

    public byte[] hmac(String algo, byte[] input, byte[] key) {
        return register.hmac(algo, input, key);
    }

    public byte[][] generateKeyPair(String algo) {
        return register.generateKeyPair(algo);
    }

    public com.fortrow.jflux.crypto.adapters.EncapsulatedSecret encapsulate(
            String algo, byte[] publicKey) {
        return register.encapsulate(algo, publicKey);
    }

    public byte[] decapsulate(String algo, byte[] encapsulation, byte[] privateKey) {
        return register.decapsulate(algo, encapsulation, privateKey);
    }

    public byte[] sign(String algo, byte[] input, byte[] privateKey) {
        return register.sign(algo, input, privateKey);
    }

    public boolean verify(String algo, byte[] input, byte[] signature,
                          byte[] publicKey) {
        return register.verify(algo, input, signature, publicKey);
    }

}
