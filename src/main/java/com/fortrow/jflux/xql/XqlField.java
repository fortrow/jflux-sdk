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

import java.util.ArrayList;
import java.util.List;

/** A scalar or repeated XML value in an XQL model. */
public final class XqlField {
    private final String name;
    private final List<String> values = new ArrayList<>();
    private boolean required;
    private boolean searchable;
    private boolean multiple;

    XqlField(String name) {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("Field name is required.");
        this.name = name;
    }

    public String name() { return name; }
    public boolean isRequired() { return required; }
    public boolean isSearchable() { return searchable; }
    public boolean isMultiple() { return multiple; }
    public List<String> values() { return List.copyOf(values); }
    public String value() { return values.isEmpty() ? null : values.getFirst(); }

    public XqlField value(Object value) {
        values.clear();
        if (value != null) values.add(String.valueOf(value));
        return this;
    }

    /** Replaces values while hydrating a model from XML. */
    void replaceValues(List<String> next) {
        values.clear();
        if (next != null) values.addAll(next);
    }

    public XqlField add(Object value) {
        multiple = true;
        if (value != null) values.add(String.valueOf(value));
        return this;
    }

    public XqlField multiple() { multiple = true; return this; }
    public XqlField required() { required = true; return this; }
    public XqlField searchable() { searchable = true; return this; }
}
