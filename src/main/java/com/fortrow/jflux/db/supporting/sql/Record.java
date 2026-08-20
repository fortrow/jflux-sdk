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
package com.fortrow.jflux.db.supporting.sql;

import com.fortrow.jflux.output.json.JSON;
import com.fortrow.jflux.todash.__;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;

;

public class Record {

    Field[] fields;
    private int currField = 0;
    private String table = null;

    public Record(ArrayList<Field> fields, String table) {
        this.fields = new Field[fields.size()];
        this.fields = fields.toArray(this.fields);
        this.table = table;
    }

    public Record(ArrayList<Field> fields) {
        this.fields = new Field[fields.size()];
        this.fields = fields.toArray(this.fields);
    }

    public Object[] pair(int index) {
        Field current = fields[index];
        return new Object[]{current.name(), val(index)};
    }

    public Object[][] toArray() {
        Object[][] arr = new Object[fields.length][2];
        for(int i=0; i<fields.length; i++) {
            arr[i] = pair(i);
        }
        return arr;
    }

    public String toString() {
        return Arrays.deepToString(toArray());
    }

    public String json() {
        Object[][] objArr = toArray();
        JSONObject root = new JSONObject();
        JSONArray arr = new JSONArray();
        for(int i=0; i< objArr.length; i++) {
            JSONObject data = new JSONObject();
            data.put((String) objArr[i][0].toString(), objArr[i][1]);
            arr.add(data);
        }
        root.put(table, arr);
        return JSON.getString(root);
    }



    public String column(int index) {
        return fields[index].column().name();
    }

    public String function(int index) {
        return fields[index].function().name();
    }

    public <Any> Any  val(int index) {
        return fields[index].get();
    }

    public <Any> Any val(String name) {
        for(int i=0; i<fields.length; i++) {
            String fieldName = fields[i].name();
            if(__.same(fieldName, name) || __.same(fieldName, "`" + name + "`")) return fields[i].get();
        }
        return null;
    }


}
