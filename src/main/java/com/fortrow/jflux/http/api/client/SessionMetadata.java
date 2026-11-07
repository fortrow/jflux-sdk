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

package com.fortrow.jflux.http.api.client;

import java.util.Map;
import java.util.LinkedHashMap;
import com.fortrow.jflux.exceptions.ExceptionHandling;

/** Optional, client-reported display hints. Never identity or authorization evidence. */
public final class SessionMetadata {
    private SessionMetadata() {}
    private static final Map<String,Integer> LIMITS = Map.ofEntries(
        Map.entry("machine_id",191), Map.entry("machine_hostname",255), Map.entry("machine_platform",64),
        Map.entry("machine_ip",64), Map.entry("os_name",120), Map.entry("os_version",120),
        Map.entry("device_model",191), Map.entry("cpu_arch",64), Map.entry("cpu_model",191),
        Map.entry("cpu_count",10), Map.entry("memory_bytes",19), Map.entry("client_type",64),
        Map.entry("client_application",120), Map.entry("client_version",64));
    public static Map<String,String> validate(Map<?,?> supplied) {
        if (supplied == null || supplied.size() > LIMITS.size()) throw ExceptionHandling.invalid("Invalid session metadata.");
        Map<String,String> result = new LinkedHashMap<>(); int bytes = 0;
        for (var entry : supplied.entrySet()) {
            if (!(entry.getKey() instanceof String name) || !LIMITS.containsKey(name)
                || !(entry.getValue() instanceof String value) || value.isBlank() || value.length() > LIMITS.get(name)
                || value.codePoints().anyMatch(c -> c < 32 || c == 127)) throw ExceptionHandling.invalid("Invalid session metadata field.");
            if (name.equals("cpu_count") || name.equals("memory_bytes")) {
                if (!value.matches("[1-9][0-9]{0,18}")) throw ExceptionHandling.invalid("Invalid numeric session metadata.");
                try { Long.parseLong(value); } catch (NumberFormatException error) { throw ExceptionHandling.invalid("Invalid numeric session metadata."); }
            }
            bytes += name.length() + value.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
            if (bytes > 4096) throw ExceptionHandling.invalid("Session metadata exceeds limit.");
            result.put(name,value);
        }
        return Map.copyOf(result);
    }
    /** Explicit opt-in to JVM OS/architecture/CPU hints; does not collect serial numbers or network addresses. */
    public static Map<String,String> local(String application, String type, String version) {
        Map<String,String> values = new LinkedHashMap<>();
        values.put("client_application", application); values.put("client_type", type); values.put("client_version", version);
        values.put("os_name", System.getProperty("os.name")); values.put("os_version", System.getProperty("os.version"));
        values.put("cpu_arch", System.getProperty("os.arch")); values.put("cpu_count", Integer.toString(Runtime.getRuntime().availableProcessors()));
        String hostname = System.getenv("COMPUTERNAME"); if (hostname == null || hostname.isBlank()) hostname = System.getenv("HOSTNAME");
        if (hostname != null && !hostname.isBlank()) values.put("machine_hostname",hostname);
        return validate(values);
    }
}
