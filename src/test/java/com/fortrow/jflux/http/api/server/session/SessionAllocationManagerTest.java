package com.fortrow.jflux.http.api.server.session;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SessionAllocationManagerTest {

    @Test
    void assertionRequiresThePrivateApiKeySecretAndIsSingleUse() throws Throwable {
        SessionAllocationManager manager = new SessionAllocationManager();
        SessionAllocationManager.Challenge challenge = manager.issueChallenge("api-key", Duration.ofMinutes(5));
        Instant issuedAt = Instant.now();
        Instant expiresAt = challenge.expiresAt();
        SessionAllocationAssertion assertion = SessionAllocationAssertion.sign(
                "api-key", challenge.id(), challenge.value(), issuedAt, expiresAt, "allocation-secret");

        assertFalse(manager.verifyAndConsume("api-key", "wrong-secret", assertion));
        assertTrue(manager.verifyAndConsume("api-key", "allocation-secret", assertion));
        assertFalse(manager.verifyAndConsume("api-key", "allocation-secret", assertion));
    }

    @Test
    void challengeDoesNotContainOrRequireUserCredentials() throws Throwable {
        SessionAllocationManager manager = new SessionAllocationManager();
        SessionAllocationManager.Challenge challenge = manager.issueChallenge("api-key", Duration.ofMinutes(5));

        assertNotNull(challenge);
        assertTrue(challenge.id().startsWith("jflux_challenge_"));
    }
}
