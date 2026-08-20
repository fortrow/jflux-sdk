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
 * File created by Ryan Fitzgerald (ryan@fortrow.com) on 2026-05-11 19:48:14 EDT
 * 
 * -------------------------------------------------------------------------

 */
package com.fortrow.jflux.http.api.client;

import tech.kwik.core.QuicClientConnection;
import tech.kwik.flupke.Http3SingleConnectionClient;

import java.lang.reflect.Field;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.concurrent.ExecutorService;

import static tech.kwik.flupke.server.Http3ApplicationProtocolFactory.HTTP3_PROTOCOL_ID;

final class ClosableHttp3Transport implements AutoCloseable {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration MAX_IDLE_TIMEOUT = Duration.ofSeconds(5);

    private final QuicClientConnection connection;
    private final HttpClient client;

    private ClosableHttp3Transport(QuicClientConnection connection, HttpClient client) {
        this.connection = connection;
        this.client = client;
    }

    static ClosableHttp3Transport create(String endpointUri, boolean allowSelfSigned, MtlsConfig mtlsConfig) {
        try {
            QuicClientConnection.Builder builder = QuicClientConnection.newBuilder()
                    .uri(URI.create(endpointUri))
                    .applicationProtocol(HTTP3_PROTOCOL_ID)
                    .connectTimeout(CONNECT_TIMEOUT)
                    .maxIdleTimeout(MAX_IDLE_TIMEOUT);

            if (allowSelfSigned) {
                builder.noServerCertificateCheck();
            } else if (mtlsConfig != null && mtlsConfig.hasTrustStore()) {
                builder.customTrustStore(TlsMaterial.loadTrustStore(mtlsConfig));
            }
            if (mtlsConfig != null) {
                builder.clientKeyManager(TlsMaterial.loadIdentityKeyManager(mtlsConfig));
            }

            QuicClientConnection connection = builder.build();
            HttpClient client = new Http3SingleConnectionClient(connection, CONNECT_TIMEOUT, null);
            return new ClosableHttp3Transport(connection, client);
        } catch (Exception e) {
            throw new RuntimeException("Failed to create HTTP/3 transport: " + e.getMessage(), e);
        }
    }

    static ClosableHttp3Transport create(String endpointUri, boolean allowSelfSigned) {
        return create(endpointUri, allowSelfSigned, null);
    }

    HttpClient client() {
        return client;
    }

    @Override
    public void close() {
        try {
            connection.close();
        } catch (Exception ignored) {
        }
        shutdownExecutor(client, "executorService");
        shutdownNestedExecutor(client, "http3ConnectionFactory", "executorService");
    }

    private static void shutdownNestedExecutor(Object target, String holderField, String executorField) {
        Object holder = fieldValue(target, holderField);
        if(holder != null) shutdownExecutor(holder, executorField);
    }

    private static void shutdownExecutor(Object target, String fieldName) {
        Object executor = fieldValue(target, fieldName);
        if(executor instanceof ExecutorService service) service.shutdownNow();
    }

    private static Object fieldValue(Object target, String fieldName) {
        if(target == null) return null;
        Class<?> type = target.getClass();
        while(type != null) {
            try {
                Field field = type.getDeclaredField(fieldName);
                field.setAccessible(true);
                return field.get(target);
            } catch (NoSuchFieldException ignored) {
                type = type.getSuperclass();
            } catch (Exception ignored) {
                return null;
            }
        }
        return null;
    }
}
