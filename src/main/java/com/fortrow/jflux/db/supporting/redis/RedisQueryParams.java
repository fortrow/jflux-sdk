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
 * File created by Ryan Fitzgerald (ryan@fortrow.com) on 2026-05-06 11:46:03 EDT
 * 
 * -------------------------------------------------------------------------

 */
package com.fortrow.jflux.db.supporting.redis;

import com.fortrow.jflux.db.supporting.QueryRequest;

public class RedisQueryParams implements QueryRequest {

    private final String dbLabel;
    private final int dbIndex;
    private final String recordId;
    private final RedisQueryAction action;
    private final String data;

    public RedisQueryParams(String dbLabel, int dbIndex, String recordId, RedisQueryAction action, String data) {
        this.dbLabel = dbLabel;
        this.dbIndex = dbIndex;
        this.recordId = recordId;
        this.action = action;
        this.data = data;
    }

    public String getDbLabel() {
        return dbLabel;
    }

    public int getDbIndex() {
        return dbIndex;
    }

    public String getRecordId() {
        return recordId;
    }

    public RedisQueryAction getAction() {
        return action;
    }

    public String getData() {
        return data;
    }

    @Override
    public boolean returns() {
        return action.equals(RedisQueryAction.GET);
    }
}
