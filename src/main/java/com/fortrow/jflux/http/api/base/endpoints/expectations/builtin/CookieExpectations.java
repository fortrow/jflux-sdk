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
package com.fortrow.jflux.http.api.base.endpoints.expectations.builtin;

import com.fortrow.jflux.http.api.base.endpoints.InboundRequest;
import com.fortrow.jflux.http.api.base.endpoints.expectations.ApiExpectationResult;
import com.fortrow.jflux.http.api.server.containers.RequestHeaders;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

public class CookieExpectations {

    private final LinkedHashSet<String> required = new LinkedHashSet<>();
    private final HashMap<String, String> exactValues = new HashMap<>();

    public CookieExpectations require(String... keys) {
        addAll(required, keys);
        return this;
    }

    public CookieExpectations requireValue(String key, String value) {
        if(key != null && !key.isBlank()) {
            required.add(key);
            exactValues.put(key, value == null ? "" : value);
        }
        return this;
    }

    public Set<String> getRequired() {
        return Set.copyOf(required);
    }

    public boolean isEmpty() {
        return required.isEmpty() && exactValues.isEmpty();
    }

    public ApiExpectationResult validate(InboundRequest request) {
        RequestHeaders headers = request == null ? null : request.getHeaders();
        if(headers == null || !headers.contains("Cookie")) {
            return isEmpty() ? ApiExpectationResult.ok() : ApiExpectationResult.fail("Request cookies are required.");
        }

        Map<String, String> cookies = parseCookies(headers.getString("Cookie"));
        for(String key : required) {
            if(!cookies.containsKey(key)) {
                return ApiExpectationResult.fail("Missing required cookie: " + key);
            }
        }

        for(Map.Entry<String, String> entry : exactValues.entrySet()) {
            String value = cookies.get(entry.getKey());
            if(value == null || !value.equals(entry.getValue())) {
                return ApiExpectationResult.fail("Cookie has an unexpected value: " + entry.getKey());
            }
        }

        return ApiExpectationResult.ok();
    }

    private Map<String, String> parseCookies(String rawCookieHeader) {
        HashMap<String, String> cookies = new HashMap<>();
        if(rawCookieHeader == null || rawCookieHeader.isBlank()) return cookies;
        for(String part : rawCookieHeader.split(";")) {
            String[] split = part.trim().split("=", 2);
            if(split.length == 0 || split[0].isBlank()) continue;
            cookies.put(split[0].trim(), split.length > 1 ? split[1].trim() : "");
        }
        return cookies;
    }

    private void addAll(LinkedHashSet<String> target, String... keys) {
        if(keys == null) return;
        for(String key : keys) {
            if(key != null && !key.isBlank()) target.add(key);
        }
    }

}
