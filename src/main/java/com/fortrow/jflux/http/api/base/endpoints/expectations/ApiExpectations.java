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
package com.fortrow.jflux.http.api.base.endpoints.expectations;

import com.fortrow.jflux.http.api.base.endpoints.InboundRequest;
import com.fortrow.jflux.http.api.base.endpoints.expectations.builtin.BodyExpectations;
import com.fortrow.jflux.http.api.base.endpoints.expectations.builtin.CookieExpectations;
import com.fortrow.jflux.http.api.base.endpoints.expectations.builtin.HeaderExpectations;

public class ApiExpectations {

    private BodyExpectations bodyExpectations = null;
    private CookieExpectations cookieExpectations = null;
    private HeaderExpectations headerExpectations = null;

    public ApiExpectations() { }

    public static ApiExpectations none() {
        return new ApiExpectations();
    }

    public ApiExpectations putBodyExpectations(BodyExpectations bodyExpectations) {
        this.bodyExpectations = bodyExpectations;
        return this;
    }

    public ApiExpectations putCookieExpectations(CookieExpectations cookieExpectations) {
        this.cookieExpectations = cookieExpectations;
        return this;
    }

    public ApiExpectations putHeaderExpectations(HeaderExpectations headerExpectations) {
        this.headerExpectations = headerExpectations;
        return this;
    }

    public ApiExpectations body(String... keys) {
        if(bodyExpectations == null) bodyExpectations = new BodyExpectations();
        bodyExpectations.require(keys);
        return this;
    }

    public ApiExpectations nonBlankBody(String... keys) {
        if(bodyExpectations == null) bodyExpectations = new BodyExpectations();
        bodyExpectations.requireNonBlank(keys);
        return this;
    }

    public ApiExpectations headers(String... keys) {
        if(headerExpectations == null) headerExpectations = new HeaderExpectations();
        headerExpectations.require(keys);
        return this;
    }

    public ApiExpectations nonBlankHeaders(String... keys) {
        if(headerExpectations == null) headerExpectations = new HeaderExpectations();
        headerExpectations.requireNonBlank(keys);
        return this;
    }

    public ApiExpectations cookies(String... keys) {
        if(cookieExpectations == null) cookieExpectations = new CookieExpectations();
        cookieExpectations.require(keys);
        return this;
    }

    public BodyExpectations getBodyExpectations() { return bodyExpectations; }
    public CookieExpectations getCookieExpectations() { return cookieExpectations; }
    public HeaderExpectations getHeaderExpectations() { return headerExpectations; }

    public boolean isEmpty() {
        return bodyExpectations == null
                && cookieExpectations == null
                && headerExpectations == null;
    }

    public ApiExpectationResult validate(InboundRequest request) {
        ApiExpectationResult result;

        if(headerExpectations != null) {
            result = headerExpectations.validate(request);
            if(!result.isValid()) return result;
        }

        if(cookieExpectations != null) {
            result = cookieExpectations.validate(request);
            if(!result.isValid()) return result;
        }

        if(bodyExpectations != null) {
            result = bodyExpectations.validate(request);
            if(!result.isValid()) return result;
        }

        return ApiExpectationResult.ok();
    }

}
