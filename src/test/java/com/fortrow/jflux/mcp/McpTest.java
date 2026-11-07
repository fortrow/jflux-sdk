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

package com.fortrow.jflux.mcp;

import com.fortrow.jflux.exceptions.reporting.types.McpReporter;
import com.fortrow.jflux.exceptions.types.ReportedException;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.net.URI;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class McpTest {

    @Test
    void inProcessClientListsStructuredReports() {
        McpServer server = new McpServer(new McpErrorStore(10, 10, 4096));
        server.record(new ReportedException("MCP test failure"));

        try (McpClient client = McpClient.inProcess(server)) {
            assertEquals(McpServer.PROTOCOL_VERSION,
                    client.initialize().get("protocolVersion").getAsString());
            assertTrue(client.listTools().size() >= 3);

            var listed = client.listErrors(5, null, "test failure", false);
            assertEquals(1, listed.get("count").getAsInt());
            var error = listed.getAsJsonArray("errors").get(0).getAsJsonObject();
            assertFalse(error.has("stacktrace"));
            assertEquals("MCP test failure", error.get("message").getAsString());
            assertNotNull(client.getError(error.get("id").getAsString(), true).get("error"));
            assertEquals(1, client.summary().get("count").getAsInt());
        }
    }

    @Test
    void mcpReporterCapturesReportedExceptions() throws Throwable {
        McpServer server = new McpServer(new McpErrorStore(5, 5, 4096));
        McpServer previous = McpReporter.getServer();
        try {
            McpReporter.setConfiguration(server);
            new ReportedException("captured by MCP reporter");
            assertEquals(1, server.errorStore().size());
        } finally {
            if (previous == null) McpReporter.disable();
            else McpReporter.setConfiguration(previous);
        }
    }

    @Test
    void httpTransportServesMcpRequests() {
        McpServer server = new McpServer();
        try (McpHttpServer http = new McpHttpServer(server, new InetSocketAddress("127.0.0.1", 0))) {
            http.start();
            URI endpoint = URI.create("http://127.0.0.1:" + http.address().getPort() + "/mcp");
            try (McpClient client = McpClient.http(endpoint)) {
                assertEquals(3, client.listTools().size());
            }
        }
    }
}
