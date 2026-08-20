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
 * File created by Ryan Fitzgerald (ryan@fortrow.com) on 2026-05-06 19:39:17 EDT
 * 
 * -------------------------------------------------------------------------

 */
package com.fortrow.jflux.db.supporting;

import com.fortrow.jflux.db.supporting.sql.*;
import com.fortrow.jflux.exceptions.types.CommonException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

public class SqlQuery implements Query {

    private String database;
    private String table;

    private ArrayList<Field> select = new ArrayList<Field>();
    private ArrayList<Field> update = new ArrayList<Field>();
    private ArrayList<Field> insert = new ArrayList<Field>();
    private ArrayList<Condition> where = new ArrayList<Condition>();
    private ArrayList<Join> join = new ArrayList<Join>();
    private HashMap<String, ArrayList<TableSchema>> createTable = new HashMap<>();

    private boolean delete = false;
    private boolean softDelete = false;
    private boolean setTimestamps = false;

    private ColumnOrFunction orderByColumn;
    private OrderBy orderByDirection;

    private ColumnOrFunction groupByColumn;

    private DbConnection connection;

    public SqlQuery(DbConnection connection) {
        this.connection = connection;
    }

    public SqlQuery database(String database) {
        this.database = database;
        return this;
    }

    public SqlQuery table(String table) {
        this.table = table;
        return this;
    }

//    public Query database(String database, boolean create) {
//        this.database = table;
//        if(create) {
//
//        }
//        return this;
//    }

//    public Query table(String table, String[] fields) {
//        this.table = table;
//        if(fields.length > 0) {
//
//        }
//        return this;
//    }

    public SqlQuery createTable(String tableName, TableSchema...cols) {
        ArrayList<TableSchema> list = new ArrayList<>();
        for(TableSchema col : cols) list.add(col);
        createTable.put(tableName, list);
        return this;
    }

    public SqlQuery select() {
        select(Schema.getColumns(this.connection, database, table));
        return this;
    }

    public SqlQuery select(String... columns) {
        for(String column: columns) {
            select.add(new Field(makeColumn(column), null));
        }
        return this;
    }

    public SqlQuery insert(HashMap<String, Object> toInsert) {

        for(String key : toInsert.keySet()) {
            Object value = toInsert.get(key);
            insert(key, value);
        }

        return this;
    }

    public SqlQuery insert(String column, Object value) {

        Field field = new Field(makeColumn(column), value);
        insert.add(field);
        return this;
    }

    public SqlQuery update(HashMap<String, Object> toUpdate) {

        for(String key : toUpdate.keySet()) {
            Object value = toUpdate.get(key);
            update(key, value);
        }

        return this;
    }

    public SqlQuery update(String column, Object value) {

        Field field = new Field(makeColumn(column), value);
        update.add(field);
        return this;
    }

    public SqlQuery addJoin(Join join) {
        this.join.add(join);
        return this;
    }

    public SqlQuery timestamps() {
        setTimestamps = true;
        return this;
    }

    public Join join(JoinTypes type) {
        return new Join(this, type);
    }

    public Join join(JoinTypes type, String table) {
        Join j = new Join(this, type);
        j.table(makeTable(table));
        return j;
    }

    public SQLFunction func(String name, Object... args) {
        return SQLFunction.create(name, args);
    }

    public SQLFunction func(String name, String storeAs, Object... args) {
        return SQLFunction.createWithStoreAs(name, storeAs, args);
    }
    
    private void addAllWhere(List<Condition> conditions) {
        if(join.isEmpty()) for (Condition condition : conditions) condition.notForJoin();
        else for (Condition condition : conditions) condition.forJoin();
        where.addAll(conditions);
    }
    
    private void addWhere(Condition condition) {
        if(join.isEmpty()) condition.notForJoin();
        else condition.forJoin();
        where.add(condition);
    }

    public SqlQuery where(ArrayList<Condition> conditions) {
        addAllWhere(conditions);
        return this;
    }

    public SqlQuery where(Condition[] conditions) {
        addAllWhere(Arrays.asList(conditions));
        return this;
    }

    public SqlQuery where(Condition value) {
        addWhere(value);
        return this;
    }

    public SqlQuery where(String column, String operator, Object value) {
        try {
            Condition c = new Condition(makeColumn(column), Operator.parse(operator), value);
            addWhere(c);
        } catch (CommonException e) {
            throw new RuntimeException(e);
        }
        return this;
    }

    public SqlQuery orWhere(ArrayList<Condition> conditions) {
        for(Condition condition : conditions) {
            condition.or();
        }
        addAllWhere(conditions);
        return this;
    }

