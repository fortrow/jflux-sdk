package com.fortrow.jflux.websockets.client;

import com.fortrow.jflux.exceptions.ExceptionHandling;
import com.fortrow.jflux.websockets.server.FluxWebSocketSession;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.SSLContext;

/**
 * Protocol-neutral JFlux WebSocket client matching FluxWebSocketServer.
 * Authentication and REST/encrypted envelopes belong in the injected handler.
 */
public class FluxWebSocketClient extends WebSocketClient implements AutoCloseable {

    public static final Duration DEFAULT_CONNECT_TIMEOUT = Duration.ofSeconds(10);
    public static final int DEFAULT_MAX_MESSAGE_BYTES = 4 * 1024 * 1024;

    private final URI endpoint;
    private volatile FluxWebSocketClientHandler handler;
    private volatile FluxWebSocketSession session;
    private volatile int maxMessageBytes = DEFAULT_MAX_MESSAGE_BYTES;

    public FluxWebSocketClient(URI endpoint) {
        this(endpoint, Map.of(), null);
    }

    public FluxWebSocketClient(URI endpoint, FluxWebSocketClientHandler handler) {
        this(endpoint, Map.of(), handler);
    }

    public FluxWebSocketClient(URI endpoint, Map<String, String> headers,
                               FluxWebSocketClientHandler handler) {
        this(endpoint, headers, handler, DEFAULT_MAX_MESSAGE_BYTES);
    }

    /** Sets the parser allocation bound before any connection is opened. */
    public FluxWebSocketClient(URI endpoint, Map<String, String> headers,
                               FluxWebSocketClientHandler handler, int parserLimitBytes) {
        super(validateEndpoint(endpoint), new org.java_websocket.drafts.Draft_6455(
                java.util.List.of(), parserLimitBytes), headers == null ? Map.of() : Map.copyOf(headers));
        if (parserLimitBytes < 1) throw ExceptionHandling.invalid("Parser message limit must be positive.");
        this.maxMessageBytes = parserLimitBytes;
        this.endpoint = endpoint;
        this.handler = handler == null ? new FluxWebSocketClientHandler() { } : handler;
    }

    public FluxWebSocketClient setHandler(FluxWebSocketClientHandler handler) {
        this.handler = handler == null ? new FluxWebSocketClientHandler() { } : handler;
        return this;
    }

    public FluxWebSocketClient setMaxMessageBytes(int maxMessageBytes) {
        if (maxMessageBytes < 1) throw ExceptionHandling.invalid("Maximum WebSocket message size must be positive.");
        this.maxMessageBytes = maxMessageBytes;
        return this;
    }

    public int getMaxMessageBytes() { return maxMessageBytes; }

    public URI endpoint() { return endpoint; }

    /** Configures a custom client TLS context for a wss endpoint before connecting. */
    public FluxWebSocketClient setSslContext(SSLContext sslContext) {
        if (!"wss".equalsIgnoreCase(endpoint.getScheme())) {
            throw ExceptionHandling.invalid("A custom SSL context requires a wss endpoint.");
        }
        if (isOpen()) throw ExceptionHandling.invalid("TLS configuration must be set before the WebSocket client connects.");
        if (sslContext == null) throw ExceptionHandling.invalid("WebSocket SSL context is required.");
        setSocketFactory(sslContext.getSocketFactory());
        return this;
    }

    public FluxWebSocketSession session() { return session; }

    public boolean connect(Duration timeout) {
        if (timeout == null || timeout.isZero() || timeout.isNegative()) {
            throw ExceptionHandling.invalid("WebSocket connect timeout must be positive.");
        }
        try {
            return connectBlocking(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            ExceptionHandling.report(e);
            return false;
        }
    }

    /** Connects and waits using the supplied timeout. */
    public boolean connectAndWait(Duration timeout) { return connect(timeout); }

    /** Connects and waits using the default timeout. */
    public boolean connectAndWait() { return connect(DEFAULT_CONNECT_TIMEOUT); }

    public void sendText(String message) {
        FluxWebSocketSession current = requireSession();
        if (message == null) throw ExceptionHandling.invalid("WebSocket text message is required.");
        if (message.getBytes(StandardCharsets.UTF_8).length > maxMessageBytes) {
            throw ExceptionHandling.invalid("WebSocket message exceeds the configured maximum size.");
        }
        current.sendText(message);
    }

    public void sendBinary(ByteBuffer message) {
        FluxWebSocketSession current = requireSession();
        if (message == null) throw ExceptionHandling.invalid("WebSocket binary message is required.");
        if (message.remaining() > maxMessageBytes) {
            throw ExceptionHandling.invalid("WebSocket message exceeds the configured maximum size.");
        }
        current.sendBinary(message);
    }

    @Override
    public void onOpen(ServerHandshake handshake) {
        session = new FluxWebSocketSession(this, endpoint.getPath());
        dispatch(() -> handler.onOpen(session, handshake));
    }

    @Override
    public void onMessage(String message) {
        FluxWebSocketSession current = session;
        if (current == null) return;
        if (message == null || message.getBytes(StandardCharsets.UTF_8).length > maxMessageBytes) {
            current.close(1009, "Message exceeds the configured maximum size.");
            return;
        }
        dispatch(() -> handler.onText(current, message));
    }

    @Override
    public void onMessage(ByteBuffer message) {
        FluxWebSocketSession current = session;
        if (current == null || message == null) return;
        if (message.remaining() > maxMessageBytes) {
            current.close(1009, "Message exceeds the configured maximum size.");
            return;
        }
        ByteBuffer copy = message.asReadOnlyBuffer();
        dispatch(() -> handler.onBinary(current, copy));
    }

    @Override
    public void onClose(int code, String reason, boolean remote) {
        FluxWebSocketSession current = session;
        session = null;
        if (current != null) dispatch(() -> handler.onClose(current, code, reason, remote));
    }

    @Override
    public void onError(Exception error) {
        ExceptionHandling.report(error);
        FluxWebSocketSession current = session;
        if (current != null) dispatch(() -> handler.onError(current, error));
    }

    @Override
    public void close() {
        FluxWebSocketSession current = session;
        if (current != null) current.close();
        else super.close();
    }

    private FluxWebSocketSession requireSession() {
        FluxWebSocketSession current = session;
        if (current == null || !current.isOpen()) throw ExceptionHandling.failure("WebSocket client is not connected.", null);
        return current;
    }

    private void dispatch(Runnable callback) {
        try {
            callback.run();
        } catch (Throwable error) {
            ExceptionHandling.report(error);
            FluxWebSocketSession current = session;
            if (current != null && current.isOpen()) current.close(1011, "WebSocket handler failure.");
        }
    }

    private static URI validateEndpoint(URI endpoint) {
        if (endpoint == null || endpoint.getHost() == null || endpoint.getHost().isBlank()) {
            throw ExceptionHandling.invalid("WebSocket endpoint URI with a host is required.");
        }
        String scheme = endpoint.getScheme();
        if (!"ws".equalsIgnoreCase(scheme) && !"wss".equalsIgnoreCase(scheme)) {
            throw ExceptionHandling.invalid("WebSocket endpoint must use ws or wss.");
        }
        return endpoint;
    }
}
