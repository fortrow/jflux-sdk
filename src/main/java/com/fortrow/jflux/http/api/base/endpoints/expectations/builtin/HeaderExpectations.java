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

public class HeaderExpectations {

    private final LinkedHashSet<String> required = new LinkedHashSet<>();
    private final LinkedHashSet<String> nonBlank = new LinkedHashSet<>();
    private final HashMap<String, String> exactValues = new HashMap<>();

    public HeaderExpectations require(String... keys) {
        addAll(required, keys);
        return this;
    }

    public HeaderExpectations requireNonBlank(String... keys) {
        addAll(required, keys);
        addAll(nonBlank, keys);
        return this;
    }

    public HeaderExpectations requireValue(String key, String value) {
        if(key != null && !key.isBlank()) {
            required.add(key);
            exactValues.put(key.toUpperCase(), value == null ? "" : value);
        }
        return this;
    }

    public Set<String> getRequired() {
        return Set.copyOf(required);
    }

    public Map<String, String> getExactValues() {
        return Map.copyOf(exactValues);
    }

    public boolean isEmpty() {
        return required.isEmpty() && nonBlank.isEmpty() && exactValues.isEmpty();
    }

    public ApiExpectationResult validate(InboundRequest request) {
        RequestHeaders headers = request == null ? null : request.getHeaders();
        if(headers == null) {
            return isEmpty() ? ApiExpectationResult.ok() : ApiExpectationResult.fail("Request headers are required.");
        }

        for(String key : required) {
            if(!headers.contains(key)) {
                return ApiExpectationResult.fail("Missing required header: " + key);
            }
        }

        for(String key : nonBlank) {
            String value = headers.getString(key);
            if(value == null || value.isBlank()) {
                return ApiExpectationResult.fail("Header cannot be blank: " + key);
            }
        }

        for(Map.Entry<String, String> entry : exactValues.entrySet()) {
            String value = headers.getString(entry.getKey());
            if(value == null || !value.equals(entry.getValue())) {
                return ApiExpectationResult.fail("Header has an unexpected value: " + entry.getKey());
            }
        }

        return ApiExpectationResult.ok();
    }

    private void addAll(LinkedHashSet<String> target, String... keys) {
        if(keys == null) return;
        for(String key : keys) {
            if(key != null && !key.isBlank()) target.add(key);
        }
    }

}
