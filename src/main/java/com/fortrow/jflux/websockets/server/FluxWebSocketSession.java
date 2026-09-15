package com.fortrow.jflux.websockets.server;

import com.fortrow.jflux.exceptions.ExceptionHandling;
import org.java_websocket.WebSocket;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Thread-safe connection wrapper intended for future API authentication state. */
public final class FluxWebSocketSession {

    private final String id = UUID.randomUUID().toString();
    private final WebSocket socket;
    private final String resource;
    private final Map<String, Object> attributes = new ConcurrentHashMap<>();

    public FluxWebSocketSession(WebSocket socket, String resource) {
        this.socket = socket;
        this.resource = resource == null ? "" : resource;
    }

    public String id() { return id; }

    public String resource() { return resource; }

    public boolean isOpen() { return socket != null && socket.isOpen(); }

    public InetSocketAddress remoteAddress() {
        return socket == null ? null : socket.getRemoteSocketAddress();
    }

    public InetSocketAddress localAddress() {
        return socket == null ? null : socket.getLocalSocketAddress();
    }

    public void sendText(String message) {
        if (message == null) throw ExceptionHandling.invalid("WebSocket text message is required.");
        if (!isOpen()) throw ExceptionHandling.failure("WebSocket session is not open.", null);
        socket.send(message);
    }

    public void sendBinary(ByteBuffer message) {
        if (message == null) throw ExceptionHandling.invalid("WebSocket binary message is required.");
        if (!isOpen()) throw ExceptionHandling.failure("WebSocket session is not open.", null);
        socket.send(message.asReadOnlyBuffer());
    }

    public void close() { close(1000, ""); }

    public void close(int code, String reason) {
        if (socket != null && !socket.isClosed()) socket.close(code, reason == null ? "" : reason);
    }

    public void setAttribute(String key, Object value) {
        if (key == null || key.isBlank()) throw ExceptionHandling.invalid("WebSocket session attribute key is required.");
        if (value == null) attributes.remove(key);
        else attributes.put(key, value);
    }

    public Object attribute(String key) { return key == null ? null : attributes.get(key); }

    public <T> T attribute(String key, Class<T> type) {
        Object value = attribute(key);
        return type != null && type.isInstance(value) ? type.cast(value) : null;
    }

    public Map<String, Object> attributes() { return Map.copyOf(attributes); }

    WebSocket socket() { return socket; }
}
