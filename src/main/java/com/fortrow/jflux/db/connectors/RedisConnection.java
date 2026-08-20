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
 * File created by Ryan Fitzgerald (ryan@fortrow.com) on 2026-05-06 11:48:25 EDT
 * 
 * -------------------------------------------------------------------------

 */
package com.fortrow.jflux.db.connectors;

import com.fortrow.jflux.db.connectors.details.DbConnectionDetails;
import com.fortrow.jflux.db.supporting.*;
import com.fortrow.jflux.db.supporting.redis.RedisQueryAction;
import com.fortrow.jflux.db.supporting.redis.RedisQueryParams;
import com.fortrow.jflux.exceptions.types.CriticalException;
import com.fortrow.jflux.todash.__;
import redis.clients.jedis.Jedis;

public class RedisConnection extends DbConnection {

    private Jedis conn = null;

    public RedisConnection(DbConnectionDetails details) {
        super(details);
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
        this.conn = new Jedis((String) this.details.get("host"), this.details.get("port"));
    }

    @Override
    public void close() throws Exception {
        if(__.isset(conn)) conn.close();
    }

    @Override
    public void changeDb(String database) throws Exception, CriticalException {
        changeDb(Integer.parseInt(database));
    }

    public void changeDb(int dbIndex) throws Exception, CriticalException {
       conn.select(dbIndex);
    }

    @Override
    public QueryResults execute(QueryRequest req) {

        if(conn == null) {
            try {
                connect();
            } catch (Exception e) {
                e.printStackTrace();
            } catch (CriticalException e) {
                e.printStackTrace();
            }
        }

        RedisQueryParams p = (RedisQueryParams) req;

        try {
            conn.select(p.getDbIndex());
            if(p.getAction().equals(RedisQueryAction.GET)) {
                String value = conn.get(p.getRecordId());
                if(__.isset(value)) return new QueryResults(value);
            }
        } catch (Exception e) {
            onError(new CriticalException("Failed to execute query.", e));
        }

        return null;
    }

    @Override
    public void executeUpdate(QueryRequest req) {

        if(conn == null) {
            try {
                connect();
            } catch (Exception e) {
                e.printStackTrace();
            } catch (CriticalException e) {
                e.printStackTrace();
            }
        }

        RedisQueryParams p = (RedisQueryParams) req;

        try {
            conn.select(p.getDbIndex());
            if(p.getAction().equals(RedisQueryAction.PUT)) conn.set(p.getRecordId(), p.getData());
            else if(p.getAction().equals(RedisQueryAction.DELETE)) conn.del(p.getRecordId());
        } catch (Exception e) {
            onError(new CriticalException("Failed to execute query.", e));
        }

    }

}
