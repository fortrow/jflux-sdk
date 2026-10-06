package com.fortrow.jflux.http.api.server.session;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class SessionResumeTokenStoreTest {

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
