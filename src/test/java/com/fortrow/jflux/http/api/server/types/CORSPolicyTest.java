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
 * File created by Ryan Fitzgerald (ryan@fortrow.com) on 2026-05-11 14:11:36 EDT
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
