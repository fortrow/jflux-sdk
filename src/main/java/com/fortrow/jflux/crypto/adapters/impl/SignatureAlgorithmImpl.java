package com.fortrow.jflux.crypto.adapters.impl;

public abstract class SignatureAlgorithmImpl {
    public abstract byte[][] generateKeyPair();
    public abstract byte[] sign(byte[] input, byte[] privateKey);
    public abstract boolean verify(byte[] input, byte[] signature, byte[] publicKey);
}
