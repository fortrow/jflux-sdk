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
 */

package com.fortrow.jflux.json;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

/**
 * Compatibility facade for the historical {@code com.fortrow.jflux.json}
 * namespace. New code should use {@link com.fortrow.jflux.output.json.JSON}.
 */
@Deprecated(since = "1.1.0")
public final class JSON {

    private JSON() { }

    public static <T> T parse(String json) {
        return com.fortrow.jflux.output.json.JSON.parse(json);
    }

    public static <T> T parse(String json, Class<T> type) {
        return com.fortrow.jflux.output.json.JSON.parse(json, type);
    }

    public static String string(Object value) {
        return com.fortrow.jflux.output.json.JSON.string(value);
    }

    public static String prettyString(Object value) {
        return com.fortrow.jflux.output.json.JSON.prettyString(value);
    }

    public static JSONObject getObject(String body) {
        return com.fortrow.jflux.output.json.JSON.getObject(body);
    }

    public static JSONArray getArray(String body) {
        return com.fortrow.jflux.output.json.JSON.getArray(body);
    }

    public static String getString(JSONArray array) {
        return com.fortrow.jflux.output.json.JSON.getString(array);
    }

    public static String getString(JSONObject object) {
        return com.fortrow.jflux.output.json.JSON.getString(object);
    }

    public static String getString(Object value) {
        return com.fortrow.jflux.output.json.JSON.getString(value);
    }

    public static String getString(JSONObject object, boolean format) {
        return com.fortrow.jflux.output.json.JSON.getString(object, format);
    }

    public static String getString(JSONArray array, boolean format) {
        return com.fortrow.jflux.output.json.JSON.getString(array, format);
    }

    public static String getString(Object value, boolean format) {
        return com.fortrow.jflux.output.json.JSON.getString(value, format);
    }

    public static boolean isValid(String json) {
        return com.fortrow.jflux.output.json.JSON.isValid(json);
    }

    /**
     * Preserves the historical null-on-failure sentinel for this namespace.
     * The output namespace uses the more explicit {@code "false"} sentinel.
     */
    public static String verify(String json) {
        String verified = com.fortrow.jflux.output.json.JSON.verify(json);
        return "false".equals(verified) ? null : verified;
    }
}
