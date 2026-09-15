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
 * File created by Ryan Fitzgerald (ryan@fortrow.com) on 2026-05-06 19:39:17 EDT
 * 
 * -------------------------------------------------------------------------

 */
package com.fortrow.jflux.db.supporting.sql;

import com.fortrow.jflux.exceptions.types.CommonException;

public class Condition {

    private Object left = null;
    private Object right = null;
    private Operator.V operator = null;
    private boolean or = false;
    private int valuePos = 0;

    private boolean notForJoin = false;
    private boolean forJoin = false;

    private int groupIndex = -2;

    private String matchStr = null;

    private boolean singleValue = false;

    public Condition(Object left, Object operator, Object right) {
        if(!(left instanceof ColumnOrFunction || right instanceof ColumnOrFunction))
            new CommonException("At least one side of the condition must be a column object.").report();
        if(!(left instanceof ColumnOrFunction)) valuePos = -1;
        if(!(right instanceof ColumnOrFunction)) valuePos = 1;
        this.left = left;
        try {
            this.operator = (operator.getClass() == Operator.V.class) ? (Operator.V) operator : Operator.parse(operator);
        } catch (CommonException e) {
            e.report();
        }
        this.right = right;
    }

    public Condition(Object column, Object v) {
        this.valuePos = -1;
        this.left = column;
        try {
            this.operator = (v.getClass() == Operator.V.class) ? (Operator.V) v : Operator.parse(v);
        } catch (CommonException e) {
            e.report();
        }
    }

    public Condition(Object column, Operator.V v) {
        this.valuePos = -1;
        this.left = column;
        this.operator = v;
    }

    public Condition(String value) {
        this.singleValue = true;
        this.left = value;
    }

    public Condition(boolean closeGroup) {
        groupIndex = closeGroup ? 1 : -1;
    }

    public static Condition GROUP_BEGIN() {
        return new Condition(false);
    }

    public static Condition GROUP_END() {
        return new Condition(true);
    }

    public Object left() {
        return this.left;
    }

    public Object right() {
        return this.right;
    }

    public Operator.V operator() {
        return this.operator;
    }

    public void or() {
        or = true;
    }

    public boolean isGroup() {
        return groupIndex == 0;
    }

    public boolean isClose() {
        return groupIndex == 1;
    }

    public boolean isOpen() {
        return groupIndex == -1;
    }

    public boolean isOr() {
        return or;
    }

    public void notForJoin() {
        notForJoin = true;
    }

    public void forJoin() {
        forJoin = true;
    }

    public boolean isForJoin() {
        return forJoin;
    }

    public boolean isNotForJoin() {
        return notForJoin;
    }

    public boolean isSingleValue() {
        return singleValue;
    }

    public boolean isDualColumns() {
        return valuePos == 0;
    }

    public Object value() {
        if(valuePos == -1) return left;
        if(valuePos == 1) return right;
        return new ColumnOrFunction[]{(ColumnOrFunction) left, (ColumnOrFunction) right};
    }
}
