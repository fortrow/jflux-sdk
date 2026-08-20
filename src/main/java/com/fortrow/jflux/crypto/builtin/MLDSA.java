package com.fortrow.jflux.crypto.builtin;

import com.fortrow.jflux.crypto.adapters.SignatureAlgorithm;
import com.fortrow.jflux.exceptions.ExceptionHandling;
import org.bouncycastle.crypto.AsymmetricCipherKeyPair;
import org.bouncycastle.pqc.crypto.mldsa.MLDSAKeyGenerationParameters;
import org.bouncycastle.pqc.crypto.mldsa.MLDSAKeyPairGenerator;
import org.bouncycastle.pqc.crypto.mldsa.MLDSAParameters;
import org.bouncycastle.pqc.crypto.mldsa.MLDSAPrivateKeyParameters;
import org.bouncycastle.pqc.crypto.mldsa.MLDSAPublicKeyParameters;
import org.bouncycastle.pqc.crypto.mldsa.MLDSASigner;

import java.security.SecureRandom;

/** FIPS 204 ML-DSA adapters backed by Bouncy Castle's lightweight API. */
public final class MLDSA {

    private static final SecureRandom RANDOM = new SecureRandom();

    private MLDSA() {
    }

    public static boolean isAvailable() {
        try {
            Class.forName("org.bouncycastle.pqc.crypto.mldsa.MLDSAParameters");
            return true;
        } catch (ClassNotFoundException | LinkageError e) {
            return false;
        }
    }

    public static SignatureAlgorithm adapter(String parameterSet) {
        MLDSAParameters parameters = parameters(parameterSet);
        String identifier = normalize(parameterSet);
        return new SignatureAlgorithm(
                identifier,
                () -> generateKeyPair(parameters),
                (input, privateKey) -> sign(input, privateKey, parameters),
                (input, signature, publicKey) -> verify(input, signature, publicKey, parameters));
    }

    public static byte[][] generateKeyPair(String parameterSet) {
        return generateKeyPair(parameters(parameterSet));
    }

    public static byte[] sign(byte[] input, byte[] privateKey, String parameterSet) {
        return sign(input, privateKey, parameters(parameterSet));
    }

    public static boolean verify(byte[] input, byte[] signature, byte[] publicKey, String parameterSet) {
        return verify(input, signature, publicKey, parameters(parameterSet));
    }

    private static byte[][] generateKeyPair(MLDSAParameters parameters) {
        MLDSAKeyPairGenerator generator = new MLDSAKeyPairGenerator();
        generator.init(new MLDSAKeyGenerationParameters(RANDOM, parameters));
        AsymmetricCipherKeyPair pair = generator.generateKeyPair();
        byte[] publicKey = ((MLDSAPublicKeyParameters) pair.getPublic()).getEncoded();
        byte[] privateKey = ((MLDSAPrivateKeyParameters) pair.getPrivate()).getEncoded();
        return new byte[][]{publicKey, privateKey};
    }

    private static byte[] sign(byte[] input, byte[] privateKey, MLDSAParameters parameters) {
        MLDSASigner signer = new MLDSASigner();
        signer.init(true, new MLDSAPrivateKeyParameters(parameters, privateKey));
        for (byte value : input) signer.update(value);
        try {
            return signer.generateSignature();
        } catch (Exception e) {
            throw ExceptionHandling.failure("Unable to create ML-DSA signature", e);
        }
    }

    private static boolean verify(byte[] input, byte[] signature, byte[] publicKey, MLDSAParameters parameters) {
        MLDSASigner signer = new MLDSASigner();
        signer.init(false, new MLDSAPublicKeyParameters(parameters, publicKey));
        for (byte value : input) signer.update(value);
        return signer.verifySignature(signature);
    }

    private static MLDSAParameters parameters(String parameterSet) {
        return switch (normalize(parameterSet)) {
            case "ml-dsa-44" -> MLDSAParameters.ml_dsa_44;
            case "ml-dsa-65" -> MLDSAParameters.ml_dsa_65;
            case "ml-dsa-87" -> MLDSAParameters.ml_dsa_87;
            default -> throw ExceptionHandling.invalid("Unsupported ML-DSA parameter set: " + parameterSet);
        };
    }

    private static String normalize(String parameterSet) {
        return parameterSet.toLowerCase().replace('_', '-');
    }
}
