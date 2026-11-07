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
 *
 * File created by Ryan Fitzgerald (ryan@fortrow.com) on 2026-05-06 11:38:29 EDT
 */
package com.fortrow.jflux.db.supporting.sql;

public class Field {

    private ColumnOrFunction key;
    private Object value;

    public Field(ColumnOrFunction key, Object value) {
        this.key = key;
        this.value = value;
    }

    public Class getType() {
        return key.getType();
    }

    public Column column() {
        return (Column) key;
    }

    public SQLFunction function() {
        return (SQLFunction) key;
    }

    public String name() {
        if(key.getType() == Column.class) return column().name();
        else if(key.getType() == SQLFunction.class) return function().full();
        return null;
    }

    public String label() {
        return name();
    }

    public ColumnOrFunction key() {
        return key;
    }

    public <Any> Any get() {
        if(value == null) return null;
        if(value.getClass() == String.class) return (Any) ((String) value);
        else if(value.getClass() == Integer.class) return (Any) ((Integer) (int) value);
        else if(value.getClass() == Boolean.class) return (Any) ((Boolean)(boolean) value);
        else if(value.getClass() == Float.class) return (Any) ((Float) (float) value);
        else return (Any) value;
    }

    public <Any> Any value() {
        return get();
    }

}
