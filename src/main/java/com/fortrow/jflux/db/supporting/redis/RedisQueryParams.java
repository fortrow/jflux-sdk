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
 * File created by Ryan Fitzgerald (ryan@fortrow.com) on 2026-05-06 11:46:03 EDT
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
