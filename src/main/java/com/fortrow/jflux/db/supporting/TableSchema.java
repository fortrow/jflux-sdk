/*
 * -------------------------------------------------------------------------
 * 
 * Copyright (c) 2026 Fortrow LLC. All rights reserved.
 *  
 * NOTICE: All information contained herein is, and remains the property of 
 * Fortrow, LLC. The intellectual and technical concepts contained herein are 
 * proprietary to [Company Name] and may be covered by U.S. and Foreign Patents, 
 * patents in process, and are protected by trade secret or copyright law.
 * 
 * Dissemination of this information or reproduction of this material is 
 * strictly forbidden unless prior written permission is obtained from 
 * Fortrow, LLC.
 * 
 * -------------------------------------------------------------------------
 * 
 * File created by Ryan Fitzgerald (ryan@fortrow.com) on 2026-05-04 15:27:05 EDT
 * 
 * -------------------------------------------------------------------------

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
