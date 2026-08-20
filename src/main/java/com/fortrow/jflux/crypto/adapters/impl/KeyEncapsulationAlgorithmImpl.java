package com.fortrow.jflux.crypto.adapters.impl;

import com.fortrow.jflux.crypto.adapters.EncapsulatedSecret;

public abstract class KeyEncapsulationAlgorithmImpl {
    public abstract byte[][] generateKeyPair();
    public abstract EncapsulatedSecret encapsulate(byte[] publicKey);
    public abstract byte[] decapsulate(byte[] encapsulation, byte[] privateKey);
}
