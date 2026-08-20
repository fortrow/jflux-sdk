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
 * File created by Ryan Fitzgerald (ryan@fortrow.com) on 2026-05-11 20:39:42 EDT
 * 
 * -------------------------------------------------------------------------

 */
package com.fortrow.jflux.http.api.base.prereqs;

import com.fortrow.jflux.db.connectors.details.DbConnectionSqlLiteDetails;
import com.fortrow.jflux.http.api.base.endpoints.ApiResource;
import com.fortrow.jflux.http.api.base.endpoints.InboundRequest;
import com.fortrow.jflux.http.api.base.endpoints.expectations.ApiExpectations;
import com.fortrow.jflux.http.api.base.endpoints.permissions.ApiEndpointPermissions;
import com.fortrow.jflux.http.api.base.endpoints.permissions.ApiUserPermissions;
import com.fortrow.jflux.http.api.server.config.APIServerConfig;
import com.fortrow.jflux.http.api.server.containers.APIResponse;
import com.fortrow.jflux.http.api.server.containers.BodyParameters;
import com.fortrow.jflux.http.api.server.containers.RequestHeaders;
import com.fortrow.jflux.http.api.server.containers.ResponseCode;
import com.fortrow.jflux.http.api.server.types.ContentType;
import com.fortrow.jflux.http.api.types.DatabaseServerType;
import com.sun.net.httpserver.Headers;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApiServiceEndpointPolicyTest {

    @Test
    void validatesExpectationsAndApiKeyPermissionsFromService() throws Throwable {
        ApiService service = service();
        service.registerApiUserPermissions(new ApiUserPermissions("apikey_allowed").grant("records.read"));
        ApiResource resource = protectedResource();

        assertNull(service.validateEndpointRequest(resource, request("apikey_allowed", "{\"name\":\"Ada\"}")));

        APIResponse denied = service.validateEndpointRequest(resource, request("apikey_denied", "{\"name\":\"Ada\"}"));
        assertEquals(ResponseCode.FORBIDDEN, denied.getResponseCode());

        APIResponse missingBody = service.validateEndpointRequest(resource, request("apikey_allowed", "{}"));
        assertEquals(ResponseCode.REQUEST_FAILED, missingBody.getResponseCode());
    }

    @Test
    void grantsEndpointRequiredKeysFromService() throws Throwable {
        ApiService service = service();
        service.grantApiKeyPermission("apikey_allowed", "records", "123");

        ApiResource resource = new ApiResource(1, "records/123", ApiResource.EndpointType.ENDPOINT, null) {
            @Override
            protected ApiEndpointPermissions permissions() {
                return ApiEndpointPermissions.none().requireKey("records", "123");
            }
        };

        assertNull(service.validateEndpointRequest(resource, request("apikey_allowed", "{}")));
        assertEquals(ResponseCode.FORBIDDEN, service.validateEndpointRequest(resource, request("apikey_denied", "{}")).getResponseCode());
    }

    @Test
    void persistentApiPermissionsRoundTripWithoutRawApiKey() throws Throwable {
        Path db = java.nio.file.Files.createTempFile("jflux-api-service-permissions", ".db");
        try {
            APIServerConfig config = new APIServerConfig(0);
            config.setSessionDb(DatabaseServerType.SQLITE, new DbConnectionSqlLiteDetails(db.toString()));

            ApiService writer = service();
            writer.configureDatabases(config);
            writer.registerApiUserPermissions(new ApiUserPermissions("apikey_policy").grant("records.read").grantKey("records", "123"));

            ApiService reader = service();
            reader.configureDatabases(config);
            ApiUserPermissions loaded = reader.lookupApiUserPermissions("apikey_policy");
            assertNotNull(loaded);
            assertTrue(loaded.has("records.read"));
            assertTrue(loaded.hasKey("records", "123"));

            String persistedPayload;
            String persistedApiKey;
            try(Connection conn = DriverManager.getConnection("jdbc:sqlite:" + db);
                Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery("SELECT payload, api_key FROM linkdb_api_api_permissions")) {
                rs.next();
                persistedPayload = rs.getString("payload");
                persistedApiKey = rs.getString("api_key");
            }

            assertFalse(persistedPayload.contains("apikey_policy"));
            assertFalse(persistedApiKey.equals("apikey_policy"));
        } finally {
            java.nio.file.Files.deleteIfExists(db);
        }
    }

    private ApiService service() {
        return new ApiService(
                "LinkDB",
                ApiService.generateAppKey("LinkDB", "f4b2d9f0f5a18f35d6708a53d861f7c2")
        );
    }

    private ApiResource protectedResource() {
        return new ApiResource(0, "records", ApiResource.EndpointType.ENDPOINT, null) {
            @Override
            protected ApiExpectations expectations() {
                return new ApiExpectations().nonBlankBody("name");
            }

            @Override
            protected ApiEndpointPermissions permissions() {
                return new ApiEndpointPermissions("records.read");
            }
        };
    }

    private InboundRequest request(String apiKey, String body) {
        Headers headers = new Headers();
        headers.add("X-Api-Key", apiKey);
        BodyParameters params = new BodyParameters(body, ContentType.JSON, null, null, false);
        return new InboundRequest(null, params, new RequestHeaders(headers));
    }
}
