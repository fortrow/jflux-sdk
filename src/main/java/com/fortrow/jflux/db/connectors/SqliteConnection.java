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
 * File created by Ryan Fitzgerald (ryan@fortrow.com) on 2026-05-06 11:20:06 EDT
 * 
 * -------------------------------------------------------------------------

 */
package com.fortrow.jflux.db.connectors;

import com.fortrow.jflux.db.connectors.details.DbConnectionSqlLiteDetails;
import com.fortrow.jflux.db.supporting.DbConnection;
import com.fortrow.jflux.db.supporting.ExecutesQueries;
import com.fortrow.jflux.db.supporting.QueryParams;
import com.fortrow.jflux.db.supporting.QueryResults;
import com.fortrow.jflux.db.supporting.*;
import com.fortrow.jflux.db.supporting.sql.Condition;
import com.fortrow.jflux.db.supporting.sql.Field;
import com.fortrow.jflux.db.supporting.sql.SQLBuilder;
import com.fortrow.jflux.exceptions.types.CriticalException;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

public class SqliteConnection extends DbConnection {

    private Connection conn = null;

    public SqliteConnection(DbConnectionSqlLiteDetails details) {
        super(details);
        connectOnConstruction();
    }

    @Override
    public Object getConn() {
        return conn;
    }

    @Override
    public void onError(CriticalException e) {
        e.report();
    }

    @Override
    protected void connect() throws Exception, CriticalException {

        String jdbc = "jdbc:sqlite:" + details.get("filepath");
        this.conn = DriverManager.getConnection(jdbc);
        if(this.conn == null) throw new CriticalException("Failed to connect to sqlite.");

    }

    @Override
    public void close() throws Exception {
        if(conn != null) {
            conn.close();
            conn = null;
        }
    }

    @Override
    public void changeDb(String database) throws Exception, CriticalException {
    }

    @Override
    public QueryResults execute(QueryRequest req) {

        try { ensureConnected(); }
        catch (Exception | CriticalException e) {
            onError(e instanceof CriticalException ? (CriticalException) e : new CriticalException("Failed to connect to sqlite.", e));
            return null;
        }

        QueryParams p = (QueryParams) req;

        try {
            SQLBuilder builder = new SQLBuilder(p);
            String preparedSql = builder.getPreparedString(true);
            try(PreparedStatement stmt = conn.prepareStatement(preparedSql)) {
                builder.bind(stmt);
                try(ResultSet resultSet = stmt.executeQuery()) {
                    return new QueryResults(resultSet, p.selectColumns(), p.tableName());
                }
            }
        } catch (Exception e) {
            onError(new CriticalException("Failed to execute query.", e));
        }

        return null;
    }

    @Override
    public void executeUpdate(QueryRequest req) {

        try { ensureConnected(); }
        catch (Exception | CriticalException e) {
            onError(e instanceof CriticalException ? (CriticalException) e : new CriticalException("Failed to connect to sqlite.", e));
            return;
        }

        QueryParams p = (QueryParams) req;

        try {
            SQLBuilder builder = new SQLBuilder(p);
            String preparedSql = builder.getPreparedString(true);
            try(PreparedStatement stmt = conn.prepareStatement(preparedSql)) {
                builder.bind(stmt);
                stmt.executeUpdate();
            }
        } catch (Exception e) {
            onError(new CriticalException("Failed to execute query.", e));
        }

    }
}
