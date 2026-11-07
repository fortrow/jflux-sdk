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
