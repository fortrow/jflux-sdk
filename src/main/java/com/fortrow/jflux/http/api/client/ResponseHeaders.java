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
 * File created by Ryan Fitzgerald (ryan@fortrow.com) on 2026-05-08 11:36:29 EDT
 */
package com.fortrow.jflux.http.api.client;


import com.fortrow.jflux.todash.__;
import org.apache.http.Header;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.Locale;

public class ResponseHeaders {

    private HashMap<String, String> values = new HashMap<String, String>();

    public ResponseHeaders(Header[] headers) {

        if (headers == null) return;
        for(Header header: headers) {
            if (header == null || header.getName() == null) continue;
            this.values.put(header.getName().toUpperCase(Locale.ROOT), header.getValue());
        }

    }

    public ResponseHeaders(java.net.http.HttpHeaders headers) {

        if (headers == null) return;
        for(String key: headers.map().keySet()) {
            if (key == null || headers.map().get(key) == null || headers.map().get(key).isEmpty()) continue;
            this.values.put(key.toUpperCase(Locale.ROOT), headers.map().get(key).getFirst());
        }

    }

    public boolean contains(String key) {
        return key != null && values.containsKey(key.toUpperCase(Locale.ROOT));
    }

    public int getInt(String key) {
        return Integer.parseInt(values.get(key.toUpperCase(Locale.ROOT)));
    }

    public String getString(String key) {
        if(!contains(key)) return null;
        String value = values.get(key.toUpperCase(Locale.ROOT));
        return value == null ? null : value.contains(",") ? value.replace(",", ", ") : value;
    }

    public double getDouble(String key) {
        return Double.parseDouble(values.get(key.toUpperCase(Locale.ROOT)));
    }

    public boolean getBoolean(String key) throws Exception {

        String value = getString(key);
        if(value == null) throw new IllegalArgumentException("Missing response header: " + key);
        String val = value.toUpperCase(Locale.ROOT);

        if(val.equals("TRUE") || val.equals("1")) {
            return true;
        } else if(val.equals("FALSE") || val.equals("0")) {
            return false;
        }

        throw new Exception();

    }

    public String[] getArr(String key) {
        return getArr(key, ",");
    }

    public String[] getArr(String key, String delimiter) {
        String value = getString(key);
        return value == null ? new String[0] : __.split(value, delimiter);
    }

    public LocalDateTime getDateTimeFromTimestamp(String key) {
        String value = getString(key);
        if (value == null || value.isBlank()) return null;
        try {
            return LocalDateTime.ofEpochSecond(Long.parseLong(value.trim()), 0, ZoneOffset.UTC);
        } catch (RuntimeException ignored) {
            return null;
        }

    }

    /** Creates response headers from transport-neutral name/value pairs. */
    public ResponseHeaders(Map<String, String> headers) {
        if (headers == null) return;
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null) continue;
            this.values.put(entry.getKey().toUpperCase(Locale.ROOT), entry.getValue());
        }
    }

}
