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
 * File created by Ryan Fitzgerald (ryan@fortrow.com) on 2026-05-04 15:27:05 EDT
 */
package com.fortrow.jflux.db.supporting;

public class TableSchema {

    private String col;
    private String type;
    private boolean nullable;
    private boolean id;
    private String defaultValue;
    private boolean autoIncrement = false;

    public TableSchema(String col, String type, boolean nullable, boolean id, String defaultValue) {
        this.col = col;
        this.type = type;
        this.nullable = nullable;
        this.id = id;
        this.defaultValue = defaultValue;
    }

    public TableSchema(String col, boolean id) {
        this.col = col;
        this.type = "int";
        this.nullable = false;
        this.id = id;
        this.autoIncrement = true;
    }

    public static TableSchema id() {
        return new TableSchema("id", "varchar(40)", false, true, null);
    }

    public static TableSchema autoIncrement() {
        return new TableSchema("id", "integer", true, true, null);
    }

    public static TableSchema[] timestamps() {
        return new TableSchema[]{
                new TableSchema("created_at", "int", false, false, null),
                new TableSchema("updated_at", "int", false, false, null),
                new TableSchema("deleted_at", "int", true, false, null),
        };
    }

    public static TableSchema col(String col, String type, boolean nullable, String defaultValue) {
        return new TableSchema(col, type, nullable, false, defaultValue);
    }

    public static TableSchema col(String col, String type, boolean nullable) {
        return new TableSchema(col, type, nullable, false, null);
    }

    public String getCol() {
        return col;
    }

    public boolean isId() {
        return id;
    }

    public boolean isNullable() {
        return nullable;
    }

    public String getDefaultValue() {
        return defaultValue;
    }

    public String getType() {
        return type;
    }
}
