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
 * File created by Ryan Fitzgerald (ryan@fortrow.com) on 2026-05-11 20:33:09 EDT
 */
package com.fortrow.jflux.http.api.base.endpoints.permissions;

import com.fortrow.jflux.http.api.base.endpoints.InboundRequest;
import com.fortrow.jflux.http.api.server.containers.RequestHeaders;
import com.sun.net.httpserver.Headers;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApiEndpointPermissionsTest {

    @Test
    void grantsPermissionsByApiKey() {
        InboundRequest request = requestWithApiKey("apikey_allowed");
        ApiEndpointPermissions permissions = new ApiEndpointPermissions("records.read")
                .addUserPermissions(new ApiUserPermissions("apikey_allowed").grant("records.read"));

        assertTrue(permissions.isPermitted(request));
        assertFalse(permissions.isPermitted(requestWithApiKey("apikey_other")));
    }

    @Test
    void allowedApiKeyBypassesNamedPermissionLookup() {
        ApiEndpointPermissions permissions = new ApiEndpointPermissions("records.delete")
                .allowApiKey("apikey_admin");

        assertTrue(permissions.isPermitted(requestWithApiKey("apikey_admin")));
        assertFalse(permissions.isPermitted(requestWithApiKey("apikey_user")));
    }

    @Test
    void grantsResourceKeysByApiKey() {
        ApiEndpointPermissions permissions = ApiEndpointPermissions.none()
                .requireKey("records", "123")
                .addUserPermissions(new ApiUserPermissions("apikey_allowed").grantKey("records", "123"));

        assertTrue(permissions.isPermitted(requestWithApiKey("apikey_allowed")));
        assertFalse(permissions.isPermitted(requestWithApiKey("apikey_other")));
    }

    private InboundRequest requestWithApiKey(String apiKey) {
        Headers headers = new Headers();
        headers.add("X-Api-Key", apiKey);
        return new InboundRequest(null, null, new RequestHeaders(headers));
    }
}
