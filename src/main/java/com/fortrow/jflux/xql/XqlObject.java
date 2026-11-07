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

/** Named, nested XML object used to compose an {@link XqlModel}. */
public class XqlObject {
    private String name;
    private final List<Object> children = new ArrayList<>();
    private boolean required;
    private boolean multiple;

    protected XqlObject(String name) {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("Object name is required.");
        this.name = name;
    }

    public String name() { return name; }

    /** Allows a root model to derive its name after Java has established its runtime type. */
    protected final void name(String name) {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("Object name is required.");
        this.name = name;
    }
    public boolean isRequired() { return required; }
    public boolean isMultiple() { return multiple; }
    public List<Object> children() { return List.copyOf(children); }

    public XqlField field(String name) {
        XqlField field = new XqlField(name);
        children.add(field);
        return field;
    }

    public XqlObject group(String name) {
        XqlObject group = new XqlObject(name);
        children.add(group);
        return group;
    }

    public XqlObject required() { required = true; return this; }
    public XqlObject multiple() { multiple = true; return this; }

    protected void addChild(Object child) { children.add(child); }
}
