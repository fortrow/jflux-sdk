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
package com.fortrow.jflux.db.supporting;

import com.fortrow.jflux.db.connectors.details.DbConnectionDetails;
import com.fortrow.jflux.exceptions.types.CriticalException;

public abstract class DbConnection implements ExecutesQueries, AutoCloseable {

    protected DbConnectionDetails details;

    public DbConnection(DbConnectionDetails details) {
        this.details = details;
    }

    protected final synchronized void connectOnConstruction() {
        try {
            ensureConnected();
        } catch(Exception | CriticalException e) {
            onError(e instanceof CriticalException
                    ? (CriticalException) e
                    : new CriticalException("Failed to connect to database", e));
        }
    }

    protected final synchronized void ensureConnected() throws Exception, CriticalException {
        if(getConn() == null) connect();
    }

    public abstract Object getConn();
    public abstract void onError(CriticalException e);
    protected abstract void connect() throws Exception, CriticalException;
    public abstract void close() throws Exception;
    public abstract void changeDb(String database) throws Exception, CriticalException;

    public DbConnectionDetails getConnectionDetails() {
        return details;
    }

}
