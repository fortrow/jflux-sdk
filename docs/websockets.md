# WebSockets

JFlux 1.1.1 provides a transport-level WebSocket server in
`com.fortrow.jflux.websockets.server`. It uses the existing Java-WebSocket
dependency and deliberately leaves authentication, routing, and message
envelopes to an injected `FluxWebSocketHandler`.

The WebSocket classes and their dependency are included in the
`jflux-1.1.1-http.jar` artifact.

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

The same transport is also used by the encrypted API WebSocket listener
described below. The protocol-neutral classes remain available for applications
that need their own message format.
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

## Encrypted API transport

An API can start a separate WebSocket listener beside its configured HTTP
listener. The listener is disabled by default, and port `0` asks the operating
system for an available port:

```java
APIServerConfig config = new APIServerConfig("127.0.0.1", 8181, "/api")
        .useWebSocket(8182)
        .setWebSocketPath("/encrypted-api")
        .setWebSocketMaxMessageBytes(4 * 1024 * 1024);
```

`useWebSocket(port)` enables the listener. The WebSocket bind host follows the
HTTP API bind host unless `setWebSocketBindAddress(...)` is used. A custom
`SSLContext` enables WSS with `setWebSocketSslContext(...)`; when the API is
already configured for TLS, its API context is reused if no WebSocket context
is supplied.

The WebSocket frame contains a request ID, HTTP method, relative API path,
headers, and body. The server adapts that request directly into the shared API
dispatcher, retaining the actual immutable WSS TLS peer identity. API-key checks,
timestamp validation, encrypted session initialization/resume/close/reset,
sequence ratchets, resource authorization, response encryption, rate limits,
and error codes are shared with HTTP rather than reimplemented for WebSocket.
The frame itself is only a transport envelope; it does not replace the
encrypted API protocol.

`EncryptedClient` selects this transport automatically when its endpoint uses
`ws://` or `wss://`:

```java
try (EncryptedClient client = new EncryptedClient(
        apiKey, apiSecret, sessionSecret, psk,
        "ws://127.0.0.1:8182/encrypted-api",
        HttpVersion.HTTP_1, false)
        .setWebSocketRequestTimeout(Duration.ofSeconds(30))
        .build()) {
    client.beginSessionOrThrow("/session/init", "/session/close");
    EncryptedResponse response = client.request("records", requestBody);
}
```

The HTTP version argument is retained for source compatibility and is ignored
for a WebSocket endpoint. `setWebSocketSslContext(...)` can provide a custom
trust or mTLS context before `build()`. `allowSelfSigned` remains an explicit
opt-in for development environments only.

The client keeps one WebSocket connection and correlates responses by request
ID. Encrypted exchanges on each client use a reentrant lock through response verification
and ratchet advancement, avoiding JDK23 virtual-thread carrier pinning; parallel callers cannot reuse a payload key/nonce. Closing the connection fails pending requests; it does not create,
resume, or reset an encrypted API session. Session lifecycle remains controlled
by the same explicit encrypted API endpoints.

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

## Verified TLS peers and binary streaming readiness

For a protocol-neutral server, `setSslContext(context, true)` requires a client
certificate at the TLS handshake. The context must use a validating trust
manager. `session.verifiedPeerCertificates()` returns the transport's verified
X509 chain; it never reads connection attributes or request headers. It returns
an empty chain on plaintext connections and fails if a TLS peer is unverified.
Applications must bind this identity to their authorized API key themselves.

`EncryptedClient.setMtlsConfig(...)` now loads both client identity and trust
material for WSS, as it does for HTTP. Do not combine it with a custom WebSocket
SSL context. Hostname checking stays enabled by Java-WebSocket's HTTPS endpoint
identification. A custom SSL context alone does not require client certificates
at the server.

The protocol-neutral constructors accepting `parserLimitBytes` configure
`Draft_6455` before frame decoding, bounding frames and fragmented-message
assembly. Existing constructors use a 4 MiB parser bound. `setMaxMessageBytes`
adds a dispatch/send bound; it cannot increase the constructor's parser bound.
The API server's constructor parser bound matches its configured message limit.

The encrypted API listener still accepts text envelopes. It now dispatches directly
with the TLS peer identity and shares encrypted exchange locks with HTTP. Configure
`APIServerConfig.setPeerIdentityAuthorizer((apiKey, peer) -> ...)` before creating
the API to bind each key to the enrolled certificate's `peer.leafSha256()`.
The authorizer is captured at server construction and requires a verified peer;
missing identity, duplicate key headers, callback failure, or a rejected binding
returns 401 before API-key lookup, session inspection, or ratchet mutation. Caller
headers and mutable connection attributes never supply peer identity. HTTP/3's
current request interface lacks peer-certificate access and therefore fails closed
when this binding hook is configured. Applications must keep that transport
disabled until its verified TLS identity can be provided directly.

A fixed 4096-stripe `ReentrantLock` table bounds memory while serializing the complete
encrypted exchange for each key, including validation, resource execution,
response writing, secret commit, and rollback; hash collisions only add
serialization. A short state lock protects shared client/session indexes and
returns stable references/snapshots; it never covers crypto or resource execution.
Expiration skips another key's active exchange. Administrative key resets use
the same key locks. `setWebSocketMaxPendingRequests(int)` bounds queued/running
WSS tasks (default 128, range 1–4096); saturation sends 503 and closes 1013 before
submitting a task. Permits release in `finally`. Default session
capacity stays one; allocation and resume behavior retain existing explicit
configuration. Fleet integration must configure its enrolled certificate/key authorizer and
explicitly enable the binary API after its own end-to-end ingestion tests pass.

