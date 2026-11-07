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

import org.junit.jupiter.api.Test;
import javax.net.ssl.*;
import java.io.EOFException;
import java.nio.ByteBuffer;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.net.InetSocketAddress;
import java.lang.reflect.Modifier;
import java.util.concurrent.Executors;
import static org.junit.jupiter.api.Assertions.*;

class HandshakeTlsChannelTest {
    @Test void reproducesThePinnedUpstreamAssertionBeforeTheFix() throws Exception {
        assertTrue(org.java_websocket.SSLSocketChannel2.class.desiredAssertionStatus());
        try (var server = ServerSocketChannel.open(); var tasks = Executors.newSingleThreadExecutor()) {
            server.bind(new InetSocketAddress("127.0.0.1", 0));
            try (var client = SocketChannel.open(server.getLocalAddress()); var socket = server.accept()) {
                socket.configureBlocking(false);
                assertThrows(AssertionError.class, () -> new org.java_websocket.SSLSocketChannel2(socket, new TerminalEngine(true), tasks, null));
            }
        }
    }

    @Test void terminalTransitionWithoutFinishedDoesNotKillSelector() throws Exception {
        assertTrue(HandshakeTlsChannel.class.desiredAssertionStatus(), "Run with assertions enabled");
        for (boolean closed : new boolean[]{false, true}) {
            try (var server = ServerSocketChannel.open(); var tasks = Executors.newSingleThreadExecutor()) {
                server.bind(new InetSocketAddress("127.0.0.1", 0));
                try (var client = SocketChannel.open(server.getLocalAddress()); var socket = server.accept()) {
                    socket.configureBlocking(false);
                    var engine = new TerminalEngine(closed);
                    if (closed) assertThrows(EOFException.class, () -> new HandshakeTlsChannel(socket, engine, tasks, null));
                    else {
                        var channel = new HandshakeTlsChannel(socket, engine, tasks, null);
                        assertTrue(channel.isOpen());
                        assertSame(engine, channel.getSSLEngine());
                        channel.close();
                    }
                }
            }
        }
    }

    @Test void closeUsesTheHandshakeMonitor() throws Exception {
        assertTrue(Modifier.isSynchronized(HandshakeTlsChannel.class.getDeclaredMethod("close").getModifiers()));
    }

    private static final class TerminalEngine extends SSLEngine {
        final SSLEngine delegate;
        final boolean closed;
        int wraps;
        TerminalEngine(boolean closed) throws Exception { this.closed = closed; delegate = SSLContext.getDefault().createSSLEngine(); }
        @Override public SSLEngineResult wrap(ByteBuffer[] src, int off, int len, ByteBuffer dst) {
            wraps++;
            return new SSLEngineResult(closed && wraps >= 2 ? SSLEngineResult.Status.CLOSED : SSLEngineResult.Status.OK,
                    getHandshakeStatus(), 0, 0);
        }
        @Override public SSLEngineResult unwrap(ByteBuffer src, ByteBuffer[] dst, int off, int len) { throw new UnsupportedOperationException(); }
        @Override public SSLEngineResult.HandshakeStatus getHandshakeStatus() { return wraps < 2 ? SSLEngineResult.HandshakeStatus.NEED_WRAP : SSLEngineResult.HandshakeStatus.NOT_HANDSHAKING; }
        @Override public boolean isInboundDone() { return closed && wraps >= 2; }
        @Override public boolean isOutboundDone() { return closed && wraps >= 2; }
        @Override public SSLSession getSession() { return delegate.getSession(); }
        @Override public Runnable getDelegatedTask() { return null; }
        @Override public void closeInbound() { }
        @Override public void closeOutbound() { }
        @Override public void beginHandshake() { }
        @Override public String[] getSupportedCipherSuites() { return delegate.getSupportedCipherSuites(); }
        @Override public String[] getEnabledCipherSuites() { return delegate.getEnabledCipherSuites(); }
        @Override public void setEnabledCipherSuites(String[] v) { delegate.setEnabledCipherSuites(v); }
        @Override public String[] getSupportedProtocols() { return delegate.getSupportedProtocols(); }
        @Override public String[] getEnabledProtocols() { return delegate.getEnabledProtocols(); }
        @Override public void setEnabledProtocols(String[] v) { delegate.setEnabledProtocols(v); }
        @Override public void setUseClientMode(boolean v) { delegate.setUseClientMode(v); }
        @Override public boolean getUseClientMode() { return delegate.getUseClientMode(); }
        @Override public void setNeedClientAuth(boolean v) { delegate.setNeedClientAuth(v); }
        @Override public boolean getNeedClientAuth() { return delegate.getNeedClientAuth(); }
        @Override public void setWantClientAuth(boolean v) { delegate.setWantClientAuth(v); }
        @Override public boolean getWantClientAuth() { return delegate.getWantClientAuth(); }
        @Override public void setEnableSessionCreation(boolean v) { delegate.setEnableSessionCreation(v); }
        @Override public boolean getEnableSessionCreation() { return delegate.getEnableSessionCreation(); }
    }
}
