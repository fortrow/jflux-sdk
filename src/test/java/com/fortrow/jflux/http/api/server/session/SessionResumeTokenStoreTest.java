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
 */

package com.fortrow.jflux.http.api.server.session;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class SessionResumeTokenStoreTest {

    @Test void hashedAdministrativeRevocationPersistsAndPreservesOtherSessions() throws Throwable {
        Path path=Files.createTempDirectory("jflux-revoke-test").resolve("tokens.json");
        SessionResumeTokenStore store=new SessionResumeTokenStore(path);
        String first=store.issue("api","secret","first",Duration.ofMinutes(5));
        String second=store.issue("api","secret","second",Duration.ofMinutes(5));
        String other=store.issue("other","secret","first",Duration.ofMinutes(5));
        store.invalidateForSessionHash(com.fortrow.jflux.crypto.builtin.SHA.get256("api"),"first");
        SessionResumeTokenStore reopened=new SessionResumeTokenStore(path);
        assertEquals(null,reopened.sessionForToken("api","secret",first));
        assertEquals("second",reopened.sessionForToken("api","secret",second));
        assertEquals("first",reopened.sessionForToken("other","secret",other));
    }

    @Test
    void earlyTokensRefreshWithoutReservingAnotherLiveSlot() throws Throwable {
        SessionResumeTokenStore store = new SessionResumeTokenStore(null);
        String token = store.issueWhileActive("api", "secret", "session", Duration.ofMinutes(30));
        assertEquals(token, store.issueWhileActive("api", "secret", "session", Duration.ofMinutes(30)));
        assertEquals(0, store.outstandingForApiKey("api", java.util.Set.of("session")));
        assertEquals(1, store.outstandingForApiKey("api", java.util.Set.of()));
        assertEquals("session", store.sessionForToken("api", "secret", token));
        org.junit.jupiter.api.Assertions.assertNull(store.sessionForToken("other", "secret", token));
        assertEquals(SessionResumeTokenStore.ConsumeResult.VALID, store.consume("api", "secret", token));
        org.junit.jupiter.api.Assertions.assertNull(store.sessionForToken("api", "secret", token));
    }

    @Test
    void closingInvalidatesOnlyTheAssociatedEarlyToken() throws Throwable {
        SessionResumeTokenStore store = new SessionResumeTokenStore(null);
        String first = store.issueWhileActive("api", "secret", "first", Duration.ofMinutes(30));
        String second = store.issueWhileActive("api", "secret", "second", Duration.ofMinutes(30));
        store.invalidateForSession("api", "first");
        org.junit.jupiter.api.Assertions.assertNull(store.sessionForToken("api", "secret", first));
        assertEquals("second", store.sessionForToken("api", "secret", second));
        store.invalidateForApiKey("api");
        org.junit.jupiter.api.Assertions.assertNull(store.sessionForToken("api", "secret", second));
    }

    @Test
    void tokensAreBoundToTheApiKeyAndSingleUse() throws Throwable {
        SessionResumeTokenStore store = new SessionResumeTokenStore(null);
        String token = store.issue("api-key", "api-secret", "session-1", Duration.ofMinutes(5));

        assertEquals(1, store.outstandingForApiKey("api-key"));
        assertEquals(SessionResumeTokenStore.ConsumeResult.API_KEY_MISMATCH,
                store.consume("other-key", "api-secret", token));
        assertEquals(SessionResumeTokenStore.ConsumeResult.VALID,
                store.consume("api-key", "api-secret", token));
        assertEquals(SessionResumeTokenStore.ConsumeResult.REPLAY,
                store.consume("api-key", "api-secret", token));
        assertEquals(0, store.outstandingForApiKey("api-key"));
    }

    @Test
    void resumeConsumesAndInvalidatesTheSessionTokenSetTogether() throws Throwable {
        SessionResumeTokenStore store = new SessionResumeTokenStore(null);
        String token = store.issue("api-key", "api-secret", "session-1", Duration.ofMinutes(5));
        String sibling = store.issue("api-key", "api-secret", "session-1", Duration.ofMinutes(5));

        assertEquals(SessionResumeTokenStore.ConsumeResult.VALID,
                store.consumeAndInvalidateForSession("api-key", "api-secret", token, "session-1"));
        assertEquals(SessionResumeTokenStore.ConsumeResult.REPLAY,
                store.consume("api-key", "api-secret", token));
        assertEquals(SessionResumeTokenStore.ConsumeResult.REPLAY,
                store.consume("api-key", "api-secret", sibling));
    }

    @Test
    void durableStoreDoesNotWriteRawTokensAndReloadsReservations() throws Throwable {
        Path path = Files.createTempFile("jflux-resume", ".json");
        try {
            SessionResumeTokenStore first = new SessionResumeTokenStore(path);
            String token = first.issue("api-key", "api-secret", "session-1", Duration.ofMinutes(5));
            SessionResumeTokenStore second = new SessionResumeTokenStore(path);

            assertEquals(1, second.outstandingForApiKey("api-key"));
            assertNotEquals(-1, token.indexOf("jflux_resume_v1."));
            String persisted = Files.readString(path);
            org.junit.jupiter.api.Assertions.assertFalse(persisted.contains(token));
        } finally {
            Files.deleteIfExists(path);
        }
    }
}
