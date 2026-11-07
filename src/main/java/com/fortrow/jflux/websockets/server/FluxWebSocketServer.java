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
        this(new InetSocketAddress(InetAddress.getByName(validateHost(host)), validatePort(port)), handler);
    }

    public FluxWebSocketServer(InetSocketAddress address, FluxWebSocketHandler handler) {
        this(address, handler, DEFAULT_MAX_MESSAGE_BYTES);
    }

    /** Sets the parser allocation bound before any connection is accepted. */
    public FluxWebSocketServer(InetSocketAddress address, FluxWebSocketHandler handler, int parserLimitBytes) {
        super(validateAddress(address), List.of(new org.java_websocket.drafts.Draft_6455(List.of(), parserLimitBytes)));
        if (parserLimitBytes < 1) throw ExceptionHandling.invalid("Parser message limit must be positive.");
        this.maxMessageBytes = parserLimitBytes;
        this.handler = handler == null ? new FluxWebSocketHandler() { } : handler;
        setReuseAddr(true);
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
        return setSslContext(sslContext, false);
    }

    /** Enables mandatory TLS client certificates when requireClientCertificate is true. */
    public FluxWebSocketServer setSslContext(SSLContext sslContext, boolean requireClientCertificate) {
        if (started) throw ExceptionHandling.invalid("TLS configuration must be set before the WebSocket server starts.");
        if (sslContext == null) throw ExceptionHandling.invalid("WebSocket SSL context is required.");
        this.sslContext = sslContext;
        setWebSocketFactory(requireClientCertificate ? new ClientAuthWebSocketServerFactory(sslContext)
                : new DefaultSSLWebSocketServerFactory(sslContext));
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
        FluxWebSocketSession session = webSocket == null ? null : sessions.get(webSocket);
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
