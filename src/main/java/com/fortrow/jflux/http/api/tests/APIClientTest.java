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
 * File created by Ryan Fitzgerald (ryan@fortrow.com) on 2026-05-08 22:52:37 EDT
 * 
 * -------------------------------------------------------------------------

 */
package com.fortrow.jflux.http.api.tests;

import com.fortrow.jflux.http.api.client.*;
import com.fortrow.jflux.http.api.server.config.HttpVersion;
import com.fortrow.jflux.http.api.server.exceptions.APIException;
import org.json.simple.JSONObject;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.EnumSet;

public class APIClientTest {

    private static final String API_KEY = "apikey_ZyiUHezSAgCOTUlbIiFbS8bjCVNx6fpgoZNl5Njix6exjUPvw9dxMd9HBrHuyhZRJFbwDxFC";
    private static final String API_SECRET = "secret_HKUsdVA0lVFtVcLa4obTI2CWxckl4spMgH93aUuZDIZ5ZQvrUjcrc3U2CXz9KHBF22Gx6YYz";
    private static final String API_SESSION_SECRET = "c00a9411a30f53f23f58ca076f54cdc39f6afaa2f7a86218d4e5bfd98c95e822";
    private static final String API_PSK = "480cde81e2d825ddcf2217a72f36078e2ffdfb2388fc10b5fe4b4d353be7a321";

    public static void main(String[] args) throws Throwable {
        boolean async = hasArg(args, "--async");
        boolean rest = hasArg(args, "--rest");
        boolean http1 = hasArg(args, "--http1");
        boolean timing = hasArg(args, "--timing");
        boolean closeSession = hasArg(args, "--close-session");
        boolean heartbeat = hasArg(args, "--heartbeat");
        boolean closeResume = hasArg(args, "--close-resume");

        if(rest) testRest(http1, async, timing);
        else testEncrypted(http1, async, timing, closeSession || closeResume, heartbeat, closeResume);
    }

    private static void testRest(boolean http1, boolean async, boolean timing) throws APIException {
        HttpVersion version = http1 ? HttpVersion.HTTP_1 : HttpVersion.HTTP_3;
        String endpoint = http1 ? "http://127.0.0.1:8181/api" : "https://127.0.0.1:8181/api";
        long start = System.nanoTime();
        RESTClient clientBuilder = new RESTClient(API_KEY, endpoint, version, true);
        if(!http1) clientBuilder.setHttp3HttpsUpgradePort(8182);
        try (RESTClient client = clientBuilder.build()) {
            printTiming(timing, "rest.build", start);
            JSONObject testData = testData();
            long requestStart = System.nanoTime();
            RESTClient.Response response = async
                    ? client.requestAndWait("query/new", RequestType.POST, testData)
                    : client.post("query/new", testData);
            printTiming(timing, "rest.request", requestStart);
            System.out.println("status=" + response.getStatus());
            System.out.println("body=" + response.getBody());
        } finally {
            printTiming(timing, "rest.total", start);
        }
    }

    private static void testEncrypted(boolean http1, boolean async, boolean timing,
                                      boolean closeSession, boolean heartbeat, boolean closeResume) throws APIException, IOException {
        HttpVersion version = http1 ? HttpVersion.HTTP_1 : HttpVersion.HTTP_3;
        Path stateDir = Path.of(System.getProperty("user.home"), ".jflux-api-test");
        File pskFile = writeFile(stateDir.resolve("test.psk"), API_PSK);
        File sessionSecretFile = seedFile(stateDir.resolve("session-secret.txt"), API_SESSION_SECRET);
        SecretStore sessionSecretStore = new FileSecretStore(sessionSecretFile.toPath(), API_SESSION_SECRET);
        ReadOnlySecretStore pskStore = new FileSecretStore(pskFile.toPath(), API_PSK);

        long start = System.nanoTime();
        EncryptedClient clientBuilder = new EncryptedClient(
                API_KEY,
                API_SECRET,
                sessionSecretStore,
                pskStore,
                "https://127.0.0.1:8181/api",
                version,
                true
        );
        if(!http1) clientBuilder.setHttp3HttpsUpgradePort(8182);
        try (EncryptedClient client = clientBuilder.build()) {
            printTiming(timing, "encrypted.build", start);

            long initStart = System.nanoTime();
            client.beginSessionOrThrow("/session/init", "/session/close");
            printTiming(timing, "encrypted.session_init", initStart);

            long requestStart = System.nanoTime();
            EncryptedResponse response = async
                    ? client.requestAndWait("query/new", testData())
                    : client.request("query/new", testData());
            printTiming(timing, "encrypted.request", requestStart);
            System.out.println("status=" + response.getStatus());
            System.out.println("body=" + response.getBody());

            if(heartbeat) {
                EncryptedResponse heartbeatResponse = client.heartbeat();
                System.out.println("heartbeat_status=" + heartbeatResponse.getStatus());
                System.out.println("heartbeat_body=" + heartbeatResponse.getBody());
            }

            if(closeSession) {
                EncryptedResponse closeResponse = client.closeSessionOrThrow(closeResume);
                System.out.println("close_status=" + closeResponse.getStatus());
                System.out.println("close_body=" + closeResponse.getBody());
            }
        } finally {
            printTiming(timing, "encrypted.total", start);
        }
    }

    private static JSONObject testData() {
        JSONObject testData = new JSONObject();
        testData.put("sql", "FIX ME");
        testData.put("parameters", "WITH CODE");
        return testData;
    }

    private static File seedFile(Path path, String value) throws IOException {
        Path parent = path.getParent();
        if(parent != null) Files.createDirectories(parent);
        if(!Files.exists(path) || Files.readString(path, StandardCharsets.UTF_8).trim().isBlank()) {
            writeFile(path, value);
        }
        return path.toFile();
    }

    private static File writeFile(Path path, String value) throws IOException {
        Path parent = path.getParent();
        if(parent != null) Files.createDirectories(parent);
        Files.writeString(path, value + System.lineSeparator(), StandardCharsets.UTF_8);
        secureOwnerOnly(path);
        return path.toFile();
    }

    private static void secureOwnerOnly(Path file) {
        try {
            Files.setPosixFilePermissions(file, EnumSet.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE));
        } catch (UnsupportedOperationException | IOException ignored) {
        }
    }

    private static void printTiming(boolean enabled, String label, long startedAtNanos) {
        if(!enabled) return;
        long elapsedMs = (System.nanoTime() - startedAtNanos) / 1_000_000L;
        System.out.println(label + "_ms=" + elapsedMs);
    }

    private static boolean hasArg(String[] args, String expected) {
        for(String arg : args) {
            if(expected.equalsIgnoreCase(arg)) return true;
        }
        return false;
    }
}
