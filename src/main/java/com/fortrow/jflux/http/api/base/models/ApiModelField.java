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
 * File created by Ryan Fitzgerald (ryan@fortrow.com) on 2026-05-04 15:27:04 EDT
 * 
 * -------------------------------------------------------------------------

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
