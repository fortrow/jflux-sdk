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

import com.fortrow.jflux.exceptions.ExceptionHandling;

import java.util.Arrays;

/** The ciphertext and shared secret returned by a KEM encapsulation operation. */
public final class EncapsulatedSecret implements AutoCloseable {

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

    /** Erases the retained shared secret after the caller has derived its keys. */
    @Override
    public void close() {
        Arrays.fill(secret, (byte) 0);
    }
}
