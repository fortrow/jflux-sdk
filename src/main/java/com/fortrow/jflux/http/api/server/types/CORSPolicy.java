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
 * File created by Ryan Fitzgerald (ryan@fortrow.com) on 2026-05-10 22:51:50 EDT
 * 
 * -------------------------------------------------------------------------

 */
package com.fortrow.jflux.http.api.server.types;

import java.util.ArrayList;
import java.util.Locale;

public class CORSPolicy {

    private static final String[] DEFAULT_ORIGINS = new String[]{"*"};
    private static final String[] DEFAULT_METHODS = new String[]{"GET", "POST", "OPTIONS"};
    private static final String[] REST_HEADERS = new String[]{
            "Origin", "X-Requested-With", "Content-Type", "Accept", "X-Api-Key"
    };
    private static final String[] ENCRYPTED_HEADERS = new String[]{
            "Origin", "X-Requested-With", "Content-Type", "Accept",
            "Content-Transfer-Encoding", "X-Api-Key", "X-Request-Timestamp",
            "X-Request-Signature", "X-Request-Sequence", "X-Api-Session-Id",
            "X-Api-Key-Set-Id", "X-Api-Session-Authorization",
            "X-Api-Authorization", "X-Api-Random-Bytes", "X-Api-Resource-Method"
    };

    private final ArrayList<String> origins = new ArrayList<String>();
    private final ArrayList<String> methods = new ArrayList<String>();
    private boolean isPermitted = false;
    private boolean wildcardOriginsAllowed = true;

    public CORSPolicy(boolean isPermitted) {
        this.isPermitted = isPermitted;
    }

    public CORSPolicy(boolean isPermitted, boolean wildcardOriginsAllowed) {
        this.isPermitted = isPermitted;
        this.wildcardOriginsAllowed = wildcardOriginsAllowed;
    }

    public CORSPolicy restrictOrigins() {
        wildcardOriginsAllowed = false;
        validateOrThrow();
        return this;
    }

    public CORSPolicy allowWildcardOrigins(boolean val) {
        wildcardOriginsAllowed = val;
        validateOrThrow();
        return this;
    }

    public boolean wildcardOriginsAllowed() {
        return wildcardOriginsAllowed;
    }

    public void isPermitted(boolean val) {
        isPermitted = val;
    }

    public boolean isPermitted() {
        return isPermitted;
    }

    public void setOrigins(String[] origins) {
        if(origins == null) throw new IllegalArgumentException("CORS origins cannot be null.");
        this.origins.clear();
        for (String origin : origins) {
            addOrigin(origin);
        }
    }

    public void setMethods(String[] methods) {
        if(methods == null) throw new IllegalArgumentException("CORS methods cannot be null.");
        this.methods.clear();
        for (String method : methods) {
            addMethod(method);
        }
    }

    @Deprecated
    public void setHeaders(String[] headers) {
        throw new UnsupportedOperationException("CORS headers are derived from the active API security mode.");
    }

    public void addOrigin(String origin) {
        String normalized = normalizeOrigin(origin);
        validateOrigin(normalized);
        addUnique(origins, normalized);
    }

    @Deprecated
    public void addHeader(String header) {
        throw new UnsupportedOperationException("CORS headers are derived from the active API security mode.");
    }

    public void addMethod(String method) {
        String normalized = normalizeMethod(method);
        validateMethod(normalized);
        addUnique(methods, normalized);
    }

    public String[] getOrigins() {
        if(origins.size() == 0) return DEFAULT_ORIGINS.clone();
        return toArr(origins);
    }

    public String[] getHeaders() {
        return getHeaders(APIServerType.ENCRYPTED_SYNC);
    }

    public String[] getHeaders(APIServerType serverType) {
        if(serverType == APIServerType.REST_SYNC || serverType == APIServerType.REST_ASYNC) {
            return REST_HEADERS.clone();
        }
        return ENCRYPTED_HEADERS.clone();
    }

    public String[] getExposeHeaders(APIServerType serverType) {
        if(serverType == APIServerType.REST_SYNC || serverType == APIServerType.REST_ASYNC) {
            return new String[]{"X-Api-Key"};
        }
        return ENCRYPTED_HEADERS.clone();
    }

    public String[] getMethods() {
        if(methods.size() == 0) return DEFAULT_METHODS.clone();
        return toArr(methods);
    }

    public boolean validate() {
        try {
            validateOrThrow();
            return true;
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    public void validateOrThrow() {
        if(!isPermitted) return;

        if(origins.size() == 0 && !wildcardOriginsAllowed) {
            throw new IllegalArgumentException("Restricted CORS policies must define at least one origin.");
        }

        for(String origin : getOrigins()) {
            validateOrigin(origin);
        }

        for(String method : getMethods()) {
            validateMethod(method);
        }
    }

    public String getCommaSeparated(String[] arr) {
        if(arr == null) return "";
        return String.join(", ", arr);
    }

    private String normalizeOrigin(String origin) {
        if(origin == null) throw new IllegalArgumentException("CORS origin cannot be null.");
        return origin.trim();
    }

    private String normalizeMethod(String method) {
        if(method == null) throw new IllegalArgumentException("CORS method cannot be null.");
        return method.trim().toUpperCase(Locale.ROOT);
    }

    private void validateOrigin(String origin) {
        if(origin == null || origin.isBlank()) {
            throw new IllegalArgumentException("CORS origin cannot be blank.");
        }
        if("*".equals(origin)) {
            if(!wildcardOriginsAllowed) {
                throw new IllegalArgumentException("Wildcard CORS origins are not allowed by this policy.");
            }
            return;
        }
        if(!origin.startsWith("https://") && !origin.startsWith("http://")) {
            throw new IllegalArgumentException("CORS origin must include an http:// or https:// scheme: " + origin);
        }
        if(origin.endsWith("/")) {
            throw new IllegalArgumentException("CORS origin must not include a trailing slash: " + origin);
        }
    }

    private void validateMethod(String method) {
        if(method == null || method.isBlank()) {
            throw new IllegalArgumentException("CORS method cannot be blank.");
        }
        if(!isAllowedMethod(method)) {
            throw new IllegalArgumentException("Unsupported CORS method: " + method);
        }
    }

    private boolean isAllowedMethod(String method) {
        return "GET".equals(method)
                || "POST".equals(method)
                || "PUT".equals(method)
                || "PATCH".equals(method)
                || "DELETE".equals(method)
                || "OPTIONS".equals(method)
                || "HEAD".equals(method);
    }

    private void addUnique(ArrayList<String> list, String value) {
        if(!list.contains(value)) list.add(value);
    }

    private String[] toArr(ArrayList<String> list) {
        String[] retVal = new String[list.size()];
        for(int i=0; i<retVal.length; i++) {
            retVal[i] = list.get(i);
        }
        return retVal;
    }

}
