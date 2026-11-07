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

import com.fortrow.jflux.websockets.client.FluxWebSocketClient;
import com.fortrow.jflux.websockets.client.FluxWebSocketClientHandler;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.junit.jupiter.api.Test;

import java.net.URI;
import javax.net.ssl.SSLContext;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class FluxWebSocketServerTest {

    @Test
    void serverDispatchesLifecycleAndUtf8TextWithoutSharedMutableState() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch opened = new CountDownLatch(1);
        CountDownLatch received = new CountDownLatch(1);
        AtomicReference<FluxWebSocketSession> session = new AtomicReference<>();
        AtomicReference<String> message = new AtomicReference<>();

        FluxWebSocketServer server = new FluxWebSocketServer("127.0.0.1", 0, new FluxWebSocketHandler() {
            @Override public void onStart() { started.countDown(); }
            @Override public void onOpen(FluxWebSocketSession value, org.java_websocket.handshake.ClientHandshake handshake) {
                session.set(value);
                value.setAttribute("role", "timing-client");
                opened.countDown();
            }
            @Override public void onText(FluxWebSocketSession value, String valueText) {
                message.set(valueText);
                value.sendText("ack:" + valueText);
                received.countDown();
            }
        }).setMaxMessageBytes(1024);

        WebSocketClient client = null;
        try {
            server.start();
            assertTrue(started.await(2, TimeUnit.SECONDS));
            client = new WebSocketClient(URI.create("ws://127.0.0.1:" + server.getPort() + "/timing")) {
                @Override public void onOpen(ServerHandshake handshake) { send("héllo"); }
                @Override public void onMessage(String value) { }
                @Override public void onClose(int code, String reason, boolean remote) { }
                @Override public void onError(Exception error) { }
            };
            assertTrue(client.connectBlocking(2, TimeUnit.SECONDS));
            assertTrue(opened.await(2, TimeUnit.SECONDS));
            assertTrue(received.await(2, TimeUnit.SECONDS));
            assertEquals("héllo", message.get());
            assertNotNull(session.get());
            assertEquals("timing-client", session.get().attribute("role", String.class));
            assertEquals("/timing", session.get().resource());
            assertEquals(1, server.sessions().size());
        } finally {
            if (client != null) client.closeBlocking();
            server.close();
        }
        assertTrue(server.sessions().isEmpty());
    }

    @Test
    void oversizedTextIsRejectedBeforeHandlerDispatch() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch handled = new CountDownLatch(1);
        FluxWebSocketServer server = new FluxWebSocketServer("127.0.0.1", 0, new FluxWebSocketHandler() {
            @Override public void onStart() { started.countDown(); }
            @Override public void onText(FluxWebSocketSession session, String message) { handled.countDown(); }
        }).setMaxMessageBytes(4);
        WebSocketClient client = null;
        try {
            server.start();
            assertTrue(started.await(2, TimeUnit.SECONDS));
            client = new WebSocketClient(URI.create("ws://127.0.0.1:" + server.getPort())) {
                @Override public void onOpen(ServerHandshake handshake) { send("ééé"); }
                @Override public void onMessage(String message) { }
                @Override public void onClose(int code, String reason, boolean remote) { }
                @Override public void onError(Exception error) { }
            };
            assertTrue(client.connectBlocking(2, TimeUnit.SECONDS));
            assertFalse(handled.await(500, TimeUnit.MILLISECONDS));
        } finally {
            if (client != null) client.closeBlocking();
            server.close();
        }
    }

    @Test
    void messageLimitAndBindInputsAreValidated() throws Exception {
        FluxWebSocketServer server = new FluxWebSocketServer("127.0.0.1", 0);
        try {
            assertThrows(IllegalArgumentException.class, () -> server.setMaxMessageBytes(0));
            assertEquals(FluxWebSocketServer.DEFAULT_MAX_MESSAGE_BYTES, server.getMaxMessageBytes());
        } finally {
            server.close();
        }
    }

    @Test
    void clientUsesTheSameSessionAndMessageContract() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch received = new CountDownLatch(1);
        AtomicReference<String> response = new AtomicReference<>();
        FluxWebSocketServer server = new FluxWebSocketServer("127.0.0.1", 0, new FluxWebSocketHandler() {
            @Override public void onStart() { started.countDown(); }
            @Override public void onText(FluxWebSocketSession session, String message) { session.sendText("ack:" + message); }
        });
        FluxWebSocketClient client = null;
        try {
            server.start();
            assertTrue(started.await(2, TimeUnit.SECONDS));
            client = new FluxWebSocketClient(
                    URI.create("ws://127.0.0.1:" + server.getPort() + "/api"),
                    new FluxWebSocketClientHandler() {
                        @Override public void onText(FluxWebSocketSession session, String message) {
                            response.set(message);
                            received.countDown();
                        }
                    });
            assertTrue(client.connectAndWait());
            client.sendText("request");
            assertTrue(received.await(2, TimeUnit.SECONDS));
            assertEquals("ack:request", response.get());
            assertNotNull(client.session());
            assertEquals("/api", client.session().resource());
        } finally {
            if (client != null) client.close();
            server.close();
        }
    }

    @Test
    void clientRejectsNonWebSocketEndpoints() {
        assertThrows(IllegalArgumentException.class, () -> new FluxWebSocketClient(URI.create("http://localhost/api")));
    }

    @Test
    void serverAndClientExposeExplicitTlsConfiguration() throws Exception {
        SSLContext context = SSLContext.getDefault();
        FluxWebSocketServer server = new FluxWebSocketServer("127.0.0.1", 0,
                new FluxWebSocketHandler() { }, context);
        try {
            assertTrue(server.isTlsEnabled());
            FluxWebSocketClient client = new FluxWebSocketClient(URI.create("wss://localhost/api"));
            assertSame(client, client.setSslContext(context));
            client.close();
        } finally {
            server.close();
        }
    }
}
