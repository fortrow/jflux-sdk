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
 * File created by Ryan Fitzgerald (ryan@fortrow.com) on 2026-05-07 14:52:42 EDT
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

public class Db implements AutoCloseable {

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
        } catch (Exception e) {
            new CriticalException("Failed to select Redis database.", e).report();
        } catch (CriticalException e) {
            e.report();
        }
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
        } catch (Exception e) {
            new CriticalException("Failed to select database.", e).report();
        } catch (CriticalException e) {
            e.report();
        }
        return null;
    }

    private String dbName(String db) {
        return db;
    }

    public DbDriver getRawDriver() {
        return this.driver;
    }

    @Override
    public void close() throws Exception {
        if(driver != null) driver.close();
    }

}
