# HTTP, REST, and encrypted APIs

JFlux 1.2.0 treats HTTP failures, invalid request data, and encrypted protocol
failures as reportable API events through `APIException`, which extends
`ReportedException`. Applications can configure the normal exception reporting
policy and use `tryReport()` when an API failure should be escalated explicitly.

## Configurable post-quantum API encryption (1.2.0)

Select the same `EncryptedApiCryptoSuite` on both peers before opening a session:

```java
import com.fortrow.jflux.http.api.server.encryption.EncryptedApiCryptoSuite;

APIServerConfig config = new APIServerConfig(8443, "/api")
        .useDynamicApiKey(true)
        .setEncryptedCryptoSuite(EncryptedApiCryptoSuite.ML_KEM_768);

// Construct client with your existing credentials and transport configuration.
client.setEncryptedCryptoSuite(EncryptedApiCryptoSuite.ML_KEM_768);
client.beginSessionOrThrow("/session/init", "/session/close");
```

Options are `RSA_2048` (compatibility default), `ML_KEM_512`, `ML_KEM_768`, and
`ML_KEM_1024`. ML-KEM implements the key-encapsulation algorithm standardized in
[NIST FIPS 203](https://csrc.nist.gov/pubs/fips/203/final). Its shared secret derives
an AES-256-GCM wrapping key through HKDF-SHA256. This replaces RSA around each
rotating payload-key envelope. Payload encryption remains AES-256-GCM, with the
existing HMAC authentication, sequence checking, session allocation, and resume
rules. Enable `useDynamicApiKey(true)` for rotating key envelopes.

New clients generate their own key pair for both RSA and ML-KEM, sending only
`X-JFlux-Client-Public-Key` and the explicit `X-JFlux-Crypto-Suite` on init/resume.
The server generates its own pair. The PSK-authenticated bootstrap response names
the suite and confirms the client public key. Neither endpoint transfers its
private key. Every resumed session creates fresh pairs. The configured suite is
required: missing or mismatched PQ negotiation is rejected before consuming a
resume token, with no automatic downgrade to RSA. Duplicate negotiation headers
and malformed public keys are rejected.

The encrypted CORS policy includes both negotiation headers. Key-envelope header
values are bounded before Base64 decoding, and failed session bootstrap discards
the client's temporary session keys.
Client session checks reject empty session material, so suite selection works
before initialization and cleared sessions cannot issue authenticated requests.

The RSA default retains compatibility with old clients that omit both negotiation
headers: only that legacy path still transfers a server-generated client private
key under the PSK. New RSA clients can also read that bootstrap format from old
servers that do not advertise a crypto suite. Responses advertising a suite
cannot supply a client private key. ML-KEM clients always require client-owned
private keys.

This policy applies to HTTP and WebSocket, sync and async resources, and binary
WebSocket payloads because they share the key-envelope implementation. It does
not change TLS cipher suites or certificate algorithms. Continue using HTTPS/WSS,
and mTLS where required; the PSK and API root credentials remain necessary for
authentication. Standard Bouncy Castle implements these algorithms but is not a
FIPS 140 validated module. The full and HTTP shaded JARs bundle its provider;
unshaded Maven consumers must add the optional provider explicitly.

## Server request boundaries

`APIServerConfig` limits the request body retained in memory. The default is
4 MiB and applications can change it before starting the server:

```java
APIServerConfig config = new APIServerConfig(8443, "/api")
        .setMaxRequestBodyBytes(2 * 1024 * 1024);
```

Requests over the limit receive HTTP 413 with a `request_too_large` status. Body
reads use bounded byte buffers and UTF-8 decoding rather than repeated string
concatenation.

JSON request bodies are validated before endpoint execution. Malformed JSON,
non-object JSON payloads, and missing registered endpoint parameters are rejected
as request failures. Missing or malformed encrypted timestamps are rejected as
unauthorized requests instead of becoming server errors.

## Client resource handling

`RESTClient` and `EncryptedClient` should be used with try-with-resources. Their
HTTP/1 response entities are consumed and released, HTTP/3 transports are closed
with the client, and encrypted request execution does not share mutable request
objects between concurrent calls.

```java
try (RESTClient client = new RESTClient(apiKey, endpoint, HttpVersion.HTTP_1, false).build()) {
    RESTClient.Response response = client.get("/health", null);
}
```

The encrypted client continues to expose `beginSession`, `beginSessionOrThrow`,
`request`, `requestAndWait`, and `closeSessionOrThrow`. Invalid encrypted response
headers or authentication material result in an unverified response or a
reportable `APIException`; callers must check `isVerified()` before trusting
encrypted response data.

Malformed encrypted response JSON or ciphertext is also treated as an
unverified response and is sent through the configured exception reporters.
Resource paths are normalized only when they begin with the configured API
prefix, preventing unrelated path text from being stripped accidentally.

## Encrypted API WebSocket transport

The encrypted API can run a separate WebSocket listener beside HTTP by calling
`APIServerConfig.useWebSocket(port)`. It is disabled by default. Configure the
handshake path with `setWebSocketPath(...)`, the frame limit with
`setWebSocketMaxMessageBytes(...)`, and WSS with `setWebSocketSslContext(...)`.
Use port `0` for an operating-system-selected listener port and read the bound
port from `api.getServer().getWebSocketPort()` after startup.

`EncryptedClient` automatically selects the WebSocket transport for `ws://` and
`wss://` endpoints. The frame is only a request/response envelope; the client
still sends the normal encrypted API headers, ciphertext, signatures, sequence
numbers, and session paths. The server forwards the frame through the existing
shared transport-neutral API dispatcher directly, so authentication, session
capacity, authorization, rate limiting, and error semantics remain consistent
between HTTP and WebSocket. See [websockets.md](websockets.md) for complete
configuration examples and WSS guidance.

## Compatibility

Existing REST and encrypted public entry points remain available. Header and
body containers now return safe null/empty values for absent optional fields,
while strict request validation occurs at the server boundary. Existing
application-level error response codes and JSON status fields are preserved.

## Encrypted session capacity

Encrypted session capacity is allocated per root API key. Every API key starts
with one available session slot. The old global `setMaxSessions` setting is
deprecated and is no longer used for enforcement.

The configured encrypted lifecycle paths are, in order:

1. `session/init` — creates the first session or an additional session when a
   slot has been allocated.
2. `session/close` — closes a live session. Set `create_resume_token` to
   `true` in the encrypted JSON body to receive a one-time resume token.
3. `session/resume` — consumes that close-generated token and creates a fresh
   encrypted session. The original API key and root session authorization are
   still required.
4. `session/reset` — invalidates every live session, all resume tokens, and
   pending allocation challenges for the API key, then returns its capacity
   to one.
5. `session/allocate` — issues a one-time challenge, then verifies a signed
   allocation assertion from the application's external authentication
   backend. Each accepted assertion increases the API key's capacity by one.
6. `session/heartbeat` — refreshes the inactivity deadline for an existing
   encrypted session. It is authenticated and ratcheted like a normal request,
   but it does not allocate a session or create a resume token.

When active sessions plus outstanding resume-token reservations reach the
per-key capacity, new initialization returns HTTP 429 and a structured
`JFLUX_SESSION_LIMIT_REACHED` error. Clients should resume a closed session or
call `session/reset`; they should not repeatedly retry `session/init`.

The limit response is deliberately two-tiered. Before the requested session's
normal API/session authorization has succeeded, JFlux returns only the generic
limit status, message, and configured capacity. After authorization succeeds
and the request is blocked solely because capacity is full, the response also
contains `active_sessions`, `outstanding_resume_tokens`, and the unique
`session_ip_addresses` currently associated with the API key. This prevents an
unauthenticated caller from using the limit endpoint to enumerate active
sessions or client addresses.

Resume-token metadata stores only token hashes, API-key hashes, session IDs,
expiry times, and used flags. Configure a durable metadata file when tokens
and allocated per-key capacities must survive a server restart:

```java
APIServerConfig config = new APIServerConfig(8443, "/api")
        .setSessionResumeStorePath("/var/lib/my-app/jflux-resume-tokens.json")
        .setResumeTokenMinutes(30);
```

Expiration based on the last request is enabled by default for 30 minutes and
can be disabled with `setSessionExpirationEnabled(false)`.

## Session allocation and external authentication

JFlux is credential-blind. It does not receive or store usernames, passwords,
MFA values, or identity-provider credentials. The separate web-authentication
backend performs that authentication and signs an allocation assertion after
the user has been authenticated.

Every API key has a separate `session_allocation_secret`. It is generated with
the API key, encrypted with the API service's existing key-record storage, and
is never included in an HTTP request or response. The JFlux server and the
external authentication backend must provision the same secret privately.
Do not reuse the API secret, session secret, or PSK.

When provisioning an API key, copy the secret to the private configuration of
the external authentication backend through a secure administrative channel:

```java
APIKeys apiKeys = api.generateApiKeys();
String allocationSecret = apiKeys.getRawSessionAllocationSecret();
// Store allocationSecret in the web-authentication backend's private secret store.
```

The value should never be logged, returned by an application endpoint, or sent
to an encrypted API client.

The client flow is:

```java
JSONObject challenge = client.requestAllocationChallenge();
// Send challenge_id and challenge to the web-authentication backend.
// That backend performs username/password/MFA authentication privately.

SessionAllocationAssertion assertion = SessionAllocationAssertion.sign(
        apiKey,
        (String) challenge.get("challenge_id"),
        (String) challenge.get("challenge"),
        Instant.now(),
        Instant.ofEpochMilli((Long) challenge.get("expires_at_ms")),
        allocationSecret);

JSONObject allocation = client.allocateSession(assertion);
```

The external backend must use its private copy of the allocation secret. The
assertion contains only the API-key identifier, challenge values, timestamps,
and an HMAC-SHA384 signature. JFlux verifies the signature with its own copy,
checks the challenge and expiration, consumes the challenge once, and adds
exactly one session slot. Replaying or modifying an assertion fails.

For non-Java authentication services, sign this canonical payload with
HMAC-SHA384 and send the resulting lowercase hexadecimal signature:

```text
JFLUX-HTTP-API/1
action:session-allocate
api-key:<api-key>
challenge-id:<challenge-id>
challenge:<challenge>
issued-at-ms:<issued-at-ms>
expires-at-ms:<expires-at-ms>
```

The allocation request sends these fields to JFlux:
`api_key`, `challenge_id`, `challenge`, `issued_at_ms`, `expires_at_ms`, and
`signature`. There is no OAuth token and there is no configurable allocation
amount. The deprecated client methods that accepted usernames, MFA values, or
OAuth-style tokens now reject those flows.

`EncryptedClient` can automatically use a saved resume token before trying a
new initialization. Use `setResumeTokenFile(...)` or `setResumeTokenStore(...)`
to enable this behavior. `closeSessionOrThrow(true)` requests and persists a
resume token; `resetSessions()` clears the local token after a successful
server reset.

### Shared HTTP request dispatch

HTTP/1 and HTTP/3 now adapt their header snapshot, original method, client address,
resource path, query source, and lazy body stream into one private string/JSON
request dispatcher. Resource validation, API-key lookup, session selection and
controls, signature/sequence validation, decryption, parameter validation, rate
limits, resource execution, and response decisions share the same implementation.
Each transport retains its existing response writer, CORS headers, encrypted
response-secret commit, write-failure rollback, and session-close behavior.
HTTP/1 keeps uppercase method handling and full URI query parsing; HTTP/3 keeps
its original method and path source. Text WSS now calls this dispatcher directly,
without a loopback proxy. An optional startup-snapshotted peer identity authorizer
checks the actual HTTPS/WSS certificate binding before key lookup or session
mutation. Encrypted exchanges and administrative key resets share a bounded
4096-stripe reentrant lock table across transports, through response commit/rollback.
HTTP/3 currently fails closed when peer binding is configured because its request
interface does not expose verified certificate identity. Opt-in binary WSS resources now use the same dispatcher and cryptographic rules;
Fleet must configure the binding authorizer before enabling its own integration. API WSS TLS now honors
`APIServerConfig.setClientAuth(true)` by configuring required client auth on its
SSL engine factory, as well as the underlying HTTP listener.

The shared client/session indexes use a short state lock for snapshots, reference
lookups, registration, removal, and traversal. Crypto and resource execution run
outside it under their key exchange lock. Lookups return stable object references,
not indexes that another key could invalidate. Expiration skips another key's
active exchange; session/client list getters return independent snapshots.
WebSocket task admission is bounded by `setWebSocketMaxPendingRequests(int)`
(default 128; valid range 1–4096). The permit is acquired before virtual-thread
submission and released in `finally`; saturated requests receive 503 and their
connection closes with retryable overload code 1013 before authentication state
changes. Applications must apply their usual authenticated reconnect recovery.

Binary WSS APIs: `setBinaryWebSocketEnabled(true)` enables validated startup
opt-in for encrypted sync plus required mTLS and peer/key authorization.
`EncryptedClient.postBytes` sends raw encrypted bytes; `InboundRequest.getBodyBytes`
reads authenticated binary plaintext; `APIResponse.binary` returns bytes; verified
`EncryptedResponse.getBodyBytes` exposes the reply. HTTP and session-control
requests retain their existing text/JSON formats. See `websockets.md` for framing,
size bounds, and application metadata authentication requirements.

### Encrypted WebSocket shutdown

`APIServer.stop()` closes the WebSocket listener and drains its request executor
for up to five seconds before interrupting outstanding tasks. Shutdown preserves
the thread interrupt status. The drain prevents authenticated close/ratchet
writes from racing caller-owned database or temporary-directory cleanup.
