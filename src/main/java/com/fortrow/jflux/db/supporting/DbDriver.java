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
 * File created by Ryan Fitzgerald (ryan@fortrow.com) on 2026-05-07 14:41:49 EDT
 * 
 * -------------------------------------------------------------------------

 */
package com.fortrow.jflux.db.supporting;

import com.fortrow.jflux.db.connectors.MysqlConnection;
import com.fortrow.jflux.db.connectors.RedisConnection;
import com.fortrow.jflux.db.connectors.SqliteConnection;
import com.fortrow.jflux.db.connectors.details.DbConnectionDetails;
import com.fortrow.jflux.db.connectors.details.DbConnectionMysqlDetails;
import com.fortrow.jflux.db.connectors.details.DbConnectionRedisDetails;
import com.fortrow.jflux.db.connectors.details.DbConnectionSqlLiteDetails;
import com.fortrow.jflux.db.supporting.redis.RedisQuery;

public class DbDriver implements AutoCloseable {

    private DbType type;
    private DbConnection connection;

    public DbDriver(DbConnectionDetails details) {
        this.type = details.getType();
        if(type == DbType.MYSQL) this.connection = new MysqlConnection((DbConnectionMysqlDetails) details);
        else if(type == DbType.SQLITE) this.connection = new SqliteConnection((DbConnectionSqlLiteDetails) details);
        else if(type == DbType.REDIS) this.connection = new RedisConnection((DbConnectionRedisDetails) details);
    }

    public DbConnection conn() {
        return connection;
    }

    public Query buildQuery() {
        if(type == DbType.MYSQL || type == DbType.SQLITE)
            return new SqlQuery(connection);
        else if(type == DbType.REDIS)
            return new RedisQuery(connection);
        return null;
    }

    public Query buildQuery(String db) {
        if(db == null) return buildQuery();
        else {
            if(type == DbType.MYSQL || type == DbType.SQLITE)
                return new SqlQuery(connection).database(db);
            else if(type == DbType.REDIS)
                return new RedisQuery(connection).database(Integer.parseInt(db));
            else {
                return null;
            }
        }
    }

    public DbType getType() {
        return type;
    }

    @Override
    public void close() throws Exception {
        if(connection != null) connection.close();
    }
}
