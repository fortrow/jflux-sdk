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
