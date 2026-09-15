package com.fortrow.jflux.websockets.server;

import com.fortrow.jflux.exceptions.ExceptionHandling;
import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import javax.net.ssl.SSLContext;
import org.java_websocket.server.DefaultSSLWebSocketServerFactory;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Small transport-level WebSocket server suitable for future REST and encrypted
 * API protocols. Authentication, routing, and message envelopes belong in the
 * injected handler rather than in this transport class.
 */
public class FluxWebSocketServer extends WebSocketServer implements AutoCloseable {

    public static final int DEFAULT_MAX_MESSAGE_BYTES = 4 * 1024 * 1024;

    private final Map<WebSocket, FluxWebSocketSession> sessions = new ConcurrentHashMap<>();
    private volatile FluxWebSocketHandler handler;
    private volatile int maxMessageBytes = DEFAULT_MAX_MESSAGE_BYTES;
    private volatile boolean started;
    private volatile SSLContext sslContext;

    public FluxWebSocketServer(String host, int port) throws UnknownHostException {
        this(host, port, new FluxWebSocketHandler() { });
    }

    public FluxWebSocketServer(String host, int port, FluxWebSocketHandler handler) throws UnknownHostException {
        super(new InetSocketAddress(InetAddress.getByName(validateHost(host)), validatePort(port)));
        this.handler = handler == null ? new FluxWebSocketHandler() { } : handler;
    }

    public FluxWebSocketServer(InetSocketAddress address, FluxWebSocketHandler handler) {
        super(validateAddress(address));
        this.handler = handler == null ? new FluxWebSocketHandler() { } : handler;
    }

    public FluxWebSocketServer(String host, int port, FluxWebSocketHandler handler, SSLContext sslContext)
            throws UnknownHostException {
        this(host, port, handler);
        setSslContext(sslContext);
    }

    public FluxWebSocketServer setHandler(FluxWebSocketHandler handler) {
        this.handler = handler == null ? new FluxWebSocketHandler() { } : handler;
        return this;
    }

    public FluxWebSocketServer setMaxMessageBytes(int maxMessageBytes) {
        if (maxMessageBytes < 1) throw ExceptionHandling.invalid("Maximum WebSocket message size must be positive.");
        this.maxMessageBytes = maxMessageBytes;
        return this;
    }

    public int getMaxMessageBytes() { return maxMessageBytes; }

    /** Configures WSS before {@link #start()} is called. */
    public FluxWebSocketServer setSslContext(SSLContext sslContext) {
        if (started) throw ExceptionHandling.invalid("TLS configuration must be set before the WebSocket server starts.");
        if (sslContext == null) throw ExceptionHandling.invalid("WebSocket SSL context is required.");
        this.sslContext = sslContext;
        setWebSocketFactory(new DefaultSSLWebSocketServerFactory(sslContext));
        return this;
    }

    public boolean isTlsEnabled() { return sslContext != null; }

    public boolean isStarted() { return started; }

    public List<FluxWebSocketSession> sessions() { return List.copyOf(sessions.values()); }

    public FluxWebSocketSession session(WebSocket socket) { return socket == null ? null : sessions.get(socket); }

    public void broadcastText(String message) {
        if (message == null) throw ExceptionHandling.invalid("WebSocket broadcast message is required.");
        broadcast(message);
    }

    @Override
    public void onOpen(WebSocket webSocket, ClientHandshake clientHandshake) {
        FluxWebSocketSession session = new FluxWebSocketSession(webSocket,
                clientHandshake == null ? "" : clientHandshake.getResourceDescriptor());
        sessions.put(webSocket, session);
        dispatch(() -> handler.onOpen(session, clientHandshake), session);
    }

    @Override
    public void onClose(WebSocket webSocket, int code, String reason, boolean remote) {
        FluxWebSocketSession session = sessions.remove(webSocket);
        if (session != null) dispatch(() -> handler.onClose(session, code, reason, remote), session);
    }

    @Override
    public void onMessage(WebSocket webSocket, String message) {
        FluxWebSocketSession session = sessions.get(webSocket);
        if (session == null) return;
        if (message == null || message.getBytes(StandardCharsets.UTF_8).length > maxMessageBytes) {
            session.close(1009, "Message exceeds the configured maximum size.");
            return;
        }
        dispatch(() -> handler.onText(session, message), session);
    }

    @Override
    public void onMessage(WebSocket webSocket, ByteBuffer message) {
        FluxWebSocketSession session = sessions.get(webSocket);
        if (session == null || message == null) return;
        if (message.remaining() > maxMessageBytes) {
            session.close(1009, "Message exceeds the configured maximum size.");
            return;
        }
        ByteBuffer copy = message.asReadOnlyBuffer();
        dispatch(() -> handler.onBinary(session, copy), session);
    }

    @Override
    public void onError(WebSocket webSocket, Exception error) {
        FluxWebSocketSession session = sessions.get(webSocket);
        ExceptionHandling.report(error);
        if (session != null) dispatch(() -> handler.onError(session, error), session);
    }

    @Override
    public void onStart() {
        started = true;
        dispatch(handler::onStart, null);
    }

    @Override
    public void close() {
        started = false;
        for (FluxWebSocketSession session : sessions.values()) session.close();
        sessions.clear();
        try {
            stop();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            ExceptionHandling.report(e);
        }
    }

    private void dispatch(Runnable callback, FluxWebSocketSession session) {
        try {
            callback.run();
        } catch (Throwable error) {
            ExceptionHandling.report(error);
            if (session != null && session.isOpen()) session.close(1011, "WebSocket handler failure.");
        }
    }

    private static String validateHost(String host) {
        if (host == null || host.isBlank()) throw ExceptionHandling.invalid("WebSocket host is required.");
        return host;
    }

    private static int validatePort(int port) {
        if (port < 0 || port > 65535) throw ExceptionHandling.invalid("WebSocket port must be between 0 and 65535.");
        return port;
    }

    private static InetSocketAddress validateAddress(InetSocketAddress address) {
        if (address == null) throw ExceptionHandling.invalid("WebSocket bind address is required.");
        validatePort(address.getPort());
        return address;
    }
}
