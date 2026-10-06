package com.fortrow.jflux.http.api.client;

import com.fortrow.jflux.db.connectors.details.DbConnectionSqlLiteDetails;
import com.fortrow.jflux.http.api.base.prereqs.ApiService;
import com.fortrow.jflux.http.api.server.base.EncryptedAPI;
import com.fortrow.jflux.http.api.server.config.APIServerConfig;
import com.fortrow.jflux.http.api.server.config.HttpVersion;
import com.fortrow.jflux.http.api.server.containers.APIResponse;
import com.fortrow.jflux.http.api.server.containers.ResponseCode;
import com.fortrow.jflux.http.api.server.encryption.APIKeys;
import com.fortrow.jflux.http.api.server.resources.APIResource;
import com.fortrow.jflux.http.api.server.session.SessionResumeTokenStore;
import com.fortrow.jflux.http.api.server.types.APIServerType;
import com.fortrow.jflux.http.api.types.DatabaseServerType;
import org.json.simple.JSONObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EncryptedResourceErrorRatchetTest {

    @TempDir Path tempDir;

    @Test
    void applicationFailureDoesNotDesynchronizeTheNextRequest() throws Throwable {
        Path database = Files.createTempFile("jflux-error-ratchet", ".db");
        APIServerConfig config = new APIServerConfig("127.0.0.1", 0, "/api")
                .setServerType(APIServerType.ENCRYPTED_SYNC)
                .setSessionDb(DatabaseServerType.SQLITE, new DbConnectionSqlLiteDetails(database.toString()))
                .setSessionResumeStorePath(tempDir.resolve("resume-state.json"))
                .setActiveSessionRecoveryEnabled(true)
                .useDynamicApiKey(true)
                .useWebSocket(0)
                .setWebSocketPath("/api");
        ApiService service = new ApiService("error-ratchet-test", ApiService.generateAppKey("error-ratchet-test", "test-nonce"));
        service.configureDatabases(config);
        APIKeys keys = service.registerApiKeys(APIKeys.generateKeys());
        EncryptedAPI api = new EncryptedAPI(config, service);
        api.addResource("failure", "POST", new APIResource() {
            @Override public APIResponse execute(com.fortrow.jflux.http.api.base.endpoints.InboundRequest request) {
                JSONObject body = new JSONObject();
                body.put("request_status", "failure");
                body.put("error", "expected application failure");
                return new APIResponse(request.getSession(), body, ResponseCode.REQUEST_FAILED);
            }
        });
        api.addResource("success", "POST", new APIResource() {
            @Override public APIResponse execute(com.fortrow.jflux.http.api.base.endpoints.InboundRequest request) {
                JSONObject body = new JSONObject();
                body.put("request_status", "success");
                return new APIResponse(request.getSession(), body, ResponseCode.SUCCESSFUL);
            }
        });
        api.addResource("required", "POST", new String[]{"value"}, new APIResource() {
            @Override public APIResponse execute(com.fortrow.jflux.http.api.base.endpoints.InboundRequest request) {
                JSONObject body = new JSONObject();
                body.put("request_status", "success");
                return new APIResponse(request.getSession(), body, ResponseCode.SUCCESSFUL);
            }
        });
        api.start();
        try {
            long deadline = System.nanoTime() + Duration.ofSeconds(2).toNanos();
            while (!api.getServer().isWebSocketStarted() && System.nanoTime() < deadline) Thread.sleep(10);
            assertTrue(api.getServer().isWebSocketStarted());
            String endpoint = "ws://127.0.0.1:" + api.getServer().getWebSocketPort() + "/api";
            try (EncryptedClient client = new EncryptedClient(keys.getKey(), keys.getSecret(),
                    new StringSecretStore(keys.getRawSessionSecret()), new StringReadOnlySecretStore(keys.getPsk()),
                    endpoint, HttpVersion.HTTP_1, false).build()) {
                client.beginSessionOrThrow("/session/init", "/session/close");
                assertEquals(1, new SessionResumeTokenStore(tempDir.resolve("resume-state.json"))
                        .outstandingForApiKey(keys.getKey()));
                EncryptedResponse failure = client.post("/failure", new JSONObject());
                assertEquals(400, failure.getStatus());
                assertTrue(failure.isVerified());
                EncryptedResponse missingParameter = client.post("/required", new JSONObject());
                assertEquals(400, missingParameter.getStatus());
                assertTrue(missingParameter.isVerified());
                EncryptedResponse success = client.post("/success", new JSONObject());
                assertEquals(200, success.getStatus());
                assertTrue(success.isVerified());
            }
        } finally {
            api.stop();
            Files.deleteIfExists(database);
        }
    }
}
