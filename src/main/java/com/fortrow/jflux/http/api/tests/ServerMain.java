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
 * File created by Ryan Fitzgerald (ryan@fortrow.com) on 2026-05-08 22:56:29 EDT
 * 
 * -------------------------------------------------------------------------

 */
package com.fortrow.jflux.http.api.tests;

import com.fortrow.jflux.db.connectors.details.DbConnectionSqlLiteDetails;
import com.fortrow.jflux.http.api.base.endpoints.InboundRequest;
import com.fortrow.jflux.http.api.base.prereqs.ApiService;
import com.fortrow.jflux.http.api.server.API;
import com.fortrow.jflux.http.api.server.base.EncryptedAPI;
import com.fortrow.jflux.http.api.server.base.RESTfulAPI;
import com.fortrow.jflux.http.api.server.config.APIServerConfig;
import com.fortrow.jflux.http.api.server.containers.APIResponse;
import com.fortrow.jflux.http.api.server.containers.IpAddress;
import com.fortrow.jflux.http.api.server.containers.ResponseCode;
import com.fortrow.jflux.http.api.server.encryption.APIKeys;
import com.fortrow.jflux.http.api.server.exceptions.APIException;
import com.fortrow.jflux.http.api.server.resources.APIResource;
import com.fortrow.jflux.http.api.server.types.APIServerType;
import com.fortrow.jflux.http.api.server.types.CORSPolicy;
import com.fortrow.jflux.http.api.types.DatabaseServerType;
import com.fortrow.jflux.ssl.SslCertKeyPair;
import com.fortrow.jflux.ssl.keystore.JavaKeyStore;
import com.fortrow.jflux.ssl.selfsigned.SelfSignedSSL;
import org.json.simple.JSONObject;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.EnumSet;
import java.util.List;

public class ServerMain {

    private static final long ASYNC_TEST_SLEEP_MS = 3000L;

    public static void main(String[] args) throws Throwable {
        final boolean ASYNC = hasArg(args, "--async");
        final boolean REST = hasArg(args, "--rest");
        final boolean HTTP1 = hasArg(args, "--http1");
        final String APP_NAME = "jflux-test-api";
        final String APP_KEY_NONCE = "1e69f12df6cc9e9d0fe1b8ab7962ec388c048361808c92fbafe85e4cc47729fe";
        final String API_KEY = "apikey_ZyiUHezSAgCOTUlbIiFbS8bjCVNx6fpgoZNl5Njix6exjUPvw9dxMd9HBrHuyhZRJFbwDxFC";
        final String API_SECRET = "secret_HKUsdVA0lVFtVcLa4obTI2CWxckl4spMgH93aUuZDIZ5ZQvrUjcrc3U2CXz9KHBF22Gx6YYz";
        final String API_SESSION_SECRET = "c00a9411a30f53f23f58ca076f54cdc39f6afaa2f7a86218d4e5bfd98c95e822";
        final String API_PSK = "480cde81e2d825ddcf2217a72f36078e2ffdfb2388fc10b5fe4b4d353be7a321";

        Path stateDir = Path.of(System.getProperty("user.home"), ".jflux-api-test");
        Path sessionDbFile = stateDir.resolve("sessions.db");
        Path sessionSecretFile = stateDir.resolve("session-secret.txt");
        String currentSessionSecret = readSessionSecret(sessionSecretFile, API_SESSION_SECRET);

        TestKeySet keySet = new TestKeySet(API_KEY, API_SECRET, currentSessionSecret, API_PSK);
        printKeys(keySet, sessionDbFile, sessionSecretFile);

        if(REST && HTTP1) {
            testRestHttp1(APP_NAME, APP_KEY_NONCE, keySet, ASYNC);
        } else if(REST) {
            testRestHttp3(APP_NAME, APP_KEY_NONCE, keySet, ASYNC);
        } else if(HTTP1) {
            testEncryptedHttp1(APP_NAME, APP_KEY_NONCE, keySet, sessionDbFile, sessionSecretFile, ASYNC);
        } else {
            testEncryptedHttp3(APP_NAME, APP_KEY_NONCE, keySet, sessionDbFile, sessionSecretFile, ASYNC);
        }
    }

    private static boolean hasArg(String[] args, String expected) {
        for(String arg : args) {
            if(expected.equalsIgnoreCase(arg)) return true;
        }
        return false;
    }

