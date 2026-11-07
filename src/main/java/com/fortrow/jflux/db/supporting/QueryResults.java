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
 * File created by Ryan Fitzgerald (ryan@fortrow.com) on 2026-05-06 11:46:03 EDT
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
