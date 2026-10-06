package com.fortrow.jflux.websockets.server;

import org.java_websocket.SSLSocketChannel2;
import org.java_websocket.server.DefaultSSLWebSocketServerFactory;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import java.io.IOException;
import java.nio.channels.ByteChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;

/** TLS factory which cannot negotiate an unauthenticated client connection. */
final class ClientAuthWebSocketServerFactory extends DefaultSSLWebSocketServerFactory {
    ClientAuthWebSocketServerFactory(SSLContext context) { super(context); }

    @Override
    public ByteChannel wrapChannel(SocketChannel channel, SelectionKey key) throws IOException {
        SSLEngine engine = sslcontext.createSSLEngine();
        engine.setUseClientMode(false);
        engine.setNeedClientAuth(true);
        return new SSLSocketChannel2(channel, engine, exec, key);
    }
}
