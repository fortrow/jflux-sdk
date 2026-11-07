# Optional session reporting

Clients may configure `setSessionMetadata(Map<String,String>)` before opening a session. A nonempty report is sent over the authenticated encrypted heartbeat immediately after initialization or resume. Existing clients send nothing additional by default. `reportSessionMetadata()` updates the current report explicitly. Failed report confirmation fails the configured begin operation; session ownership/checkpoint rules still apply.

`SessionMetadata.local(application,type,version)` explicitly opts in to JVM OS/version, architecture, available CPU count and an existing HOSTNAME/COMPUTERNAME environment hint. It does not collect serial numbers, physical RAM estimates, or network addresses. Callers can provide optional hostname, device model, CPU model, physical-memory bytes and application details themselves. Reports accept only the documented whitelist in SessionMetadata, at most4096 UTF-8 bytes, bounded nonblank strings without control characters, and positive decimal CPU/memory values. Never include credentials.

Reports are client assertions, not verified machine identity or authorization. They cannot select another session: the server attaches them only to the current authenticated session. Report snapshots are immutable and in-memory for that session; clients configured with metadata resend on resume/reconnect. Application telemetry should persist these display hints separately from server-observed peer identity/address.

# Administrative revocation

`APIServer.revokeSession(apiKey,sessionId)` serializes against that API key's encrypted exchanges, invalidates the exact session's persistent resume tokens, and deactivates only that session through existing persistence/events. Repeating a revoke is safe; its boolean reports whether a live session was found. It never rewinds ratchets or changes root credentials. Dashboard integrations must authenticate the operator, verify exact ownership, and pass only server-recorded targets. Account-wide sign-out should enumerate owned session IDs and revoke each; resetting a shared bootstrap API key can affect other accounts and is not appropriate. Existing `resetSessionsForApiKey` remains a separate root-key administrative operation.

`InboundRequest.getResourcePath()` exposes the resolved resource path separately from `getResourceMethod()` for telemetry. Session administrative integrations may use `revokeSessionByKeyHash(sha256ApiKey,sessionId)` to avoid transferring root API keys; the resume store can invalidate the same exact hashed scope persistently, including sessions no longer live. These are privileged server APIs, never public resources by themselves.

Distribution qualification must exercise the packaged SDK, not only Maven dependency classpaths. Shaded SDK variants merge Java service-provider descriptors, including JDBC drivers, so session persistence can discover every bundled driver.
