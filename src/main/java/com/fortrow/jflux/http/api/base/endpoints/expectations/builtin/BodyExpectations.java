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
import com.fortrow.jflux.http.api.server.containers.BodyParameters;

import java.util.LinkedHashSet;
import java.util.Set;

public class BodyExpectations {

    private final LinkedHashSet<String> required = new LinkedHashSet<>();
    private final LinkedHashSet<String> nonBlank = new LinkedHashSet<>();

    public BodyExpectations require(String... keys) {
        addAll(required, keys);
        return this;
    }

    public BodyExpectations requireNonBlank(String... keys) {
        addAll(required, keys);
        addAll(nonBlank, keys);
        return this;
    }

    public Set<String> getRequired() {
        return Set.copyOf(required);
    }

    public Set<String> getNonBlank() {
        return Set.copyOf(nonBlank);
    }

    public boolean isEmpty() {
        return required.isEmpty() && nonBlank.isEmpty();
    }

    public ApiExpectationResult validate(InboundRequest request) {
        BodyParameters params = request == null ? null : request.getParams();
        if(params == null) {
            return isEmpty() ? ApiExpectationResult.ok() : ApiExpectationResult.fail("Request body parameters are required.");
        }

        for(String key : required) {
            if(!params.present(key)) {
                return ApiExpectationResult.fail("Missing required body parameter: " + key);
            }
        }

        for(String key : nonBlank) {
            Object value = params.get(key);
            if(value == null || value.toString().isBlank()) {
                return ApiExpectationResult.fail("Body parameter cannot be blank: " + key);
            }
        }

        return ApiExpectationResult.ok();
    }

    protected void addAll(LinkedHashSet<String> target, String... keys) {
        if(keys == null) return;
        for(String key : keys) {
            if(key != null && !key.isBlank()) target.add(key);
        }
    }

}
