/*
 * -------------------------------------------------------------------------
 * 
 * Copyright (c) 2026 Fortrow LLC. All rights reserved.
 *  
 * NOTICE: All information contained herein is, and remains the property of 
 * Fortrow, LLC. The intellectual and technical concepts contained herein are 
 * proprietary to [Company Name] and may be covered by U.S. and Foreign Patents, 
 * patents in process, and are protected by trade secret or copyright law.
 * 
 * Dissemination of this information or reproduction of this material is 
 * strictly forbidden unless prior written permission is obtained from 
 * Fortrow, LLC.
 * 
 * -------------------------------------------------------------------------
 * 
 * File created by Ryan Fitzgerald (ryan@fortrow.com) on 2026-05-06 10:38:40 EDT
 * 
 * -------------------------------------------------------------------------

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
