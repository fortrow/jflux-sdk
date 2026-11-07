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
import com.fortrow.jflux.exceptions.types.ReportedException;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonParseException;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Small MCP JSON-RPC server with bounded JFlux exception tools.
 *
 * <p>The server is transport-agnostic. Use {@link #handle(String)} for an HTTP
 * integration, or {@link #run(InputStream, OutputStream)} for MCP stdio.</p>
 */
public final class McpServer {

    public static final String PROTOCOL_VERSION = "2025-06-18";
    public static final String SERVER_NAME = "jflux-mcp";
    public static final String SERVER_VERSION = "1.2.0";

    private static final int PARSE_ERROR = -32700;
    private static final int INVALID_REQUEST = -32600;
    private static final int METHOD_NOT_FOUND = -32601;
    private static final int INVALID_PARAMS = -32602;
    private static final int INTERNAL_ERROR = -32603;

    private final Gson gson = new Gson();
    private final McpErrorStore errorStore;
    private final Map<String, McpTool> tools = new ConcurrentHashMap<>();

    public McpServer() {
        this(new McpErrorStore());
    }

    public McpServer(McpErrorStore errorStore) {
        if (errorStore == null) throw ExceptionHandling.invalid("MCP error store is required.");
        this.errorStore = errorStore;
        registerBuiltInTools();
    }

    public McpErrorStore errorStore() {
        return errorStore;
    }

    /** Registers or replaces a tool by name. */
    public McpServer registerTool(McpTool tool) {
        if (tool == null) throw ExceptionHandling.invalid("MCP tool is required.");
        tools.put(tool.name(), tool);
        return this;
    }

    /** Records a report for later MCP retrieval. */
    public boolean record(ReportedException exception) {
        if (exception == null) return false;
        errorStore.add(exception);
        return true;
    }

    /** Handles one MCP JSON-RPC request or notification. Null means no response. */
    public String handle(String requestJson) {
        if (requestJson == null || requestJson.isBlank()) return error(null, PARSE_ERROR, "Request is empty.");
        JsonObject request;
        try {
            JsonElement parsed = JsonParser.parseString(requestJson);
            if (!parsed.isJsonObject()) return error(null, INVALID_REQUEST, "MCP request must be a JSON object.");
            request = parsed.getAsJsonObject();
        } catch (JsonParseException | IllegalStateException e) {
            return error(null, PARSE_ERROR, "Invalid JSON request.");
        }

        JsonElement id = request.get("id");
        if (!request.has("jsonrpc") || !"2.0".equals(request.get("jsonrpc").getAsString())
                || !request.has("method") || !request.get("method").isJsonPrimitive()) {
            return error(id, INVALID_REQUEST, "Invalid JSON-RPC request.");
        }
        String method = request.get("method").getAsString();
        if (!request.has("id")) {
            if ("notifications/initialized".equals(method) || "notifications/cancelled".equals(method)) return null;
            dispatch(method, object(request, "params"));
            return null;
        }

        try {
            return dispatchRequest(id, method, object(request, "params"));
        } catch (IllegalArgumentException e) {
            return error(id, INVALID_PARAMS, e.getMessage());
        } catch (RuntimeException e) {
            ExceptionHandling.report(e);
            return error(id, INTERNAL_ERROR, "MCP server operation failed.");
        }
    }

    /** Runs newline-delimited MCP stdio until input closes. */
    public void run(InputStream input, OutputStream output) {
        if (input == null || output == null) throw ExceptionHandling.invalid("MCP stdio streams are required.");
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8));
            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(output, StandardCharsets.UTF_8));
            String line;
            while ((line = reader.readLine()) != null) {
                String response = handle(line);
                if (response != null) {
                    writer.write(response);
                    writer.newLine();
                    writer.flush();
                }
            }
        } catch (IOException e) {
            throw ExceptionHandling.failure("MCP stdio server failed.", e);
        }
    }

    private String dispatchRequest(JsonElement id, String method, JsonObject params) {
        return switch (method) {
            case "initialize" -> response(id, initialize(params));
            case "ping" -> response(id, new JsonObject());
            case "tools/list" -> response(id, listTools());
            case "tools/call" -> response(id, callTool(params));
            default -> error(id, METHOD_NOT_FOUND, "Unknown MCP method: " + method);
        };
    }

    private JsonObject dispatch(String method, JsonObject params) {
        if ("notifications/initialized".equals(method)) return new JsonObject();
        return switch (method) {
            case "ping" -> new JsonObject();
            default -> new JsonObject();
        };
    }

    private JsonObject initialize(JsonObject params) {
        JsonObject result = new JsonObject();
        String requestedVersion = string(params, "protocolVersion");
        result.addProperty("protocolVersion", requestedVersion == null || requestedVersion.isBlank()
                ? PROTOCOL_VERSION : requestedVersion);
        JsonObject capabilities = new JsonObject();
        capabilities.add("tools", new JsonObject());
        result.add("capabilities", capabilities);
        JsonObject serverInfo = new JsonObject();
        serverInfo.addProperty("name", SERVER_NAME);
        serverInfo.addProperty("version", SERVER_VERSION);
        result.add("serverInfo", serverInfo);
        return result;
    }

    private JsonObject listTools() {
        JsonArray definitions = new JsonArray();
        tools.values().stream().sorted(java.util.Comparator.comparing(McpTool::name))
                .forEach(tool -> definitions.add(tool.definition()));
        JsonObject result = new JsonObject();
        result.add("tools", definitions);
        return result;
    }

    private JsonObject callTool(JsonObject params) {
        String name = string(params, "name");
        if (name == null || name.isBlank()) throw new IllegalArgumentException("Tool name is required.");
        McpTool tool = tools.get(name);
        if (tool == null) throw new IllegalArgumentException("Unknown MCP tool: " + name);

        JsonObject arguments = params.has("arguments") && params.get("arguments").isJsonObject()
                ? params.getAsJsonObject("arguments") : new JsonObject();
        JsonObject structured;
        try {
            structured = tool.call(arguments);
            if (structured == null) structured = new JsonObject();
        } catch (RuntimeException e) {
            ExceptionHandling.report(e);
            structured = new JsonObject();
            structured.addProperty("error", "Tool execution failed.");
        }
        JsonObject result = new JsonObject();
        JsonArray content = new JsonArray();
        JsonObject text = new JsonObject();
        text.addProperty("type", "text");
        text.addProperty("text", gson.toJson(structured));
        content.add(text);
        result.add("content", content);
        result.add("structuredContent", structured);
        if (structured.has("error")) result.addProperty("isError", true);
        return result;
    }

    private void registerBuiltInTools() {
        JsonObject listSchema = new JsonObject();
        listSchema.addProperty("type", "object");
        JsonObject listProperties = new JsonObject();
        listProperties.add("limit", property("integer", "Maximum number of newest errors to return."));
        listProperties.add("type", property("string", "Exact exception type or simple class name filter."));
        listProperties.add("contains", property("string", "Case-insensitive message filter."));
        listProperties.add("includeStacktrace", property("boolean", "Include stacktrace entries; defaults to true."));
        listSchema.add("properties", listProperties);
        registerTool(new McpTool(
                "jflux.errors.list",
                "List recent structured JFlux error reports with optional type and message filters.",
                listSchema,
                this::listErrors));

        JsonObject getSchema = new JsonObject();
        getSchema.addProperty("type", "object");
        JsonObject getProperties = new JsonObject();
        getProperties.add("id", property("string", "Error record identifier."));
        getProperties.add("includeStacktrace", property("boolean", "Include stacktrace entries; defaults to true."));
        getSchema.add("properties", getProperties);
        JsonArray requiredId = new JsonArray();
        requiredId.add("id");
        getSchema.add("required", requiredId);
        registerTool(new McpTool(
                "jflux.errors.get",
                "Retrieve one structured JFlux error report by identifier.",
                getSchema,
                this::getError));

        JsonObject summarySchema = new JsonObject();
        summarySchema.addProperty("type", "object");
        registerTool(new McpTool(
                "jflux.errors.summary",
                "Summarize the bounded JFlux error report store by exception type.",
                summarySchema,
                arguments -> errorStore.summary()));
    }

    private JsonObject listErrors(JsonObject arguments) {
        int limit = integer(arguments, "limit", 20);
        boolean includeStacktrace = bool(arguments, "includeStacktrace", true);
        JsonArray errors = new JsonArray();
        errorStore.list(limit, string(arguments, "type"), string(arguments, "contains"), includeStacktrace)
                .forEach(errors::add);
        JsonObject result = new JsonObject();
        result.add("errors", errors);
        result.addProperty("count", errors.size());
        return result;
    }

    private JsonObject getError(JsonObject arguments) {
        String id = string(arguments, "id");
        JsonObject error = errorStore.get(id, bool(arguments, "includeStacktrace", true));
        if (error == null) {
            JsonObject result = new JsonObject();
            result.addProperty("error", "Error record was not found.");
            return result;
        }
        JsonObject result = new JsonObject();
        result.add("error", error);
        return result;
    }

    private static JsonObject property(String type, String description) {
        JsonObject result = new JsonObject();
        result.addProperty("type", type);
        result.addProperty("description", description);
        return result;
    }

    private static JsonObject object(JsonObject parent, String key) {
        return parent.has(key) && parent.get(key).isJsonObject() ? parent.getAsJsonObject(key) : new JsonObject();
    }

    private static String string(JsonObject object, String key) {
        return object.has(key) && object.get(key).isJsonPrimitive() ? object.get(key).getAsString() : null;
    }

    private static int integer(JsonObject object, String key, int fallback) {
        if (!object.has(key) || !object.get(key).isJsonPrimitive()) return fallback;
        try {
            return object.get(key).getAsInt();
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("MCP argument '" + key + "' must be an integer.");
        }
    }

    private static boolean bool(JsonObject object, String key, boolean fallback) {
        if (!object.has(key) || !object.get(key).isJsonPrimitive()) return fallback;
        try {
            return object.get(key).getAsBoolean();
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("MCP argument '" + key + "' must be boolean.");
        }
    }

    private static String response(JsonElement id, JsonObject result) {
        JsonObject response = new JsonObject();
        response.addProperty("jsonrpc", "2.0");
        response.add("id", id == null ? com.google.gson.JsonNull.INSTANCE : id);
        response.add("result", result);
        return response.toString();
    }

    private static String error(JsonElement id, int code, String message) {
        JsonObject response = new JsonObject();
        response.addProperty("jsonrpc", "2.0");
        response.add("id", id == null ? com.google.gson.JsonNull.INSTANCE : id);
        JsonObject error = new JsonObject();
        error.addProperty("code", code);
        error.addProperty("message", message == null || message.isBlank() ? "MCP request failed." : message);
        response.add("error", error);
        return response.toString();
    }
}
