# Bounded Encrypted HTTP/1 Clients

Before `build()`, call `setHttp1Limits(Duration timeout, int maxResponseBytes)`
for a bounded HTTP/1 encrypted service connection. The opt-in profile disables
redirects, automatic transport retries and transparent decompression; bounds
connection/pool/read waits; and aborts the complete individual HTTP request at
the deadline, including a trickling body. Every bootstrap and resource response
is checked before reading beyond the specified byte limit. This limit counts
the encrypted wire JSON, not the decrypted application JSON.

Timeout is 1..60,000 milliseconds; body bound is 1..64 MiB. Changing limits after
build, or using this HTTP/1 method with WSS/HTTP3, is rejected. Existing callers
retain their previous configuration unless they opt in. This method does not
enable self-signed trust or change hostname verification, mTLS or session crypto.
Application-level exact DTO/authorization checks remain required.

The HTTP/1 HTTPS listener also applies required/optional client authentication to
the actual `SSLParameters` object passed to `HttpsParameters.setSSLParameters`.
Setting only the outer HttpsParameters flags after copying defaults is not
sufficient on the JDK HTTPS listener. A client without a certificate must fail
the TLS handshake before any encrypted resource or peer-identity grant is used.
