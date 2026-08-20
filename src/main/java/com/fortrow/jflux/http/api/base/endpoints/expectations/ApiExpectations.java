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
 * File created by Ryan Fitzgerald (ryan@fortrow.com) on 2026-05-04 15:27:04 EDT
 * 
 * -------------------------------------------------------------------------

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
