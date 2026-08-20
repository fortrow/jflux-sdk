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
 * File created by Ryan Fitzgerald (ryan@fortrow.com) on 2026-05-06 11:14:06 EDT
 * 
 * -------------------------------------------------------------------------

 */
package com.fortrow.jflux.db.connectors.details;

import com.fortrow.jflux.db.supporting.DbType;

public class DbConnectionRedisDetails extends DbConnectionDetails {

    private String host;
    private int port;
    private String dbNameLabel;
    private int dbIndex;

    public DbConnectionRedisDetails(String dbNameLabel, String host, int port, int dbIndex) {
        super(DbType.REDIS);
        this.dbNameLabel = dbNameLabel;
        this.host = host;
        this.port = port;
        this.dbIndex = dbIndex;
        build();
    }

    @Override
    protected void build() {
        append("host", String.class, host);
        append("port", Integer.class, port);
        append("dbNameLabel", String.class, dbNameLabel);
        append("dbIndex", Integer.class, dbIndex);
    }

    public String dbName() {
        return dbNameLabel;
    }

    public int dbIndex() {
        return dbIndex;
    }

}
