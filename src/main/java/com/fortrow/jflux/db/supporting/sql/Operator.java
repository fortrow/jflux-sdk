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
package com.fortrow.jflux.db.supporting.sql;

import com.fortrow.jflux.exceptions.types.CommonException;

public class Operator {
    public enum V {
        EQUALS,
        NOT_EQUAL,
        GREATER_THAN,
        LESS_THAN,
        GREATER_THAN_OR_EQUAL,
        LESS_THAN_OR_EQUAL,
        TRUE,
        FALSE,
        NULL,
        NOT_NULL,
        SOFT_DELETED,
        LIKE,
        NOT_LIKE
    }

    public static boolean hasRight(V v) {
        switch(v) {
            case EQUALS, NOT_EQUAL, GREATER_THAN,
                    LESS_THAN, GREATER_THAN_OR_EQUAL,
                    LESS_THAN_OR_EQUAL, LIKE, NOT_LIKE -> {
                return true;
            }
            default -> {
                return false;
            }
        }
    }

    public static String get(V v) {
        switch(v) {
            case EQUALS -> {
                return "=";
            }
            case NOT_EQUAL -> {
                return "<>";
            }
            case GREATER_THAN -> {
                return ">";
            }
            case LESS_THAN -> {
                return "<";
            }
            case GREATER_THAN_OR_EQUAL -> {
                return ">=";
            }
            case LESS_THAN_OR_EQUAL -> {
                return "<=";
            }
            case TRUE -> {
                return "=1";
            }
            case FALSE -> {
                return "=0";
            }
            case NULL -> {
                return "IS NULL";
            }
            case NOT_NULL, SOFT_DELETED -> {
                return "IS NOT NULL";
            }
            case LIKE -> {
                return "LIKE ";
            }
            case NOT_LIKE -> {
                return "NOT LIKE ";
            }
            default -> {
                return "";
            }
        }
    }

    public static V parse(Object o) throws CommonException {
        if(o == null) return V.NULL;
        if(o.getClass() == String.class) return fromString((String) o);
        if(o.getClass() == Boolean.class) {
            boolean b = (boolean) o;
            if(b == true) return V.TRUE;
            if(b == false) return V.FALSE;
        }
        if(o.getClass() == Integer.class) {
            int i = (int) o;
            if(!(i == 0 || i == 1)) throw new CommonException("boolean must be 1 or 0");
            boolean b = false;
            if(i == 1) b = true;
            if(b == true) return V.TRUE;
            if(b == false) return V.FALSE;
        }
        throw new CommonException("Unknown where operator.");
    }

    private static V fromString(String str) {
        str = str.trim();
        if (str.equals("!=") || str.equals("<>")) return V.NOT_EQUAL;
        if (str.equals(">")) return V.GREATER_THAN;
        if (str.equals("<")) return V.LESS_THAN;
        if (str.equals(">=")) return V.GREATER_THAN_OR_EQUAL;
        if (str.equals("<=")) return V.LESS_THAN_OR_EQUAL;
        if (str.equals("==") || str.equals("=") || str.equals("===")) return V.EQUALS;
        if (str.equals("!") || str.toLowerCase().equals("not null") || str.toLowerCase().equals("!null")) return V.NOT_NULL;
        if (str.isEmpty() || str == null || str.toLowerCase().equals("null")) return V.NULL;
        if (str.toLowerCase().equals("true")) return V.TRUE;
        if (str.toLowerCase().equals("false")) return V.FALSE;
        if(str.toLowerCase().equals("like")) return V.LIKE;
        if(str.toLowerCase().equals("not like") || str.toLowerCase().equals("!like")) return V.NOT_LIKE;
        return null;
    }

}



