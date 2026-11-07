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
    private final TlsPeerIdentity peerIdentity;
    private final Map<String, Object> attributes = new ConcurrentHashMap<>();

    public FluxWebSocketSession(WebSocket socket, String resource) {
        this.socket = socket;
        this.resource = resource == null ? "" : resource;
        this.peerIdentity = snapshotIdentity(socket);
    }

    private static TlsPeerIdentity snapshotIdentity(WebSocket socket) {
        if (socket == null || !socket.hasSSLSupport()) return null;
        javax.net.ssl.SSLSession session = socket.getSSLSession();
        try { session.getPeerCertificates(); }
        catch (javax.net.ssl.SSLPeerUnverifiedException optionalClientAuth) { return null; }
        return TlsPeerIdentity.fromVerifiedSession(session);
    }

    /** Handshake-derived identity, separate from mutable application attributes; null for plaintext WS. */
    public TlsPeerIdentity peerIdentity() { return peerIdentity; }

    public String id() { return id; }

    public String resource() { return resource; }

    public boolean isOpen() { return socket != null && socket.isOpen(); }

    /** Bytes queued by the transport; callers may deny work before adding more buffered output. */
    public long bufferedBytes() {
        WebSocket connection = socket instanceof org.java_websocket.client.WebSocketClient client ? client.getConnection() : socket;
        if (connection instanceof org.java_websocket.WebSocketImpl implementation) {
            long bytes = 0;
            for (ByteBuffer buffer : implementation.outQueue) bytes += buffer.remaining();
            return bytes;
        }
        return connection != null && connection.hasBufferedData() ? Long.MAX_VALUE : 0;
    }

    public InetSocketAddress remoteAddress() {
        return socket == null ? null : socket.getRemoteSocketAddress();
    }

    public InetSocketAddress localAddress() {
        return socket == null ? null : socket.getLocalSocketAddress();
    }

    /** Returns only the certificate chain verified by the actual TLS transport. */
    public java.security.cert.X509Certificate[] verifiedPeerCertificates() {
        if (socket == null || !socket.hasSSLSupport()) return new java.security.cert.X509Certificate[0];
        try {
            java.security.cert.Certificate[] chain = socket.getSSLSession().getPeerCertificates();
            java.security.cert.X509Certificate[] result = new java.security.cert.X509Certificate[chain.length];
            for (int i = 0; i < chain.length; i++) {
                if (!(chain[i] instanceof java.security.cert.X509Certificate certificate)) {
                    throw ExceptionHandling.failure("TLS peer returned a non-X509 certificate.", null);
                }
                result[i] = certificate;
            }
            return result;
        } catch (javax.net.ssl.SSLPeerUnverifiedException e) {
            throw ExceptionHandling.failure("TLS peer identity is not verified.", e);
        }
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
