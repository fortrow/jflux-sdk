package com.fortrow.jflux.websockets.server;

import com.fortrow.jflux.websockets.client.FluxWebSocketClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.net.ssl.*;
import java.net.URI;
import java.nio.file.Path;
import java.security.KeyStore;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class FluxWebSocketMtlsTest {
    @TempDir Path directory;

    @Test
    void requiresClientCertificateAndExposesVerifiedPeerAndChecksHostname() throws Throwable {
        Path identity = directory.resolve("identity.p12");
        Process keytool = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "keytool").toString(),
                "-genkeypair", "-alias", "test", "-keyalg", "RSA", "-keysize", "2048",
                "-dname", "CN=localhost", "-ext", "SAN=dns:localhost", "-validity", "1",
                "-storetype", "PKCS12", "-keystore", identity.toString(),
                "-storepass", "test-password", "-keypass", "test-password", "-noprompt")
                .redirectErrorStream(true).redirectOutput(directory.resolve("keytool.log").toFile()).start();
        assertTrue(keytool.waitFor(20, TimeUnit.SECONDS));
        assertEquals(0, keytool.exitValue());
        KeyStore store = KeyStore.getInstance("PKCS12");
        try (var input = java.nio.file.Files.newInputStream(identity)) { store.load(input, "test-password".toCharArray()); }
        KeyManagerFactory keys = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        keys.init(store, "test-password".toCharArray());
        TrustManagerFactory trust = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        trust.init(store);
        SSLContext authenticated = SSLContext.getInstance("TLS");
        authenticated.init(keys.getKeyManagers(), trust.getTrustManagers(), null);
        SSLContext anonymous = SSLContext.getInstance("TLS");
        anonymous.init(null, trust.getTrustManagers(), null);

        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch received = new CountDownLatch(1);
        AtomicReference<String> subject = new AtomicReference<>();
        AtomicReference<TlsPeerIdentity> observedIdentity = new AtomicReference<>();
        FluxWebSocketServer server = new FluxWebSocketServer("127.0.0.1", 0, new FluxWebSocketHandler() {
            @Override public void onStart() { started.countDown(); }
            @Override public void onText(FluxWebSocketSession session, String message) {
                subject.set(session.verifiedPeerCertificates()[0].getSubjectX500Principal().getName());
                observedIdentity.set(session.peerIdentity());
                session.setAttribute("peerIdentity", "caller-controlled-spoof");
                assertSame(observedIdentity.get(), session.peerIdentity());
                received.countDown();
            }
        }).setSslContext(authenticated, true);
        try {
            server.start();
            assertTrue(started.await(3, TimeUnit.SECONDS));
            URI endpoint = URI.create("wss://localhost:" + server.getPort());
            try (FluxWebSocketClient missing = new FluxWebSocketClient(endpoint).setSslContext(anonymous)) {
                assertFalse(missing.connectAndWait(Duration.ofSeconds(3)));
            }
            try (FluxWebSocketClient wrongHost = new FluxWebSocketClient(
                    URI.create("wss://127.0.0.1:" + server.getPort())).setSslContext(authenticated)) {
                assertFalse(wrongHost.connectAndWait(Duration.ofSeconds(3)));
            }
            // The encrypted client must load its configured identity and trust store for WSS.
            var material = com.fortrow.jflux.http.api.client.MtlsConfig.pkcs12(identity,
                    "test-password".toCharArray(), identity, "test-password".toCharArray());
            try (var encrypted = new com.fortrow.jflux.http.api.client.EncryptedClient(
                    "api-key", "api-secret", "session-secret", "psk", endpoint.toString(),
                    com.fortrow.jflux.http.api.server.config.HttpVersion.HTTP_1, false, material)) {
                assertSame(encrypted, encrypted.build());
            }
            try (FluxWebSocketClient valid = new FluxWebSocketClient(endpoint).setSslContext(authenticated)) {
                assertTrue(valid.connectAndWait(Duration.ofSeconds(3)));
                valid.sendText("authenticated");
                assertTrue(received.await(3, TimeUnit.SECONDS));
                assertEquals("CN=localhost", subject.get());
                byte[] expectedDer = store.getCertificate("test").getEncoded();
                assertEquals(java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(expectedDer)),
                        observedIdentity.get().leafSha256());
                byte[][] copied = observedIdentity.get().certificateChainDer(); copied[0][0] ^= 1;
                assertArrayEquals(expectedDer, observedIdentity.get().certificateChainDer()[0]);
            }
        } finally { server.close(); }
        // API-level configuration must reach its separately created WSS listener too.
        var apiConfig = new com.fortrow.jflux.http.api.server.config.APIServerConfig("127.0.0.1", 0, "/api")
                .setServerType(com.fortrow.jflux.http.api.server.types.APIServerType.REST_SYNC)
                .setSessionDbType(com.fortrow.jflux.http.api.types.DatabaseServerType.PROCESS_MEMORY)
                .apiIsPublic().useWebSocket(0).setWebSocketSslContext(authenticated).setClientAuth(true);
        var api = new com.fortrow.jflux.http.api.server.base.RESTfulAPI(apiConfig, null);
        api.start();
        try {
            long deadline = System.nanoTime() + Duration.ofSeconds(3).toNanos();
            while (!api.getServer().isWebSocketStarted() && System.nanoTime() < deadline) Thread.sleep(10);
            assertTrue(api.getServer().isWebSocketStarted());
            URI apiEndpoint = URI.create("wss://localhost:" + api.getServer().getWebSocketPort() + apiConfig.getWebSocketPath());
            try (FluxWebSocketClient noIdentity = new FluxWebSocketClient(apiEndpoint).setSslContext(anonymous)) {
                assertFalse(noIdentity.connectAndWait(Duration.ofSeconds(3)));
            }
            try (FluxWebSocketClient identified = new FluxWebSocketClient(apiEndpoint).setSslContext(authenticated)) {
                assertTrue(identified.connectAndWait(Duration.ofSeconds(3)));
            }
        } finally { api.stop(); }

        var encryptedConfig = new com.fortrow.jflux.http.api.server.config.APIServerConfig("127.0.0.1", 0, "/api")
                .setServerType(com.fortrow.jflux.http.api.server.types.APIServerType.ENCRYPTED_SYNC)
                .setSessionDb(com.fortrow.jflux.http.api.types.DatabaseServerType.SQLITE,
                        new com.fortrow.jflux.db.connectors.details.DbConnectionSqlLiteDetails(directory.resolve("binding.db").toString()))
                .setSessionResumeStorePath(directory.resolve("binding-state.json"))
                .useDynamicApiKey(true).useWebSocket(0).setWebSocketSslContext(authenticated).setClientAuth(true)
                .setMaxRequestBodyBytes(64 * 1024);
        var service = new com.fortrow.jflux.http.api.base.prereqs.ApiService("binding-test",
                com.fortrow.jflux.http.api.base.prereqs.ApiService.generateAppKey("binding-test", "nonce"));
        service.configureDatabases(encryptedConfig);
        var apiKeys = service.registerApiKeys(com.fortrow.jflux.http.api.server.encryption.APIKeys.generateKeys());
        AtomicReference<String> expectedFingerprint = new AtomicReference<>("00".repeat(32));
        encryptedConfig.setPeerIdentityAuthorizer((apiKey, peer) -> apiKeys.getKey().equals(apiKey)
                && expectedFingerprint.get().equals(peer.leafSha256()));
        encryptedConfig.setBinaryWebSocketEnabled(true);
        var encryptedApi = new com.fortrow.jflux.http.api.server.base.EncryptedAPI(encryptedConfig, service);
        encryptedApi.addResource("bytes", "POST", new com.fortrow.jflux.http.api.server.resources.APIResource() {
            @Override public com.fortrow.jflux.http.api.server.containers.APIResponse execute(
                    com.fortrow.jflux.http.api.base.endpoints.InboundRequest request) {
                return com.fortrow.jflux.http.api.server.containers.APIResponse.binary(request.getSession(),
                        request.getBodyBytes(), com.fortrow.jflux.http.api.server.containers.ResponseCode.SUCCESSFUL);
            }
        });
        encryptedApi.start();
        AtomicReference<byte[]> firstBinaryFrame = new AtomicReference<>();
        var handlerField = FluxWebSocketServer.class.getDeclaredField("handler"); handlerField.setAccessible(true);
        var originalHandler = (FluxWebSocketHandler) handlerField.get(encryptedApi.getServer().getWebSocketServer());
        encryptedApi.getServer().getWebSocketServer().setHandler(new FluxWebSocketHandler() {
            @Override public void onOpen(FluxWebSocketSession session, org.java_websocket.handshake.ClientHandshake handshake) {
                originalHandler.onOpen(session, handshake);
            }
            @Override public void onText(FluxWebSocketSession session, String message) { originalHandler.onText(session, message); }
            @Override public void onBinary(FluxWebSocketSession session, java.nio.ByteBuffer message) {
                byte[] copy = new byte[message.remaining()]; message.asReadOnlyBuffer().get(copy);
                firstBinaryFrame.compareAndSet(null, copy);
                originalHandler.onBinary(session, message);
            }
            @Override public void onClose(FluxWebSocketSession session, int code, String reason, boolean remote) {
                originalHandler.onClose(session, code, reason, remote);
            }
        });
        try {
            long deadline = System.nanoTime() + Duration.ofSeconds(3).toNanos();
            while (!encryptedApi.getServer().isWebSocketStarted() && System.nanoTime() < deadline) Thread.sleep(10);
            String encryptedEndpoint = "wss://localhost:" + encryptedApi.getServer().getWebSocketPort()
                    + encryptedConfig.getWebSocketPath();
            var identityMaterial = com.fortrow.jflux.http.api.client.MtlsConfig.pkcs12(identity,
                    "test-password".toCharArray(), identity, "test-password".toCharArray());
            try (var denied = new com.fortrow.jflux.http.api.client.EncryptedClient(apiKeys.getKey(), apiKeys.getSecret(),
                    apiKeys.getRawSessionSecret(), apiKeys.getPsk(), encryptedEndpoint,
                    com.fortrow.jflux.http.api.server.config.HttpVersion.HTTP_1, false, identityMaterial).build()) {
                assertThrows(com.fortrow.jflux.http.api.server.exceptions.APIException.class,
                        () -> denied.beginSessionOrThrow("/session/init", "/session/close"));
                assertEquals(0, encryptedApi.getServer().getClientSessions().size());
            }
            expectedFingerprint.set(java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                    .digest(store.getCertificate("test").getEncoded())));
            try (var permitted = new com.fortrow.jflux.http.api.client.EncryptedClient(apiKeys.getKey(), apiKeys.getSecret(),
                    apiKeys.getRawSessionSecret(), apiKeys.getPsk(), encryptedEndpoint,
                    com.fortrow.jflux.http.api.server.config.HttpVersion.HTTP_1, false, identityMaterial).build()) {
                permitted.beginSessionOrThrow("/session/init", "/session/close");
                assertEquals(1, encryptedApi.getServer().getClientSessions().size());
                byte[] evidence = {0x46, 0x53, 0x50, 0x31, 0, -1, -128, 13, 10};
                var binaryReply = permitted.postBytes("/bytes", evidence);
                assertEquals(200, binaryReply.getStatus());
                assertTrue(binaryReply.isVerified());
                assertArrayEquals(evidence, binaryReply.getBodyBytes());
                byte[] returned = binaryReply.getBodyBytes(); returned[0] = 0;
                assertArrayEquals(evidence, binaryReply.getBodyBytes());
                var secondReply = permitted.postBytes("/bytes", new byte[0]);
                assertTrue(secondReply.isVerified());
                assertArrayEquals(new byte[0], secondReply.getBodyBytes());
                var liveSession = encryptedApi.getServer().getClientSessions().getFirst().getSession();
                var priorSession = liveSession.snapshot();
                var priorKeys = liveSession.getApiKeys().snapshot();
                try (var oversized = new com.fortrow.jflux.http.api.client.EncryptedWebSocketTransport(URI.create(encryptedEndpoint))
                        .setSslContext(authenticated)) {
                    assertThrows(com.fortrow.jflux.http.api.server.exceptions.APIException.class,
                            () -> oversized.executeBinary("POST", "/bytes", Map.of("X-Api-Key", apiKeys.getKey()),
                                    new byte[64 * 1024 + 1]));
                }
                assertEquals(priorSession.lastRequestSequence(), liveSession.snapshot().lastRequestSequence());
                String correctFingerprint = expectedFingerprint.getAndSet("00".repeat(32));
                try (var spoofed = new com.fortrow.jflux.http.api.client.EncryptedWebSocketTransport(URI.create(encryptedEndpoint))
                        .setSslContext(authenticated)) {
                    assertEquals(401, spoofed.execute("POST", "/session/heartbeat", Map.of(
                            "X-Api-Key", apiKeys.getKey(), "X-Peer-Certificate", correctFingerprint), "{}").status());
                }
                try (var spoofedBinary = new com.fortrow.jflux.http.api.client.EncryptedWebSocketTransport(URI.create(encryptedEndpoint))
                        .setSslContext(authenticated)) {
                    var deniedBinary = spoofedBinary.executeBinary("POST", "/bytes", Map.of("X-Api-Key", apiKeys.getKey(),
                            "X-Peer-Certificate", correctFingerprint), new byte[16]);
                    assertEquals(401, deniedBinary.status());
                }
                assertEquals(priorSession.lastRequestSequence(), liveSession.snapshot().lastRequestSequence());
                assertEquals(priorSession.sessionAuth(), liveSession.snapshot().sessionAuth());
                assertEquals(priorKeys.clientPayloadKey(), liveSession.getApiKeys().getClientPayloadKey());
                assertEquals(priorKeys.clientIv(), liveSession.getApiKeys().getClientIv());
                expectedFingerprint.set(correctFingerprint);
                assertTrue(permitted.closeSessionOrThrow().isVerified());
                permitted.beginSessionOrThrow("/session/init", "/session/close");
                firstBinaryFrame.set(null);
                assertTrue(permitted.postBytes("/bytes", evidence).isVerified());
                byte[] replay = firstBinaryFrame.get(); assertNotNull(replay);
                var replayReply = new java.util.concurrent.CompletableFuture<Integer>();
                try (var replayClient = new FluxWebSocketClient(URI.create(encryptedEndpoint), new com.fortrow.jflux.websockets.client.FluxWebSocketClientHandler() {
                    @Override public void onBinary(FluxWebSocketSession session, java.nio.ByteBuffer message) {
                        replayReply.complete(com.fortrow.jflux.http.api.websocket.EncryptedBinaryWebSocketMessage.decode(message).status());
                    }
                }).setSslContext(authenticated)) {
                    assertTrue(replayClient.connectAndWait(Duration.ofSeconds(3)));
                    replayClient.sendBinary(java.nio.ByteBuffer.wrap(replay));
                    assertEquals(401, replayReply.get(3, TimeUnit.SECONDS));
                }
                // An authenticated stale ratchet invalidates only that logical session.
                permitted.discardSessionState();
            }
        } finally { encryptedApi.stop(); }
    }

    @Test
    void parserRejectsDeclaredOversizedFrameBeforePayloadAllocation() throws Exception {
        var draft = new org.java_websocket.drafts.Draft_6455(List.of(), 1024);
        draft.setParseMode(org.java_websocket.enums.Role.CLIENT);
        // A binary frame declares 65536 payload bytes without supplying its payload.
        var header = java.nio.ByteBuffer.wrap(new byte[]{(byte) 0x82, 127, 0, 0, 0, 0, 0, 1, 0, 0});
        assertThrows(org.java_websocket.exceptions.LimitExceededException.class, () -> draft.translateFrame(header));
        try (FluxWebSocketClient client = new FluxWebSocketClient(URI.create("ws://localhost"), Map.of(), null, 1024)) {
            assertEquals(1024, ((org.java_websocket.drafts.Draft_6455) client.getDraft()).getMaxFrameSize());
        }
    }
}
