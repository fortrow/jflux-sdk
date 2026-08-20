package com.fortrow.jflux.crypto.adapters;

import com.fortrow.jflux.crypto.adapters.impl.KeyEncapsulationAlgorithmImpl;
import com.fortrow.jflux.exceptions.ExceptionHandling;

/** Adapter for algorithms that establish a shared secret with a public/private key pair. */
public class KeyEncapsulationAlgorithm extends KeyEncapsulationAlgorithmImpl {

    @FunctionalInterface
    public interface KeyPairGenerator {
        byte[][] generate();
    }

    @FunctionalInterface
    public interface Encapsulator {
        EncapsulatedSecret encapsulate(byte[] publicKey);
    }

    @FunctionalInterface
    public interface Decapsulator {
        byte[] decapsulate(byte[] encapsulation, byte[] privateKey);
    }

    private final String identifier;
    private final KeyPairGenerator keyPairGenerator;
    private final Encapsulator encapsulator;
    private final Decapsulator decapsulator;

    public KeyEncapsulationAlgorithm(
            String identifier,
            KeyPairGenerator keyPairGenerator,
            Encapsulator encapsulator,
            Decapsulator decapsulator) {
        if (identifier == null || identifier.isBlank()) throw ExceptionHandling.invalid("Algorithm identifier is required.");
        if (keyPairGenerator == null) throw ExceptionHandling.invalid("Key-pair generator is required.");
        if (encapsulator == null) throw ExceptionHandling.invalid("Encapsulator is required.");
        if (decapsulator == null) throw ExceptionHandling.invalid("Decapsulator is required.");
        this.identifier = identifier;
        this.keyPairGenerator = keyPairGenerator;
        this.encapsulator = encapsulator;
        this.decapsulator = decapsulator;
    }

    public String identifier() {
        return identifier;
    }

    @Override
    public byte[][] generateKeyPair() {
        return keyPairGenerator.generate();
    }

    @Override
    public EncapsulatedSecret encapsulate(byte[] publicKey) {
        return encapsulator.encapsulate(publicKey);
    }

    @Override
    public byte[] decapsulate(byte[] encapsulation, byte[] privateKey) {
        return decapsulator.decapsulate(encapsulation, privateKey);
    }
}
