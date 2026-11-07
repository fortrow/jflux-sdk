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
