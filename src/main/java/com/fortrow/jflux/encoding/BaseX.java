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
 * File created by Ryan Fitzgerald (ryan@fortrow.com) on 2026-05-08 13:58:51 EDT
 */
package com.fortrow.jflux.encoding;

import com.fortrow.jflux.exceptions.ExceptionHandling;

import java.util.Base64;
import java.nio.charset.StandardCharsets;

public final class BaseX {

    private BaseX() {
    }

    public static String encode64(String input) {
        if (input == null) throw ExceptionHandling.invalid("Base64 input must not be null");
        return encode64(input.getBytes(StandardCharsets.UTF_8));
    }

    public static String encode64(byte[] input) {
        if (input == null) throw ExceptionHandling.invalid("Base64 input must not be null");
        return Base64.getEncoder().encodeToString(input);
    }

    public static String stringFrom64(String input) {
        try {
            return new String(Base64.getDecoder().decode(input), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            throw ExceptionHandling.invalid("Invalid Base64 input", e);
        }
    }

    public static String stringFrom64(byte[] input) {
        try {
            return new String(Base64.getDecoder().decode(input), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            throw ExceptionHandling.invalid("Invalid Base64 input", e);
        }
    }

    public static byte[] bytesFrom64(String input) {
        try {
            return Base64.getDecoder().decode(input);
        } catch (IllegalArgumentException e) {
            ExceptionHandling.report(e);
            return null;
        }
    }

    public static byte[] bytesFrom64(byte[] input) {
        try {
            return Base64.getDecoder().decode(input);
        } catch (IllegalArgumentException e) {
            throw ExceptionHandling.invalid("Invalid Base64 input", e);
        }
    }

}
