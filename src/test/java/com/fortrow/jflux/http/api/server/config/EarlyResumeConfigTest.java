package com.fortrow.jflux.http.api.server.config;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class EarlyResumeConfigTest {
    @Test void onlyEarlyIssuanceRequiresOptIn() {
        APIServerConfig config = new APIServerConfig(8443);
        assertFalse(config.isActiveSessionRecoveryEnabled());
        String[] paths = config.getEncryptedSessionPaths().clone();
        int lifetime = config.getResumeTokenMinutes();
        config.setActiveSessionRecoveryEnabled(true);
        assertTrue(config.isActiveSessionRecoveryEnabled());
        assertArrayEquals(paths, config.getEncryptedSessionPaths());
        assertEquals(lifetime, config.getResumeTokenMinutes());
    }
}
