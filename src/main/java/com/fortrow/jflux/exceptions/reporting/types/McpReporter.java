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

package com.fortrow.jflux.exceptions.reporting.types;

import com.fortrow.jflux.exceptions.ExceptionHandling;
import com.fortrow.jflux.exceptions.types.ReportedException;
import com.fortrow.jflux.mcp.McpServer;

/** Exception reporter that stores structured reports for MCP model clients. */
public final class McpReporter {

    private static volatile McpServer server;

    private McpReporter() { }

    /** Enables MCP reporting and attaches the application-owned MCP server. */
    public static void setConfiguration(McpServer value) {
        if (value == null) throw ExceptionHandling.invalid("MCP server is required.");
        server = value;
    }

    public static McpServer getServer() {
        return server;
    }

    public static boolean hasConfiguration() {
        return server != null;
    }

    public static boolean report(ReportedException exception) {
        McpServer value = server;
        return value != null && value.record(exception);
    }

    public static void disable() {
        server = null;
    }
}
