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
 * File created by Ryan Fitzgerald (ryan@fortrow.com) on 2026-05-06 11:48:25 EDT
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
        this.conn = new Jedis((String) this.details.get("host"), this.details.get("port"));
    }

    @Override
    public void close() throws Exception {
        if(__.isset(conn)) {
            conn.close();
            conn = null;
        }
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
        try { ensureConnected(); }
        catch (Exception | CriticalException e) {
            onError(e instanceof CriticalException ? (CriticalException) e : new CriticalException("Failed to connect to redis.", e));
            return null;
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
        try { ensureConnected(); }
        catch (Exception | CriticalException e) {
            onError(e instanceof CriticalException ? (CriticalException) e : new CriticalException("Failed to connect to redis.", e));
            return;
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
