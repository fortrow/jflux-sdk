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
package com.fortrow.jflux.db.supporting;

import com.fortrow.jflux.db.supporting.redis.JsonObjectKey;
import com.fortrow.jflux.db.supporting.sql.Field;
import com.fortrow.jflux.db.supporting.sql.Record;
import com.fortrow.jflux.db.supporting.sql.ColumnOrFunction;
import com.fortrow.jflux.exceptions.types.CriticalException;
import com.fortrow.jflux.json.JSON;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.util.ArrayList;
import java.util.Map;

public class QueryResults {

    private int currRecord = 0;
    private ArrayList<Record> records = new ArrayList<Record>();

    public QueryResults(ResultSet rs, ArrayList<Field> select, String table) {
        try {
            ResultSetMetaData metadata = rs.getMetaData();
            while(rs.next()) {
                ArrayList<Field> fields = new ArrayList<Field>();
                int count = select == null || select.isEmpty() ? metadata.getColumnCount() : select.size();
                for(int i=0; i<count; i++) {
                    ColumnOrFunction col = select != null && !select.isEmpty()
                            ? select.get(i).key()
                            : new com.fortrow.jflux.db.supporting.sql.Column(table, metadata.getColumnLabel(i + 1));
                    fields.add(new Field(col, rs.getObject(i + 1)));
                }
                records.add(new Record(fields, table));
            }
        } catch (Exception e) {
            new CriticalException("Failed to get results from query.", e).report();
        }
    }

    public QueryResults(String jsonString) {
        try {
            JSONArray arr = JSON.getArray(jsonString);
            for (Object obj : arr) {
                if (obj instanceof JSONObject) {
                    Map<String, Object> map = (Map<String, Object>) obj;
                    ArrayList<Field> fields = new ArrayList<>();
                    for (Map.Entry<String, Object> entry : map.entrySet()) {
                        String key = entry.getKey();
                        Object value = entry.getValue();
                        JsonObjectKey keyObj = new JsonObjectKey(key);
                        fields.add(new Field(keyObj, value));
                    }
                    records.add(new Record(fields));
                }
            }
        } catch (Exception e) {
            new CriticalException("Failed to get results from query.", e).report();
        }
    }

    public ArrayList<Record> getList() {
        return records;
    }

    public Record next() {
        if(currRecord == records.size()) return null;
        Record r = records.get(currRecord);
        currRecord++;
        return r;
    }

    public int size() {
        return records.size();
    }

}
