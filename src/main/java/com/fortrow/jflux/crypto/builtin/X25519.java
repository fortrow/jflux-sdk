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
 *
 * File created by Ryan Fitzgerald (ryan@fortrow.com) on 2026-05-10 20:55:29 EDT
 */
package com.fortrow.jflux.crypto.builtin;

import com.fortrow.jflux.exceptions.ExceptionHandling;

import javax.crypto.KeyAgreement;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;

public final class X25519 {

    private X25519() {
    }

    public static KeyPair generateKeyPair() {
        try {
            return KeyPairGenerator.getInstance("X25519").generateKeyPair();
        } catch (Exception e) {
            throw ExceptionHandling.failure("Unable to generate X25519 key pair", e);
        }
    }

    public static byte[] sharedSecret(PrivateKey privateKey, PublicKey publicKey) {
        try {
            KeyAgreement keyAgreement = KeyAgreement.getInstance("X25519");
            keyAgreement.init(privateKey);
            keyAgreement.doPhase(publicKey, true);
            return keyAgreement.generateSecret();
        } catch (Exception e) {
            throw ExceptionHandling.failure("Unable to derive X25519 shared secret", e);
        }
    }

    public static PublicKey publicKeyFromBytes(byte[] input) {
        try {
            return KeyFactory.getInstance("X25519").generatePublic(new X509EncodedKeySpec(input));
        } catch (Exception e) {
            throw ExceptionHandling.invalid("Invalid X25519 public key", e);
        }
    }

    public static PrivateKey privateKeyFromBytes(byte[] input) {
        try {
            return KeyFactory.getInstance("X25519").generatePrivate(new PKCS8EncodedKeySpec(input));
        } catch (Exception e) {
            throw ExceptionHandling.invalid("Invalid X25519 private key", e);
        }
    }
}
