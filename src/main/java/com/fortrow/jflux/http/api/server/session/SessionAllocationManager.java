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

import com.fortrow.jflux.crypto.Rand;
import com.fortrow.jflux.crypto.builtin.SecureCompare;
import com.fortrow.jflux.encoding.Hex;
import com.fortrow.jflux.http.api.server.exceptions.APIException;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

/** In-memory, one-time challenge coordinator for signed session allocations. */
public final class SessionAllocationManager {

    public record Challenge(String id, String value, Instant expiresAt) { }

    private record ChallengeState(String apiKey, String value, Instant expiresAt) { }

    private final ConcurrentHashMap<String, ChallengeState> challenges = new ConcurrentHashMap<>();

    /** Issues a challenge that is bound to the API key and can be consumed once. */
    public Challenge issueChallenge(String apiKey, Duration lifetime) throws APIException {
        if (apiKey == null || apiKey.isBlank()) throw new APIException("API key is required for session allocation.");
        Duration safeLifetime = lifetime == null || lifetime.isNegative() || lifetime.isZero()
                ? Duration.ofMinutes(5) : lifetime;
        Instant expiresAt = Instant.now().plus(safeLifetime);
        String id = "jflux_challenge_" + Hex.getHex(Rand.secure(24));
        String value = Hex.getHex(Rand.secure(32));
        challenges.put(id, new ChallengeState(apiKey, value, expiresAt));
        return new Challenge(id, value, expiresAt);
    }

    /**
     * Verifies and consumes one signed allocation assertion. A successful call
     * authorizes exactly one capacity increment; replaying the assertion fails.
     */
    public boolean verifyAndConsume(String apiKey, String allocationSecret,
                                    SessionAllocationAssertion assertion) {
        if (apiKey == null || apiKey.isBlank() || allocationSecret == null || allocationSecret.isBlank()
                || assertion == null || assertion.challengeId() == null || assertion.challengeId().isBlank()) return false;
        ChallengeState challenge = challenges.get(assertion.challengeId());
        Instant now = Instant.now();
        if (challenge == null || !challenge.expiresAt().isAfter(now)) {
            if (challenge != null && !challenge.expiresAt().isAfter(now)) {
                challenges.remove(assertion.challengeId(), challenge);
            }
            return false;
        }
        if (!SecureCompare.equals(apiKey, assertion.apiKey())
                || !SecureCompare.equals(apiKey, challenge.apiKey())
                || !SecureCompare.equals(challenge.value(), assertion.challenge())
                || assertion.issuedAtMs() <= 0
                || assertion.expiresAtMs() <= assertion.issuedAtMs()
                || assertion.expiresAtMs() <= now.toEpochMilli()
                || assertion.expiresAtMs() > challenge.expiresAt().toEpochMilli()
                || !assertion.verify(allocationSecret)) return false;

        return challenges.remove(assertion.challengeId(), challenge);
    }

    public void clear() {
        challenges.clear();
    }

    public void invalidateApiKey(String apiKey) {
        if (apiKey == null || apiKey.isBlank()) return;
        challenges.entrySet().removeIf(entry -> apiKey.equals(entry.getValue().apiKey()));
    }
}
