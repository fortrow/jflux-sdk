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
import com.google.gson.JsonObject;

/** A tool exposed by a jflux MCP server. */
public final class McpTool {

    @FunctionalInterface
    public interface Handler {
        JsonObject call(JsonObject arguments);
    }

    private final String name;
    private final String description;
    private final JsonObject inputSchema;
    private final Handler handler;

    public McpTool(String name, String description, JsonObject inputSchema, Handler handler) {
        if (name == null || name.isBlank()) throw ExceptionHandling.invalid("MCP tool name is required.");
        if (description == null || description.isBlank()) throw ExceptionHandling.invalid("MCP tool description is required.");
        if (inputSchema == null) throw ExceptionHandling.invalid("MCP tool input schema is required.");
        if (handler == null) throw ExceptionHandling.invalid("MCP tool handler is required.");
        this.name = name;
        this.description = description;
        this.inputSchema = inputSchema;
        this.handler = handler;
    }

    public String name() {
        return name;
    }

    public JsonObject definition() {
        JsonObject result = new JsonObject();
        result.addProperty("name", name);
        result.addProperty("description", description);
        result.add("inputSchema", inputSchema.deepCopy());
        return result;
    }

    JsonObject call(JsonObject arguments) {
        return handler.call(arguments == null ? new JsonObject() : arguments);
    }
}
