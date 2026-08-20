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
