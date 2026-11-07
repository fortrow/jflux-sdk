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

package com.fortrow.jflux.runtime;

import java.time.Instant;
import java.util.List;

/** Metadata controlling function identity, expiration, and authorized replacement. */
public record RuntimeFunctionDescriptor(String id, String version, RuntimeFunctionSource source,
                                        List<String> replaces, Instant expiresAt,
                                        String packageId, String keyId, boolean overrideRequested) {
    public RuntimeFunctionDescriptor(String id, String version, RuntimeFunctionSource source,
                                     List<String> replaces, Instant expiresAt,
                                     String packageId, String keyId) {
        this(id, version, source, replaces, expiresAt, packageId, keyId, false);
    }

    public RuntimeFunctionDescriptor {
        if(id == null || id.isBlank()) throw new IllegalArgumentException("function id is required");
        version = version == null || version.isBlank() ? "0" : version;
        source = source == null ? RuntimeFunctionSource.BUILTIN : source;
        replaces = replaces == null ? List.of() : List.copyOf(replaces);
        packageId = packageId == null || packageId.isBlank() ? id : packageId;
        keyId = keyId == null ? "" : keyId;
    }

    public boolean expired(Instant now) {
        return expiresAt != null && !expiresAt.isAfter(now);
    }
}
