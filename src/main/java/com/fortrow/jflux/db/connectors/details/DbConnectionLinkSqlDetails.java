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
 * File created by Ryan Fitzgerald (ryan@fortrow.com) on 2026-05-04 15:27:04 EDT
 */
package com.fortrow.jflux.db.connectors.details;

import com.fortrow.jflux.db.supporting.DbType;

public class DbConnectionLinkSqlDetails extends DbConnectionDetails {

    private String name;
    private String host;
    private int port;
    private String username;
    private String password;

    public DbConnectionLinkSqlDetails(String name, String host, int port, String username, String password) {
        super(DbType.MYSQL);
        this.name = name;
        this.host = host;
        this.port = port;
        this.username = username;
        this.password = password;
        build();
    }

    @Override
    protected void build() {
        append("name", String.class, name);
        append("host", String.class, host);
        append("port", Integer.class, port);
        append("username", String.class, username);
        append("password", String.class, password);
    }

}
