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
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.time.Instant;
/** Immutable, bounded error data exposed through an MCP server. */
public final class McpErrorRecord {

    private final String id;
    private final String occurredAt;
    private final String payload;

    private McpErrorRecord(String id, String occurredAt, String payload) {
        this.id = id;
        this.occurredAt = occurredAt;
        this.payload = payload;
    }

    /** Creates a bounded snapshot of a reported jflux exception. */
    public static McpErrorRecord from(ReportedException exception,
                                      int maxStacktraceEntries,
                                      int maxPayloadCharacters) {
        if (exception == null) throw ExceptionHandling.invalid("MCP exception record requires an exception.");
        JsonObject report = JsonParser.parseString(exception.reportJsonString()).getAsJsonObject();
        JsonArray stacktrace = report.getAsJsonArray("stacktrace");
        while (stacktrace != null && stacktrace.size() > maxStacktraceEntries) {
            stacktrace.remove(stacktrace.size() - 1);
        }

        String payload = report.toString();
        if (payload.length() > maxPayloadCharacters) {
            report.remove("stacktrace");
            report.addProperty("truncated", true);
            payload = report.toString();
        }
        if (payload.length() > maxPayloadCharacters) {
            JsonObject compact = new JsonObject();
            compact.addProperty("type", report.has("type") ? report.get("type").getAsString() : "unknown");
            compact.addProperty("message", truncate(report.get("message"), maxPayloadCharacters / 2));
            compact.addProperty("user_message", truncate(report.get("user_message"), maxPayloadCharacters / 2));
            compact.addProperty("code", report.has("code") ? report.get("code").getAsInt() : 0);
            compact.addProperty("truncated", true);
            payload = compact.toString();
        }

        return new McpErrorRecord(
                java.util.UUID.randomUUID().toString(),
                Instant.now().toString(),
                payload);
    }

    public String id() {
        return id;
    }

    public String occurredAt() {
        return occurredAt;
    }

    /** Returns a fresh JSON object so callers cannot mutate stored error data. */
    public JsonObject json(boolean includeStacktrace) {
        JsonObject result = JsonParser.parseString(payload).getAsJsonObject();
        result.addProperty("id", id);
        result.addProperty("occurred_at", occurredAt);
        if (!includeStacktrace) result.remove("stacktrace");
        return result;
    }

    public boolean matches(String type, String contains) {
        JsonObject data = json(true);
        boolean typeMatches = type == null || type.isBlank()
                || type.equals(data.has("type") ? data.get("type").getAsString() : "")
                || (data.has("type") && data.get("type").getAsString().endsWith("." + type));
        if (!typeMatches) return false;
        if (contains == null || contains.isBlank()) return true;
        String message = data.has("message") ? data.get("message").getAsString() : "";
        String userMessage = data.has("user_message") ? data.get("user_message").getAsString() : "";
        return message.toLowerCase().contains(contains.toLowerCase())
                || userMessage.toLowerCase().contains(contains.toLowerCase());
    }

    private static String truncate(com.google.gson.JsonElement value, int maxCharacters) {
        String text = value == null || value.isJsonNull() ? "" : value.getAsString();
        int limit = Math.max(0, maxCharacters);
        return text.length() <= limit ? text : text.substring(0, limit);
    }
}
