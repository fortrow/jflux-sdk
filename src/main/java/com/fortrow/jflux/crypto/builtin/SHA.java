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
 * File created by Ryan Fitzgerald (ryan@fortrow.com) on 2026-05-04 16:47:40 EDT
 * 
 * -------------------------------------------------------------------------

 */
package com.fortrow.jflux.crypto.builtin;

import com.fortrow.jflux.encoding.Hex;
import com.fortrow.jflux.exceptions.ExceptionHandling;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class SHA {

    public static String getSha1(String input)
    {
        try {

            MessageDigest md = MessageDigest.getInstance("SHA-1");

            byte[] messageDigest = md.digest(input.getBytes());

            BigInteger num = new BigInteger(1, messageDigest);
            String hashtext = num.toString(16);

            while (hashtext.length() < 40) {
                hashtext = "0" + hashtext;
            }

            return hashtext;
        }

        catch (NoSuchAlgorithmException e) {
            ExceptionHandling.report(e);
        }

        return "";

    }

    public static String getSha1(byte[] input)
    {
        try {

            MessageDigest md = MessageDigest.getInstance("SHA-1");

            byte[] messageDigest = md.digest(input);

            BigInteger num = new BigInteger(1, messageDigest);
            String hashtext = num.toString(16);

            while (hashtext.length() < 40) {
                hashtext = "0" + hashtext;
            }

            return hashtext;
        }

        catch (NoSuchAlgorithmException e) {
            ExceptionHandling.report(e);
        }

        return "";

    }

    public static String get512(String input)
    {
        try {

            MessageDigest md = MessageDigest.getInstance("SHA-512");

            byte[] messageDigest = md.digest(input.getBytes());

            BigInteger num = new BigInteger(1, messageDigest);
            String hashtext = num.toString(16);

            while (hashtext.length() < 128) {
                hashtext = "0" + hashtext;
            }

            return hashtext;
        }

        catch (NoSuchAlgorithmException e) {
            ExceptionHandling.report(e);
        }

        return "";

    }

    public static String get512(byte[] input)
    {
        try {

            MessageDigest md = MessageDigest.getInstance("SHA-512");

            byte[] messageDigest = md.digest(input);

            BigInteger num = new BigInteger(1, messageDigest);
            String hashtext = num.toString(16);

            while (hashtext.length() < 128) {
                hashtext = "0" + hashtext;
            }

            return hashtext;
        }

        catch (NoSuchAlgorithmException e) {
            ExceptionHandling.report(e);
        }

        return "";

    }


    public static String get384(String input)
    {
        try {

            MessageDigest md = MessageDigest.getInstance("SHA-384");

            byte[] messageDigest = md.digest(input.getBytes());

            BigInteger num = new BigInteger(1, messageDigest);
            String hashtext = num.toString(16);

            while (hashtext.length() < 96) {
                hashtext = "0" + hashtext;
            }

            return hashtext;
        }

        catch (NoSuchAlgorithmException e) {
            ExceptionHandling.report(e);
        }

        return "";

    }

    public static String get384(byte[] input)
    {
        try {

            MessageDigest md = MessageDigest.getInstance("SHA-384");

            byte[] messageDigest = md.digest(input);

            BigInteger num = new BigInteger(1, messageDigest);
            String hashtext = num.toString(16);

            while (hashtext.length() < 96) {
                hashtext = "0" + hashtext;
            }

            return hashtext;
        }

        catch (NoSuchAlgorithmException e) {
            ExceptionHandling.report(e);
        }

        return "";

    }

    public static String get256(String input)
    {
        try {

            MessageDigest md = MessageDigest.getInstance("SHA-256");

            byte[] messageDigest = md.digest(input.getBytes());

            BigInteger num = new BigInteger(1, messageDigest);
            String hashtext = num.toString(16);

            while (hashtext.length() < 64) {
                hashtext = "0" + hashtext;
            }

            return hashtext;
        }

        catch (NoSuchAlgorithmException e) {
            ExceptionHandling.report(e);
        }

        return "";

    }

    public static String get256(byte[] input)
    {
        try {

            MessageDigest md = MessageDigest.getInstance("SHA-256");

            byte[] messageDigest = md.digest(input);

            BigInteger num = new BigInteger(1, messageDigest);
            String hashtext = num.toString(16);

            while (hashtext.length() < 64) {
                hashtext = "0" + hashtext;
            }

            return hashtext;
        }

        catch (NoSuchAlgorithmException e) {
            ExceptionHandling.report(e);
        }

        return "";

    }

    public static String getHmac512(String input, String key) {

        try {

            Mac sha256_HMAC = Mac.getInstance("HmacSHA512");
            SecretKeySpec secret_key = new SecretKeySpec(key.getBytes("UTF-8"), "HmacSHA512");
            sha256_HMAC.init(secret_key);

            return Hex.getHex(sha256_HMAC.doFinal(input.getBytes("UTF-8")));

        } catch (Exception ex) {
            ExceptionHandling.report(ex);
            return null;

        }

    }

    public static String getHmac512(String input, byte[] key) {

        try {

            Mac sha256_HMAC = Mac.getInstance("HmacSHA512");
            SecretKeySpec secret_key = new SecretKeySpec(key, "HmacSHA512");
            sha256_HMAC.init(secret_key);

            return Hex.getHex(sha256_HMAC.doFinal(input.getBytes("UTF-8")));

        } catch (Exception ex) {
            ExceptionHandling.report(ex);
            return null;

        }

    }

    public static String getHmac384Hex(String input, byte[] key) {

        try {

            Mac sha384_HMAC = Mac.getInstance("HmacSHA384");
            SecretKeySpec secret_key = new SecretKeySpec(key, "HmacSHA384");
            sha384_HMAC.init(secret_key);

            return Hex.getHex(sha384_HMAC.doFinal(input.getBytes("UTF-8")));

        } catch (Exception ex) {
            ExceptionHandling.report(ex);
            return null;

        }

    }

    public static String getHmac384(String input, String key) {

        try {

            Mac sha384_HMAC = Mac.getInstance("HmacSHA384");
            SecretKeySpec secret_key = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA384");
            sha384_HMAC.init(secret_key);

            return Hex.getHex(sha384_HMAC.doFinal(input.getBytes("UTF-8")));

        } catch (Exception ex) {
            ExceptionHandling.report(ex);
            return null;

        }

    }

    public static String getHmac384Hex(String input, String key) {

        try {

            Mac sha384_HMAC = Mac.getInstance("HmacSHA384");
            SecretKeySpec secret_key = new SecretKeySpec(Hex.fromHex(key), "HmacSHA384");
            sha384_HMAC.init(secret_key);

            return Hex.getHex(sha384_HMAC.doFinal(input.getBytes("UTF-8")));

        } catch (Exception ex) {
            ExceptionHandling.report(ex);
            return null;

        }

    }

    public static String getHmac256(String input, String key) {

        try {

            Mac sha256_HMAC = Mac.getInstance("HmacSHA256");
            SecretKeySpec secret_key = new SecretKeySpec(key.getBytes("UTF-8"), "HmacSHA256");
            sha256_HMAC.init(secret_key);

            return Hex.getHex(sha256_HMAC.doFinal(input.getBytes("UTF-8")));

        } catch (Exception ex) {
            ExceptionHandling.report(ex);
            return null;

        }

    }

    public static byte[] getHmac256(byte[] input, byte[] key) {

        try {

            Mac sha256_HMAC = Mac.getInstance("HmacSHA256");
            SecretKeySpec secret_key = new SecretKeySpec(key, "HmacSHA256");
            sha256_HMAC.init(secret_key);

            return sha256_HMAC.doFinal(input);

        } catch (Exception ex) {
            ExceptionHandling.report(ex);
            return null;

        }

    }


    public static String getHmac256(String input, byte[] key) {

        try {

            Mac sha256_HMAC = Mac.getInstance("HmacSHA256");
            SecretKeySpec secret_key = new SecretKeySpec(key, "HmacSHA256");
            sha256_HMAC.init(secret_key);

            return Hex.getHex(sha256_HMAC.doFinal(input.getBytes("UTF-8")));

        } catch (Exception ex) {
            ExceptionHandling.report(ex);
            return null;

        }

    }

    public static class Bites {
        public static byte[] getSha1(byte[] input) {
            try {

                MessageDigest md = MessageDigest.getInstance("SHA-1");

                byte[] messageDigest = md.digest(input);

                BigInteger num = new BigInteger(1, messageDigest);
                return num.toByteArray();
            } catch (NoSuchAlgorithmException e) {
                ExceptionHandling.report(e);
            }

            return new byte[0];
        }

        public static byte[] getSha1(String input) {
            try {

                MessageDigest md = MessageDigest.getInstance("SHA-1");

                byte[] messageDigest = md.digest(input.getBytes());

                BigInteger num = new BigInteger(1, messageDigest);
                return num.toByteArray();
            } catch (NoSuchAlgorithmException e) {
                ExceptionHandling.report(e);
            }

            return new byte[0];
        }
    }

}
