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

import com.fortrow.jflux.crypto.Cryptography;
import com.fortrow.jflux.crypto.builtin.AES;
import com.fortrow.jflux.crypto.builtin.SHA;
import com.fortrow.jflux.db.connectors.details.DbConnectionSqlLiteDetails;
import com.fortrow.jflux.encoding.BaseX;
import com.fortrow.jflux.encoding.Hex;
import com.fortrow.jflux.http.api.base.endpoints.InboundRequest;
import com.fortrow.jflux.http.api.base.prereqs.ApiService;
import com.fortrow.jflux.http.api.server.base.APIServer;
import com.fortrow.jflux.http.api.server.base.EncryptedAPI;
import com.fortrow.jflux.http.api.server.config.APIServerConfig;
import com.fortrow.jflux.http.api.server.config.HttpVersion;
import com.fortrow.jflux.http.api.server.containers.APIResponse;
import com.fortrow.jflux.http.api.server.containers.ResponseCode;
import com.fortrow.jflux.http.api.server.encryption.APIKeys;
import com.fortrow.jflux.http.api.server.encryption.EncryptedApiCryptoSuite;
import com.fortrow.jflux.http.api.server.exceptions.APIException;
import com.fortrow.jflux.http.api.server.resources.APIResource;
import com.fortrow.jflux.http.api.server.types.APIServerType;
import com.fortrow.jflux.http.api.types.DatabaseServerType;
import com.fortrow.jflux.json.JSON;
import com.sun.net.httpserver.HttpServer;
import org.json.simple.JSONObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import static org.junit.jupiter.api.Assertions.*;

class EncryptedApiCryptoSuiteTest {
    @TempDir Path tempDir;

    @ParameterizedTest
    @EnumSource(EncryptedApiCryptoSuite.class)
    void clientOwnedKeysWorkAcrossHttpWebSocketAndResume(EncryptedApiCryptoSuite suite) throws Throwable {
        try (Fixture f = new Fixture(suite)) {
            StringSecretStore sessionSecret = new StringSecretStore(f.keys.getRawSessionSecret());
            StringSecretStore resumeToken = new StringSecretStore("");
            byte[] firstClientKey;
            try (EncryptedClient client = f.client(f.httpEndpoint(), suite, sessionSecret, resumeToken)) {
                client.beginSessionOrThrow("/session/init", "/session/close");
                var session = f.api.getServer().getClientSessions().getFirst().getSession();
                assertEquals(suite, session.getCryptoSuite());
                assertNull(session.getClientPrivateKey(), "Server must never possess the modern client's private key");
                var serverPrivate = session.getServerPrivateKey().getPrivateKey();
                firstClientKey = session.getClientPublicKey().getPublicKey().getEncoded();
                assertEcho(client);
                assertTrue(client.heartbeat().isVerified());
                assertTrue(client.closeSessionOrThrow(true).isVerified());
                long closedBy = System.nanoTime() + Duration.ofSeconds(2).toNanos();
                while (!f.api.getServer().getClientSessions().isEmpty() && System.nanoTime() < closedBy) Thread.sleep(5);
                assertTrue(f.api.getServer().getClientSessions().isEmpty());
                assertNull(session.getServerPrivateKey().getPrivateKey());
                if (suite.isPostQuantum()) assertTrue(serverPrivate.isDestroyed());
                assertFalse(resumeToken.read().isBlank());
            }
            // A rejected downgrade attempt must not burn a valid saved resume token.
            try (HttpClient http = HttpClient.newHttpClient()) {
                JSONObject body = new JSONObject();
                body.put("resume_token", resumeToken.read());
                var rejected = bootstrap(f, sessionSecret.read())
                        .uri(URI.create(f.httpEndpoint() + "/session/resume"))
                        .header(EncryptedApiCryptoSuite.SUITE_HEADER, suite.isPostQuantum() ? "rsa-2048" : "ml-kem-768")
                        .header(EncryptedApiCryptoSuite.CLIENT_KEY_HEADER, BaseX.encode64(firstClientKey))
                        .POST(HttpRequest.BodyPublishers.ofString(body.toJSONString())).build();
                assertEquals(401, http.send(rejected, HttpResponse.BodyHandlers.ofString()).statusCode());
            }
            try (EncryptedClient client = f.client(f.wsEndpoint(), suite, sessionSecret, resumeToken)) {
                client.beginSessionOrThrow("/session/init", "/session/close");
                assertTrue(resumeToken.read().isBlank(), "Successful resume consumes the saved token");
                var resumed = f.api.getServer().getClientSessions().getFirst().getSession();
                assertNull(resumed.getClientPrivateKey());
                assertFalse(java.util.Arrays.equals(firstClientKey, resumed.getClientPublicKey().getPublicKey().getEncoded()));
                EncryptedResponse failure = client.post("/echo", new JSONObject());
                assertEquals(400, failure.getStatus());
                assertTrue(failure.isVerified());
                assertEcho(client);
                assertTrue(client.closeSessionOrThrow().isVerified());
            }
        }
    }

