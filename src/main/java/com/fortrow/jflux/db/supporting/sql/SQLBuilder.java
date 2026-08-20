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

import com.fortrow.jflux.db.supporting.QueryParams;
import com.fortrow.jflux.db.supporting.TableSchema;
import com.fortrow.jflux.todash.__;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoField;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;

public class SQLBuilder {

    private int preparedSize = 0;
    QueryParams p;

    public SQLBuilder(QueryParams params) {

        this.p = params;
        preparedSize = params.selectSize()
                + (params.insertSize() * 2)
                + (params.updateSize() * 2)
                + (params.whereSize() * 2)
                + (params.joinSize() * 2);

    }

    public String getPreparedString(boolean sqlite) {

        //TODO add support for sql functions, delete, order by, group by, and LIKE

        if(!p.getTablesToCreate().isEmpty()) return getCreateTableString(p.getTablesToCreate());

        String sql = "";

        if(p.selectSize() > 0) {
            sql += "SELECT";
            for(int i=0; i<p.selectSize(); i++) {
                sql += (i > 0) ? ", " : " ";
                sql += getColumnOrFunctionKey(p.nextSelect(), sqlite);
                if(p.selectSize() == 1) sql += " ";
            }
            sql += "FROM " + p.table() + " ";
        } else if(p.insertSize() > 0) {
            sql += "INSERT INTO " + p.table() + " (";
            for (int i = 0; i < p.insertSize(); i++) {
                sql += (i > 0) ? ", " : "";
                sql += getColumnOrFunctionKey(p.nextInsert(), sqlite);
            }
            if(p.setTimestamps()) {
                sql += p.table() + ".`created_at`";
            }
            sql += ") VALUES (";
            for (int i = 0; i < p.insertSize(); i++) {
                sql += (i > 0) ? ", " : "";
                sql += "?";
            }
            if(p.setTimestamps()) {
                sql += ", now()";
            }
            sql += ")";
            p.resetIndexes();
            return cleanQuery(sql);
        } else if(p.updateSize() > 0 || p.softDelete()) {
            if(p.softDelete()) {
                sql += "UPDATE " + p.table() + " SET ";
                sql += p.table() + ".`deleted_at`=now() ";
            } else {
                sql += "UPDATE " + p.table() + " SET ";
                for(int i=0; i<p.updateSize(); i++) {
                    sql += (i > 0) ? ", " : "";
                    sql += getColumnOrFunctionKey(p.nextUpdate(), sqlite)+ "=?";
                    if(p.updateSize() == 1) sql += " ";
                }
                if(p.setTimestamps()) {
                    sql += p.table() + ".`updated_at`=now() ";
                }
            }
        } else if(p.delete()) {
            sql += "DELETE FROM " + p.table() + " ";
        }

        if(p.whereSizeNotForJoin() > 0) {
            sql += " WHERE ";
            int s = p.whereSizeNotForJoin();
            Condition c = p.nextNotJoinWhere();
            if(c.isSingleValue()) {
                if(c.left().getClass() == String.class) sql += c.left() + " ";
                else if(c.left() instanceof ColumnOrFunction) sql += ((ColumnOrFunction) c.left()).full() + " ";
            } else {
                if (c.isGroup() && c.isOpen()) {
                    sql += "(";
                    s--;
                }
                for (int i = 0; i < s; i++) {
                    if (c.isGroup()) {
                        if (c.isOpen()) {
                            sql += "(";
                        } else if (c.isClose()) {
                            sql += ")";
                        }
                        c = p.nextNotJoinWhere();
                        continue;
                    }

                    if (i == 0) {
                        Object left = c.left();
                        Operator.V operator = c.operator();
                        Object right = c.right();
                        ColumnOrFunction col = null;
                        if(left instanceof ColumnOrFunction && right instanceof ColumnOrFunction) {
                            sql += " " + ((ColumnOrFunction) left).full() + Operator.get(operator) + ((ColumnOrFunction) right).full() + " ";
                            c = p.nextNotJoinWhere();
                            continue;
                        }
                        if (left instanceof ColumnOrFunction) col = (ColumnOrFunction) left;
                        else if (right instanceof ColumnOrFunction) col = (ColumnOrFunction) right;
                        if (col == null) {
                            c = p.nextNotJoinWhere();
                            continue;
                        }
                        if (Operator.hasRight(operator)) {
                            sql += col.full() + Operator.get(operator) + "? ";
                        } else {
                            sql += col.full() + Operator.get(operator);
                        }
                    } else {
                        Object left = c.left();
                        Operator.V operator = c.operator();
                        Object right = c.right();
                        ColumnOrFunction col = null;
                        if(left instanceof ColumnOrFunction && right instanceof ColumnOrFunction) {
                            sql += (c.isOr() ? " OR " : " AND ") + ((ColumnOrFunction) left).full() + Operator.get(operator) + ((ColumnOrFunction) right).full() + " ";
                            c = p.nextNotJoinWhere();
                            continue;
                        }
                        if (left instanceof ColumnOrFunction) col = (ColumnOrFunction) left;
                        else if (right instanceof ColumnOrFunction) col = (ColumnOrFunction) right;
                        if (col == null) {
                            c = p.nextNotJoinWhere();
                            continue;
                        }
                        if (Operator.hasRight(operator)) {
                            sql += (c.isOr() ? " OR " : " AND ") + col.full() + Operator.get(operator) + "? ";
                        } else {
                            sql += (c.isOr() ? " OR " : " AND ") + col.full() + Operator.get(operator);
                        }
                    }
                    c = p.nextNotJoinWhere();
                }
            }
        }

        if (p.joinSize() > 0) {
            for (int i = 0; i < p.joinSize(); i++) {
                Join j = p.nextJoin();
                sql += " " + j.getType() + " " + j.getTable() + " ON ";
                int s = j.conditionsSize();
                Condition c = j.nextCondition();
                if (c.isGroup() && c.isOpen()) {
                    sql += "(";
                    s--;
                }
                for (int x = 0; x < s; x++) {
                    if (c.isGroup()) {
                        if (c.isOpen()) {
                            sql += "(";
                        } else if (c.isClose()) {
                            sql += ")";
                        }
                        c = j.nextCondition();
                        continue;
                    }

                    if (i == 0) {
                        Object left = c.left();
                        Operator.V operator = c.operator();
                        Object right = c.right();
                        ColumnOrFunction col = null;
                        if(left instanceof ColumnOrFunction && right instanceof ColumnOrFunction) {
                            sql += " " + ((ColumnOrFunction) left).full() + Operator.get(operator) + ((ColumnOrFunction) right).full() + " ";
                            c = j.nextCondition();
                            continue;
                        }
                        if (left instanceof ColumnOrFunction) col = (ColumnOrFunction) left;
                        else if (right instanceof ColumnOrFunction) col = (ColumnOrFunction) right;
                        if (col == null) {
                            c = j.nextCondition();
                            continue;
                        }
                        if (Operator.hasRight(operator)) {
                            sql += col.full() + Operator.get(operator) + "? ";
                        } else {
                            sql += col.full() + Operator.get(operator);
                        }
                    } else {
                        Object left = c.left();
                        Operator.V operator = c.operator();
                        Object right = c.right();
                        ColumnOrFunction col = null;
                        if(left instanceof ColumnOrFunction && right instanceof ColumnOrFunction) {
                            sql += (c.isOr() ? " OR " : " AND ") + ((ColumnOrFunction) left).full() + Operator.get(operator) + ((ColumnOrFunction) right).full() + " ";
                            c = j.nextCondition();
                            continue;
                        }
                        if (left instanceof ColumnOrFunction) col = (ColumnOrFunction) left;
                        else if (right instanceof ColumnOrFunction) col = (ColumnOrFunction) right;
                        if (col == null) {
                            c = j.nextCondition();
                            continue;
                        }
                        if (Operator.hasRight(operator)) {
                            sql += (c.isOr() ? " OR " : " AND ") + col.full() + Operator.get(operator) + "? ";
                        } else {
                            sql += (c.isOr() ? " OR " : " AND ") + col.full() + Operator.get(operator);
                        }
                    }
                    c = j.nextCondition();
                }
            }
        }

        if(p.whereSizeForJoin() > 0) {
            sql += " WHERE ";
            int s = p.whereSizeForJoin();
            Condition c = p.nextJoinWhere();
            if(c.isSingleValue()) {
                if(c.left().getClass() == String.class) sql += c.left() + " ";
                else if(c.left() instanceof ColumnOrFunction) sql += ((ColumnOrFunction) c.left()).full() + " ";
            } else {
                if (c.isGroup() && c.isOpen()) {
                    sql += "(";
                    s--;
                }
                for (int i = 0; i < s; i++) {
                    if (c.isGroup()) {
                        if (c.isOpen()) {
                            sql += "(";
                        } else if (c.isClose()) {
                            sql += ")";
                        }
                        c = p.nextJoinWhere();
                        continue;
                    }

                    if (i == 0) {
                        Object left = c.left();
                        Operator.V operator = c.operator();
                        Object right = c.right();
                        ColumnOrFunction col = null;
                        if(left instanceof ColumnOrFunction && right instanceof ColumnOrFunction) {
                            sql += " " + ((ColumnOrFunction) left).full() + Operator.get(operator) + ((ColumnOrFunction) right).full() + " ";
                            c = p.nextJoinWhere();
                            continue;
                        }
                        if (left instanceof ColumnOrFunction) col = (ColumnOrFunction) left;
                        else if (right instanceof ColumnOrFunction) col = (ColumnOrFunction) right;
                        if (col == null) {
                            c = p.nextJoinWhere();
                            continue;
                        }
                        if (Operator.hasRight(operator)) {
                            sql += col.full() + Operator.get(operator) + "? ";
                        } else {
                            sql += col.full() + Operator.get(operator);
                        }
                    } else {
                        Object left = c.left();
                        Operator.V operator = c.operator();
                        Object right = c.right();
                        ColumnOrFunction col = null;
                        if(left instanceof ColumnOrFunction && right instanceof ColumnOrFunction) {
                            sql += (c.isOr() ? " OR " : " AND ") + ((ColumnOrFunction) left).full() + Operator.get(operator) + ((ColumnOrFunction) right).full() + " ";
                            c = p.nextJoinWhere();
                            continue;
                        }
                        if (left instanceof ColumnOrFunction) col = (ColumnOrFunction) left;
                        else if (right instanceof ColumnOrFunction) col = (ColumnOrFunction) right;
                        if (col == null) {
                            c = p.nextJoinWhere();
                            continue;
                        }
                        if (Operator.hasRight(operator)) {
                            sql += (c.isOr() ? " OR " : " AND ") + col.full() + Operator.get(operator) + "? ";
                        } else {
                            sql += (c.isOr() ? " OR " : " AND ") + col.full() + Operator.get(operator);
                        }
                    }
                    c = p.nextJoinWhere();
                }
            }
        }

        if(p.hasGroupBy()) {
            sql += " GROUP BY " + p.getGroupByColumn().full();
        }

        if(p.hasOrderBy()) {
            sql += " ORDER BY " + p.getOrderByColumn().full() + (p.getOrderByDirection() == OrderBy.ASC ? " ASC" : " DESC");
        }

        p.resetIndexes();
        return cleanQuery(sql);
    }

