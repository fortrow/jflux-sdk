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

/** Limits used while expanding a runtime JAR into memory. */
public record RuntimeLoadOptions(int maxEntries, int maxClassBytes,
                                 int maxResourceBytes, long maxTotalBytes) {
    public RuntimeLoadOptions {
        if(maxEntries < 1 || maxClassBytes < 1 || maxResourceBytes < 1 || maxTotalBytes < 1) {
            throw new IllegalArgumentException("Runtime JAR limits must be positive");
        }
        if(maxTotalBytes < maxClassBytes) throw new IllegalArgumentException("Total limit is too small");
    }

    public static RuntimeLoadOptions defaults() {
        return new RuntimeLoadOptions(4096, 32 * 1024 * 1024, 16 * 1024 * 1024, 128L * 1024 * 1024);
    }
}
