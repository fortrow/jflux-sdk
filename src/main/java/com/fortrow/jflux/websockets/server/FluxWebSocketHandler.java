/*
 * Copyright (c) 2026 Fortrow LLC.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Fortrow-Commercial
 *
 * JFlux is free software: you can redistribute it and/or modify it under the
 * terms of the GNU Affero General Public License, version 3, as published by
 * the Free Software Foundation.
 *
 * JFlux is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Affero General Public License for
 * more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with JFlux. If not, see <https://www.gnu.org/licenses/>.
 *
 * Alternatively, a separate commercial license is available from Fortrow LLC.
 * For terms permitting use without the AGPL copyleft requirements, contact
 * ryan@fortrow.com. Commercial rights require a separate written agreement.
 * See LICENSE and COMMERCIAL-LICENSE.md.
 */

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