    private static String getColumnOrFunctionKey(Field field, boolean sqlite) {
        if(field.getType() == Column.class) return sqlite ? field.column().name() : field.column().full();
        else if(field.getType() == SQLFunction.class) return field.function().full();
        return null;
    }

    public Object next() {
        Object v;
        v = p.nextInsert();
        if(v != null) return v;
        v = p.nextUpdate();
        if(v != null) return v;
        v = p.nextNotJoinWhere();
        if(v != null && ((Condition) v).isDualColumns()) return next();
        if(v != null) return v;
        v = p.nextJoin().nextCondition();
        if(v != null && ((Condition) v).isDualColumns()) return next();
        if(v != null) return v;
        v = p.nextJoinWhere();
        if(v != null && ((Condition) v).isDualColumns()) return next();
        if(v != null) return v;
        return null;
    }

    public int preparedSize() {
        return preparedSize;
    }

    public int valueSize() {
        return p.insertSize()
                + p.updateSize()
                + p.whereSize()
                + p.joinSize();
    }

    private String getCreateTableString(HashMap<String, ArrayList<TableSchema>> tables) {
        String sql = "";
        for(String table : tables.keySet()) {
            sql += "CREATE TABLE IF NOT EXISTS " + table + "(";
            ArrayList<TableSchema> fields = tables.get(table);
            boolean commaFlag = true;
            for(TableSchema field : fields) {
                if(field.isId()) {
                    sql += (!commaFlag ? ", " : "") + "`" + field.getCol()  + "` " + field.getType() + " PRIMARY KEY NOT NULL";
                } else {
                    sql += (!commaFlag ? ", `" : "`") + field.getCol() + "` " + field.getType();
                    if(!field.isNullable()) sql += " NOT NULL";
                    if(field.getDefaultValue() != null) sql += " DEFAULT " + field.getDefaultValue();
                }
                commaFlag = false;
            }
            sql += "); ";
        }
        return sql;
    }

