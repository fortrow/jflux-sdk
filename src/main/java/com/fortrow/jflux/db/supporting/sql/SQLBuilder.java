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
 */
package com.fortrow.jflux.db.supporting.sql;

import com.fortrow.jflux.db.supporting.QueryParams;
import com.fortrow.jflux.db.supporting.TableSchema;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Builds validated prepared SQL and keeps its bind values in SQL order. */
public class SQLBuilder {

    private final QueryParams p;
    private final ArrayList<Object> values = new ArrayList<>();
    private int nextValue;

    public SQLBuilder(QueryParams params) {
        this.p = params;
        this.nextValue = 0;
    }

    public String getPreparedString(boolean sqlite) {
        values.clear();
        nextValue = 0;
        p.resetIndexes();

        if(p.isPragma()) return pragmaSql();
        if(!p.getTablesToCreate().isEmpty()) return getCreateTableString(p.getTablesToCreate());

        StringBuilder sql = new StringBuilder();
        if(p.selectSize() > 0) {
            sql.append("SELECT ");
            if(p.distinct()) sql.append("DISTINCT ");
            appendFields(sql, p.selectColumns(), sqlite);
            sql.append(" FROM ").append(p.table());
        } else if(p.insertSize() > 0) {
            appendInsert(sql, sqlite);
        } else if(p.updateSize() > 0 || p.softDelete()) {
            appendUpdate(sql, sqlite);
        } else if(p.delete()) {
            sql.append("DELETE FROM ").append(p.table());
        } else {
            throw new IllegalArgumentException("A query must select, insert, update, delete, create a table, or use PRAGMA.");
        }

        // JOIN clauses belong immediately after FROM and before WHERE.
        for(Join join : p.joins()) appendJoin(sql, join);

        appendConditions(sql, " WHERE ", filterWhere());
        if(p.hasGroupBy()) sql.append(" GROUP BY ").append(p.getGroupByColumn().full());
        appendConditions(sql, " HAVING ", p.having());
        if(p.hasOrderBy()) {
            sql.append(" ORDER BY ").append(p.getOrderByColumn().full())
                    .append(p.getOrderByDirection() == OrderBy.ASC ? " ASC" : " DESC");
        }
        if(p.hasLimit()) sql.append(" LIMIT ").append(p.limit());
        if(p.hasOffset()) sql.append(" OFFSET ").append(p.offset());
        appendReturning(sql);
        return sql.toString();
    }

    private void appendInsert(StringBuilder sql, boolean sqlite) {
        sql.append("INSERT");
        if(p.insertIgnore()) sql.append(sqlite ? " OR IGNORE" : " IGNORE");
        sql.append(" INTO ").append(p.table()).append(" (");
        appendFieldNames(sql, p.insert(), sqlite);
        if(p.setTimestamps()) sql.append(", `created_at`");
        sql.append(") VALUES ");

        int rows = p.hasBatchInsert() ? p.insertRows().size() : 1;
        for(int row = 0; row < rows; row++) {
            if(row > 0) sql.append(", ");
            sql.append("(");
            if(p.hasBatchInsert()) {
                Map<String, Object> valuesForRow = p.insertRows().get(row);
                for(int i = 0; i < p.insertSize(); i++) {
                    if(i > 0) sql.append(", ");
                    Field field = p.insert().get(i);
                    String key = field.column().rawName();
                    if(!valuesForRow.containsKey(key)) {
                        throw new IllegalArgumentException("Every batch insert row must contain column '" + key + "'.");
                    }
                    sql.append("?");
                    values.add(valuesForRow.get(key));
                }
            } else {
                for(int i = 0; i < p.insertSize(); i++) {
                    if(i > 0) sql.append(", ");
                    sql.append("?");
                    values.add(p.insert().get(i).get());
                }
            }
            if(p.setTimestamps()) sql.append(", CURRENT_TIMESTAMP");
            sql.append(")");
        }
    }

    private void appendUpdate(StringBuilder sql, boolean sqlite) {
        sql.append("UPDATE ").append(p.table()).append(" SET ");
        if(p.softDelete()) {
            sql.append("`deleted_at`=CURRENT_TIMESTAMP");
        } else {
            for(int i = 0; i < p.updateSize(); i++) {
                if(i > 0) sql.append(", ");
                Field field = p.update().get(i);
                sql.append(getColumnOrFunctionKey(field, sqlite)).append("=?");
                values.add(field.get());
            }
            if(p.setTimestamps()) sql.append(", `updated_at`=CURRENT_TIMESTAMP");
        }
    }

    private void appendJoin(StringBuilder sql, Join join) {
        sql.append(" ").append(join.getType()).append(" ").append(join.getTable()).append(" ON ");
        appendConditionList(sql, join.conditions());
    }

    private List<Condition> filterWhere() {
        return p.whereConditions();
    }

    private void appendConditions(StringBuilder sql, String prefix, List<Condition> conditions) {
        if(conditions == null || conditions.isEmpty()) return;
        sql.append(prefix);
        appendConditionList(sql, conditions);
    }

