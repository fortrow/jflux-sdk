package com.fortrow.jflux.http.api.client;

import com.fortrow.jflux.exceptions.ExceptionHandling;
import com.fortrow.jflux.http.api.server.exceptions.APIException;
import com.fortrow.jflux.http.api.websocket.EncryptedWebSocketMessage;
import com.fortrow.jflux.websockets.client.FluxWebSocketClient;
import com.fortrow.jflux.websockets.server.FluxWebSocketSession;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.net.URI;
import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Request/response transport used by {@link EncryptedClient} for ws:// and
 * wss:// endpoints. It does not implement API authentication itself; the
 * existing encrypted client creates the same signed HTTP-shaped request and
 * response that it uses for HTTP/1 and HTTP/3.
 */
public final class EncryptedWebSocketTransport implements AutoCloseable {

    public static final Duration DEFAULT_REQUEST_TIMEOUT = Duration.ofSeconds(30);

    public record Response(int status, Map<String, String> headers, String body) { }
    public record BinaryResponse(int status, Map<String, String> headers, byte[] ciphertext) {
        public BinaryResponse { headers = Map.copyOf(headers); ciphertext = ciphertext.clone(); }
        @Override public byte[] ciphertext() { return ciphertext.clone(); }
    }
    private final ConcurrentHashMap<UUID, CompletableFuture<com.fortrow.jflux.http.api.websocket.EncryptedBinaryWebSocketMessage.Message>> binaryPending = new ConcurrentHashMap<>();

    private final FluxWebSocketClient client;
    private final ConcurrentHashMap<String, CompletableFuture<EncryptedWebSocketMessage.Response>> pending = new ConcurrentHashMap<>();
    private volatile Duration requestTimeout = DEFAULT_REQUEST_TIMEOUT;
    private volatile boolean closed;

    public EncryptedWebSocketTransport(URI endpoint) {
        this.client = new FluxWebSocketClient(endpoint, new ClientHandler());
    }

    public URI endpoint() { return client.endpoint(); }

    public boolean isOpen() { return client.isOpen(); }

    public EncryptedWebSocketTransport setRequestTimeout(Duration timeout) {
        if (timeout == null || timeout.isZero() || timeout.isNegative()) {
            throw ExceptionHandling.invalid("WebSocket request timeout must be positive.");
        }
        requestTimeout = timeout;
        return this;
    }

    public Duration requestTimeout() { return requestTimeout; }

    public EncryptedWebSocketTransport setMaxMessageBytes(int maxMessageBytes) {
        client.setMaxMessageBytes(maxMessageBytes);
        return this;
    }

    public EncryptedWebSocketTransport setSslContext(SSLContext sslContext) {
        client.setSslContext(sslContext);
        return this;
    }

    /** Explicitly enables the opt-in trust-all behavior used by legacy test deployments. */
    public EncryptedWebSocketTransport allowSelfSigned() {
        if (!"wss".equalsIgnoreCase(endpoint().getScheme())) {
            throw ExceptionHandling.invalid("Self-signed TLS is only available for a wss endpoint.");
        }
        try {
            client.setSslContext(trustAllContext());
            return this;
        } catch (Exception e) {
            throw ExceptionHandling.failure("Unable to initialize WebSocket TLS trust configuration.", e);
        }
    }

    public boolean connect(Duration timeout) {
        if (closed) throw ExceptionHandling.failure("Encrypted WebSocket transport is closed.", null);
        return client.connectAndWait(timeout);
    }

    public boolean connect() { return connect(FluxWebSocketClient.DEFAULT_CONNECT_TIMEOUT); }

    public Response execute(String method, String path, Map<String, String> headers, String body) throws APIException {
        if (closed) throw new APIException("Encrypted WebSocket transport is closed.");
        if (!client.isOpen() && !connect()) throw new APIException("Unable to connect to the encrypted API WebSocket server.");

        String requestId = UUID.randomUUID().toString();
        CompletableFuture<EncryptedWebSocketMessage.Response> result = new CompletableFuture<>();
        pending.put(requestId, result);
        try {
            if (closed) throw new APIException("Encrypted WebSocket transport is closed.");
            client.sendText(EncryptedWebSocketMessage.request(requestId, method, path, headers, body));
            EncryptedWebSocketMessage.Response response = result.get(Math.max(1L, requestTimeout.toMillis()), TimeUnit.MILLISECONDS);
            return new Response(response.status(), response.headers(), response.body());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new APIException("Interrupted while waiting for the encrypted WebSocket response.", e);
        } catch (TimeoutException e) {
            throw new APIException("Timed out waiting for the encrypted WebSocket response.", e);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause() == null ? e : e.getCause();
            throw new APIException("Encrypted WebSocket request failed.", cause);
        } finally {
            pending.remove(requestId);
        }
    }

