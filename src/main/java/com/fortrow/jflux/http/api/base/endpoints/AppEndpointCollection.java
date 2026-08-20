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
package com.fortrow.jflux.http.api.base.endpoints;

import java.util.HashMap;

public class AppEndpointCollection {

    private final HashMap<String, ApiResource> endpoints = new HashMap<>();

    public AppEndpointCollection() { }

    public ApiResource getEndpoint(String path) {
        return endpoints.get(normalize(path));
    }

    public void putEndpoint(String basePath, ApiResource endpoint) {
        endpoints.put(normalize(basePath), endpoint);
    }

    public void putEndpoint(String basePath, String endpointPath, ApiResource endpoint) {
        endpoints.put(normalize(join(basePath, endpointPath)), endpoint);
    }

    private static String join(String basePath, String endpointPath) {
        String base = normalize(basePath);
        String endpoint = normalize(endpointPath);
        if(base.isBlank()) return endpoint;
        if(endpoint.isBlank()) return base;
        return base + "/" + endpoint;
    }

    private static String normalize(String input) {
        if(input == null || input.isBlank()) return "";
        String output = input.trim().replaceAll("\\s+", "").replaceAll("\\.", "/");
        while(output.startsWith("/")) output = output.substring(1);
        while(output.endsWith("/") && output.length() > 1) output = output.substring(0, output.length() - 1);
        return output;
    }

}
