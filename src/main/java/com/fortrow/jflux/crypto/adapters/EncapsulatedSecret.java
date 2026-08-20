package com.fortrow.jflux.crypto.adapters;

import com.fortrow.jflux.exceptions.ExceptionHandling;

import java.util.Arrays;

/** The ciphertext and shared secret returned by a KEM encapsulation operation. */
public final class EncapsulatedSecret {

    private final byte[] encapsulation;
    private final byte[] secret;

    public EncapsulatedSecret(byte[] encapsulation, byte[] secret) {
        if (encapsulation == null || secret == null) {
            throw ExceptionHandling.invalid("Encapsulation and shared secret are required.");
        }
        this.encapsulation = Arrays.copyOf(encapsulation, encapsulation.length);
        this.secret = Arrays.copyOf(secret, secret.length);
    }

    public byte[] encapsulation() {
        return Arrays.copyOf(encapsulation, encapsulation.length);
    }

    public byte[] secret() {
        return Arrays.copyOf(secret, secret.length);
    }
}
