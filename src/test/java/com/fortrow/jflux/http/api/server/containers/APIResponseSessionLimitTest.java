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
