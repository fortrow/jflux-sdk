# WebSocket TLS Handshake Lifecycle

Mandatory-client-certificate WSS uses `HandshakeTlsChannel`, a package-private
copy of Java-WebSocket 1.6.0 `SSLSocketChannel2` under its original MIT license.
The source is pinned to the v1.6.0 upstream tag. No dependency version or global
assertion configuration is changed.

The upstream handshake assumed that every transition to NOT_HANDSHAKING passed
through an observable FINISHED wrap/unwrap result. A terminal transition during
delegated TLS work or close can violate that assumption and crash the selector
with assertions enabled. The local channel checks closed directions and throws
EOFException for that connection, explicitly accepts an already completed
handshake, and serializes close against the synchronized handshake operation.
It does not suppress unrelated assertions or SSLExceptions.

The SSLEngine still performs certificate validation. The factory still requires
client authentication; the client still verifies the hostname. No TLS version,
trust manager, peer identity or application authorization check is relaxed.
Plain WSS without mandatory client authentication retains the upstream factory.

Qualification must include assertions-enabled lifecycle regression tests,
missing-certificate and hostname rejection, and repeated encrypted Fleet relay
collection. A test pass is not approval to enable live logging.