    /** Sends bounded ciphertext bytes; API authentication is owned by EncryptedClient. */
    public BinaryResponse executeBinary(String method, String path, Map<String, String> headers, byte[] ciphertext) throws APIException {
        if (closed) throw new APIException("Encrypted WebSocket transport is closed.");
        if (!"wss".equalsIgnoreCase(endpoint().getScheme())) throw new APIException("Binary encrypted API requires WSS.");
        if (!client.isOpen() && !connect()) throw new APIException("Unable to connect to binary encrypted API.");
        UUID id = UUID.randomUUID();
        byte[] frame = com.fortrow.jflux.http.api.websocket.EncryptedBinaryWebSocketMessage.encode(
                new com.fortrow.jflux.http.api.websocket.EncryptedBinaryWebSocketMessage.Message(id, false, 0, method, path, headers, ciphertext));
        var result = new CompletableFuture<com.fortrow.jflux.http.api.websocket.EncryptedBinaryWebSocketMessage.Message>();
        binaryPending.put(id, result);
        try {
            if (closed) throw new APIException("Encrypted WebSocket transport is closed.");
            client.sendBinary(ByteBuffer.wrap(frame));
            var response = result.get(Math.max(1L, requestTimeout.toMillis()), TimeUnit.MILLISECONDS);
            return new BinaryResponse(response.status(), response.headers(), response.ciphertext());
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt(); throw new APIException("Interrupted waiting for binary API response.", error);
        } catch (TimeoutException | ExecutionException error) {
            throw new APIException("Binary API exchange failed.", error);
        } finally { binaryPending.remove(id); }
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        if (!pending.isEmpty()) {
            APIException failure = new APIException("Encrypted WebSocket transport closed while a request was pending.");
            pending.forEach((id, future) -> future.completeExceptionally(failure));
        }
        pending.clear();
        binaryPending.forEach((id, future) -> future.completeExceptionally(new APIException("Binary WebSocket transport closed.")));
        binaryPending.clear();
        client.close();
    }

    private final class ClientHandler implements com.fortrow.jflux.websockets.client.FluxWebSocketClientHandler {
        @Override
        public void onText(FluxWebSocketSession session, String message) {
            try {
                EncryptedWebSocketMessage.Response response = EncryptedWebSocketMessage.parseResponse(message);
                CompletableFuture<EncryptedWebSocketMessage.Response> future = pending.get(response.requestId());
                if (future != null) future.complete(response);
            } catch (RuntimeException e) {
                ExceptionHandling.report(e);
                failPending(new APIException("Encrypted API WebSocket returned an invalid response frame.", e));
                if (session != null && session.isOpen()) session.close(1008, "Invalid encrypted API WebSocket response.");
            }
        }

        @Override
        public void onBinary(FluxWebSocketSession session, ByteBuffer message) {
            try {
                var response = com.fortrow.jflux.http.api.websocket.EncryptedBinaryWebSocketMessage.decode(message);
                if (!response.response()) throw ExceptionHandling.invalid("Binary API response frame required.");
                var future = binaryPending.get(response.requestId());
                if (future != null) future.complete(response);
                else throw ExceptionHandling.invalid("Unexpected binary API response ID.");
            } catch (RuntimeException error) {
                failPending(new APIException("Invalid binary API response.", error));
                if (session != null && session.isOpen()) session.close(1008, "Invalid binary API response.");
            }
        }

        @Override
        public void onClose(FluxWebSocketSession session, int code, String reason, boolean remote) {
            if (!pending.isEmpty() || !binaryPending.isEmpty()) {
                APIException failure = new APIException("Encrypted API WebSocket closed: " + code + " " + (reason == null ? "" : reason));
                failPending(failure);
            }
        }

        @Override
        public void onError(FluxWebSocketSession session, Exception error) {
            if (!pending.isEmpty() || !binaryPending.isEmpty()) failPending(new APIException("Encrypted API WebSocket transport failed.", error));
        }
    }

    private void failPending(APIException failure) {
        pending.forEach((id, future) -> future.completeExceptionally(failure));
        binaryPending.forEach((id, future) -> future.completeExceptionally(failure));
    }

    private static SSLContext trustAllContext() throws Exception {
        TrustManager[] managers = {new X509TrustManager() {
            public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
            public void checkClientTrusted(X509Certificate[] chain, String authType) { }
            public void checkServerTrusted(X509Certificate[] chain, String authType) { }
        }};
        SSLContext context = SSLContext.getInstance("TLS");
        context.init(null, managers, new SecureRandom());
        return context;
    }
}
