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
 * File created by Ryan Fitzgerald (ryan@fortrow.com) on 2026-05-07 16:07:46 EDT
 * 
 * -------------------------------------------------------------------------

 */
package com.fortrow.jflux.db.supporting.redis;

import com.fortrow.jflux.db.connectors.details.DbConnectionRedisDetails;
import com.fortrow.jflux.db.supporting.*;
import com.fortrow.jflux.json.JSON;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

public class RedisQuery implements Query {

    private String dbLabel;
    private int dbIndex;
    private String recordId;
    private RedisQueryAction action;
    private String data;

    private DbConnection connection;

    public RedisQuery(DbConnection connection) {
        this.connection = connection;
        DbConnectionRedisDetails details = (DbConnectionRedisDetails) this.connection.getConnectionDetails();
        this.dbLabel = details.dbName();
        this.dbIndex = details.dbIndex();
    }

    public RedisQuery database(int index) {
        this.dbIndex = index;
        return this;
    }

    @Override
    public QueryRequest p() {
        return new RedisQueryParams(this.dbLabel, this.dbIndex, this.recordId, this.action, this.data);
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
    }

    public RedisQuery getEntry(String recordId) {
        this.action = RedisQueryAction.GET;
        this.recordId = recordId;
        return this;
    }

    public RedisQuery put(String recordId, JSONArray arr) {
        return put(recordId, JSON.getString(arr));
    }

    public RedisQuery put(String recordId, JSONObject data) {
        JSONArray arr = new JSONArray();
        arr.add(data);
        return put(recordId, JSON.getString(arr));
    }

    public RedisQuery put(String recordId, String data) {
        this.action = RedisQueryAction.PUT;
        this.recordId = recordId;
        this.data = data;
        return this;
    }

    public RedisQuery create(String recordId, JSONObject data) {
        return put(recordId, data);
    }

    public RedisQuery create(String recordId, String data) {
        return put(recordId, data);
    }

    public RedisQuery delete(String recordId) {
        this.action = RedisQueryAction.DELETE;
        this.recordId = recordId;
        return this;
    }

}
