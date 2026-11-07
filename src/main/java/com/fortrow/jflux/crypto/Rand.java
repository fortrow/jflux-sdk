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
 * File created by Ryan Fitzgerald (ryan@fortrow.com) on 2026-05-04 15:27:04 EDT
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
