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

import java.util.HashMap;

public abstract class DbConnectionDetails {

    protected DbType type;
    protected HashMap<String, DbDetailsPair> values = new HashMap<String, DbDetailsPair>();

    public DbConnectionDetails(DbType type) {
        this.type = type;
    }

    protected abstract void build();

    public <Any> Any get(String key) {
        if(!values.containsKey(key)) return null;
        return values.get(key).get();
    }

    public DbType getType() {
        return type;
    }

    protected void append(String key, Class<?> type, Object value) {
        values.put(key, new DbDetailsPair(type, value));
    }

    public static class DbDetailsPair {

        private Class<?> type;
        private Object v;

        public DbDetailsPair(Class<?> type, Object v) {
            this.type = type;
            this.v = v;
        }

        public <Any> Any get() {
            return (Any) type.cast(v);
        }

    }

}