    public static void set(PreparedStatement stmt, int pos, Object value) throws SQLException {
        if(!__.isset(value)) stmt.setString(pos, null);
        else if(value.getClass() == String.class) stmt.setString(pos, (String) value);
        else if(value.getClass() == Integer.class) stmt.setInt(pos, (Integer) value);
        else if(value.getClass() == Long.class) stmt.setLong(pos, (Long) value);
        else if(value.getClass() == Float.class) stmt.setFloat(pos, (Float) value);
        else if(value.getClass() == Double.class) stmt.setDouble(pos, (Double) value);
        else if(value.getClass() == Boolean.class) stmt.setBoolean(pos, (Boolean) value);
        else if(value.getClass() == LocalDate.class) stmt.setDate(pos, getDate((LocalDate) value));
        else if(value.getClass() == Date.class) stmt.setTimestamp(pos, getDateTime((Date) value));
        else if(value.getClass() == LocalDateTime.class) stmt.setTimestamp(pos, getDateTime((LocalDateTime) value));
        else if(value.getClass() == LocalTime.class) stmt.setTime(pos, getTime((LocalTime) value));
    }

    private static java.sql.Date getDate(LocalDate ld) {
        return new java.sql.Date(ld.getLong(ChronoField.MILLI_OF_SECOND));
    }

    private static java.sql.Date getDate(Date d) {
        return new java.sql.Date(d.getTime());
    }

    private static java.sql.Timestamp getDateTime(LocalDateTime ldt) {
        return new java.sql.Timestamp(ldt.getLong(ChronoField.MILLI_OF_SECOND));
    }

    private static java.sql.Timestamp getDateTime(Date d) {
        return new java.sql.Timestamp(d.getTime());
    }

    private static java.sql.Time getTime(LocalTime lt) {
        return new java.sql.Time(lt.getLong(ChronoField.MILLI_OF_SECOND));
    }

    private static String cleanQuery(String sql) {
        return sql.replaceAll("\\s+", " ").replaceAll("\\s$", "");
    }

}
