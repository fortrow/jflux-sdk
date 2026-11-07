# WebSocket Output Admission

JFlux 1.2.0 `FluxWebSocketSession.bufferedBytes()` reports bytes currently queued
by Java-WebSocket. It does not reserve space or impose a global quota. Application
writers must serialize admission with their send and enforce their own per-session
and global limits. A slow consumer may be closed rather than buffered indefinitely.
TLS, peer authentication and encrypted API ratchet behavior are unchanged.
