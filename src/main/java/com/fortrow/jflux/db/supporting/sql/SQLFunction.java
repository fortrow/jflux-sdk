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
 * File created by Ryan Fitzgerald (ryan@fortrow.com) on 2026-05-06 11:38:29 EDT
 */
package com.fortrow.jflux.db.supporting.sql;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoField;
import java.util.Date;

public class SQLFunction extends ColumnOrFunction {

    private String name = null;
    private String as = null;
    private Object[] args = null;

    public SQLFunction(String name, Object...args) {
        this.name = name;
        this.args = args;
    }

    public SQLFunction(String name, String as, Object...args) {
        this.name = name;
        this.as = as;
        this.args = args;
    }

    public static SQLFunction createWithStoreAs(String name, String as, Object...args) {
        return new SQLFunction(name, as, args);
    }

    public static SQLFunction create(String name, Object...args) {
        return new SQLFunction(name, args);
    }

    private String get() {
        String call = this.name.toUpperCase() + "(";
        for(int i=0; i<args.length; i++) {
            call += (i > 0) ? ", " : "";
            Object arg = args[i];
            if(arg.getClass() == Column.class) call += "" + ((Column) arg).full();
            if(arg.getClass() == Integer.class) call += "" + ((int) arg);
            if(arg.getClass() == String.class) call += "\"" + stripColumnChars((String) arg) + "\"";
            if(arg.getClass() == Column.class) call += "\"" + ((Column) arg).full() + "\"";
            if(arg.getClass() == Float.class) call += ""  + ((float) arg);
            if(arg.getClass() == Double.class) call += "" + ((double) arg);
            if(arg.getClass() == Boolean.class) call += "" + ((((boolean) arg)) ? 1 : 0);
            if(arg.getClass() == LocalDate.class) call += "" + getDate((LocalDate) arg);
            if(arg.getClass() == Date.class) call += "" + getDate((Date) arg);
            if(arg.getClass() == LocalDateTime.class) call += "" + getDateTime((LocalDateTime) arg);
            if(arg.getClass() == LocalTime.class) call += "" + getTime((LocalTime) arg);
        }
        call += ") ";
        if(as != null) call += "AS " + as + " ";
        return call;
    }

    private String stripColumnChars(String input) {
        return input.replaceAll("[`'\"]", "");
    }

    private long getDate(LocalDate ld) {
        return ld.getLong(ChronoField.MILLI_OF_SECOND);
    }

    private long getDate(Date d) {
        return d.getTime();
    }

    private long getDateTime(LocalDateTime ldt) {
        return ldt.getLong(ChronoField.MILLI_OF_SECOND);
    }

    private long getDateTime(Date d) {
        return d.getTime();
    }

    private long getTime(LocalTime lt) {
        return lt.getLong(ChronoField.MILLI_OF_SECOND);
    }


    public Class getType() {
        return SQLFunction.class;
    }

    public String getTypeString() {
        return "Function";
    }

    public String full() {
        return get();
    }

    public String name() {
        return get();
    }
}
