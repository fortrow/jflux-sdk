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

import com.fortrow.jflux.exceptions.ExceptionHandling;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/** Line-oriented TCP client base class with explicit, idempotent lifecycle handling. */
public abstract class SocketClient implements AutoCloseable {

    protected volatile Socket socket;
    private final String uuid;
    private final Charset charset;
    private final AtomicBoolean destroyed = new AtomicBoolean();
    private final AtomicBoolean destroyCallbackCalled = new AtomicBoolean();
    private volatile CallbackBufferedReader in;
    private volatile PrintWriter out;

    public SocketClient(Socket socket) throws Exception {
        this(socket, StandardCharsets.UTF_8);
    }

    public SocketClient(Socket socket, Charset charset) throws Exception {
        if (socket == null) throw ExceptionHandling.invalid("Socket is required.");
        if (charset == null) throw ExceptionHandling.invalid("Socket charset is required.");
        this.uuid = UUID.randomUUID().toString();
        this.charset = charset;
        this.socket = socket;
        initializeStreams(socket);
    }

    protected synchronized void createNewSocket(String host, int port) {
        try {
            Socket replacement = new Socket(host, port);
            destroy();
            socket = replacement;
            destroyed.set(false);
            destroyCallbackCalled.set(false);
            initializeStreams(replacement);
        } catch (Exception e) {
            throw ExceptionHandling.failure("Unable to create socket connection.", e);
        }
    }

    private void initializeStreams(Socket source) throws IOException {
        in = new CallbackBufferedReader(new InputStreamReader(source.getInputStream(), charset)) {
            @Override
            public void onDestroy() {
                notifyDestroyed();
            }
        };
        out = new PrintWriter(new BufferedWriter(new OutputStreamWriter(source.getOutputStream(), charset)), true);
    }

    /** Writes one line using the configured charset and flushes immediately. */
    public synchronized void write(String output) {
        if (output == null) throw ExceptionHandling.invalid("Socket output is required.");
        PrintWriter writer = out;
        if (writer == null || destroyed.get()) throw ExceptionHandling.failure("Socket is closed.", null);
        writer.println(output);
        writer.flush();
        if (writer.checkError()) throw ExceptionHandling.failure("Socket write failed.", null);
    }

    public abstract void response(String content);

    public abstract void onDestroy();

    public void parse(String content) {
        response(content);
    }

    /** Reads one newline-delimited message, reporting I/O failures and preserving EOF semantics. */
    public String read() {
        CallbackBufferedReader reader = in;
        if (reader == null || destroyed.get()) throw endOfStream("Socket is closed.");
        try {
            String line = reader.readLine();
            if (line == null) throw endOfStream("Socket reached end of stream.");
            return line;
        } catch (IOException e) {
            ExceptionHandling.report(e);
            throw endOfStream(e.getMessage());
        }
    }

    @Override
    public void close() throws Exception {
        destroy();
    }

    public synchronized void destroy() throws Exception {
        if (!destroyed.compareAndSet(false, true)) return;
        Exception failure = null;
        try {
            if (in != null) in.close();
        } catch (Exception e) {
            failure = e;
            ExceptionHandling.report(e);
        }
        if (out != null) out.close();
        if (socket != null && !socket.isClosed()) {
            try {
                socket.close();
            } catch (IOException e) {
                failure = failure == null ? e : failure;
                ExceptionHandling.report(e);
            }
        }
        socket = null;
        in = null;
        out = null;
        if (failure != null) throw failure;
    }

    public String getUuid() {
        return uuid;
    }

    public Charset getCharset() {
        return charset;
    }

    public boolean isOpen() {
        Socket current = socket;
        return !destroyed.get() && current != null && current.isConnected() && !current.isClosed();
    }

    public void setReadTimeout(int timeoutMillis) throws SocketException {
        if (timeoutMillis < 0) throw ExceptionHandling.invalid("Socket timeout cannot be negative.");
        requireSocket().setSoTimeout(timeoutMillis);
    }

    public void setTcpNoDelay(boolean enabled) throws SocketException {
        requireSocket().setTcpNoDelay(enabled);
    }

    public void setKeepAlive(boolean enabled) throws SocketException {
        requireSocket().setKeepAlive(enabled);
    }

    public InetAddress getRemoteInetAddress() {
        return requireSocket().getInetAddress();
    }

    public InetAddress getLocalInetAddress() {
        return requireSocket().getLocalAddress();
    }

    private Socket requireSocket() {
        Socket current = socket;
        if (current == null || destroyed.get()) throw ExceptionHandling.failure("Socket is closed.", null);
        return current;
    }

    private void notifyDestroyed() {
        if (!destroyCallbackCalled.compareAndSet(false, true)) return;
        try {
            onDestroy();
        } catch (Throwable throwable) {
            ExceptionHandling.report(throwable);
        }
    }

    private NoSuchElementException endOfStream(String message) {
        return new NoSuchElementException(message == null ? "Socket closed." : message);
    }
}
