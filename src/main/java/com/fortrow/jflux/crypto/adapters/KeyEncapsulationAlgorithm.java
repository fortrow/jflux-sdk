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
