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

package com.fortrow.jflux.http.api.server.session;

import com.fortrow.jflux.crypto.builtin.SHA;
import com.fortrow.jflux.http.api.server.exceptions.APIException;
import com.fortrow.jflux.output.json.JSON;
import org.json.simple.JSONObject;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermission;

import java.util.concurrent.ConcurrentHashMap;

/** Per-root-API-key session limits. Every key starts with one session slot. */
public final class SessionCapacity {

    private final ConcurrentHashMap<String, Integer> limits = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Integer> persistedLimits = new ConcurrentHashMap<>();
    private final Path path;

    public SessionCapacity() {
        this.path = null;
    }

    public SessionCapacity(Path resumeStorePath) throws APIException {
        this.path = resumeStorePath == null
                ? null
                : resumeStorePath.resolveSibling(resumeStorePath.getFileName() + ".capacity");
        load();
    }

    public int limitFor(String apiKey) {
        if (apiKey == null || apiKey.isBlank()) return 0;
        Integer current = limits.get(apiKey);
        if (current != null) return current;
        Integer persisted = persistedLimits.get(SHA.get256(apiKey));
        if (persisted == null) return 1;
        limits.putIfAbsent(apiKey, persisted);
        return persisted;
    }

    /** Increases a key's limit by exactly one after a valid allocation assertion. */
    public synchronized int allocate(String apiKey) throws APIException {
        if (apiKey == null || apiKey.isBlank()) throw new APIException("API key is required for session allocation.");
        long next = (long) limitFor(apiKey) + 1;
        if (next > Integer.MAX_VALUE) throw new APIException("Session allocation exceeds the supported limit.");
        limits.put(apiKey, (int) next);
        persistedLimits.put(SHA.get256(apiKey), (int) next);
        persist();
        return (int) next;
    }

    /**
     * @deprecated Allocation is intentionally single-slot. Callers must use
     * {@link #allocate(String)}; values other than one are rejected.
     */
    @Deprecated
    public synchronized int allocate(String apiKey, int additionalSessions) throws APIException {
        if (additionalSessions != 1) {
            throw new APIException("Session allocation increases capacity by exactly one.");
        }
        return allocate(apiKey);
    }

    /** Reset returns the key to the single-session baseline. */
    public synchronized void reset(String apiKey) throws APIException {
        if (apiKey != null && !apiKey.isBlank()) {
            limits.put(apiKey, 1);
            persistedLimits.put(SHA.get256(apiKey), 1);
            persist();
        }
    }

    public void clear() {
        limits.clear();
        persistedLimits.clear();
    }

    private void load() throws APIException {
        if (path == null || !Files.exists(path)) return;
        try {
            JSONObject object = JSON.getObject(Files.readString(path, StandardCharsets.UTF_8));
            for (Object key : object.keySet()) {
                Object value = object.get(key);
                int limit = value instanceof Number number ? number.intValue() : Integer.parseInt(String.valueOf(value));
                if (limit >= 1) persistedLimits.put(String.valueOf(key), limit);
            }
        } catch (Exception ex) {
            throw new APIException("Failed to load encrypted API session capacity metadata.", ex);
        }
    }

    private void persist() throws APIException {
        if (path == null) return;
        try {
            Path parent = path.getParent();
            if (parent != null) Files.createDirectories(parent);
            JSONObject object = new JSONObject();
            object.putAll(persistedLimits);
            Path temporary = path.resolveSibling(path.getFileName() + ".tmp");
            Files.writeString(temporary, JSON.getString(object), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
            try {
                Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException ignored) {
                Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
            }
            try {
                Files.setPosixFilePermissions(path, java.util.EnumSet.of(
                        PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE));
            } catch (UnsupportedOperationException | IOException ignored) {
                // Non-POSIX filesystems do not expose owner-only permissions.
            }
        } catch (IOException ex) {
            throw new APIException("Failed to persist encrypted API session capacity metadata.", ex);
        }
    }
}