### Binary encrypted API resources

`APIServerConfig.setBinaryWebSocketEnabled(true)` opts into raw encrypted byte
resource exchanges. It is disabled by default and captured at server construction.
Validation requires `ENCRYPTED_SYNC`, an enabled WSS listener, required client
certificates, and a peer identity authorizer. Session init, heartbeat, close,
resume, allocation, and reset continue using the existing text API.

Resource code reads authenticated plaintext through `InboundRequest.getBodyBytes()`
and returns `APIResponse.binary(request.getSession(), replyBytes, ResponseCode.SUCCESSFUL)`.
The mTLS-configured `EncryptedClient.postBytes(path, bytes)` or
`requestBytes(path, RequestType, bytes)` encrypts arbitrary bytes; the reply exposes
`getBodyBytes()` only when `isVerified()` passes both the existing ratchet proof
and AES-GCM payload authentication. These methods require WSS, validating
`MtlsConfig`, and an active session initialized through the text API. Responses
and request inputs are bounded; returned plaintext uses defensive copies.

FSP records can therefore be sent as already compressed bytes with no JSON or
Base64 payload wrapper on the WebSocket wire. `EncryptedBinaryWebSocketMessage`
encodes ciphertext directly. For compatibility with existing JFlux request
signatures and response writers, the dispatcher temporarily represents
ciphertext as Base64 internally, then removes that representation before binary
wire transmission. Cryptographic routing/AAD, timestamp, sequence, key-set,
session, and signature rules remain unchanged. Arbitrary custom headers are not
covered by those signature rules: application identity metadata must be inside
the encrypted plaintext or signed route, not trusted custom headers.

The cipher body must fit `setMaxRequestBodyBytes`; the entire envelope must fit
the WebSocket parser/message bound. Admission permits cover binary tasks as well
as text tasks. Disabled binary frames close with 1003; malformed frames, session
controls, or excessive body declarations close before authenticated dispatch.
An authenticated stale/replayed ratchet invalidates only the affected session.
Missing/mismatched certificates are denied before ratchet mutation. No binary
fallback to plaintext WS or unverified response delivery exists.

The big-endian 36-byte header carries magic `JFB1`, version 1, type (1 request or
2 response), zero reserved flags, a UUID request ID, unsigned 16-bit response
status (zero for requests), unsigned 16-bit header count, and unsigned 32-bit
metadata/body lengths. Metadata contains method/path followed by header name/value
pairs; each string has a 16-bit byte length and strict UTF-8 encoding. Responses
have empty method/path. Encoders normalize and sort header names; duplicate names,
including case variants, are rejected. Bodies contain ciphertext directly,
without Base64 or compression. Limits are 64 headers, 8 KiB per string, 64 KiB
metadata, and 4 MiB ciphertext. Exact lengths and reserved fields are checked
before body allocation. Callers retain ownership of input buffers; payloads and
header maps are immutable copies.

Binary dispatch authenticates routing and protocol authentication headers through
the existing request-signature/AAD rules before resource execution. Peer certificates
must come from the verified TLS connection, never envelope headers. Serializing
only WebSocket writes is insufficient: encryption, authentication, resource
execution, response verification, and ratchet advancement must share the same
per-key exchange boundary across transports. Direct text dispatch now provides
that boundary and authentic TLS identity for both text and binary resources.

`EncryptedPayload.encryptBytes(byte[])` and `decryptBytes(byte[])` now reuse the
existing AES-GCM implementation directly, retaining arbitrary plaintext bytes.
They use the current server-outbound/client-inbound `APIKeys` material, or the
explicit key/nonce constructor. Binary plaintext is bounded to 4 MiB minus 16 bytes; ciphertext
is bounded to 4 MiB and must include a 16-byte GCM tag. Nonces must be 12 bytes and AES keys 16, 24, or 32
bytes. Authentication failure returns no plaintext. Constructors copy AAD, and
binary crypto clears temporary key/nonce arrays. These helpers do not advance a
session or reserve a nonce: the serialized API exchange ensures each
key/nonce pair is used once for resource requests. Existing String/Base64 methods retain their format.

`FluxWebSocketSession.peerIdentity()` provides an immutable `TlsPeerIdentity`
snapshot obtained from the actual TLS session at connection construction. Its
lowercase SHA-256 leaf fingerprint and copied DER chain are separate from mutable
application attributes. Plain WS and optional-auth WSS without a client certificate
have no peer identity; applications requiring mTLS must require TLS client auth
and reject a missing identity. Trust validation remains the TLS engine's job.

HTTP/1, HTTP/3, and WSS use the shared dispatcher and exchange locks. Binary
resources are enabled only through validated startup opt-in. HTTP/3 still fails
closed for configured peer binding until its transport exposes verified identity.
API-level WSS forwards `APIServerConfig.setClientAuth(true)` to the required-client-auth
TLS factory. Existing HTTP/text paths keep their format and behavior.
