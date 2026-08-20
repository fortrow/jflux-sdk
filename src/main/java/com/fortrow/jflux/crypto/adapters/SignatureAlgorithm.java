package com.fortrow.jflux.crypto.adapters;

import com.fortrow.jflux.crypto.adapters.impl.SignatureAlgorithmImpl;
import com.fortrow.jflux.exceptions.ExceptionHandling;

/** Adapter for digital signature algorithms. */
public class SignatureAlgorithm extends SignatureAlgorithmImpl {

    @FunctionalInterface
    public interface KeyPairGenerator {
        byte[][] generate();
    }

    @FunctionalInterface
    public interface Signer {
        byte[] sign(byte[] input, byte[] privateKey);
    }

    @FunctionalInterface
    public interface Verifier {
        boolean verify(byte[] input, byte[] signature, byte[] publicKey);
    }

    private final String identifier;
    private final KeyPairGenerator keyPairGenerator;
    private final Signer signer;
    private final Verifier verifier;

    public SignatureAlgorithm(
            String identifier,
            KeyPairGenerator keyPairGenerator,
            Signer signer,
            Verifier verifier) {
        if (identifier == null || identifier.isBlank()) throw ExceptionHandling.invalid("Algorithm identifier is required.");
        if (keyPairGenerator == null) throw ExceptionHandling.invalid("Key-pair generator is required.");
        if (signer == null) throw ExceptionHandling.invalid("Signer is required.");
        if (verifier == null) throw ExceptionHandling.invalid("Verifier is required.");
        this.identifier = identifier;
        this.keyPairGenerator = keyPairGenerator;
        this.signer = signer;
        this.verifier = verifier;
    }

    public String identifier() {
        return identifier;
    }

    @Override
    public byte[][] generateKeyPair() {
        return keyPairGenerator.generate();
    }

    @Override
    public byte[] sign(byte[] input, byte[] privateKey) {
        return signer.sign(input, privateKey);
    }

    @Override
    public boolean verify(byte[] input, byte[] signature, byte[] publicKey) {
        return verifier.verify(input, signature, publicKey);
    }
}
