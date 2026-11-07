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
