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

import com.fortrow.jflux.db.connectors.details.DbConnectionSqlLiteDetails;
import com.fortrow.jflux.http.api.base.prereqs.ApiService;
import com.fortrow.jflux.http.api.server.base.EncryptedAPI;
import com.fortrow.jflux.http.api.server.base.RESTfulAPI;
import com.fortrow.jflux.http.api.server.config.APIServerConfig;
import com.fortrow.jflux.http.api.server.encryption.APIKeys;
import com.fortrow.jflux.http.api.server.types.APIServerType;
import com.fortrow.jflux.http.api.types.DatabaseServerType;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EncryptedWebSocketTransportTest {

    @Test
    void websocketTransportUsesTheExistingApiHandlerAndConfigurablePath() throws Throwable {
        APIServerConfig config = new APIServerConfig("127.0.0.1", 0, "/api")
                .setServerType(APIServerType.REST_SYNC)
                .setSessionDbType(DatabaseServerType.PROCESS_MEMORY)
                .apiIsPublic()
                .useWebSocket(0)
                .setWebSocketPath("/encrypted-api");
        RESTfulAPI api = new RESTfulAPI(config, null);
        api.start();
        try {
            long deadline = System.nanoTime() + Duration.ofSeconds(2).toNanos();
            while (!api.getServer().isWebSocketStarted() && System.nanoTime() < deadline) {
                Thread.sleep(10);
            }
            assertTrue(api.getServer().isWebSocketStarted());

            URI endpoint = URI.create("ws://127.0.0.1:" + api.getServer().getWebSocketPort() + "/encrypted-api");
            try (EncryptedWebSocketTransport transport = new EncryptedWebSocketTransport(endpoint)) {
                assertTrue(transport.connect());
                EncryptedWebSocketTransport.Response response = transport.execute("GET", "/missing", Map.of(), "");
                assertEquals(400, response.status());
            }
        } finally {
            api.stop();
        }
    }

    @Test
    void encryptedClientUsesTheSameSessionLifecycleOverWebSocket() throws Throwable {
        Path database = Files.createTempFile("jflux-encrypted-websocket", ".db");
        APIServerConfig config = new APIServerConfig("127.0.0.1", 0, "/api")
                .setServerType(APIServerType.ENCRYPTED_SYNC)
                .setSessionDb(DatabaseServerType.SQLITE, new DbConnectionSqlLiteDetails(database.toString()))
                .useDynamicApiKey(true)
                .useWebSocket(0)
                .setWebSocketPath("/encrypted-api");
        ApiService service = new ApiService("encrypted-websocket-test", ApiService.generateAppKey("encrypted-websocket-test", "test-nonce"));
        service.configureDatabases(config);
        APIKeys registered = service.registerApiKeys(APIKeys.generateKeys());
        EncryptedAPI api = new EncryptedAPI(config, service);
        api.start();
        try {
            long deadline = System.nanoTime() + Duration.ofSeconds(2).toNanos();
            while (!api.getServer().isWebSocketStarted() && System.nanoTime() < deadline) Thread.sleep(10);
            assertTrue(api.getServer().isWebSocketStarted());

            String endpoint = "ws://127.0.0.1:" + api.getServer().getWebSocketPort() + "/encrypted-api";
            try (EncryptedClient client = new EncryptedClient(
                    registered.getKey(), registered.getSecret(),
                    new StringSecretStore(registered.getRawSessionSecret()),
                    new StringReadOnlySecretStore(registered.getPsk()), endpoint,
                    com.fortrow.jflux.http.api.server.config.HttpVersion.HTTP_1, false).build()) {
                client.beginSessionOrThrow("/session/init", "/session/close");
                assertTrue(client.closeSessionOrThrow().isVerified());
            }
        } finally {
            api.stop();
            Files.deleteIfExists(database);
        }
    }
}
