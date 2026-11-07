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
 * File created by Ryan Fitzgerald (ryan@fortrow.com) on 2026-05-06 11:20:06 EDT
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
