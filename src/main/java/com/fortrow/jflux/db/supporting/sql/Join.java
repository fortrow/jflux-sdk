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
 * File created by Ryan Fitzgerald (ryan@fortrow.com) on 2026-05-06 10:38:40 EDT
 */
package com.fortrow.jflux.db.supporting.sql;

import com.fortrow.jflux.db.supporting.SqlQuery;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Join {

    private int currCondition = -1;
    private ArrayList<Condition> conditions = new ArrayList<>();
    private JoinTypes type;
    private SqlQuery q;
    private String table;

    public Join(SqlQuery q, JoinTypes type) {
        this.type = type;
        this.q = q;
    }

    public Join table(String table) {
        this.table = table;
        return this;
    }

    public Join on(Object left, Object operator, Object right) {
        this.conditions.add(new Condition(left, operator, right));
        return this;
    }

    public Join orOn(Object left, Object operator, Object right) {
        Condition c = new Condition(left, operator, right);
        c.or();
        this.conditions.add(c);
        return this;
    }

    public SqlQuery end() {
        q.addJoin(this);
        return q;
    }

    public int conditionsSize() {
        return conditions.size();
    }

    public String getType() {
        if(type == JoinTypes.DEFAULT_JOIN) return "JOIN";
        if(type == JoinTypes.INNER_JOIN) return "INNER JOIN";
        if(type == JoinTypes.LEFT_JOIN) return "LEFT JOIN";
        if(type == JoinTypes.RIGHT_JOIN) return "RIGHT JOIN";
        if(type == JoinTypes.FULL_JOIN) return "FULL JOIN";
        return "JOIN";
    }

    public Condition nextCondition() {
        currCondition++;
        if(currCondition < conditions.size()) return conditions.get(currCondition);
        return null;
    }

    public String getTable() {
        return table;
    }

    public List<Condition> conditions() {
        return Collections.unmodifiableList(conditions);
    }

}
