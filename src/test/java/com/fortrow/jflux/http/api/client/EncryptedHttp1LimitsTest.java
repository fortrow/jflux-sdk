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

package com.fortrow.jflux.http.api.client;

import com.fortrow.jflux.http.api.server.config.HttpVersion;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class EncryptedHttp1LimitsTest {
    @Test void boundsBodiesRedirectsAndCompleteRequestTime() throws Exception {
        AtomicInteger mode = new AtomicInteger(), redirected = new AtomicInteger();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 4);
        server.setExecutor(java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor());
        server.createContext("/api/session/init", exchange -> {
            try {
                if (mode.get() == 0) {
                    exchange.sendResponseHeaders(200, 0); exchange.getResponseBody().write(new byte[1025]);
                } else if (mode.get() == 1) {
                    exchange.getResponseHeaders().set("Location", "/unexpected"); exchange.sendResponseHeaders(302, -1);
                } else {
                    exchange.sendResponseHeaders(200, 0);
                    for (int i=0; i<100; i++) { exchange.getResponseBody().write('x'); exchange.getResponseBody().flush(); try { Thread.sleep(50); } catch (InterruptedException e) { return; } }
                }
            } catch (java.io.IOException closedByClient) { }
            finally { exchange.close(); }
        });
        server.createContext("/unexpected", exchange -> { redirected.incrementAndGet(); exchange.sendResponseHeaders(500, -1); exchange.close(); });
        server.start();
        try {
            for (int i=0; i<3; i++) {
                mode.set(i);
                try (var client = new EncryptedClient("key", "secret", "session", "psk",
                        "http://127.0.0.1:"+server.getAddress().getPort()+"/api", HttpVersion.HTTP_1, false)
                        .setHttp1Limits(Duration.ofMillis(500), 1024).build()) {
                    assertThrows(IllegalArgumentException.class, () -> client.setHttp1Limits(Duration.ofSeconds(1), 100));
                    long start=System.nanoTime();
                    assertThrows(com.fortrow.jflux.http.api.server.exceptions.APIException.class, () -> client.beginSessionOrThrow("/session/init", "/session/close"));
                    assertTrue(System.nanoTime()-start < Duration.ofSeconds(3).toNanos());
                }
            }
            assertEquals(0, redirected.get());
        } finally { server.stop(0); }
    }
}
