/*
 * Copyright (c) 2026 Fortrow LLC.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Fortrow-Commercial
 *
 * JFlux is free software: you can redistribute it and/or modify it under the
 * terms of the GNU Affero General Public License, version 3, as published by
 * the Free Software Foundation.
 *
 * JFlux is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Affero General Public License for
 * more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with JFlux. If not, see <https://www.gnu.org/licenses/>.
 *
 * Alternatively, a separate commercial license is available from Fortrow LLC.
 * For terms permitting use without the AGPL copyleft requirements, contact
 * ryan@fortrow.com. Commercial rights require a separate written agreement.
 * See LICENSE and COMMERCIAL-LICENSE.md.
 */

package com.fortrow.jflux.crypto.builtin;

import com.fortrow.jflux.crypto.adapters.EncapsulatedSecret;
import com.fortrow.jflux.crypto.adapters.KeyEncapsulationAlgorithm;
import com.fortrow.jflux.exceptions.ExceptionHandling;
import org.bouncycastle.crypto.AsymmetricCipherKeyPair;
import org.bouncycastle.crypto.SecretWithEncapsulation;
import org.bouncycastle.crypto.kems.MLKEMExtractor;
import org.bouncycastle.crypto.kems.MLKEMGenerator;
import org.bouncycastle.crypto.params.MLKEMKeyGenerationParameters;
import org.bouncycastle.crypto.generators.MLKEMKeyPairGenerator;
import org.bouncycastle.crypto.params.MLKEMParameters;
import org.bouncycastle.crypto.params.MLKEMPrivateKeyParameters;
import org.bouncycastle.crypto.params.MLKEMPublicKeyParameters;

import java.security.SecureRandom;

/** FIPS 203 ML-KEM adapters backed by Bouncy Castle's lightweight API. */
public final class MLKEM {

    private static final SecureRandom RANDOM = new SecureRandom();

    private MLKEM() {
    }

    public static boolean isAvailable() {
        try {
            Class.forName("org.bouncycastle.crypto.params.MLKEMParameters");
            return true;
        } catch (ClassNotFoundException | LinkageError e) {
            return false;
        }
    }

    public static KeyEncapsulationAlgorithm adapter(String parameterSet) {
        MLKEMParameters parameters = parameters(parameterSet);
        String identifier = normalize(parameterSet);
        return new KeyEncapsulationAlgorithm(
                identifier,
                () -> generateKeyPair(parameters),
                publicKey -> encapsulate(publicKey, parameters),
                (encapsulation, privateKey) -> decapsulate(encapsulation, privateKey, parameters));
    }

    public static byte[][] generateKeyPair(String parameterSet) {
        return generateKeyPair(parameters(parameterSet));
    }

    public static EncapsulatedSecret encapsulate(byte[] publicKey, String parameterSet) {
        return encapsulate(publicKey, parameters(parameterSet));
    }

    public static byte[] decapsulate(byte[] encapsulation, byte[] privateKey, String parameterSet) {
        return decapsulate(encapsulation, privateKey, parameters(parameterSet));
    }

    private static byte[][] generateKeyPair(MLKEMParameters parameters) {
        MLKEMKeyPairGenerator generator = new MLKEMKeyPairGenerator();
        generator.init(new MLKEMKeyGenerationParameters(RANDOM, parameters));
        AsymmetricCipherKeyPair pair = generator.generateKeyPair();
        byte[] publicKey = ((MLKEMPublicKeyParameters) pair.getPublic()).getEncoded();
        byte[] privateKey = ((MLKEMPrivateKeyParameters) pair.getPrivate()).getEncoded();
        return new byte[][]{publicKey, privateKey};
    }

    private static EncapsulatedSecret encapsulate(byte[] publicKey, MLKEMParameters parameters) {
        SecretWithEncapsulation result = new MLKEMGenerator(RANDOM)
                .generateEncapsulated(new MLKEMPublicKeyParameters(parameters, publicKey));
        try {
            return new EncapsulatedSecret(result.getEncapsulation(), result.getSecret());
        } finally {
            destroy(result);
        }
    }

    private static byte[] decapsulate(byte[] encapsulation, byte[] privateKey, MLKEMParameters parameters) {
        return new MLKEMExtractor(new MLKEMPrivateKeyParameters(parameters, privateKey))
                .extractSecret(encapsulation);
    }

    private static MLKEMParameters parameters(String parameterSet) {
        return switch (normalize(parameterSet)) {
            case "ml-kem-512" -> MLKEMParameters.ml_kem_512;
            case "ml-kem-768" -> MLKEMParameters.ml_kem_768;
            case "ml-kem-1024" -> MLKEMParameters.ml_kem_1024;
            default -> throw ExceptionHandling.invalid("Unsupported ML-KEM parameter set: " + parameterSet);
        };
    }

    private static String normalize(String parameterSet) {
        return parameterSet.toLowerCase().replace('_', '-');
    }

    private static void destroy(SecretWithEncapsulation result) {
        try {
            result.destroy();
        } catch (Exception e) {
            // The copied result is already detached from the provider object.
            ExceptionHandling.report(e);
        }
    }
}
