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
 * File created by Ryan Fitzgerald (ryan@fortrow.com) on 2026-05-08 13:58:51 EDT
 * 
 * -------------------------------------------------------------------------

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
