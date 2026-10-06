package com.fortrow.jflux.http.api.client;

import com.fortrow.jflux.db.connectors.details.DbConnectionSqlLiteDetails;
import com.fortrow.jflux.http.api.base.prereqs.ApiService;
import com.fortrow.jflux.http.api.server.base.APIServer;
import com.fortrow.jflux.http.api.server.base.EncryptedAPI;
import com.fortrow.jflux.http.api.server.config.APIServerConfig;
import com.fortrow.jflux.http.api.server.config.HttpVersion;
import com.fortrow.jflux.http.api.server.containers.APIResponse;
import com.fortrow.jflux.http.api.server.containers.ResponseCode;
import com.fortrow.jflux.http.api.server.encryption.APIKeys;
import com.fortrow.jflux.http.api.server.resources.APIResource;
import com.fortrow.jflux.http.api.server.types.APIServerType;
import com.fortrow.jflux.http.api.types.DatabaseServerType;
import com.sun.net.httpserver.HttpServer;
import java.net.URI;
import java.net.http.*;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.*;
import org.json.simple.JSONObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class EncryptedTransportSerializationTest {
    @TempDir Path directory;
    @Test void manyKeysMaintainIndependentSessionsWhileResourcesRunInParallel() throws Throwable {
        var config = new APIServerConfig("127.0.0.1", 0, "/api")
                .setServerType(APIServerType.ENCRYPTED_SYNC)
                .setSessionDb(DatabaseServerType.SQLITE, new DbConnectionSqlLiteDetails(directory.resolve("parallel.db").toString()))
                .setSessionResumeStorePath(directory.resolve("parallel-state.json")).useDynamicApiKey(true).useWebSocket(0);
        var service = new ApiService("parallel-test", ApiService.generateAppKey("parallel-test", "nonce"));
        service.configureDatabases(config);
        var keys = new java.util.ArrayList<APIKeys>();
        var stripes = new java.util.HashSet<Integer>();
        while (keys.size() < 8) {
            var candidate = APIKeys.generateKeys();
            if (stripes.add(Math.floorMod(candidate.getKey().hashCode(), 4096))) keys.add(service.registerApiKeys(candidate));
        }
        var api = new EncryptedAPI(config, service);
        var entered = new CountDownLatch(keys.size());
        api.addResource("parallel", "POST", new APIResource() {
            @Override public APIResponse execute(com.fortrow.jflux.http.api.base.endpoints.InboundRequest request) {
                entered.countDown();
                try { assertTrue(entered.await(15, TimeUnit.SECONDS), "Independent keys must execute resources concurrently."); }
                catch (InterruptedException error) { Thread.currentThread().interrupt(); throw new RuntimeException(error); }
                return new APIResponse(request.getSession(), new JSONObject(), ResponseCode.SUCCESSFUL);
            }
        });
        api.start();
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            long deadline = System.nanoTime() + Duration.ofSeconds(3).toNanos();
            while (!api.getServer().isWebSocketStarted() && System.nanoTime() < deadline) Thread.sleep(10);
            String endpoint = "ws://127.0.0.1:" + api.getServer().getWebSocketPort() + config.getWebSocketPath();
            var work = new java.util.ArrayList<Future<Boolean>>();
            for (var identity : keys) work.add(executor.submit(() -> {
                try (var client = new EncryptedClient(identity.getKey(), identity.getSecret(), identity.getRawSessionSecret(),
                        identity.getPsk(), endpoint, HttpVersion.HTTP_1, false).build()) {
                    client.beginSessionOrThrow("/session/init", "/session/close");
                    for (int count = 0; count < 10; count++) {
                        var response = client.post("/parallel", new JSONObject());
                        if (response.getStatus() != 200 || !response.isVerified()) return false;
                    }
                    return client.closeSessionOrThrow().isVerified();
                } catch (com.fortrow.jflux.http.api.server.exceptions.APIException error) { throw new RuntimeException(error); }
            }));
            for (var result : work) assertTrue(result.get(25, TimeUnit.SECONDS));
            long closeDeadline = System.nanoTime() + Duration.ofSeconds(3).toNanos();
            while (!api.getServer().getClientSessions().isEmpty() && System.nanoTime() < closeDeadline) Thread.sleep(10);
            assertEquals(0, api.getServer().getClientSessions().size());
            assertEquals(keys.size(), api.getServer().getClients().size());
        } finally { api.stop(); }
    }

    @Test void httpRequestWithSameKeyWaitsForEntireWebSocketExchange() throws Throwable {
        var config = new APIServerConfig("127.0.0.1", 0, "/api")
                .setServerType(APIServerType.ENCRYPTED_SYNC)
                .setSessionDb(DatabaseServerType.SQLITE, new DbConnectionSqlLiteDetails(directory.resolve("sessions.db").toString()))
                .setSessionResumeStorePath(directory.resolve("state.json")).useDynamicApiKey(true).useWebSocket(0)
                .setWebSocketMaxPendingRequests(1);
        var service = new ApiService("serialization-test", ApiService.generateAppKey("serialization-test", "nonce"));
        service.configureDatabases(config);
        var keys = service.registerApiKeys(APIKeys.generateKeys());
        var api = new EncryptedAPI(config, service);
        var entered = new CountDownLatch(1); var release = new CountDownLatch(1);
        api.addResource("blocking", "POST", new APIResource() {
            @Override public APIResponse execute(com.fortrow.jflux.http.api.base.endpoints.InboundRequest request) {
                entered.countDown();
                try { assertTrue(release.await(5, TimeUnit.SECONDS)); }
                catch (InterruptedException error) { Thread.currentThread().interrupt(); throw new RuntimeException(error); }
                return new APIResponse(request.getSession(), new JSONObject(), ResponseCode.SUCCESSFUL);
            }
        });
        api.start();
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            long deadline = System.nanoTime() + Duration.ofSeconds(3).toNanos();
            while (!api.getServer().isWebSocketStarted() && System.nanoTime() < deadline) Thread.sleep(10);
            var field = APIServer.class.getDeclaredField("httpServer"); field.setAccessible(true);
            int port = ((HttpServer) field.get(api.getServer())).getAddress().getPort();
            try (var client = new EncryptedClient(keys.getKey(), keys.getSecret(), keys.getRawSessionSecret(), keys.getPsk(),
                    "ws://127.0.0.1:" + api.getServer().getWebSocketPort() + config.getWebSocketPath(), HttpVersion.HTTP_1, false).build()) {
                client.beginSessionOrThrow("/session/init", "/session/close");
                Future<EncryptedResponse> first = executor.submit(() -> {
                    try { return client.post("/blocking", new JSONObject()); }
                    catch (com.fortrow.jflux.http.api.server.exceptions.APIException error) { throw new RuntimeException(error); }
                });
                assertTrue(entered.await(3, TimeUnit.SECONDS));
                try (var overloaded = new EncryptedWebSocketTransport(URI.create("ws://127.0.0.1:"
                        + api.getServer().getWebSocketPort() + config.getWebSocketPath()))) {
                    assertEquals(503, overloaded.execute("GET", "/session/init", java.util.Map.of("X-Api-Key", keys.getKey()), "").status());
                }
                var http = HttpClient.newHttpClient();
                var second = http.sendAsync(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/session/init"))
                        .header("X-Api-Key", keys.getKey()).GET().build(), HttpResponse.BodyHandlers.ofString());
                assertThrows(TimeoutException.class, () -> second.get(250, TimeUnit.MILLISECONDS));
                release.countDown();
                assertTrue(first.get(3, TimeUnit.SECONDS).isVerified());
                assertEquals(401, second.get(3, TimeUnit.SECONDS).statusCode());
                assertTrue(client.closeSessionOrThrow().isVerified());
            }
        } finally { release.countDown(); api.stop(); }
    }
}
