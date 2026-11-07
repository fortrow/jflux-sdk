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
