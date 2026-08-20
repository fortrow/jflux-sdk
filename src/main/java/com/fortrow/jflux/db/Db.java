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
 * File created by Ryan Fitzgerald (ryan@fortrow.com) on 2026-05-07 14:52:42 EDT
 * 
 * -------------------------------------------------------------------------

 */
package com.fortrow.jflux.db;

import com.fortrow.jflux.crypto.Rand;
import com.fortrow.jflux.crypto.builtin.SHA;
import com.fortrow.jflux.db.connectors.details.DbConnectionDetails;
import com.fortrow.jflux.db.connectors.details.DbConnectionMysqlDetails;
import com.fortrow.jflux.db.connectors.details.DbConnectionRedisDetails;
import com.fortrow.jflux.db.connectors.details.DbConnectionSqlLiteDetails;
import com.fortrow.jflux.db.supporting.DbDriver;
import com.fortrow.jflux.db.supporting.Query;
import com.fortrow.jflux.db.supporting.SqlQuery;
import com.fortrow.jflux.db.supporting.redis.RedisQuery;
import com.fortrow.jflux.exceptions.types.CriticalException;

public class Db {

    private DbDriver driver;
    private String database = null;

    public Db(DbConnectionDetails details) {
        this.driver = new DbDriver(details);
        this.database = details.get("name");
    }

    public Db(DbConnectionSqlLiteDetails details) {
        this.driver = new DbDriver(details);
        this.database = details.get("name");
    }

    public Db(DbConnectionRedisDetails details) {
        this.driver = new DbDriver(details);
        this.database = "" + details.dbIndex();
    }

    public void setDatabase(String database) throws CriticalException, Exception {
        this.database = dbName(database);
        this.driver.conn().changeDb(database);
    }

    public RedisQuery redis() {
        if(database == null) return null;
        return (RedisQuery) this.driver.buildQuery();
    }

    public RedisQuery redis(int dbIndex) {
        try {
            setDatabase("" + dbIndex);
            return (RedisQuery) this.driver.buildQuery("" + dbIndex);
        } catch (Exception | CriticalException e) { e.printStackTrace(); }
        return null;
    }

    public SqlQuery query() {
        if(database == null) return null;
        return (SqlQuery) this.driver.buildQuery(dbName(database));
    }

    public SqlQuery query(String database) {
        try {
            setDatabase(database);
            return (SqlQuery) this.driver.buildQuery(dbName(database));
        } catch (Exception | CriticalException e) { e.printStackTrace(); }
        return null;
    }

    private String dbName(String db) {
        return db;
    }

    public DbDriver getRawDriver() {
        return this.driver;
    }

}
