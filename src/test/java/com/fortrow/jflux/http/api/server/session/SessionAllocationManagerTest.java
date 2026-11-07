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
