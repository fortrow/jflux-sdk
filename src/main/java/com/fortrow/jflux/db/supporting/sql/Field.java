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
 * File created by Ryan Fitzgerald (ryan@fortrow.com) on 2026-05-06 11:38:29 EDT
 * 
 * -------------------------------------------------------------------------

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
