package com.fortrow.jflux.http.api.server.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

class APIServerConfigSessionPathTest {

    @Test
    void defaultLifecyclePathsIncludeAuthenticatedHeartbeat() {
        APIServerConfig config = new APIServerConfig(0);

        assertArrayEquals(
                new String[]{
                        "session/init",
                        "session/close",
                        "session/resume",
                        "session/reset",
                        "session/allocate",
                        "session/heartbeat"
                },
                config.getEncryptedSessionPaths()
        );
    }

    @Test
    void customLifecyclePathsKeepHeartbeatAsTheLastPath() {
        APIServerConfig config = new APIServerConfig(0)
                .setSessionResourcePaths(
                        "init",
                        "close",
                        "resume",
                        "reset",
                        "allocate",
                        "heartbeat"
                );

        assertArrayEquals(
                new String[]{"init", "close", "resume", "reset", "allocate", "heartbeat"},
                config.getEncryptedSessionPaths()
        );
    }
}
