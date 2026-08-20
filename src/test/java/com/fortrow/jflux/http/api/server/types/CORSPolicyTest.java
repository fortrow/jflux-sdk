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
 * File created by Ryan Fitzgerald (ryan@fortrow.com) on 2026-05-11 14:11:36 EDT
 * 
 * -------------------------------------------------------------------------

 */
package com.fortrow.jflux.http.api.server.types;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CORSPolicyTest {

    @Test
    void restrictedPolicyRequiresExplicitNonWildcardOrigins() {
        CORSPolicy policy = new CORSPolicy(true, false);
        IllegalArgumentException missing = assertThrows(IllegalArgumentException.class, policy::validateOrThrow);
        assertTrue(missing.getMessage().contains("at least one origin"));

        IllegalArgumentException wildcard = assertThrows(IllegalArgumentException.class, () -> policy.addOrigin("*"));
        assertTrue(wildcard.getMessage().contains("Wildcard"));

        policy.addOrigin("https://linkdb.example.com");
        policy.setMethods(new String[]{"get", "post", "OPTIONS"});
        assertTrue(policy.validate());
        assertArrayEquals(new String[]{"GET", "POST", "OPTIONS"}, policy.getMethods());
    }

    @Test
    void headersAreDerivedFromServerMode() {
        CORSPolicy policy = new CORSPolicy(true);

        String[] restHeaders = policy.getHeaders(APIServerType.REST_SYNC);
        assertArrayContains(restHeaders, "X-Api-Key");
        assertArrayDoesNotContain(restHeaders, "X-Request-Signature");

        String[] encryptedHeaders = policy.getHeaders(APIServerType.ENCRYPTED_SYNC);
        assertArrayContains(encryptedHeaders, "X-Request-Signature");
        assertArrayContains(encryptedHeaders, "X-Api-Random-Bytes");
        assertArrayContains(encryptedHeaders, "Content-Transfer-Encoding");
    }

    @Test
    void customHeadersAreRejected() {
        CORSPolicy policy = new CORSPolicy(true);
        assertThrows(UnsupportedOperationException.class, () -> policy.addHeader("X-Custom"));
        assertThrows(UnsupportedOperationException.class, () -> policy.setHeaders(new String[]{"X-Custom"}));
    }

    private void assertArrayContains(String[] array, String value) {
        for(String item : array) {
            if(value.equals(item)) return;
        }
        fail("Expected array to contain " + value);
    }

    private void assertArrayDoesNotContain(String[] array, String value) {
        for(String item : array) {
            if(value.equals(item)) fail("Expected array not to contain " + value);
        }
    }
}
