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
