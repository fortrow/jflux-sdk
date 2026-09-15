package com.fortrow.jflux.websockets.client;

import com.fortrow.jflux.websockets.server.FluxWebSocketSession;
import org.java_websocket.handshake.ServerHandshake;

import java.nio.ByteBuffer;

/** Lifecycle callbacks for a JFlux WebSocket client connection. */
public interface FluxWebSocketClientHandler {

    default void onOpen(FluxWebSocketSession session, ServerHandshake handshake) { }

    default void onText(FluxWebSocketSession session, String message) { }

    default void onBinary(FluxWebSocketSession session, ByteBuffer message) { }

    default void onClose(FluxWebSocketSession session, int code, String reason, boolean remote) { }

    default void onError(FluxWebSocketSession session, Exception error) { }
}