    @Test
    void postQuantumServerRejectsMissingMismatchedMalformedAndDuplicateNegotiation() throws Throwable {
        try (Fixture f = new Fixture(EncryptedApiCryptoSuite.ML_KEM_768);
             HttpClient http = HttpClient.newHttpClient()) {
            for (String selected : new String[]{"", "rsa-2048", "ml-kem-768"}) {
                var builder = bootstrap(f, f.keys.getRawSessionSecret());
                if (!selected.isEmpty()) builder.header(EncryptedApiCryptoSuite.SUITE_HEADER, selected)
                        .header(EncryptedApiCryptoSuite.CLIENT_KEY_HEADER, "broken");
                assertEquals(401, http.send(builder.GET().build(), HttpResponse.BodyHandlers.ofString()).statusCode());
                assertTrue(f.api.getServer().getClientSessions().isEmpty());
            }
            byte[][] pair = EncryptedApiCryptoSuite.ML_KEM_768.generateKeyPair();
            var duplicate = bootstrap(f, f.keys.getRawSessionSecret())
                    .header(EncryptedApiCryptoSuite.SUITE_HEADER, "ml-kem-768")
                    .header(EncryptedApiCryptoSuite.SUITE_HEADER, "rsa-2048")
                    .header(EncryptedApiCryptoSuite.CLIENT_KEY_HEADER, BaseX.encode64(pair[0]));
            assertEquals(401, http.send(duplicate.GET().build(), HttpResponse.BodyHandlers.ofString()).statusCode());
        }
    }

    @Test
    void legacyRsaClientsStillReceiveCompatibleBootstrap() throws Throwable {
        try (Fixture f = new Fixture(EncryptedApiCryptoSuite.RSA_2048);
             HttpClient http = HttpClient.newHttpClient()) {
            var result = http.send(bootstrap(f, f.keys.getRawSessionSecret()).GET().build(), HttpResponse.BodyHandlers.ofString());
            assertEquals(200, result.statusCode());
            JSONObject outer = JSON.getObject(result.body());
            byte[] plaintext = AES.gcm(BaseX.bytesFrom64((String) outer.get("response")), Hex.fromHex(f.keys.getPsk()),
                    Hex.fromHex((String) outer.get("iv")), null, false);
            JSONObject bootstrap = JSON.getObject(new String(plaintext, StandardCharsets.UTF_8));
            assertEquals("rsa-2048", bootstrap.get("crypto_suite"));
            assertTrue(bootstrap.containsKey("client_private_key"));
        }
    }

    @Test
    void rejectsPostQuantumConfigurationWithoutDynamicKeys() {
        APIServerConfig config = new APIServerConfig(0).setServerType(APIServerType.ENCRYPTED_SYNC)
                .setEncryptedCryptoSuite(EncryptedApiCryptoSuite.ML_KEM_768);
        assertThrows(APIException.class, config::validateOrThrow);
    }

    private static void assertEcho(EncryptedClient client) throws APIException {
        JSONObject body = new JSONObject();
        body.put("value", "post-quantum \u2713");
        EncryptedResponse response = client.post("/echo", body);
        assertEquals(200, response.getStatus());
        assertTrue(response.isVerified());
        assertEquals(body.get("value"), response.getBodyObject().get("value"));
    }

    private static HttpRequest.Builder bootstrap(Fixture f, String rawSecret) throws Exception {
        return HttpRequest.newBuilder(URI.create(f.httpEndpoint() + "/session/init"))
                .header("Content-Type", "application/json")
                .header("X-Api-Key", f.keys.getKey())
                .header("X-Request-Timestamp", Long.toString(System.currentTimeMillis() / 1000))
                .header("X-Api-Random-Bytes", Hex.getHex(Cryptography.defaults().randomBytes(44)))
                .header("X-Api-Session-Authorization", SHA.get256(rawSecret));
    }

    private final class Fixture implements AutoCloseable {
        final EncryptedAPI api;
        final APIKeys keys;
        Fixture(EncryptedApiCryptoSuite suite) throws Throwable {
            APIServerConfig config = new APIServerConfig("127.0.0.1", 0, "/api")
                    .setServerType(APIServerType.ENCRYPTED_SYNC)
                    .setSessionDb(DatabaseServerType.SQLITE, new DbConnectionSqlLiteDetails(tempDir.resolve("api.db").toString()))
                    .setSessionResumeStorePath(tempDir.resolve("resume.json"))
                    .useDynamicApiKey(true).setEncryptedCryptoSuite(suite).useWebSocket(0).setWebSocketPath("/api");
            ApiService service = new ApiService("pq-test", ApiService.generateAppKey("pq-test", "test"));
            service.configureDatabases(config);
            keys = service.registerApiKeys(APIKeys.generateKeys());
            api = new EncryptedAPI(config, service);
            api.addResource("echo", "POST", new String[]{"value"}, new APIResource() {
                @Override public APIResponse execute(InboundRequest request) {
                    JSONObject body = new JSONObject();
                    body.put("value", request.getParams().get("value"));
                    body.put("request_status", "success");
                    return new APIResponse(request.getSession(), body, ResponseCode.SUCCESSFUL);
                }
            });
            api.start();
            long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
            while (!api.getServer().isWebSocketStarted() && System.nanoTime() < deadline) Thread.sleep(10);
            assertTrue(api.getServer().isWebSocketStarted());
        }
        String httpEndpoint() throws Exception {
            var field = APIServer.class.getDeclaredField("httpServer");
            field.setAccessible(true);
            int port = ((HttpServer) field.get(api.getServer())).getAddress().getPort();
            return "http://127.0.0.1:" + port + "/api";
        }
        String wsEndpoint() { return "ws://127.0.0.1:" + api.getServer().getWebSocketPort() + "/api"; }
        EncryptedClient client(String endpoint, EncryptedApiCryptoSuite suite, SecretStore secret, SecretStore resume) {
            return new EncryptedClient(keys.getKey(), keys.getSecret(), secret, new StringReadOnlySecretStore(keys.getPsk()),
                    endpoint, HttpVersion.HTTP_1, false).setResumeTokenStore(resume).setEncryptedCryptoSuite(suite).build();
        }
        @Override public void close() { api.stop(); }
    }
}
