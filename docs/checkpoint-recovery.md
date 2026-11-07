# Read-only checkpoint recovery

Opt in with `APIServerConfig.setCheckpointRecoveryEnabled(true)`. The route
`POST <api-prefix>/session/recover-checkpoint` is admitted only on an encrypted
server with client TLS authentication and an enrolled peer-identity authorizer,
or an explicit `setCheckpointRecoveryAuthorizer` key allowlist behind its private
TLS-terminating gateway. That allowlist must exclude shared bootstrap identities;
it must not replace any existing peer-identity gate.

For pinned deployments without replacing their application main class, the
equivalent root-controlled JVM flag is `-Djflux.checkpointRecovery.enabled=true`.
`-Djflux.checkpointRecovery.authority=t-api` excludes the shared bootstrap key
from `FORTROW_SERVER_API_KEY` (missing environment denies all). The separate
`certificate-gate` authority mode is only for the private install listener whose
existing HTTP certificate adapter encloses the entire API path and binds key
lookup to that admitted certificate. Do not use that mode on an unguarded API.
Existing IP restrictions and credential lookup/revocation still apply.

The SDK's `recoverCheckpointOrThrow(path)` uses a fresh 256-bit challenge, a
30-second freshness window, API identity binding, and domain-separated HMAC
proof/encryption keys derived from the API secret and PSK. The AES-256-GCM reply
is bound to that exact challenge and includes a fresh server boot identity.
An active session yields authenticated `busy`, without a checkpoint. After all
old sessions expire, the authoritative persisted root is returned encrypted.
Recovery never resets credentials, revokes a session, allocates capacity, executes
an application operation or confirms that an earlier operation did not run.

The caller must serialize recovery with normal requests, persist the recovered
checkpoint before retiring its transport hold, and separately reconcile uncertain
application actions/claims. Writers must reuse immutable idempotency identities
and verify durable receipts. Never infer command completion from transport recovery.
`FileSecretStore.write` uses a private temporary file, forced file writes and
atomic replacement; POSIX parent directories are also fsynced before return.
Windows uses an owner-only ACL and forced file writes. Unsupported atomic rename
or private permissions fail the write rather than silently using an unsafe path.
Old clients/servers remain unchanged unless explicitly opted in. Unsupported,
revoked, expired, malformed or mismatched replies do not change client state.
Encrypted HTTP/1 clients disable Apache transport retries and HTTP redirects,
including session-init GETs, because bootstrap GET is not semantically read-only.
Application callers retain responsibility for explicit, idempotent retry policy.
Verified HTTP/1 session closure also closes its transport connection, so the next
session cannot borrow an idle pooled socket from before a backend restart.

The WebSocket listener enables address reuse for clean same-port restarts.
Listener-level errors (which have no connection object) are reported without
attempting a null lookup in the session map, so bind/setup failures remain visible.
