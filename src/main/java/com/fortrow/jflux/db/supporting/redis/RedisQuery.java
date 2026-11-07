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
 * File created by Ryan Fitzgerald (ryan@fortrow.com) on 2026-05-07 16:07:46 EDT
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