    private void appendConditionList(StringBuilder sql, List<Condition> conditions) {
        boolean emitted = false;
        for(Condition condition : conditions) {
            if(condition.isOpen()) {
                if(emitted) sql.append(" AND ");
                sql.append("(");
                emitted = true;
                continue;
            }
            if(condition.isClose()) {
                sql.append(")");
                continue;
            }
            if(condition.isSingleValue()) {
                if(emitted) sql.append(condition.isOr() ? " OR " : " AND ");
                sql.append(String.valueOf(condition.left()));
                emitted = true;
                continue;
            }

            if(emitted) sql.append(condition.isOr() ? " OR " : " AND ");
            Object left = condition.left();
            Object right = condition.right();
            Operator.V operator = condition.operator();
            if(left instanceof ColumnOrFunction && right instanceof ColumnOrFunction) {
                sql.append(((ColumnOrFunction) left).full()).append(Operator.get(operator))
                        .append(((ColumnOrFunction) right).full());
            } else {
                ColumnOrFunction column = left instanceof ColumnOrFunction
                        ? (ColumnOrFunction) left : (ColumnOrFunction) right;
                sql.append(column.full()).append(Operator.get(operator));
                if(Operator.hasRight(operator)) {
                    sql.append("?");
                    values.add(condition.value());
                }
            }
            emitted = true;
        }
    }

    private void appendReturning(StringBuilder sql) {
        if(!p.isReturning()) return;
        sql.append(" RETURNING ");
        appendFields(sql, p.returning(), false);
    }

    private void appendFields(StringBuilder sql, List<Field> fields, boolean sqlite) {
        for(int i = 0; i < fields.size(); i++) {
            if(i > 0) sql.append(", ");
            sql.append(getColumnOrFunctionKey(fields.get(i), sqlite));
        }
    }

    private void appendFieldNames(StringBuilder sql, List<Field> fields, boolean sqlite) {
        appendFields(sql, fields, sqlite);
    }

    private String pragmaSql() {
        StringBuilder sql = new StringBuilder("PRAGMA ").append(p.pragma());
        if(!p.pragmaRead()) {
            if(p.pragmaValue() == null) throw new IllegalArgumentException("A PRAGMA value cannot be null.");
            sql.append(" = ").append(literal(p.pragmaValue()));
        }
        return sql.toString();
    }

    private static String literal(Object value) {
        if(value instanceof Boolean) return ((Boolean) value) ? "1" : "0";
        if(value instanceof Number) return value.toString();
        if(value instanceof Enum<?>) return "'" + ((Enum<?>) value).name().replace("'", "''") + "'";
        return "'" + String.valueOf(value).replace("'", "''") + "'";
    }

    private static String getColumnOrFunctionKey(Field field, boolean sqlite) {
        if(field.getType() == Column.class) return sqlite ? field.column().name() : field.column().full();
        if(field.getType() == SQLFunction.class) return field.function().full();
        throw new IllegalArgumentException("Unsupported SQL field type.");
    }

    public Object next() {
        return nextValue < values.size() ? values.get(nextValue++) : null;
    }

    public void bind(PreparedStatement statement) throws SQLException {
        for(int i = 0; i < values.size(); i++) set(statement, i + 1, values.get(i));
    }

    public int preparedSize() { return values.size(); }

    public int valueSize() { return values.size(); }

    private String getCreateTableString(HashMap<String, ArrayList<TableSchema>> tables) {
        StringBuilder sql = new StringBuilder();
        for(Map.Entry<String, ArrayList<TableSchema>> entry : tables.entrySet()) {
            if(sql.length() > 0) sql.append(" ");
            sql.append("CREATE TABLE IF NOT EXISTS `").append(entry.getKey()).append("` (");
            ArrayList<TableSchema> fields = entry.getValue();
            for(int i = 0; i < fields.size(); i++) {
                if(i > 0) sql.append(", ");
                TableSchema field = fields.get(i);
                sql.append("`").append(field.getCol()).append("` ").append(field.getType());
                if(field.isId()) sql.append(" PRIMARY KEY NOT NULL");
                else if(!field.isNullable()) sql.append(" NOT NULL");
                if(field.getDefaultValue() != null) sql.append(" DEFAULT ").append(field.getDefaultValue());
            }
            sql.append(")");
        }
        return sql.toString();
    }

    public static void set(PreparedStatement statement, int position, Object value) throws SQLException {
        if(value == null) statement.setNull(position, Types.NULL);
        else if(value instanceof String) statement.setString(position, (String) value);
        else if(value instanceof Integer) statement.setInt(position, (Integer) value);
        else if(value instanceof Long) statement.setLong(position, (Long) value);
        else if(value instanceof Short) statement.setShort(position, (Short) value);
        else if(value instanceof Byte) statement.setByte(position, (Byte) value);
        else if(value instanceof Float) statement.setFloat(position, (Float) value);
        else if(value instanceof Double) statement.setDouble(position, (Double) value);
        else if(value instanceof BigDecimal) statement.setBigDecimal(position, (BigDecimal) value);
        else if(value instanceof Boolean) statement.setBoolean(position, (Boolean) value);
        else if(value instanceof byte[]) statement.setBytes(position, (byte[]) value);
        else if(value instanceof LocalDate) statement.setObject(position, value);
        else if(value instanceof LocalDateTime) statement.setObject(position, value);
        else if(value instanceof LocalTime) statement.setObject(position, value);
        else if(value instanceof java.sql.Date) statement.setDate(position, (java.sql.Date) value);
        else if(value instanceof java.sql.Timestamp) statement.setTimestamp(position, (java.sql.Timestamp) value);
        else if(value instanceof java.util.Date) statement.setTimestamp(position, new java.sql.Timestamp(((java.util.Date) value).getTime()));
        else statement.setObject(position, value);
    }
}
