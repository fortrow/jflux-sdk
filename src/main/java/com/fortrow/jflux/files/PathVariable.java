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

package com.fortrow.jflux.files;

import com.fortrow.jflux.exceptions.ExceptionHandling;

/** A named placeholder used in a {@link PathObject}. */
public class PathVariable {

    private final String key;
    private String value;

    public PathVariable(String key) {
        if (key == null || key.isBlank()) throw ExceptionHandling.invalid("Variable key is required.");
        this.key = key;
    }

    public PathVariable(String key, String value) {
        this(key);
        this.value = value;
    }

    public void set(String value) {
        this.value = value;
    }

    public String getKey() {
        return key;
    }

    public String getValue() {
        return value;
    }

    public boolean isset() {
        return value != null;
    }

    PathVariable copy() {
        return new PathVariable(key, value);
    }
}
