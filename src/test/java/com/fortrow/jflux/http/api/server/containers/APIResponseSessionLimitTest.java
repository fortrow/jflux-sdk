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

package com.fortrow.jflux.http.api.server.containers;

import com.fortrow.jflux.json.JSON;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class APIResponseSessionLimitTest {

    @Test
    void genericLimitResponseDoesNotExposeSessionDiagnostics() {
        JSONObject body = JSON.getObject(APIResponse.sessionLimitReached(null, 1).getResponse());

        assertEquals("JFLUX_SESSION_LIMIT_REACHED", body.get("error_code"));
        assertFalse(body.containsKey("active_sessions"));
        assertFalse(body.containsKey("session_ip_addresses"));
        assertFalse(body.containsKey("outstanding_resume_tokens"));
    }

    @Test
    void authenticatedLimitResponseIncludesCurrentSessionsAndUniqueIps() {
        JSONObject body = JSON.getObject(APIResponse.sessionLimitReached(
                null, 2, 2, 1, List.of("192.0.2.10", "192.0.2.10", "2001:db8::10")
        ).getResponse());

        assertEquals(2L, body.get("active_sessions"));
        assertEquals(1L, body.get("outstanding_resume_tokens"));
        JSONArray addresses = (JSONArray) body.get("session_ip_addresses");
        assertEquals(List.of("192.0.2.10", "2001:db8::10"), addresses);
        assertTrue(body.containsKey("session_ip_addresses"));
    }
}