    private static void testRestHttp1(String appName, String appKeyNonce, TestKeySet keySet, boolean async) throws Throwable {
        APIServerConfig config = restConfig(Path.of(System.getProperty("user.home"), ".jflux-api-test", "rest-http1-sessions.db"), async);

        API api = buildRestApi(appName, appKeyNonce, keySet, config);
        api.start();
        addQueryResource(api, async);
    }

    private static void testRestHttp3(String appName, String appKeyNonce, TestKeySet keySet, boolean async) throws Throwable {
        SslCertKeyPair selfSigned = SelfSignedSSL.create("/tmp", "testkey", "US", "City", "Virginia", "Fortrow, LLC", "IT", "localhost");
        JavaKeyStore keyStore = new JavaKeyStore(selfSigned, "v1.3");

        APIServerConfig config = restConfig(Path.of(System.getProperty("user.home"), ".jflux-api-test", "rest-http3-sessions.db"), async);
        config.useHttp3(keyStore);
        config.setUpgradePort(8180);
        config.setHttpsUpgradePort(8182);

        API api = buildRestApi(appName, appKeyNonce, keySet, config);
        api.start();
        addQueryResource(api, async);
    }

    private static void testEncryptedHttp1(String appName, String appKeyNonce, TestKeySet keySet, Path sessionDbFile, Path sessionSecretFile, boolean async) throws Throwable {
        SslCertKeyPair selfSigned = SelfSignedSSL.create("/tmp", "testkey", "US", "City", "Virginia", "Fortrow, LLC", "IT", "localhost");
        JavaKeyStore keyStore = new JavaKeyStore(selfSigned, "v1.3");

        APIServerConfig config = encryptedConfig(sessionDbFile, async);
        config.setApiServerKeyPassword("password");
        config.setApiServerKeystorePath("/home/ryan/testkey.jks");
        config.useTls();
        config.setApiServerSslContext(keyStore.ssl());

        API api = buildEncryptedApi(appName, appKeyNonce, keySet, sessionSecretFile, config);
        api.start();
        addQueryResource(api, async);
    }

    private static void testEncryptedHttp3(String appName, String appKeyNonce, TestKeySet keySet, Path sessionDbFile, Path sessionSecretFile, boolean async) throws Throwable {
        SslCertKeyPair selfSigned = SelfSignedSSL.create("/tmp", "testkey", "US", "City", "Virginia", "Fortrow, LLC", "IT", "localhost");
        JavaKeyStore keyStore = new JavaKeyStore(selfSigned, "v1.3");

        APIServerConfig config = encryptedConfig(sessionDbFile, async);
        config.setApiServerKeyPassword("password");
        config.setApiServerKeystorePath("/home/ryan/testkey.jks");
        config.useHttp3(keyStore);
        config.setUpgradePort(8180);
        config.setHttpsUpgradePort(8182);

        API api = buildEncryptedApi(appName, appKeyNonce, keySet, sessionSecretFile, config);
        api.start();
        addQueryResource(api, async);
    }


    private static APIServerConfig restConfig(Path sessionDbFile, boolean async) throws IOException {
        APIServerConfig config = new APIServerConfig("127.0.0.1", 8181, "/api");
        configureSessionDb(config, sessionDbFile);
        configureLogFile(config);
        config.setServerType(async ? APIServerType.REST_ASYNC : APIServerType.REST_SYNC);
        config.setCorsPolicy(new CORSPolicy(true));
        config.setRateLimit(25);
        return config;
    }

    private static APIServerConfig encryptedConfig(Path sessionDbFile, boolean async) throws IOException {
        APIServerConfig config = new APIServerConfig("127.0.0.1", 8181, "/api");
        configureSessionDb(config, sessionDbFile);
        configureLogFile(config);
        config.setServerType(async ? APIServerType.ENCRYPTED_ASYNC : APIServerType.ENCRYPTED_SYNC);
        config.setCorsPolicy(new CORSPolicy(true));
        config.setRateLimit(25);
        config.useDynamicApiKey(true);
        config.setSessionResourcePaths("session/init", "session/close");
        return config;
    }

