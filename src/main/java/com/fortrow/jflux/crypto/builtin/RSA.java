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
 * File created by Ryan Fitzgerald (ryan@fortrow.com) on 2026-05-08 17:12:47 EDT
 */
package com.fortrow.jflux.crypto.builtin;

import com.fortrow.jflux.exceptions.ExceptionHandling;

import javax.crypto.Cipher;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.spec.EncodedKeySpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

public class RSA {

    public static PublicKey publicKeyFromStr(String pubKey) {

        try {

            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            EncodedKeySpec keySpec = new X509EncodedKeySpec(pubKey.getBytes(StandardCharsets.UTF_8));
            return keyFactory.generatePublic(keySpec);

        } catch (Exception ex) {
            ExceptionHandling.report(ex);
        }

        return null;

    }

    public static PublicKey publicKeyFromBytes(byte[] pubKey) {

        try {

            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            EncodedKeySpec keySpec = new X509EncodedKeySpec(pubKey);
            return keyFactory.generatePublic(keySpec);

        } catch (Exception ex) {
            ExceptionHandling.report(ex);
        }

        return null;

    }

    public static PrivateKey privateKeyFromStr(String prikey) {

        try {

            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(prikey.getBytes(StandardCharsets.UTF_8));
            return keyFactory.generatePrivate(keySpec);

        } catch (Exception ex) {
            ExceptionHandling.report(ex);
        }

        return null;

    }

    public static PrivateKey privateKeyFromBytes(byte[] prikey) {

        try {

            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(prikey);
            return keyFactory.generatePrivate(keySpec);

        } catch (Exception ex) {
            ExceptionHandling.report(ex);

        }

        return null;

    }

    public static KeyPair generateKeyPair(int bitSize) {

        try {

            KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
            gen.initialize(bitSize);
            return gen.generateKeyPair();

        } catch (Exception ex) {
            ExceptionHandling.report(ex);
        }

        return null;

    }

    public static String encrypt(String input, PublicKey publicKey) {

        try {

            Cipher cipher = Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding");
            cipher.init(Cipher.ENCRYPT_MODE, publicKey);

            byte[] bytes = input.getBytes(StandardCharsets.UTF_8);
            byte[] output = cipher.doFinal(bytes);

            return Base64.getEncoder().encodeToString(output);

        } catch (Exception ex) {
            ExceptionHandling.report(ex);
        }

        return "";

    }

    public static String decrypt(byte[] input, PrivateKey privateKey) {

        try {

            Cipher cipher = Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding");
            cipher.init(Cipher.DECRYPT_MODE, privateKey);

            byte[] bytes = input;
            byte[] output = cipher.doFinal(bytes);

            return Base64.getEncoder().encodeToString(output);

        } catch (Exception ex) {
            ExceptionHandling.report(ex);

        }

        return "";


    }

    public static String encrypt(byte[] input, PublicKey publicKey) {

        try {

            Cipher cipher = Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding");
            cipher.init(Cipher.ENCRYPT_MODE, publicKey);

            byte[] bytes = input;
            byte[] output = cipher.doFinal(bytes);

            return Base64.getEncoder().encodeToString(output);

        } catch (Exception ex) {
            ExceptionHandling.report(ex);
        }

        return "";

    }

    public static String decrypt(String input, PrivateKey privateKey) {

        try {

            Cipher cipher = Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding");
            cipher.init(Cipher.DECRYPT_MODE, privateKey);

            byte[] bytes = input.getBytes(StandardCharsets.UTF_8);
            byte[] output = cipher.doFinal(bytes);

            return Base64.getEncoder().encodeToString(output);

        } catch (Exception ex) {
            ExceptionHandling.report(ex);

        }

        return "";


    }

    public static String encrypt(String input, String publicKey) {

        return encrypt(input, publicKeyFromStr(publicKey));

    }

    public static String decrypt(String input, String privateKey) {

        return decrypt(input, privateKeyFromStr(privateKey));

    }

}
