package com.fortrow.jflux.websockets.server;

import org.java_websocket.handshake.ClientHandshake;

import java.nio.ByteBuffer;

/** Lifecycle callbacks for transport-level WebSocket events. */
public interface FluxWebSocketHandler {

    default void onOpen(FluxWebSocketSession session, ClientHandshake handshake) { }

    default void onText(FluxWebSocketSession session, String message) { }

    default void onBinary(FluxWebSocketSession session, ByteBuffer message) { }

    default void onClose(FluxWebSocketSession session, int code, String reason, boolean remote) { }

    default void onError(FluxWebSocketSession session, Exception error) { }

    default void onStart() { }
}
