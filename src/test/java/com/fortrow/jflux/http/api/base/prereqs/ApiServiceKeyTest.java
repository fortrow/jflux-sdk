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
 * File created by Ryan Fitzgerald (ryan@fortrow.com) on 2026-05-11 18:25:54 EDT
 * 
 * -------------------------------------------------------------------------

 */
package com.fortrow.jflux.http.api.base.prereqs;

import com.fortrow.jflux.db.connectors.details.DbConnectionSqlLiteDetails;
import com.fortrow.jflux.http.api.server.containers.IpAddress;
import com.fortrow.jflux.http.api.server.encryption.APIKeys;
import com.fortrow.jflux.http.api.server.config.APIServerConfig;
import com.fortrow.jflux.http.api.types.DatabaseServerType;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ApiServiceKeyTest {

    @Test
    void generateAppKeyIsStableForSameAppAndNonce() {
        byte[] first = ApiService.generateAppKey("LinkDB", "f4b2d9f0f5a18f35d6708a53d861f7c2");
        byte[] second = ApiService.generateAppKey("LinkDB", "f4b2d9f0f5a18f35d6708a53d861f7c2");

        assertArrayEquals(first, second);
    }

    @Test
    void generateAppKeyChangesWhenNonceOrAppChanges() {
        byte[] baseline = ApiService.generateAppKey("LinkDB", "f4b2d9f0f5a18f35d6708a53d861f7c2");
        byte[] differentNonce = ApiService.generateAppKey("LinkDB", "8dfcf4bcd0d61ec8d50a43f4a576955e");
        byte[] differentApp = ApiService.generateAppKey("Registration", "f4b2d9f0f5a18f35d6708a53d861f7c2");

        assertFalse(Arrays.equals(baseline, differentNonce));
        assertFalse(Arrays.equals(baseline, differentApp));
    }

    @Test
    void persistentApiKeyRecordsAreEncryptedAndLookupDecrypts() throws Throwable {
        Path db = java.nio.file.Files.createTempFile("jflux-api-service", ".db");
        try {
            APIServerConfig config = new APIServerConfig(0);
            config.setSessionDb(DatabaseServerType.SQLITE, new DbConnectionSqlLiteDetails(db.toString()));

            ApiService service = new ApiService(
                    "LinkDB",
                    ApiService.generateAppKey("LinkDB", "f4b2d9f0f5a18f35d6708a53d861f7c2")
            );
            service.configureDatabases(config);

            service.registerApiKeys(
                    "apikey_test",
                    "secret_test",
                    "session_test",
                    "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
                    List.of(new IpAddress("127.0.0.1"))
            );

            APIKeys lookup = service.lookupApiKeys("apikey_test");
            assertNotNull(lookup);
            assertEquals("apikey_test", lookup.getKey());
            assertEquals("secret_test", lookup.getSecret());
            assertEquals("session_test", lookup.getRawSessionSecret());
            assertEquals("0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef", lookup.getPsk());
            assertFalse(lookup.ipIsAllowed("10.0.0.1"));

            String persistedPayload;
            String persistedCiphertext;
            try(Connection conn = DriverManager.getConnection("jdbc:sqlite:" + db);
                Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery("SELECT payload, value FROM linkdb_api_api_keys")) {
                assertNotNull(rs);
                rs.next();
                persistedPayload = rs.getString("payload");
                persistedCiphertext = rs.getString("value");
            }

            assertFalse(persistedPayload.contains("apikey_test"));
            assertFalse(persistedPayload.contains("secret_test"));
            assertFalse(persistedPayload.contains("session_test"));
            assertFalse(persistedPayload.contains("0123456789abcdef"));
            assertFalse(persistedCiphertext.contains("secret_test"));
        } finally {
            java.nio.file.Files.deleteIfExists(db);
        }
    }

    @Test
    void lockedApiKeyCannotBeLookedUpOrReused() throws Throwable {
        Path db = java.nio.file.Files.createTempFile("jflux-api-service-lock", ".db");
        try {
            APIServerConfig config = new APIServerConfig(0);
            config.setSessionDb(DatabaseServerType.SQLITE, new DbConnectionSqlLiteDetails(db.toString()));

            ApiService service = new ApiService(
                    "LinkDB",
                    ApiService.generateAppKey("LinkDB", "f4b2d9f0f5a18f35d6708a53d861f7c2")
            );
            service.configureDatabases(config);
            service.registerApiKeys("apikey_lock", "secret_lock", "session_lock", "abcdefabcdefabcdefabcdefabcdefabcdefabcdefabcdefabcdefabcdefabcd");

            assertNotNull(service.lookupApiKeys("apikey_lock"));

            service.lockApiKey("apikey_lock", "test lock");

            assertNull(service.lookupApiKeys("apikey_lock"));
            assertThrows(Throwable.class, () -> service.registerApiKeys("apikey_lock", "secret_lock", "session_lock", "abcdefabcdefabcdefabcdefabcdefabcdefabcdefabcdefabcdefabcdefabcd"));

            try(Connection conn = DriverManager.getConnection("jdbc:sqlite:" + db);
                Statement stmt = conn.createStatement();
                ResultSet apiRows = stmt.executeQuery("SELECT count(*) FROM linkdb_api_api_keys")) {
                apiRows.next();
                assertEquals(0, apiRows.getInt(1));
            }
        } finally {
            java.nio.file.Files.deleteIfExists(db);
        }
    }

    @Test
    void constructorRequiresExplicitAppKey() {
        assertThrows(IllegalArgumentException.class, () -> new ApiService("LinkDB", new byte[0]));
    }
}
