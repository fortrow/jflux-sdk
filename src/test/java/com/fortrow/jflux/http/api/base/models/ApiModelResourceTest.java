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
 * File created by Ryan Fitzgerald (ryan@fortrow.com) on 2026-05-11 20:47:16 EDT
 * 
 * -------------------------------------------------------------------------

 */
package com.fortrow.jflux.http.api.base.models;

import com.fortrow.jflux.db.Db;
import com.fortrow.jflux.db.connectors.details.DbConnectionSqlLiteDetails;
import com.fortrow.jflux.http.api.base.endpoints.InboundRequest;
import com.fortrow.jflux.http.api.base.endpoints.permissions.ApiUserPermissions;
import com.fortrow.jflux.http.api.base.prereqs.ApiService;
import com.fortrow.jflux.http.api.server.containers.APIResponse;
import com.fortrow.jflux.http.api.server.containers.BodyParameters;
import com.fortrow.jflux.http.api.server.containers.RequestHeaders;
import com.fortrow.jflux.http.api.server.containers.ResponseCode;
import com.fortrow.jflux.http.api.server.types.ContentType;
import com.fortrow.jflux.json.JSON;
import com.sun.net.httpserver.Headers;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApiModelResourceTest {

    @Test
    void fieldAccessControlsDefaultModelResponses() throws Throwable {
        Path db = java.nio.file.Files.createTempFile("jflux-api-model", ".db");
        try {
            ApiService service = service();
            service.setApplicationDb(new Db(new DbConnectionSqlLiteDetails(db.toString())));
            service.registerApiUserPermissions(new ApiUserPermissions("apikey"));
            PersonModel model = new PersonModel(service);
            service.migrateModel(model);

            APIResponse created = service.executeModelResource(
                    model,
                    request("apikey", "{\"id\":\"person_1\",\"name\":\"Ada\",\"secret\":\"protected\",\"internal\":\"private\"}"),
                    "POST"
            );
            assertEquals(ResponseCode.SUCCESSFUL, created.getResponseCode());

            JSONObject publicResponse = JSON.getObject(service.executeModelResource(model, request(null, "{\"id\":\"person_1\"}"), "GET").getResponse());
            JSONObject publicRecord = firstRecord(publicResponse);
            assertEquals("Ada", publicRecord.get("name"));
            assertFalse(publicRecord.containsKey("secret"));
            assertFalse(publicRecord.containsKey("internal"));

            JSONObject protectedResponse = JSON.getObject(service.executeModelResource(model, request("apikey", "{\"id\":\"person_1\"}"), "GET").getResponse());
            JSONObject protectedRecord = firstRecord(protectedResponse);
            assertEquals("Ada", protectedRecord.get("name"));
            assertEquals("protected", protectedRecord.get("secret"));
            assertFalse(protectedRecord.containsKey("internal"));
        } finally {
            java.nio.file.Files.deleteIfExists(db);
        }
    }


    @Test
    void protectedFieldsCanRequireFieldSpecificPermissions() throws Throwable {
        Path db = java.nio.file.Files.createTempFile("jflux-api-model-field-perms", ".db");
        try {
            ApiService service = service();
            service.setApplicationDb(new Db(new DbConnectionSqlLiteDetails(db.toString())));
            service.registerApiUserPermissions(new ApiUserPermissions("writer")
                    .grant("model:secure_people:field:secret:write")
                    .grant("model:secure_people:field:secret:read"));
            service.registerApiUserPermissions(new ApiUserPermissions("reader"));
            SecurePersonModel model = new SecurePersonModel(service);
            service.migrateModel(model);

            service.executeModelResource(
                    model,
                    request("writer", "{\"id\":\"person_1\",\"name\":\"Ada\",\"secret\":\"protected\"}"),
                    "POST"
            );

            JSONObject deniedResponse = JSON.getObject(service.executeModelResource(model, request("reader", "{\"id\":\"person_1\"}"), "GET").getResponse());
            JSONObject deniedRecord = firstRecord(deniedResponse);
            assertEquals("Ada", deniedRecord.get("name"));
            assertFalse(deniedRecord.containsKey("secret"));

            service.grantApiPermissions("reader", "model:secure_people:field:secret:read");
            JSONObject allowedResponse = JSON.getObject(service.executeModelResource(model, request("reader", "{\"id\":\"person_1\"}"), "GET").getResponse());
            JSONObject allowedRecord = firstRecord(allowedResponse);
            assertEquals("protected", allowedRecord.get("secret"));
        } finally {
            java.nio.file.Files.deleteIfExists(db);
        }
    }

    private JSONObject firstRecord(JSONObject response) {
        JSONArray records = (JSONArray) response.get("records");
        assertTrue(records.size() > 0);
        return (JSONObject) records.get(0);
    }

    private ApiService service() {
        return new ApiService(
                "LinkDB",
                ApiService.generateAppKey("LinkDB", "f4b2d9f0f5a18f35d6708a53d861f7c2")
        );
    }

    private InboundRequest request(String apiKey, String body) {
        Headers headers = new Headers();
        if(apiKey != null) headers.add("X-Api-Key", apiKey);
        BodyParameters params = new BodyParameters(body, ContentType.JSON, null, null, false);
        return new InboundRequest(null, params, new RequestHeaders(headers));
    }

    public static class PersonModel extends ApiModel {
        public PersonModel(ApiService service) {
            this("people", service);
        }

        public PersonModel(String modelName, ApiService service) {
            super(modelName, service);
        }

        @Override
        protected ArrayList<Class<? extends ApiModelField>> build(ArrayList<Class<? extends ApiModelField>> list) {
            list.add(Id.class);
            list.add(Name.class);
            list.add(Secret.class);
            list.add(Internal.class);
            return list;
        }
    }

    public static class SecurePersonModel extends PersonModel {
        public SecurePersonModel(ApiService service) {
            super("secure_people", service);
        }

        @Override
        protected boolean requireProtectedFieldPermissions() {
            return true;
        }
    }

    public static class Id extends ApiModelField {
        @Override public String name() { return "id"; }
        @Override protected FieldType type() { return FieldType.ID_STRING; }
        @Override protected FieldAccess access() { return FieldAccess.PUBLIC; }
    }

    public static class Name extends ApiModelField {
        @Override public String name() { return "name"; }
        @Override protected FieldType type() { return FieldType.STRING; }
        @Override protected FieldAccess access() { return FieldAccess.PUBLIC; }
    }

    public static class Secret extends ApiModelField {
        @Override public String name() { return "secret"; }
        @Override protected FieldType type() { return FieldType.STRING; }
        @Override protected FieldAccess access() { return FieldAccess.PROTECTED; }
    }

    public static class Internal extends ApiModelField {
        @Override public String name() { return "internal"; }
        @Override protected FieldType type() { return FieldType.STRING; }
        @Override protected FieldAccess access() { return FieldAccess.PRIVATE; }
    }
}
