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
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;

/** Durable searchable-field index. Each value maps to a model and object id. */
public final class XqlSearchIndex {
    public record Hit(String model, String id, String field, String value) { }
    private final Path file;
    private final Object lock = new Object();
    public XqlSearchIndex(Path file) { this.file = file.toAbsolutePath().normalize(); }
    public void index(String model, String id, String field, String value) throws CommonIOException {
        synchronized (lock) { try { Files.createDirectories(file.getParent()); Files.writeString(file, encode(model, id, field, value) + "\n", StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND); } catch (IOException e) { throw new CommonIOException("Unable to write XQL search index.", e); } }
    }
    public List<Hit> search(String value) throws CommonIOException {
        synchronized (lock) { try { if (!Files.exists(file)) return List.of(); List<Hit> result = new ArrayList<>(); for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) { String[] p = line.split("\\t", -1); if (p.length == 4 && decode(p[3]).equals(value)) result.add(new Hit(decode(p[0]), decode(p[1]), decode(p[2]), decode(p[3]))); } return result; } catch (IOException | RuntimeException e) { throw new CommonIOException("Unable to read XQL search index.", e); } }
    }
    private static String encode(String... values) { return java.util.Arrays.stream(values).map(v -> java.util.Base64.getUrlEncoder().withoutPadding().encodeToString((v == null ? "" : v).getBytes(StandardCharsets.UTF_8))).reduce((a,b) -> a + "\t" + b).orElse(""); }
    private static String decode(String value) { return new String(java.util.Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8); }
}
