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
package com.fortrow.jflux.db.connectors.details;

import com.fortrow.jflux.db.supporting.DbType;

public class DbConnectionMysqlDetails extends DbConnectionDetails {

    public static final String DEFAULT_SSL_MODE = "PREFERRED";
    public static final int DEFAULT_CONNECT_TIMEOUT_MS = 5_000;
    public static final int DEFAULT_SOCKET_TIMEOUT_MS = 15_000;

    private String name;
    private String host;
    private int port;
    private String username;
    private String password;
    private String sslMode;
    private String trustStoreUrl;
    private String trustStoreType;
    private String trustStorePassword;

    public DbConnectionMysqlDetails(String name, String host, int port, String username, String password) {
        this(name, host, port, username, password, DEFAULT_SSL_MODE, null, null, null);
    }

    public DbConnectionMysqlDetails(String name, String host, int port, String username, String password,
                                    String sslMode) {
        this(name, host, port, username, password, sslMode, null, null, null);
    }

    public DbConnectionMysqlDetails(String name, String host, int port, String username, String password,
                                    String sslMode, String trustStoreUrl, String trustStoreType,
                                    String trustStorePassword) {
        this(name, host, port, username, password, sslMode, trustStoreUrl, trustStoreType,
                trustStorePassword, DEFAULT_CONNECT_TIMEOUT_MS, DEFAULT_SOCKET_TIMEOUT_MS);
    }

    /**
     * Creates MySQL connection details with bounded network operations. The
     * explicit overload lets long-running applications tune the values while
     * retaining safe defaults for existing callers.
     */
    public DbConnectionMysqlDetails(String name, String host, int port, String username, String password,
                                    String sslMode, String trustStoreUrl, String trustStoreType,
                                    String trustStorePassword, int connectTimeoutMs, int socketTimeoutMs) {
        super(DbType.MYSQL);
        this.name = name;
        this.host = host;
        this.port = port;
        this.username = username;
        this.password = password;
        this.sslMode = normalizeSslMode(sslMode);
        this.trustStoreUrl = blankToNull(trustStoreUrl);
        this.trustStoreType = blankToNull(trustStoreType);
        this.trustStorePassword = blankToNull(trustStorePassword);
        if (connectTimeoutMs < 1 || socketTimeoutMs < 1) {
            throw new IllegalArgumentException("MySQL timeouts must be positive.");
        }
        this.connectTimeoutMs = connectTimeoutMs;
        this.socketTimeoutMs = socketTimeoutMs;
        build();
    }

    private int connectTimeoutMs;
    private int socketTimeoutMs;

    private static String normalizeSslMode(String value) {
        String normalized = value == null || value.isBlank() ? DEFAULT_SSL_MODE : value.trim().toUpperCase();
        if (!normalized.equals("DISABLED") && !normalized.equals("PREFERRED")
                && !normalized.equals("REQUIRED") && !normalized.equals("VERIFY_CA")
                && !normalized.equals("VERIFY_IDENTITY")) {
            throw new IllegalArgumentException("Unsupported MySQL sslMode: " + value);
        }
        return normalized;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    @Override
    protected void build() {
        append("name", String.class, name);
        append("host", String.class, host);
        append("port", Integer.class, port);
        append("username", String.class, username);
        append("password", String.class, password);
        append("sslMode", String.class, sslMode);
        append("trustStoreUrl", String.class, trustStoreUrl);
        append("trustStoreType", String.class, trustStoreType);
        append("trustStorePassword", String.class, trustStorePassword);
        append("connectTimeoutMs", Integer.class, connectTimeoutMs);
        append("socketTimeoutMs", Integer.class, socketTimeoutMs);
    }

}
