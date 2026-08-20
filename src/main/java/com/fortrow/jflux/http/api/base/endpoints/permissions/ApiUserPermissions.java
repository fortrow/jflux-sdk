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

import java.util.LinkedHashSet;
import java.util.Set;

public class ApiUserPermissions {

    private final String apiKey;
    private final LinkedHashSet<String> permissions = new LinkedHashSet<>();
    private final LinkedHashSet<String> permittedKeys = new LinkedHashSet<>();

    public ApiUserPermissions(String apiKey) {
        this.apiKey = apiKey == null ? "" : apiKey;
    }

    public String getApiKey() {
        return apiKey;
    }

    public ApiUserPermissions grant(String... permissionNames) {
        addAll(permissions, permissionNames);
        return this;
    }

    public ApiUserPermissions grantKey(String table, String id) {
        return grantKey(normalizeKey(table, id));
    }

    public ApiUserPermissions grantKey(String key) {
        if(key != null && !key.isBlank()) permittedKeys.add(key.trim());
        return this;
    }

    public boolean has(String permissionName) {
        return permissionName == null || permissionName.isBlank() || permissions.contains(permissionName);
    }

    public boolean hasKey(String table, String id) {
        return permittedKeys.contains(normalizeKey(table, id));
    }

    public boolean hasKey(String key) {
        return key != null && permittedKeys.contains(key);
    }

    public Set<String> getPermissions() {
        return Set.copyOf(permissions);
    }

    public Set<String> getPermittedKeys() {
        return Set.copyOf(permittedKeys);
    }

    static String normalizeKey(String table, String id) {
        String left = table == null ? "" : table.trim();
        String right = id == null ? "" : id.trim();
        if(left.isBlank()) return right;
        if(right.isBlank()) return left;
        return left + ":" + right;
    }

    private void addAll(LinkedHashSet<String> target, String... values) {
        if(values == null) return;
        for(String value : values) {
            if(value != null && !value.isBlank()) target.add(value);
        }
    }

}
