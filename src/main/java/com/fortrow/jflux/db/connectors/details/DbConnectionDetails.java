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
package com.fortrow.jflux.db.connectors.details;

import com.fortrow.jflux.db.supporting.DbType;

import java.util.HashMap;

public abstract class DbConnectionDetails {

    protected DbType type;
    protected HashMap<String, DbDetailsPair> values = new HashMap<String, DbDetailsPair>();

    public DbConnectionDetails(DbType type) {
        this.type = type;
    }

    protected abstract void build();

    public <Any> Any get(String key) {
        if(!values.containsKey(key)) return null;
        return values.get(key).get();
    }

    public DbType getType() {
        return type;
    }

    protected void append(String key, Class<?> type, Object value) {
        values.put(key, new DbDetailsPair(type, value));
    }

    public static class DbDetailsPair {

        private Class<?> type;
        private Object v;

        public DbDetailsPair(Class<?> type, Object v) {
            this.type = type;
            this.v = v;
        }

        public <Any> Any get() {
            return (Any) type.cast(v);
        }

    }

}