    public SqlQuery orWhere(Condition[] conditions) {
        for(Condition condition : conditions) {
            condition.or();
        }
        addAllWhere(Arrays.asList(conditions));
        return this;
    }

    public SqlQuery orWhere(Condition value) {
        value.or();
        addWhere(value);
        return this;
    }

    public SqlQuery orWhere(String column, String operator, Object value) {
        try {
            Condition c = new Condition(makeColumn(column), Operator.parse(operator), value);
            c.or();
            addWhere(c);
        } catch (CommonException e) {
            throw new RuntimeException(e);
        }
        return this;
    }

    public SqlQuery whereNull(String column) {
        Column col = makeColumn(column);
        Condition c = new Condition(col, Operator.V.NULL);
        addWhere(c);
        return this;
    }

    public SqlQuery orWhereNull(String column) {
        Column col = makeColumn(column);
        Condition c = new Condition(col, Operator.V.NULL);
        c.or();
        addWhere(c);
        return this;
    }

    public SqlQuery whereSet(String column) {
        Column col = makeColumn(column);
        Condition c = new Condition(col, Operator.V.NOT_NULL);
        addWhere(c);
        return this;
    }

    public SqlQuery orWhereSet(String column) {
        Column col = makeColumn(column);
        Condition c = new Condition(col, Operator.V.NOT_NULL);
        c.or();
        addWhere(c);
        return this;
    }

    public SqlQuery whereDeleted(String column) {
        Column col = makeColumn(column);
        Condition c = new Condition(col, Operator.V.SOFT_DELETED);
        addWhere(c);
        return this;
    }

    public SqlQuery orWhereDeleted(String column) {
        Column col = makeColumn(column);
        Condition c = new Condition(col, Operator.V.SOFT_DELETED);
        c.or();
        addWhere(c);
        return this;
    }

    public SqlQuery whereLike(String column, String match) {
        Column col = makeColumn(column);
        Condition c = new Condition(col, Operator.V.LIKE, match);
        addWhere(c);
        return this;
    }

    public SqlQuery orWhereLike(String column, String match) {
        Column col = makeColumn(column);
        Condition c = new Condition(col, Operator.V.LIKE, match);
        c.or();
        addWhere(c);
        return this;
    }

    public SqlQuery whereNotLike(String column, String match) {
        Column col = makeColumn(column);
        Condition c = new Condition(col, Operator.V.NOT_LIKE, match);
        addWhere(c);
        return this;
    }

    public SqlQuery orWhereNotLike(String column, String match) {
        Column col = makeColumn(column);
        Condition c = new Condition(col, Operator.V.NOT_LIKE, match);
        c.or();
        addWhere(c);
        return this;
    }

    public SqlQuery orderBy(String column) {
        return orderBy(column, OrderBy.ASC);
    }

    public SqlQuery orderBy(String column, OrderBy direction) {
        this.orderByColumn = makeColumn(column);
        this.orderByDirection = direction;
        return this;
    }

    public SqlQuery groupBy(String column) {
        this.groupByColumn = makeColumn(column);
        return this;
    }

    public SqlQuery allRows() {
        addWhere(new Condition("1"));
        return this;
    }

    public SqlQuery beginGroup() {
        where.add(Condition.GROUP_BEGIN());
        return this;
    }

    public SqlQuery closeGroup() {
        where.add(Condition.GROUP_END());
        return this;
    }

    public void delete() {
        this.delete = true;
        run();
    }

    public void softDelete() {
        this.softDelete = true;
        run();
    }

    @Override
    public QueryResults get() {
        QueryAdapter qa = new QueryAdapter(connection);
        return qa.q(this);
    }

    @Override
    public void run() {
        QueryAdapter qa = new QueryAdapter(connection);
        qa.q(this);
        this.createTable.clear();
    }

    private String stripColumnChars(String input) {
        return input.replaceAll("[`'\"]", "");
    }

    private Column makeColumn(String table, String column) {
        table = stripColumnChars(table);
        column = stripColumnChars(column);
        return new Column(table, column);
    }

    private Column makeColumn(String input) {
        if(input.contains(".")) {
            String[] split = input.split("\\.");
            if(split.length == 2) return makeColumn(split[0], split[1]);
            else return null;
        } else {
            input = stripColumnChars(input);
            return new Column(table, input);
        }
    }

    private String makeTable(String input) {
        return "`" + table + "`";
    }

    @Override
    public QueryRequest p() {
        return new QueryParams(
                database,
                table,
                delete,
                softDelete,
                orderByColumn,
                orderByDirection,
                groupByColumn,
                select,
                update,
                insert,
                where,
                join,
                createTable,
                setTimestamps
        );
    }

}
