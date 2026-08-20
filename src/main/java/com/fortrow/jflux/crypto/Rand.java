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
 * File created by Ryan Fitzgerald (ryan@fortrow.com) on 2026-05-04 15:27:04 EDT
 * 
 * -------------------------------------------------------------------------

 */
package com.fortrow.jflux.crypto;

import javax.crypto.spec.IvParameterSpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

public class Rand {

    public static byte[] secure(int len) {

        byte[] bytes = new byte[len];
        new SecureRandom().nextBytes(bytes);
        return bytes;

    }

    public static int num(int min, int max) {
        return ThreadLocalRandom.current().nextInt(min, max + 1);
    }

    public static byte[] secure(int len, String seed) {

        byte[] bytes = new byte[len];
        SecureRandom rand = new SecureRandom();
        rand.setSeed(seed.getBytes(StandardCharsets.UTF_8));
        rand.nextBytes(bytes);
        return bytes;

    }

    public static String letter(String seed) {
        String letters = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";
        int s = ((int) seed.charAt(0)) + ((int) seed.charAt(seed.length() - 1));
        Random r = new Random(s);
        int i = r.nextInt(52);
        return String.valueOf(letters.charAt(i));
}

    public static String str(int len, String prefix) {

        int leftLimit = 48;
        int rightLimit = 122;
        int targetStringLength = len;

        Random random = new Random();

        String generatedString = random.ints(leftLimit, rightLimit + 1)
                .filter(i -> (i <= 57 || i >= 65) && (i <= 90 || i >= 97))
                .limit(targetStringLength)
                .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append)
                .toString();

        return (prefix.isEmpty()) ? generatedString : prefix + "_" + generatedString;

    }

    public static byte[] generateKey(int bits) {

        byte[] key = new byte[bits / 8];
        new SecureRandom().nextBytes(key);
        return key;

    }

    public static byte[] generateIv(int bits) {

        byte[] iv = new byte[bits / 8];
        new SecureRandom().nextBytes(iv);
        IvParameterSpec ivParameterSpec = new IvParameterSpec(iv);
        return ivParameterSpec.getIV();

    }

    public static String randomCase(String input) {
        input = input.toLowerCase();
        StringBuilder ret = new StringBuilder();
        for(int i=0; i<input.length(); i++) {
            byte res = secure(1)[0];
            int bool = res % 2;
            boolean capitalize = bool == 1;
            String c = input.substring(i, i + 1);
            if(capitalize) c = c.toUpperCase();
            ret.append(c);
        }
        return ret.toString();
    }

}
