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
 * File created by Ryan Fitzgerald (ryan@fortrow.com) on 2026-05-11 20:47:28 EDT
 */
package com.fortrow.jflux.http.api.base.models;

import com.fortrow.jflux.crypto.Rand;
import com.fortrow.jflux.crypto.builtin.A32;
import com.fortrow.jflux.crypto.builtin.SHA;
import com.fortrow.jflux.db.supporting.TableSchema;
import com.fortrow.jflux.db.supporting.sql.Record;
import com.fortrow.jflux.encoding.Hex;
import com.fortrow.jflux.http.api.base.endpoints.expectations.ApiExpectations;
import com.fortrow.jflux.http.api.base.endpoints.permissions.ApiEndpointPermissions;
import com.fortrow.jflux.http.api.base.endpoints.permissions.ApiUserPermissions;
import com.fortrow.jflux.http.api.base.prereqs.ApiService;
import com.fortrow.jflux.http.api.server.containers.BodyParameters;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public abstract class ApiModel {

    protected String modelName;
    protected String modelId;
    protected Class<? extends ApiModelField>[] fields;
    private ApiService service;
    private final ArrayList<ApiModelField> values = new ArrayList<>();

    protected ApiModel() { }

    public ApiModel(String modelName, ApiService service) {
        this.service = service;
        initialize(modelName);
    }

    protected final void initialize(String modelName) {
        this.modelName = modelName == null || modelName.isBlank() ? getClass().getSimpleName() : modelName;
        String key = SHA.get256((service == null ? "" : service.getAppKey()) + ":" + this.modelName);
        this.modelId = A32.get(key);
        ArrayList<Class<? extends ApiModelField>> list = build(new ArrayList<>());
        Class<?>[] fields = new Class<?>[list.size()];
        list.toArray(fields);
        this.fields = (Class<? extends ApiModelField>[]) fields;
        this.values.clear();
        for(Class<?> field : fields) {
            this.values.add(newField(field));
        }
    }

    void setService(ApiService service) {
        this.service = service;
        if(this.modelName == null || this.values.isEmpty()) initialize(getClass().getSimpleName());
    }

    protected abstract ArrayList<Class<? extends ApiModelField>> build(ArrayList<Class<? extends ApiModelField>> list);

    public String getModelName() { return modelName; }
    public String getModelId() { return modelId; }
    public String getTableName() { return tableName(); }
    public String getResourcePath() { return resourcePath(); }
    public ApiService getService() { return service; }

    protected String tableName() { return modelName; }
    protected String resourcePath() { return modelName; }
    protected ApiExpectations expectations() { return ApiExpectations.none(); }
    protected ApiEndpointPermissions permissions() { return ApiEndpointPermissions.none(); }
    protected boolean requireProtectedFieldPermissions() { return false; }
    protected String fieldReadPermission(ApiModelField field) { return fieldPermission(field, "read"); }
    protected String fieldWritePermission(ApiModelField field) { return fieldPermission(field, "write"); }

    public ApiExpectations getExpectations() { return expectations(); }
    public ApiEndpointPermissions getPermissions() { return permissions(); }

    public List<ApiModelField> getFields() {
        return List.copyOf(values);
    }

    public ApiModelField getField(String name) {
        if(name == null) return null;
        for(ApiModelField field : values) {
            if(field.getName().equalsIgnoreCase(name)) return field;
        }
        return null;
    }

    public ApiModelField primaryKeyField() {
        for(ApiModelField field : values) {
            if(field.isId()) return field;
        }
        return getField("id");
    }

    public String primaryKeyName() {
        ApiModelField field = primaryKeyField();
        return field == null ? "id" : field.getName();
    }

    public String[] readableColumns() {
        return readableColumns(true);
    }

    public String[] readableColumns(boolean authenticated) {
        return readableColumns(authenticated, null);
    }

    public String[] readableColumns(boolean authenticated, ApiUserPermissions userPermissions) {
        ArrayList<String> columns = new ArrayList<>();
        for(ApiModelField field : values) {
            if(canReadField(field, authenticated, userPermissions)) columns.add(field.getName());
        }
        return columns.toArray(new String[0]);
    }

    public HashMap<String, Object> writableValues(BodyParameters params, boolean includeIds) {
        return writableValues(params, includeIds, true);
    }

    public HashMap<String, Object> writableValues(BodyParameters params, boolean includeIds, boolean authenticated) {
        return writableValues(params, includeIds, authenticated, null);
    }

    public HashMap<String, Object> writableValues(BodyParameters params, boolean includeIds, boolean authenticated, ApiUserPermissions userPermissions) {
        HashMap<String, Object> output = new HashMap<>();
        if(params == null) return output;
        for(ApiModelField field : values) {
            if(!canWriteField(field, authenticated, userPermissions)) continue;
            if(field.isAutoIncrementId()) continue;
            if(field.isId() && !includeIds) continue;
            if(params.present(field.getName())) output.put(field.getName(), coerce(field, params.get(field.getName())));
        }
        return output;
    }

    public HashMap<String, Object> createValues(BodyParameters params) {
        return createValues(params, true);
    }

    public HashMap<String, Object> createValues(BodyParameters params, boolean authenticated) {
        return createValues(params, authenticated, null);
    }

    public HashMap<String, Object> createValues(BodyParameters params, boolean authenticated, ApiUserPermissions userPermissions) {
        HashMap<String, Object> output = writableValues(params, true, authenticated, userPermissions);
        ApiModelField id = primaryKeyField();
        if(id != null && id.getType() == FieldType.ID_STRING && !output.containsKey(id.getName())) {
            output.put(id.getName(), newStringId());
        }
        return output;
    }

    public JSONObject rowJson(Record record) {
        return rowJson(record, true);
    }

    public JSONObject rowJson(Record record, boolean authenticated) {
        return rowJson(record, authenticated, null);
    }

    public JSONObject rowJson(Record record, boolean authenticated, ApiUserPermissions userPermissions) {
        JSONObject obj = new JSONObject();
        if(record == null) return obj;
        for(ApiModelField field : values) {
            if(canReadField(field, authenticated, userPermissions)) obj.put(field.getName(), record.val(field.getName()));
        }
        return obj;
    }

    public JSONArray rowsJson(Iterable<Record> records) {
        return rowsJson(records, true);
    }

    public JSONArray rowsJson(Iterable<Record> records, boolean authenticated) {
        return rowsJson(records, authenticated, null);
    }

    public JSONArray rowsJson(Iterable<Record> records, boolean authenticated, ApiUserPermissions userPermissions) {
        JSONArray arr = new JSONArray();
        if(records == null) return arr;
        for(Record record : records) arr.add(rowJson(record, authenticated, userPermissions));
        return arr;
    }

    public TableSchema[] tableSchema() {
        ArrayList<TableSchema> schema = new ArrayList<>();
        for(ApiModelField field : values) {
            schema.add(toTableSchema(field));
        }
        return schema.toArray(new TableSchema[0]);
    }

    public void migrate() {
        if(service != null) service.migrateModel(this);
    }

    public static ApiModel one(Value... values) {
        return null;
    }

    public ApiModel create(Value... values) {
        apply(values);
        return this;
    }

    public ApiModel update(Value... values) {
        apply(values);
        return this;
    }

    public ApiModel sessionExec() {
        return this;
    }

    public ApiModel exec() {
        return this;
    }

    public boolean canReadField(ApiModelField field, boolean authenticated, ApiUserPermissions userPermissions) {
        if(field == null || !field.canRead(authenticated)) return false;
        if(!requiresFieldPermission(field)) return true;
        return userPermissions != null && userPermissions.has(fieldReadPermission(field));
    }

    public boolean canWriteField(ApiModelField field, boolean authenticated, ApiUserPermissions userPermissions) {
        if(field == null || !field.canWrite(authenticated)) return false;
        if(!requiresFieldPermission(field)) return true;
        return userPermissions != null && userPermissions.has(fieldWritePermission(field));
    }

    private boolean requiresFieldPermission(ApiModelField field) {
        return requireProtectedFieldPermissions() && field.getAccess() == FieldAccess.PROTECTED;
    }

    private String fieldPermission(ApiModelField field, String action) {
        String fieldName = field == null ? "unknown" : field.getName();
        return "model:" + modelName + ":field:" + fieldName + ":" + action;
    }

    private void apply(Value... values) {
        if(values == null) return;
        for(Value val : values) {
            if(val == null) continue;
            ApiModelField field = getField(val.getField());
            if(field != null) field.set(coerce(field, val.getValue()));
        }
    }

    private ApiModelField newField(Class<?> field) {
        try {
            Constructor<?> c = field.getConstructor();
            Object instance = c.newInstance();
            return (ApiModelField) instance;
        } catch (NoSuchMethodException | InvocationTargetException | InstantiationException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    private TableSchema toTableSchema(ApiModelField field) {
        if(field.getType() == FieldType.ID_AUTO_INT) return TableSchema.autoIncrement();
        if(field.getType() == FieldType.ID_STRING) return new TableSchema(field.getName(), "varchar(96)", false, true, null);
        Object defaultValue = field.getDefaultValue();
        return TableSchema.col(field.getName(), sqlType(field.getType()), field.isNullable(), defaultValue == null ? null : String.valueOf(defaultValue));
    }

    private String sqlType(FieldType type) {
        if(type == null) return "text";
        switch (type) {
            case BOOLEAN:
                return "integer";
            case TIMESTAMP:
            case DATE:
            case TIME:
            case FOREIGN_ID:
            case ID_STRING:
            case STRING:
                return "text";
            case LARGE_STRING:
            case EXTRA_LARGE_STRING:
                return "text";
            case ID_AUTO_INT:
                return "integer";
            default:
                return "text";
        }
    }

    private Object coerce(ApiModelField field, Object value) {
        if(value == null || field == null) return value;
        switch (field.getType()) {
            case BOOLEAN:
                if(value instanceof Boolean) return ((Boolean) value) ? 1 : 0;
                String bool = String.valueOf(value).trim().toLowerCase();
                return (bool.equals("true") || bool.equals("1") || bool.equals("yes")) ? 1 : 0;
            case TIMESTAMP:
                if(value instanceof LocalDateTime) return ((LocalDateTime) value).toEpochSecond(ZoneOffset.UTC);
                return value;
            default:
                return value;
        }
    }

    private String newStringId() {
        return A32.get(SHA.get256(Hex.getHex(Rand.secure(32)) + ":" + modelName));
    }
}
