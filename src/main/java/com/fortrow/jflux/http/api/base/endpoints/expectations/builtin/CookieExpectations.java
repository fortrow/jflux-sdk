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
