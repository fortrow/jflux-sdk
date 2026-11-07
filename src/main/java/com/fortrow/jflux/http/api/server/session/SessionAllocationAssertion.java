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

import com.fortrow.jflux.crypto.builtin.SHA;
import com.fortrow.jflux.crypto.builtin.SecureCompare;
import com.fortrow.jflux.http.api.server.exceptions.APIException;
import org.json.simple.JSONObject;

import java.time.Instant;

/**
 * A short-lived assertion signed by the application's web-authentication
 * backend to authorize one encrypted-session allocation.
 *
 * <p>The signing secret is deliberately not part of this value. The web
 * authentication backend and JFlux must obtain the same per-API-key secret
 * from their private configuration or key store. The assertion can therefore
 * travel through the client without disclosing that secret.</p>
 */
public record SessionAllocationAssertion(
        String apiKey,
        String challengeId,
        String challenge,
        long issuedAtMs,
        long expiresAtMs,
        String signature
) {

    private static final String PROTOCOL = "JFLUX-HTTP-API/1";
    private static final String ACTION = "session-allocate";

    /** Creates an assertion that the external authentication backend can return to the client. */
    public static SessionAllocationAssertion sign(
            String apiKey,
            String challengeId,
            String challenge,
            long issuedAtMs,
            long expiresAtMs,
            String allocationSecret
    ) throws APIException {
        require(apiKey, "API key");
        require(challengeId, "Challenge ID");
        require(challenge, "Challenge");
        require(allocationSecret, "Allocation signing secret");
        if (issuedAtMs <= 0 || expiresAtMs <= issuedAtMs) {
            throw new APIException("Session allocation assertion timestamps are invalid.");
        }
        String canonical = canonical(apiKey, challengeId, challenge, issuedAtMs, expiresAtMs);
        String signature = SHA.getHmac384(canonical, allocationSecret);
        if (signature == null || signature.isBlank()) {
            throw new APIException("Failed to sign the session allocation assertion.");
        }
        return new SessionAllocationAssertion(apiKey, challengeId, challenge, issuedAtMs, expiresAtMs, signature);
    }

    /** Convenience overload for callers using {@link Instant} values. */
    public static SessionAllocationAssertion sign(
            String apiKey,
            String challengeId,
            String challenge,
            Instant issuedAt,
            Instant expiresAt,
            String allocationSecret
    ) throws APIException {
        if (issuedAt == null || expiresAt == null) {
            throw new APIException("Session allocation assertion timestamps are required.");
        }
        return sign(apiKey, challengeId, challenge, issuedAt.toEpochMilli(), expiresAt.toEpochMilli(), allocationSecret);
    }

    /** Parses the flat JSON form accepted by {@code session/allocate}. */
    public static SessionAllocationAssertion fromJson(JSONObject object) throws APIException {
        if (object == null) throw new APIException("Session allocation assertion is required.");
        return new SessionAllocationAssertion(
                stringValue(object.get("api_key")),
                stringValue(object.get("challenge_id")),
                stringValue(object.get("challenge")),
                longValue(object.get("issued_at_ms")),
                longValue(object.get("expires_at_ms")),
                stringValue(object.get("signature"))
        );
    }

    /** Returns the flat JSON form suitable for a JFlux encrypted request. */
    @SuppressWarnings("unchecked")
    public JSONObject toJson() {
        JSONObject object = new JSONObject();
        object.put("api_key", apiKey);
        object.put("challenge_id", challengeId);
        object.put("challenge", challenge);
        object.put("issued_at_ms", issuedAtMs);
        object.put("expires_at_ms", expiresAtMs);
        object.put("signature", signature);
        return object;
    }

    /** Returns the exact canonical bytes represented by this assertion. */
    public String canonicalPayload() {
        return canonical(apiKey, challengeId, challenge, issuedAtMs, expiresAtMs);
    }

    /** Verifies the assertion signature using the private per-API-key secret. */
    public boolean verify(String allocationSecret) {
        if (allocationSecret == null || allocationSecret.isBlank()
                || signature == null || signature.isBlank()) return false;
        String expected = SHA.getHmac384(canonicalPayload(), allocationSecret);
        return SecureCompare.equals(expected, signature);
    }

    private static String canonical(String apiKey, String challengeId, String challenge,
                                    long issuedAtMs, long expiresAtMs) {
        return PROTOCOL
                + "\naction:" + ACTION
                + "\napi-key:" + value(apiKey, "")
                + "\nchallenge-id:" + value(challengeId, "")
                + "\nchallenge:" + value(challenge, "")
                + "\nissued-at-ms:" + issuedAtMs
                + "\nexpires-at-ms:" + expiresAtMs;
    }

    private static String value(String value, String fallback) {
        return value == null ? fallback : value;
    }

    private static void require(String value, String name) throws APIException {
        if (value == null || value.isBlank()) throw new APIException(name + " is required.");
    }

    private static String stringValue(Object value) {
        return value == null ? "" : value.toString().trim();
    }

    private static long longValue(Object value) throws APIException {
        if (value instanceof Number number) return number.longValue();
        if (value == null) return 0;
        try {
            return Long.parseLong(value.toString().trim());
        } catch (NumberFormatException ex) {
            throw new APIException("Session allocation assertion timestamp is invalid.", ex);
        }
    }
}
