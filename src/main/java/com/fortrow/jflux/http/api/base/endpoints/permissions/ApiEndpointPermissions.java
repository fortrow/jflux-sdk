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
package com.fortrow.jflux.http.api.base.endpoints.permissions;

import com.fortrow.jflux.crypto.builtin.SecureCompare;
import com.fortrow.jflux.http.api.base.endpoints.InboundRequest;
import com.fortrow.jflux.http.api.server.containers.ClientSession;
import com.fortrow.jflux.http.api.server.containers.RequestHeaders;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

public class ApiEndpointPermissions {

    private final LinkedHashSet<String> requiredPermissions = new LinkedHashSet<>();
    private final LinkedHashSet<String> requiredKeys = new LinkedHashSet<>();
    private final LinkedHashSet<String> allowedApiKeys = new LinkedHashSet<>();
    private final HashMap<String, ApiUserPermissions> apiKeyPermissions = new HashMap<>();

    public ApiEndpointPermissions(String permissionName) {
        require(permissionName);
    }

    public static ApiEndpointPermissions none() {
        return new ApiEndpointPermissions("");
    }

    public ApiEndpointPermissions require(String... permissionNames) {
        addAll(requiredPermissions, permissionNames);
        return this;
    }

    public ApiEndpointPermissions allowApiKey(String apiKey) {
        if(apiKey != null && !apiKey.isBlank()) allowedApiKeys.add(apiKey);
        return this;
    }

    public ApiEndpointPermissions addUserPermissions(ApiUserPermissions userPermissions) {
        if(userPermissions != null && !userPermissions.getApiKey().isBlank()) {
            apiKeyPermissions.put(userPermissions.getApiKey(), userPermissions);
        }
        return this;
    }

    public void addPermittedKey(String table, String id) {
        requireKey(table, id);
    }

    public ApiEndpointPermissions requireKey(String table, String id) {
        String key = ApiUserPermissions.normalizeKey(table, id);
        if(!key.isBlank()) requiredKeys.add(key);
        return this;
    }

    public boolean isPermitted(String id) {
        return id == null || id.isBlank() || requiredKeys.contains(id);
    }

    public boolean isPermitted(InboundRequest request) {
        return isPermitted(request, null);
    }

    public boolean isPermitted(InboundRequest request, ApiUserPermissions servicePermissions) {
        if(isEmpty()) return true;

        String apiKey = apiKeyFrom(request);
        if(apiKey == null || apiKey.isBlank()) return false;

        for(String allowedApiKey : allowedApiKeys) {
            if(SecureCompare.equals(apiKey, allowedApiKey)) return true;
        }

        ApiUserPermissions userPermissions = apiKeyPermissions.get(apiKey);
        if(userPermissions == null && servicePermissions != null && SecureCompare.equals(apiKey, servicePermissions.getApiKey())) {
            userPermissions = servicePermissions;
        }
        if(userPermissions == null) return requiredPermissions.isEmpty() && requiredKeys.isEmpty();

        for(String requiredPermission : requiredPermissions) {
            if(!userPermissions.has(requiredPermission)) return false;
        }

        for(String requiredKey : requiredKeys) {
            if(!userPermissions.hasKey(requiredKey)) return false;
        }

        return true;
    }

    public String failureMessage() {
        return "API key does not have permission to access this endpoint.";
    }

    public boolean isEmpty() {
        return requiredPermissions.isEmpty()
                && requiredKeys.isEmpty()
                && allowedApiKeys.isEmpty()
                && apiKeyPermissions.isEmpty();
    }

    public Set<String> getRequiredPermissions() {
        return Set.copyOf(requiredPermissions);
    }

    public Set<String> getRequiredKeys() {
        return Set.copyOf(requiredKeys);
    }

    public Set<String> getAllowedApiKeys() {
        return Set.copyOf(allowedApiKeys);
    }

    public Map<String, ApiUserPermissions> getApiKeyPermissions() {
        return Map.copyOf(apiKeyPermissions);
    }

    public static String apiKeyFrom(InboundRequest request) {
        if(request == null) return null;

        ClientSession session = request.getSession();
        if(session != null) {
            if(session.getClient() != null && session.getClient().getApiKey() != null) return session.getClient().getApiKey();
            if(session.getSession() != null && session.getSession().getApiKeys() != null) return session.getSession().getApiKeys().getKey();
        }

        RequestHeaders headers = request.getHeaders();
        return headers != null && headers.contains("X-Api-Key") ? headers.getString("X-Api-Key") : null;
    }

    private void addAll(LinkedHashSet<String> target, String... values) {
        if(values == null) return;
        for(String value : values) {
            if(value != null && !value.isBlank()) target.add(value);
        }
    }

}
