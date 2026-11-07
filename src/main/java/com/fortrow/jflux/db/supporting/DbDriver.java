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
 * File created by Ryan Fitzgerald (ryan@fortrow.com) on 2026-05-07 14:41:49 EDT
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
