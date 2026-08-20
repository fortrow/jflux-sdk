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
 * File created by Ryan Fitzgerald (ryan@fortrow.com) on 2026-05-11 20:33:09 EDT
 * 
 * -------------------------------------------------------------------------

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
