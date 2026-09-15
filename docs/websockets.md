# WebSockets

JFlux 1.1.0 provides a transport-level WebSocket server in
`com.fortrow.jflux.websockets.server`. It uses the existing Java-WebSocket
dependency and deliberately leaves authentication, routing, and message
envelopes to an injected `FluxWebSocketHandler`.

The WebSocket classes and their dependency are included in the
`jflux-1.1.0-http.jar` artifact.

Both `ws://` and `wss://` are supported. Configure the server with an
`SSLContext` before calling `start()`:

```java
FluxWebSocketServer server = new FluxWebSocketServer(
        "0.0.0.0", 8443, handler, javaKeyStore.ssl());
```

The client uses the platform trust store for `wss://` by default. A custom
client or mTLS context can be supplied with `setSslContext(...)` before
connecting. Production deployments should use certificate validation and avoid
trust-all contexts.

This separation is intended to support a future WebSocket version of the REST
and encrypted APIs without coupling the transport to one application protocol.
The handler can authenticate a connection during `onOpen`, store an identity or
session object with `FluxWebSocketSession.setAttribute`, and process text or
binary envelopes in the corresponding callback.

## Basic server

```java
FluxWebSocketServer server = new FluxWebSocketServer("127.0.0.1", 8080,
        new FluxWebSocketHandler() {
            @Override
            public void onText(FluxWebSocketSession session, String message) {
                session.sendText("ack:" + message);
            }
        })
        .setMaxMessageBytes(4 * 1024 * 1024);

server.start();
```

`FluxWebSocketSession` exposes a stable connection ID, resource path, local and
remote addresses, thread-safe attributes, text/binary sends, and close methods.
`sessions()` returns an immutable snapshot, so handlers can safely maintain
application registries without depending on the underlying library collection.

## Safety and lifecycle

- Text messages are measured after UTF-8 encoding and oversized messages are
  closed with status 1009 before handler dispatch.
- Binary frames are dispatched through a read-only `ByteBuffer` and use the same
  limit.
- Handler failures are reported through `ExceptionHandling` and close the
  session with status 1011.
- Server close is idempotent for normal use, closes active sessions, clears the
  registry, and preserves interruption status.
- Invalid constructor values and message limits use the JFlux exception bridge.

The transport does not decide whether a message is REST JSON, an encrypted
envelope, or a future protocol version. Those decisions belong in the handler,
which allows the same session lifecycle to serve multiple API variants.

## Client

`FluxWebSocketClient` uses the same `FluxWebSocketSession` wrapper and message
limits:

```java
FluxWebSocketClient client = new FluxWebSocketClient(
        URI.create("wss://api.example.com/socket"),
        new FluxWebSocketClientHandler() {
            @Override
            public void onText(FluxWebSocketSession session, String message) {
                // Decode the application envelope here.
            }
        });

if (client.connectAndWait()) {
    client.sendText("request");
}
```

The inherited `connect()` remains available for asynchronous startup;
`connectAndWait(Duration)` and `connectAndWait()` provide bounded blocking
startup. Custom handshake headers can be supplied through the constructor for
future API keys or protocol negotiation.
