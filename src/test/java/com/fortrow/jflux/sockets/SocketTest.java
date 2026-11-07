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

package com.fortrow.jflux.sockets;

import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class SocketTest {

    @Test
    void serverProcessesUtf8LineAndClosesClient() throws Exception {
        EchoServer server = new EchoServer();
        try {
            assertTrue(server.awaitStarted(Duration.ofSeconds(2)));
            assertTrue(server.isRunning());

            try (Socket socket = new Socket("127.0.0.1", server.getPort())) {
                socket.setSoTimeout(2_000);
                BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
                PrintWriter writer = new PrintWriter(new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8)), true);
                writer.println("héllo");
                assertEquals("ack:héllo", reader.readLine());
            }

            assertTrue(server.received.await(2, TimeUnit.SECONDS));
            assertEquals("héllo", server.message);
        } finally {
            server.close();
        }
        assertFalse(server.isRunning());
    }

    @Test
    void callbackReaderInvokesDestroyOnlyOnce() throws Exception {
        AtomicInteger callbacks = new AtomicInteger();
        CallbackBufferedReader reader = new CallbackBufferedReader(new java.io.StringReader("value")) {
            @Override
            public void onDestroy() {
                callbacks.incrementAndGet();
            }
        };

        reader.close();
        reader.close();

        assertEquals(1, callbacks.get());
    }

    private static final class EchoServer extends SocketServer {
        private final CountDownLatch received = new CountDownLatch(1);
        private final AtomicInteger destroyed = new AtomicInteger();
        private volatile String message;

        private EchoServer() throws java.io.IOException {
            super("127.0.0.1", 0, false);
        }

        @Override
        protected void requestIn(SocketClient client, String content) {
            message = content;
            client.write("ack:" + content);
            received.countDown();
        }

        @Override
        protected void clientDestroyed(String uuid) {
            destroyed.incrementAndGet();
        }
    }
}
