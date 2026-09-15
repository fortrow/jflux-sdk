# Early resume tokens

`APIServerConfig.setActiveSessionRecoveryEnabled(true)` opts into issuing a
resume token while its session remains open. It defaults to false. The setting
controls issuance only; already issued tokens retain their normal expiration.
There is no separate recovery credential lifetime or retry-window setting.

When enabled, the encrypted bootstrap advertises `early_resume_supported`.
An authenticated encrypted `session/heartbeat` request containing
`create_resume_token: true` returns `resume_token` and `resume_expires_at_ms`.
Ordinary heartbeats do not issue tokens. The existing `setResumeTokenMinutes`
controls expiration. Repeating issuance for the same live session refreshes
the same token, so a lost heartbeat response does not invalidate a saved token.

The client stores the token in secure storage before reporting initialization
complete and refreshes it during heartbeats. Java clients can explicitly call
`heartbeat(true)` with a configured resume-token store. Flutter requests it only
when the server advertises support. Heartbeats and normal session reuse remain
available when early issuance is disabled.

Tokens associated with live sessions do not consume a second capacity slot.
Resume authenticates the token and retires only its associated session before
creating replacement request keys. An early token proves recovery authority
independently of the mutable root checkpoint, which may lag after process death.
Closed-session tokens continue to require root authorization. Invalid tokens do
not reset unrelated sessions. Ordinary close invalidates early tokens; a close
with `create_resume_token: true` replaces them with a normal close-generated token.
Explicit reset invalidates all tokens for that API key.

Fortrow enables early issuance explicitly; operators can disable it with
`FORTROW_API_EARLY_RESUME_ENABLED=false`.

## Limits

This addresses termination after the early token was saved and before it expires.
It does not provide idempotent replay of a lost successful resume response:
resume tokens remain single-use. A kill during the resume exchange can still
require recovery through reset. The separate recovery transaction store and its
unwired configuration have been removed. Live endpoint behavior and installation
must be verified before claiming the deployed app has been repaired.
