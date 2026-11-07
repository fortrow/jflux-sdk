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
 * File created by Ryan Fitzgerald (ryan@fortrow.com) on 2026-05-04 15:27:04 EDT
 */
package com.fortrow.jflux.http.api.base.models;

public abstract class ApiModelField {

    private String fieldName;
    private FieldType type;
    private FieldAccess access;
    private Object value = null;

    public ApiModelField() {
        this.fieldName = name();
        this.type = type();
        this.access = access();
    }

    public ApiModelField(Object value) {
        this.fieldName = name();
        this.type = type();
        this.access = access();
        this.value = value;
    }

    public abstract String name();
    protected abstract FieldType type();
    protected abstract FieldAccess access();

    protected boolean nullable() { return true; }
    protected Object defaultValue() { return null; }

    public String getName() { return fieldName; }
    public FieldType getType() { return type; }
    public FieldAccess getAccess() { return access; }
    public Object getValue() { return value; }
    public boolean isId() { return type == FieldType.ID_AUTO_INT || type == FieldType.ID_STRING; }
    public boolean isAutoIncrementId() { return type == FieldType.ID_AUTO_INT; }
    public boolean canRead(boolean authenticated) {
        if(access == FieldAccess.PRIVATE) return false;
        return access == FieldAccess.PUBLIC || authenticated;
    }

    public boolean canWrite(boolean authenticated) {
        if(access == FieldAccess.PRIVATE) return false;
        return access == FieldAccess.PUBLIC || authenticated;
    }

    public boolean canRead() { return canRead(true); }
    public boolean canWrite() { return canWrite(true); }
    public boolean isNullable() { return isId() ? false : nullable(); }
    public Object getDefaultValue() { return defaultValue(); }

    public ApiModelField set(Object value) {
        this.value = value;
        return this;
    }

    public <Any> Any v() {
        return (Any) value;
    }

}
