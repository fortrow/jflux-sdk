/*
 * -------------------------------------------------------------------------
 * 
 * Copyright (c) 2026 Fortrow LLC. All rights reserved.
 *  
 * NOTICE: All information contained herein is, and remains the property of 
 * Fortrow, LLC. The intellectual and technical concepts contained herein are 
 * proprietary to [Company Name] and may be covered by U.S. and Foreign Patents, 
 * patents in process, and are protected by trade secret or copyright law.
 * 
 * Dissemination of this information or reproduction of this material is 
 * strictly forbidden unless prior written permission is obtained from 
 * Fortrow, LLC.
 * 
 * -------------------------------------------------------------------------
 * 
 * File created by Ryan Fitzgerald (ryan@fortrow.com) on 2026-05-10 22:51:05 EDT
 * 
 * -------------------------------------------------------------------------

 */
package com.fortrow.jflux.crypto.builtin;

import com.fortrow.jflux.encoding.Hex;
import com.fortrow.jflux.exceptions.ExceptionHandling;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public final class HKDF {

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final int HASH_LENGTH = 32;

    private HKDF() {}

    public static byte[] extract(byte[] salt, byte[] inputKeyMaterial) {
        byte[] actualSalt = salt == null ? new byte[HASH_LENGTH] : salt;
        return hmac(actualSalt, inputKeyMaterial);
    }

    public static byte[] expand(byte[] pseudorandomKey, byte[] info, int length) {
        if (length < 0 || length > 255 * HASH_LENGTH) {
            throw ExceptionHandling.invalid("Invalid HKDF output length");
        }
        byte[] actualInfo = info == null ? new byte[0] : info;
        byte[] output = new byte[length];
        byte[] previous = new byte[0];
        int generated = 0;
        int counter = 1;
        while (generated < length) {
            byte[] input = new byte[previous.length + actualInfo.length + 1];
            System.arraycopy(previous, 0, input, 0, previous.length);
            System.arraycopy(actualInfo, 0, input, previous.length, actualInfo.length);
            input[input.length - 1] = (byte) counter;
            previous = hmac(pseudorandomKey, input);
            int copy = Math.min(previous.length, length - generated);
            System.arraycopy(previous, 0, output, generated, copy);
            generated += copy;
            counter++;
        }
        return output;
    }

    public static byte[] deriveKey(byte[] inputKeyMaterial, byte[] salt, byte[] info, int length) {
        return expand(extract(salt, inputKeyMaterial), info, length);
    }

    public static String deriveKeyHex(String inputKeyMaterialHex, String saltHex, String info, int length) {
        byte[] salt = saltHex == null ? null : Hex.fromHex(saltHex);
        return Hex.getHex(deriveKey(
                Hex.fromHex(inputKeyMaterialHex),
                salt,
                info == null ? null : info.getBytes(StandardCharsets.UTF_8),
                length
        ));
    }

    private static byte[] hmac(byte[] key, byte[] input) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(key, HMAC_ALGORITHM));
            return mac.doFinal(input == null ? new byte[0] : input);
        } catch (Exception e) {
            throw ExceptionHandling.failure("HKDF failed", e);
        }
    }
}
