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
import com.google.gson.JsonObject;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Bounded in-memory storage for reports made available to MCP clients. */
public final class McpErrorStore {

    private final int capacity;
    private final int maxStacktraceEntries;
    private final int maxPayloadCharacters;
    private final Deque<McpErrorRecord> records = new ArrayDeque<>();

    public McpErrorStore() {
        this(100, 80, 64 * 1024);
    }

    public McpErrorStore(int capacity, int maxStacktraceEntries, int maxPayloadCharacters) {
        if (capacity < 1) throw ExceptionHandling.invalid("MCP error capacity must be positive.");
        if (maxStacktraceEntries < 0) throw ExceptionHandling.invalid("MCP stacktrace limit cannot be negative.");
        if (maxPayloadCharacters < 512) throw ExceptionHandling.invalid("MCP payload limit must be at least 512 characters.");
        this.capacity = capacity;
        this.maxStacktraceEntries = maxStacktraceEntries;
        this.maxPayloadCharacters = maxPayloadCharacters;
    }

    public synchronized McpErrorRecord add(ReportedException exception) {
        McpErrorRecord record = McpErrorRecord.from(exception, maxStacktraceEntries, maxPayloadCharacters);
        if (records.size() == capacity) records.removeLast();
        records.addFirst(record);
        return record;
    }

    public synchronized List<JsonObject> list(int limit, String type, String contains, boolean includeStacktrace) {
        int safeLimit = Math.max(1, Math.min(limit, capacity));
        List<JsonObject> result = new ArrayList<>(Math.min(safeLimit, records.size()));
        for (McpErrorRecord record : records) {
            if (!record.matches(type, contains)) continue;
            result.add(record.json(includeStacktrace));
            if (result.size() == safeLimit) break;
        }
        return result;
    }

    public synchronized JsonObject get(String id, boolean includeStacktrace) {
        if (id == null || id.isBlank()) return null;
        for (McpErrorRecord record : records) {
            if (id.equals(record.id())) return record.json(includeStacktrace);
        }
        return null;
    }

    public synchronized JsonObject summary() {
        Map<String, Integer> byType = new LinkedHashMap<>();
        for (McpErrorRecord record : records) {
            JsonObject data = record.json(true);
            String type = data.has("type") ? data.get("type").getAsString() : "unknown";
            byType.merge(type, 1, Integer::sum);
        }
        JsonObject result = new JsonObject();
        result.addProperty("count", records.size());
        result.addProperty("capacity", capacity);
        JsonObject types = new JsonObject();
        byType.forEach(types::addProperty);
        result.add("by_type", types);
        if (!records.isEmpty()) {
            result.addProperty("newest_id", records.peekFirst().id());
            result.addProperty("oldest_id", records.peekLast().id());
        }
        return result;
    }

    public synchronized int size() {
        return records.size();
    }
}
