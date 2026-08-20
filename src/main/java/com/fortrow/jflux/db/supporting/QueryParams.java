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
package com.fortrow.jflux.db.supporting;

import com.fortrow.jflux.db.supporting.sql.*;

import java.util.ArrayList;
import java.util.HashMap;

//QUERY PARAMS CLASS MAY BE UNNECESSARY
//HOWEVER, FOR CLEANLINESS OF CODE AND TO SEPARATE
//THE LOGIC FOR BUILDING QUERIES ACROSS MULTIPLE DB TYPES
//IT WILL BE USED
public class QueryParams implements QueryRequest {

    private String database;
    private String table;

    private int currSelect = -1;
    private ArrayList<Field> select = new ArrayList<Field>();
    private int currUpdate = -1;
    private ArrayList<Field> update = new ArrayList<Field>();
    private int currInsert = -1;
    private ArrayList<Field> insert = new ArrayList<Field>();
    private int currWhere = -1;
    private ArrayList<Condition> where = new ArrayList<Condition>();
    private int currJoin = -1;
    private ArrayList<Join> join = new ArrayList<Join>();

    private int whereSizeForJoin = -1;
    private int whereSizeNotForJoin = -1;

    private boolean delete = false;
    private boolean softDelete = false;
    private boolean setTimestamps = false;

    private ColumnOrFunction orderByColumn = null;
    private OrderBy orderByDirection = null;

    private ColumnOrFunction groupByColumn = null;

    private HashMap<String, ArrayList<TableSchema>> createTables = new HashMap<>();

    public QueryParams(
            String database,
            String table,
            boolean delete,
            boolean softDelete,
            ColumnOrFunction orderByColumn,
            OrderBy orderByDirection,
            ColumnOrFunction groupByColumn,
            ArrayList<Field> select,
            ArrayList<Field> update,
            ArrayList<Field> insert,
            ArrayList<Condition> where,
            ArrayList<Join> join,
            HashMap<String, ArrayList<TableSchema>> createTables,
            boolean setTimestamps
    ) {
        this.database = database;
        this.table = table;
        this.delete = delete;
        this.softDelete = softDelete;
        this.orderByColumn = orderByColumn;
        this.orderByDirection = orderByDirection;
        this.groupByColumn = groupByColumn;
        this.select = select;
        this.update = update;
        this.insert = insert;
        this.where = where;
        this.join = join;
        this.createTables = createTables;
        this.setTimestamps = setTimestamps;
    }

    @Override
    public boolean returns() {
        return !select.isEmpty();
    }

    public String database() {
        return database;
    }

    public String table() {
        return "`" + table + "`";
    }

    public String tableName() { return table; }

    public boolean softDelete() { return softDelete; }

    public boolean delete() { return delete; }

    public boolean hasOrderBy() {
        return (orderByDirection != null) && (orderByColumn != null);
    }

    public boolean hasGroupBy() {
        return groupByColumn != null;
    }

    public ColumnOrFunction getOrderByColumn() {
        return orderByColumn;
    }

    public OrderBy getOrderByDirection() {
        return orderByDirection;
    }

    public ColumnOrFunction getGroupByColumn() {
        return groupByColumn;
    }

    public ArrayList<Field> selectColumns() {
        return select;
    }

    public void resetIndexes() {
        currSelect = -1;
        currInsert = -1;
        currUpdate = -1;
        currWhere = -1;
        currJoin = -1;
    }

    public Field nextSelect() {
        currSelect++;
        if(currSelect < select.size()) return select.get(currSelect);
        return null;
    }

    public Field nextInsert() {
        currInsert++;
        if(currInsert < insert.size()) return insert.get(currInsert);
        return null;
    }

    public Field nextUpdate() {
        currUpdate++;
        if(currUpdate < update.size()) return update.get(currUpdate);
        return null;
    }

    public HashMap<String, ArrayList<TableSchema>> getTablesToCreate() {
        return this.createTables;
    }

    public Condition nextNotJoinWhere() {
        currWhere++;
        if(currWhere < whereSizeNotForJoin()) return where.get(currWhere);
        else currWhere = whereSizeForJoin() - 1;
        return null;
    }

    public Condition nextJoinWhere() {
        currWhere++;
        if(currWhere < where.size()) return where.get(currWhere);
        return null;
    }

    public Join nextJoin() {
        currJoin++;
        if(currJoin < join.size()) return join.get(currJoin);
        return null;
    }

    public int selectSize() {
        return select.size();
    }

    public int insertSize() {
        return insert.size();
    }

    public int updateSize() {
        return update.size();
    }

    public int whereSizeForJoin() {
        if(whereSizeForJoin >= 0) return whereSizeForJoin;
        int n = 0;
        for(int i=0; i<where.size(); i++) {
            Condition c = where.get(i);
            if(c.isDualColumns()) continue;
            if(c.isNotForJoin()) continue;
            if(c.isForJoin()) n++;
        }
        whereSizeForJoin = n;
        return n;
    }

    public int whereSizeNotForJoin() {
        if(whereSizeNotForJoin >= 0) return whereSizeNotForJoin;
        int n = 0;
        for(int i=0; i<where.size(); i++) {
            Condition c = where.get(i);
            if(c.isDualColumns()) continue;
            if(c.isForJoin()) break;
            if(c.isNotForJoin()) n++;
        }
        whereSizeNotForJoin = n;
        return n;
    }

    public int whereSize() { return whereSizeForJoin() + whereSizeNotForJoin(); }

    public int joinSize() { return join.size(); }

    public boolean setTimestamps() {
        return setTimestamps;
    }

}
