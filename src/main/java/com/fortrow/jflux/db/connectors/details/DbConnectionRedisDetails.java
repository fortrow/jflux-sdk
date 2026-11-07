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
 * File created by Ryan Fitzgerald (ryan@fortrow.com) on 2026-05-06 11:14:06 EDT
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
