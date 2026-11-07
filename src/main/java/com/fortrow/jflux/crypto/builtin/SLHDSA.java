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

import com.fortrow.jflux.crypto.adapters.SignatureAlgorithm;
import com.fortrow.jflux.exceptions.ExceptionHandling;
import org.bouncycastle.crypto.AsymmetricCipherKeyPair;
import org.bouncycastle.crypto.params.SLHDSAKeyGenerationParameters;
import org.bouncycastle.crypto.generators.SLHDSAKeyPairGenerator;
import org.bouncycastle.crypto.params.SLHDSAParameters;
import org.bouncycastle.crypto.params.SLHDSAPrivateKeyParameters;
import org.bouncycastle.crypto.params.SLHDSAPublicKeyParameters;
import org.bouncycastle.crypto.signers.SLHDSASigner;

import java.security.SecureRandom;

/** FIPS 205 SLH-DSA adapters backed by Bouncy Castle's lightweight API. */
public final class SLHDSA {

    private static final SecureRandom RANDOM = new SecureRandom();

    private SLHDSA() {
    }

    public static boolean isAvailable() {
        try {
            Class.forName("org.bouncycastle.crypto.params.SLHDSAParameters");
            return true;
        } catch (ClassNotFoundException | LinkageError e) {
            return false;
        }
    }

    public static SignatureAlgorithm adapter(String parameterSet) {
        SLHDSAParameters parameters = parameters(parameterSet);
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

    private static byte[][] generateKeyPair(SLHDSAParameters parameters) {
        SLHDSAKeyPairGenerator generator = new SLHDSAKeyPairGenerator();
        generator.init(new SLHDSAKeyGenerationParameters(RANDOM, parameters));
        AsymmetricCipherKeyPair pair = generator.generateKeyPair();
        byte[] publicKey = ((SLHDSAPublicKeyParameters) pair.getPublic()).getEncoded();
        byte[] privateKey = ((SLHDSAPrivateKeyParameters) pair.getPrivate()).getEncoded();
        return new byte[][]{publicKey, privateKey};
    }

    private static byte[] sign(byte[] input, byte[] privateKey, SLHDSAParameters parameters) {
        SLHDSASigner signer = new SLHDSASigner();
        signer.init(true, new SLHDSAPrivateKeyParameters(parameters, privateKey));
        return signer.generateSignature(input);
    }

    private static boolean verify(byte[] input, byte[] signature, byte[] publicKey, SLHDSAParameters parameters) {
        SLHDSASigner signer = new SLHDSASigner();
        signer.init(false, new SLHDSAPublicKeyParameters(parameters, publicKey));
        return signer.verifySignature(input, signature);
    }

    private static SLHDSAParameters parameters(String parameterSet) {
        return switch (normalize(parameterSet)) {
            case "slh-dsa-sha2-128f" -> SLHDSAParameters.sha2_128f;
            case "slh-dsa-sha2-128s" -> SLHDSAParameters.sha2_128s;
            case "slh-dsa-sha2-192f" -> SLHDSAParameters.sha2_192f;
            case "slh-dsa-sha2-192s" -> SLHDSAParameters.sha2_192s;
            case "slh-dsa-sha2-256f" -> SLHDSAParameters.sha2_256f;
            case "slh-dsa-sha2-256s" -> SLHDSAParameters.sha2_256s;
            case "slh-dsa-shake-128f" -> SLHDSAParameters.shake_128f;
            case "slh-dsa-shake-128s" -> SLHDSAParameters.shake_128s;
            case "slh-dsa-shake-192f" -> SLHDSAParameters.shake_192f;
            case "slh-dsa-shake-192s" -> SLHDSAParameters.shake_192s;
            case "slh-dsa-shake-256f" -> SLHDSAParameters.shake_256f;
            case "slh-dsa-shake-256s" -> SLHDSAParameters.shake_256s;
            default -> throw ExceptionHandling.invalid("Unsupported SLH-DSA parameter set: " + parameterSet);
        };
    }

    private static String normalize(String parameterSet) {
        return parameterSet.toLowerCase().replace('_', '-');
    }
}
