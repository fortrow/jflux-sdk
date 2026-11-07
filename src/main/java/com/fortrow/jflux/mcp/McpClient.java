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

import com.fortrow.jflux.exceptions.ExceptionHandling;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

/** MCP client for jflux servers over HTTP, stdio, or an in-process transport. */
public final class McpClient implements AutoCloseable {

    @FunctionalInterface
    public interface Transport extends AutoCloseable {
        String send(String request);

        @Override
        default void close() { }
    }

    private final Transport transport;
    private final AtomicLong nextId = new AtomicLong(1);
    private boolean initialized;

    public McpClient(Transport transport) {
        if (transport == null) throw ExceptionHandling.invalid("MCP client transport is required.");
        this.transport = transport;
    }

    public static McpClient inProcess(McpServer server) {
        if (server == null) throw ExceptionHandling.invalid("MCP server is required.");
        return new McpClient(request -> server.handle(request));
    }

    public static McpClient http(URI endpoint) {
        return http(endpoint, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build());
    }

    public static McpClient http(URI endpoint, HttpClient httpClient) {
        if (endpoint == null || httpClient == null) throw ExceptionHandling.invalid("MCP endpoint and HTTP client are required.");
        return new McpClient(new HttpTransport(endpoint, httpClient));
    }

    /** Creates a client for a process speaking newline-delimited MCP stdio. */
    public static McpClient stdio(InputStream input, OutputStream output) {
        if (input == null || output == null) throw ExceptionHandling.invalid("MCP stdio streams are required.");
        return new McpClient(new StdioTransport(input, output));
    }

    public JsonObject initialize() {
        JsonObject params = new JsonObject();
        params.addProperty("protocolVersion", McpServer.PROTOCOL_VERSION);
        params.add("capabilities", new JsonObject());
        JsonObject clientInfo = new JsonObject();
        clientInfo.addProperty("name", "jflux-mcp-client");
        clientInfo.addProperty("version", McpServer.SERVER_VERSION);
        params.add("clientInfo", clientInfo);
        JsonObject result = request("initialize", params);
        initialized = true;
        notify("notifications/initialized", new JsonObject());
        return result;
    }

    public JsonArray listTools() {
        ensureInitialized();
        return request("tools/list", new JsonObject()).getAsJsonArray("tools");
    }

    public JsonObject callTool(String name, JsonObject arguments) {
        ensureInitialized();
        if (name == null || name.isBlank()) throw ExceptionHandling.invalid("MCP tool name is required.");
        JsonObject params = new JsonObject();
        params.addProperty("name", name);
        params.add("arguments", arguments == null ? new JsonObject() : arguments);
        JsonObject result = request("tools/call", params);
        return result.has("structuredContent") ? result.getAsJsonObject("structuredContent") : result;
    }

    public JsonObject listErrors(int limit, String type, String contains, boolean includeStacktrace) {
        JsonObject arguments = new JsonObject();
        arguments.addProperty("limit", limit);
        if (type != null) arguments.addProperty("type", type);
        if (contains != null) arguments.addProperty("contains", contains);
        arguments.addProperty("includeStacktrace", includeStacktrace);
        return callTool("jflux.errors.list", arguments);
    }

    public JsonObject getError(String id, boolean includeStacktrace) {
        JsonObject arguments = new JsonObject();
        arguments.addProperty("id", id);
        arguments.addProperty("includeStacktrace", includeStacktrace);
        return callTool("jflux.errors.get", arguments);
    }

    public JsonObject summary() {
        return callTool("jflux.errors.summary", new JsonObject());
    }

    @Override
    public void close() {
        try {
            transport.close();
        } catch (Exception e) {
            throw ExceptionHandling.failure("Unable to close MCP client transport.", e);
        }
    }

    private JsonObject request(String method, JsonObject params) {
        JsonObject request = new JsonObject();
        request.addProperty("jsonrpc", "2.0");
        request.addProperty("id", nextId.getAndIncrement());
        request.addProperty("method", method);
        request.add("params", params == null ? new JsonObject() : params);
        String response = transport.send(request.toString());
        if (response == null || response.isBlank()) throw ExceptionHandling.failure("MCP server returned no response.", null);
        JsonObject parsed;
        try {
            parsed = JsonParser.parseString(response).getAsJsonObject();
        } catch (RuntimeException e) {
            throw ExceptionHandling.failure("MCP server returned invalid JSON.", e);
        }
        if (parsed.has("error")) {
            JsonObject error = parsed.getAsJsonObject("error");
            throw ExceptionHandling.failure("MCP request failed: " + error.get("message").getAsString(), null);
        }
        if (!parsed.has("result") || !parsed.get("result").isJsonObject()) {
            throw ExceptionHandling.failure("MCP response did not contain a result.", null);
        }
        return parsed.getAsJsonObject("result");
    }

    private void notify(String method, JsonObject params) {
        JsonObject request = new JsonObject();
        request.addProperty("jsonrpc", "2.0");
        request.addProperty("method", method);
        request.add("params", params);
        transport.send(request.toString());
    }

    private void ensureInitialized() {
        if (!initialized) initialize();
    }

    private static final class HttpTransport implements Transport {
        private final URI endpoint;
        private final HttpClient client;

        private HttpTransport(URI endpoint, HttpClient client) {
            this.endpoint = endpoint;
            this.client = client;
        }

        @Override
        public String send(String request) {
            JsonObject message = JsonParser.parseString(request).getAsJsonObject();
            String method = message.has("method") ? message.get("method").getAsString() : "";
            HttpRequest.Builder builder = HttpRequest.newBuilder(endpoint)
                    .timeout(Duration.ofSeconds(30))
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json, text/event-stream")
                    .header("Mcp-Protocol-Version", McpServer.PROTOCOL_VERSION)
                    .header("Mcp-Method", method)
                    .POST(HttpRequest.BodyPublishers.ofString(request, StandardCharsets.UTF_8));
            if ("tools/call".equals(method) && message.has("params") && message.get("params").isJsonObject()) {
                JsonObject params = message.getAsJsonObject("params");
                if (params.has("name")) builder.header("Mcp-Name", params.get("name").getAsString());
            }
            HttpRequest httpRequest = builder.build();
            try {
                HttpResponse<String> response = client.send(httpRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                if (response.statusCode() < 200 || response.statusCode() >= 300) {
                    throw ExceptionHandling.failure("MCP HTTP server returned status " + response.statusCode(), null);
                }
                return response.body();
            } catch (IOException | InterruptedException e) {
                if (e instanceof InterruptedException) Thread.currentThread().interrupt();
                throw ExceptionHandling.failure("MCP HTTP request failed.", e);
            }
        }
    }

    private static final class StdioTransport implements Transport {
        private final BufferedReader reader;
        private final BufferedWriter writer;

        private StdioTransport(InputStream input, OutputStream output) {
            reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8));
            writer = new BufferedWriter(new OutputStreamWriter(output, StandardCharsets.UTF_8));
        }

        @Override
        public synchronized String send(String request) {
            try {
                writer.write(request);
                writer.newLine();
                writer.flush();
                return reader.readLine();
            } catch (IOException e) {
                throw ExceptionHandling.failure("MCP stdio request failed.", e);
            }
        }
    }
}
