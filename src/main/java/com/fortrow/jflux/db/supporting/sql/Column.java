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

public class Column extends ColumnOrFunction {

    private String table;
    private String name;

    public Column(String table, String name) {
        this.table = table;
        this.name = name;
    }

    public String full() {
        return "`" + this.table + "`.`" + this.name + "`";
    }

    public String name() {
        return "`" + this.name + "`";
    }

    public Class getType() {
        return Column.class;
    }

    public String getTypeString() {
        return "Column";
    }
}
