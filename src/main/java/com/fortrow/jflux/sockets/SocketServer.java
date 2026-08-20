package com.fortrow.jflux.sockets;

import com.fortrow.jflux.exceptions.ExceptionHandling;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.time.Duration;
import java.util.NoSuchElementException;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/** Virtual-thread-backed line-oriented TCP server base class. */
public abstract class SocketServer implements AutoCloseable {

    public static final int DEFAULT_BACKLOG = 256;

    private final CopyOnWriteArrayList<SocketClient> clients = new CopyOnWriteArrayList<>();
    private final InetSocketAddress bindAddr;
    private final boolean clientEndSession;
    private final int backlog;
    private final AtomicBoolean running = new AtomicBoolean();
    private final CountDownLatch started = new CountDownLatch(1);
    private volatile ServerSocket serverSocket;
    private volatile Thread thread;
    private volatile Throwable failure;

    public SocketServer(String addr, int port, boolean clientEndSession) throws IOException {
        this(addr, port, clientEndSession, DEFAULT_BACKLOG);
    }

    public SocketServer(String addr, int port, boolean clientEndSession, int backlog) throws IOException {
        if (addr == null || addr.isBlank()) throw ExceptionHandling.invalid("Socket bind address is required.");
        if (port < 0 || port > 65_535) throw ExceptionHandling.invalid("Socket port is invalid.");
        if (backlog < 1) throw ExceptionHandling.invalid("Socket backlog must be positive.");
        bindAddr = new InetSocketAddress(InetAddress.getByName(addr), port);
        this.clientEndSession = clientEndSession;
        this.backlog = backlog;
        start();
    }

    private synchronized void start() {
        if (!running.compareAndSet(false, true)) return;
        thread = Thread.ofVirtual().name("jflux-socket-server-").start(this::runServer);
    }

    private void runServer() {
        try {
            create();
        } catch (Throwable throwable) {
            failure = throwable;
            ExceptionHandling.report(throwable);
            running.set(false);
            started.countDown();
            return;
        }
        started.countDown();
        try {
            while (running.get()) {
                SocketClient client;
                try {
                    client = accept();
                } catch (SocketException e) {
                    if (running.get()) {
                        failure = e;
                        ExceptionHandling.report(e);
                    }
                    break;
                }
                clients.add(client);
                Thread.startVirtualThread(() -> handleClient(client));
            }
        } catch (Throwable throwable) {
            if (running.get()) {
                failure = throwable;
                ExceptionHandling.report(throwable);
            }
        } finally {
            running.set(false);
            closeClients();
            closeServerSocket();
        }
    }

    private void create() throws IOException {
        serverSocket = new ServerSocket(bindAddr.getPort(), backlog, bindAddr.getAddress());
    }

    private void handleClient(SocketClient client) {
        try {
            if (clientEndSession) {
                while (running.get() && client.isOpen()) {
                    client.parse(client.read());
                }
            } else {
                client.parse(client.read());
            }
        } catch (NoSuchElementException ignored) {
            // Normal EOF or a client-side close.
        } catch (Throwable throwable) {
            ExceptionHandling.report(throwable);
        } finally {
            clients.remove(client);
            try {
                client.destroy();
            } catch (Exception e) {
                ExceptionHandling.report(e);
            }
        }
    }

    protected abstract void requestIn(SocketClient client, String content);

    protected abstract void clientDestroyed(String uuid);

    private SocketClient accept() throws Exception {
        ServerSocket listener = serverSocket;
        if (listener == null) throw new SocketException("Socket server is not initialized.");
        Socket socket = listener.accept();
        return new SocketClient(socket) {
            @Override
            public void response(String content) {
                requestIn(this, content);
            }

            @Override
            public void onDestroy() {
                SocketServer.this.clientDestroyed(getUuid());
            }
        };
    }

    public boolean awaitStarted(Duration timeout) throws InterruptedException {
        if (timeout == null || timeout.isNegative()) throw ExceptionHandling.invalid("Startup timeout cannot be negative.");
        return started.await(timeout.toMillis(), TimeUnit.MILLISECONDS);
    }

    public boolean isRunning() {
        return running.get();
    }

    public int getClientCount() {
        return clients.size();
    }

    public Throwable getFailure() {
        return failure;
    }

    public int getPort() {
        ServerSocket listener = serverSocket;
        return listener == null ? bindAddr.getPort() : listener.getLocalPort();
    }

    public void interrupt() {
        if (!running.compareAndSet(true, false)) return;
        closeServerSocket();
        closeClients();
        Thread current = thread;
        if (current != null) current.interrupt();
    }

    @Override
    public void close() {
        interrupt();
    }

    private void closeClients() {
        for (SocketClient client : clients) {
            try {
                client.destroy();
            } catch (Exception e) {
                ExceptionHandling.report(e);
            }
        }
        clients.clear();
    }

    private void closeServerSocket() {
        ServerSocket listener = serverSocket;
        if (listener != null && !listener.isClosed()) {
            try {
                listener.close();
            } catch (IOException e) {
                ExceptionHandling.report(e);
            }
        }
    }
}
