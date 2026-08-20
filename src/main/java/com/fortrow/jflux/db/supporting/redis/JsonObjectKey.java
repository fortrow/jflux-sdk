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
 * File created by Ryan Fitzgerald (ryan@fortrow.com) on 2026-05-06 11:46:03 EDT
 * 
 * -------------------------------------------------------------------------

 */
package com.fortrow.jflux.db.supporting.redis;

import com.fortrow.jflux.db.supporting.sql.ColumnOrFunction;

public class JsonObjectKey extends ColumnOrFunction {

    private final String key;

    public JsonObjectKey(String key) { this.key = key; }

    @Override
    public Class getType() {
        return JsonObjectKey.class;
    }

    @Override
    public String getTypeString() {
        return "JsonObjectKey";
    }

    @Override
    public String full() {
        return key;
    }

    @Override
    public String name() {
        return full();
    }
}
