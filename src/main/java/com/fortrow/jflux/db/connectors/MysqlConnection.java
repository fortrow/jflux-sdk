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

import com.fortrow.jflux.db.connectors.details.DbConnectionMysqlDetails;
import com.fortrow.jflux.db.supporting.*;
import com.fortrow.jflux.db.supporting.sql.Condition;
import com.fortrow.jflux.db.supporting.sql.Field;
import com.fortrow.jflux.db.supporting.sql.SQLBuilder;
import com.fortrow.jflux.exceptions.types.CriticalException;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.nio.file.Path;
import java.util.Properties;

public class MysqlConnection extends DbConnection {

    private static final int DEFAULT_QUERY_TIMEOUT_SECONDS = 15;

    private Connection conn = null;
    private final Object queryLock = new Object();

    public MysqlConnection(DbConnectionMysqlDetails details) {
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

        String jdbc = "jdbc:mysql://" + details.get("host") + ":" + details.get("port") + "/" + details.get("name");
        Properties properties = new Properties();
        properties.setProperty("user", details.get("username"));
        properties.setProperty("password", details.get("password"));

        String sslMode = details.get("sslMode");
        if (sslMode != null && !sslMode.isBlank()) {
            properties.setProperty("sslMode", sslMode);
        }

        String trustStoreUrl = details.get("trustStoreUrl");
        if (trustStoreUrl != null && !trustStoreUrl.isBlank()) {
            properties.setProperty("trustCertificateKeyStoreUrl", keyStoreUrl(trustStoreUrl));
            String trustStoreType = details.get("trustStoreType");
            if (trustStoreType != null && !trustStoreType.isBlank()) {
                properties.setProperty("trustCertificateKeyStoreType", trustStoreType);
            }
        String trustStorePassword = details.get("trustStorePassword");
            if (trustStorePassword != null) {
                properties.setProperty("trustCertificateKeyStorePassword", trustStorePassword);
            }
        }

        Integer connectTimeoutMs = details.get("connectTimeoutMs");
        Integer socketTimeoutMs = details.get("socketTimeoutMs");
        properties.setProperty("connectTimeout", String.valueOf(
                connectTimeoutMs == null ? DbConnectionMysqlDetails.DEFAULT_CONNECT_TIMEOUT_MS : Math.max(1, connectTimeoutMs)));
        properties.setProperty("socketTimeout", String.valueOf(
                socketTimeoutMs == null ? DbConnectionMysqlDetails.DEFAULT_SOCKET_TIMEOUT_MS : Math.max(1, socketTimeoutMs)));
        properties.setProperty("tcpKeepAlive", "true");
        properties.setProperty("autoReconnect", "false");

        conn = DriverManager.getConnection(jdbc, properties);
        if(conn == null) throw new CriticalException("Failed to connect to mysql.");

    }

    private static String keyStoreUrl(String value) {
        return value.contains(":") ? value : Path.of(value).toAbsolutePath().toUri().toString();
    }

    /**
     * RDS may close an idle JDBC connection while the API process remains
     * alive.  A non-null Connection is not sufficient to prove that it can
     * execute another statement.
     */
    private void ensureLiveConnection() throws Exception, CriticalException {
        if (conn != null) {
            try {
                if (!conn.isClosed() && conn.isValid(2)) return;
            } catch (SQLException ignored) {
                // Reconnect below.  The driver has already told us this
                // connection cannot be trusted for another query.
            }
            closeQuietly();
        }
        connect();
    }

    private void closeQuietly() {
        Connection current = conn;
        conn = null;
        if (current == null) return;
        try {
            current.close();
        } catch (SQLException ignored) {
        }
    }

    private static boolean isConnectionFailure(SQLException error) {
        for (Throwable current = error; current != null; current = current.getCause()) {
            if (current instanceof SQLException sql) {
                String state = sql.getSQLState();
                int code = sql.getErrorCode();
                if (state != null && state.startsWith("08")) return true;
                if (code == 2006 || code == 2013 || code == 2055) return true;
            }
            String message = current.getMessage();
            if (message != null) {
                String normalized = message.toLowerCase(java.util.Locale.ROOT);
                if (normalized.contains("communications link failure")
                        || normalized.contains("connection is closed")
                        || normalized.contains("no operations allowed after connection closed")
                        || normalized.contains("broken pipe")
                        || normalized.contains("connection reset")
                        || normalized.contains("socket is closed")) return true;
            }
        }
        return false;
    }

    private static CriticalException queryFailure(Throwable error) {
        String detail = error.getMessage();
        if (detail == null || detail.isBlank()) detail = error.getClass().getSimpleName();
        return new CriticalException("Failed to execute query: " + detail, error);
    }

    @Override
    public void close() throws Exception {
        synchronized (queryLock) {
            closeQuietly();
        }
    }

    @Override
    public void changeDb(String database) throws Exception, CriticalException {

    }

    @Override
    public QueryResults execute(QueryRequest req) {
        synchronized (queryLock) {
            try {
                ensureLiveConnection();
                return executeQuery(req);
            } catch (SQLException error) {
                if (!isConnectionFailure(error)) {
                    onError(queryFailure(error));
                    return null;
                }
                // Reads are safe to retry after a connection-level failure;
                // the statement may not have reached the server.
                closeQuietly();
                try {
                    connect();
                    return executeQuery(req);
                } catch (Exception | CriticalException retryError) {
                    onError(queryFailure(retryError));
                    return null;
                }
            } catch (Exception | CriticalException error) {
                onError(error instanceof CriticalException
                        ? (CriticalException) error
                        : queryFailure(error));
                return null;
            }
        }
    }

    private QueryResults executeQuery(QueryRequest req) throws Exception {
        QueryParams p = (QueryParams) req;
        SQLBuilder builder = new SQLBuilder(p);
        String preparedSql = builder.getPreparedString(false);
        try (PreparedStatement stmt = conn.prepareStatement(preparedSql)) {
            stmt.setQueryTimeout(DEFAULT_QUERY_TIMEOUT_SECONDS);
            builder.bind(stmt);
            try (ResultSet resultSet = stmt.executeQuery()) {
                return new QueryResults(resultSet, p.selectColumns(), p.tableName());
            }
        }
    }

    @Override
    public void executeUpdate(QueryRequest req) {
        synchronized (queryLock) {
            try {
                ensureLiveConnection();
                QueryParams p = (QueryParams) req;
                SQLBuilder builder = new SQLBuilder(p);
                String preparedSql = builder.getPreparedString(false);
                try (PreparedStatement stmt = conn.prepareStatement(preparedSql)) {
                    stmt.setQueryTimeout(DEFAULT_QUERY_TIMEOUT_SECONDS);
                    builder.bind(stmt);
                    stmt.executeUpdate();
                }
            } catch (SQLException error) {
                if (isConnectionFailure(error)) {
                    // Do not blindly replay a write whose commit status is
                    // unknown. Reconnect so the next operation can proceed.
                    closeQuietly();
                    try {
                        connect();
                    } catch (Exception | CriticalException reconnectError) {
                        onError(queryFailure(reconnectError));
                        return;
                    }
                }
                onError(queryFailure(error));
            } catch (Exception | CriticalException error) {
                onError(error instanceof CriticalException
                        ? (CriticalException) error
                        : queryFailure(error));
            }
        }
    }
}
