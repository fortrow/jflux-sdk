# Sockets package

`com.fortrow.jflux.sockets` provides a small line-oriented TCP client/server
layer. It is designed for protocols where each message is terminated by a
newline, such as the existing MeridianDB socket protocols.

## Java and threading

Socket accept and client-session work uses virtual threads through the Java 21+
`Thread.ofVirtual()` and `Thread.startVirtualThread()` APIs. jflux currently
compiles with its existing Java 23 project target, while the socket/threading
implementation itself only relies on virtual-thread APIs introduced in Java 21.

## SocketClient

Subclass `SocketClient` and implement `response(String)` and `onDestroy()`.
Client streams use UTF-8 by default and can be given another `Charset` through
the second constructor. `write` sends and flushes one line; `read` blocks for a
line and retains the existing `NoSuchElementException` end-of-stream contract.

Useful lifecycle and socket controls include:

- `isOpen()` and idempotent `destroy()`/`close()`;
- `setReadTimeout`, `setTcpNoDelay`, and `setKeepAlive`;
- `getRemoteInetAddress` and `getLocalInetAddress`; and
- `getCharset` for protocol inspection.

Write failures and socket I/O failures are routed through the configured JFlux
exception reporters. Destroy callbacks run once even if both the reader and
the application close the client.

## SocketServer

Subclass `SocketServer` and implement `requestIn` and `clientDestroyed`.
Existing constructors still start the server asynchronously. Use
`awaitStarted(Duration)` before obtaining an ephemeral port with `getPort()` or
accepting clients in tests. The default listen backlog is 256; an overload
allows a custom backlog.

Each accepted client receives its own virtual thread. When `clientEndSession`
is true, the server blocks for successive lines until EOF or shutdown. The
previous fixed polling delay has been removed. When it is false, one line is
processed and the client is closed.

`isRunning`, `getClientCount`, `getFailure`, `interrupt`, and `close` expose
server lifecycle state. Shutdown closes the listening socket and all clients;
normal socket-close exceptions are not printed directly and are routed through
JFlux exception handling.
