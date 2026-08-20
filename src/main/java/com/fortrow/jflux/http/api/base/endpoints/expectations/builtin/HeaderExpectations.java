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
