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

package com.fortrow.jflux.xql;

import com.fortrow.jflux.exceptions.types.CommonIOException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** UTF-8 XQL object store for development, tests, and self-hosted deployments. */
public final class LocalXqlStorage implements XqlStorage {
    private final Path root;

    public LocalXqlStorage(Path root) {
        if (root == null) throw new IllegalArgumentException("Storage root is required.");
        this.root = root.toAbsolutePath().normalize();
    }

    @Override
    public void put(String key, String content) throws CommonIOException {
        Path target = resolve(key);
        try {
            Files.createDirectories(target.getParent());
            Files.writeString(target, content, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new CommonIOException("Unable to write XQL object: " + key, e);
        }
    }

    @Override
    public String get(String key) throws CommonIOException {
        Path target = resolve(key);
        try {
            return Files.readString(target, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new CommonIOException("Unable to read XQL object: " + key, e);
        }
    }

    private Path resolve(String key) throws CommonIOException {
        if (key == null || key.isBlank()) throw new CommonIOException("XQL object key is required.");
        Path target = root.resolve(key.replace('\\', '/')).normalize();
        if (!target.startsWith(root)) throw new CommonIOException("XQL object key escapes the storage root.");
        return target;
    }
}