    private static API buildRestApi(String appName, String appKeyNonce, TestKeySet keySet, APIServerConfig config) throws Throwable {
        return new RESTfulAPI(config, buildService(appName, appKeyNonce, keySet, null, config));
    }

    private static API buildEncryptedApi(String appName, String appKeyNonce, TestKeySet keySet, Path sessionSecretFile, APIServerConfig config) throws Throwable {
        return new EncryptedAPI(config, buildService(appName, appKeyNonce, keySet, sessionSecretFile, config));
    }

    private static ApiService buildService(String appName, String appKeyNonce, TestKeySet keySet, Path sessionSecretFile, APIServerConfig config) throws Throwable {
        ApiService service = new ApiService(appName, ApiService.generateAppKey(appName, appKeyNonce)) {
            @Override
            public void updateSessionSecret(APIKeys keys, String sessionSecret) throws APIException {
                super.updateSessionSecret(keys, sessionSecret);
                if(sessionSecretFile != null) {
                    try {
                        writeSessionSecret(sessionSecretFile, sessionSecret);
                    } catch (IOException e) {
                        throw new APIException("Failed to write API session secret file: " + e.getMessage());
                    }
                }
            }
        };

        service.configureDatabases(config);
        service.registerApiKeys(
                keySet.apiKey,
                keySet.apiSecret,
                keySet.sessionSecret,
                keySet.psk,
                List.of(new IpAddress("127.0.0.1"))
        );
        return service;
    }

    private static void addQueryResource(API api, boolean async) {
        api.addResource("query/new", "POST", async, new String[]{"sql", "parameters"}, new APIResource() {
            @Override
            public APIResponse execute(InboundRequest request) throws Exception {
                if(async) request.getExecutionContext().sleep(ASYNC_TEST_SLEEP_MS);
                JSONObject obj = new JSONObject();
                obj.put("response", "RESPONSE");
                return new APIResponse(request.getSession(), obj, ResponseCode.SUCCESSFUL);
            }
        });
    }

    private static void configureSessionDb(APIServerConfig config, Path sessionDbFile) throws IOException {
        ensureParent(sessionDbFile);
        config.setSessionDb(DatabaseServerType.SQLITE, new DbConnectionSqlLiteDetails(sessionDbFile.toString()));
    }

    private static void configureLogFile(APIServerConfig config) throws IOException {
        Path logFile = Path.of(System.getProperty("user.home"), ".jflux-api-test", "server.log");
        ensureParent(logFile);
        config.setLogFile(logFile.toString());
    }

    private static String readSessionSecret(Path file, String defaultValue) throws IOException {
        ensureParent(file);
        if(Files.exists(file)) {
            String value = Files.readString(file, StandardCharsets.UTF_8).trim();
            if(!value.isBlank()) return value;
        }
        writeSessionSecret(file, defaultValue);
        return defaultValue;
    }

    private static void writeSessionSecret(Path file, String sessionSecret) throws IOException {
        ensureParent(file);
        Files.writeString(file, sessionSecret + System.lineSeparator(), StandardCharsets.UTF_8);
        secureOwnerOnly(file);
    }

    private static void ensureParent(Path file) throws IOException {
        Path parent = file.getParent();
        if(parent != null) Files.createDirectories(parent);
    }

    private static void secureOwnerOnly(Path file) {
        try {
            Files.setPosixFilePermissions(file, EnumSet.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE));
        } catch (UnsupportedOperationException | IOException ignored) {
        }
    }

    private static void printKeys(TestKeySet keySet, Path sessionDbFile, Path sessionSecretFile) {
        System.out.println("API KEY = " + keySet.apiKey);
        System.out.println("SECRET = " + keySet.apiSecret);
        System.out.println("SESSION SECRET = " + keySet.sessionSecret);
        System.out.println("PRE-SHARED KEY = " + keySet.psk);
        System.out.println("SESSION DB = " + sessionDbFile);
        System.out.println("SESSION SECRET FILE = " + sessionSecretFile);
    }

    private static final class TestKeySet {
        private final String apiKey;
        private final String apiSecret;
        private final String sessionSecret;
        private final String psk;

        private TestKeySet(String apiKey, String apiSecret, String sessionSecret, String psk) {
            this.apiKey = apiKey;
            this.apiSecret = apiSecret;
            this.sessionSecret = sessionSecret;
            this.psk = psk;
        }
    }
}
