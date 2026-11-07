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
 * File created by Ryan Fitzgerald (ryan@fortrow.com) on 2026-05-12 15:56:33 EDT
 */
package com.fortrow.jflux.http.api.client;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class Http3UpgradeResolver {
    private static final Pattern H3_PORT = Pattern.compile("h3(?:-[^=]*)?=\\\":([0-9]+)\\\"");
    private static final int UPGRADE_TIMEOUT_MS = 5_000;

    private Http3UpgradeResolver() { }

    static String upgradeUri(String endpointUri, int port, boolean https) {
        if(port <= 0) throw new IllegalArgumentException("HTTP/3 upgrade port must be greater than zero.");
        URI endpoint = URI.create(endpointUri);
        String scheme = https ? "https" : "http";
        return buildUri(scheme, endpoint.getHost(), port, endpoint.getRawPath(), endpoint.getRawQuery(), endpoint.getRawFragment()).toString();
    }

    static String resolveEndpoint(String endpointUri, String upgradeUri, boolean allowSelfSigned) throws IOException {
        return resolveEndpoint(endpointUri, upgradeUri, allowSelfSigned, null);
    }

    static String resolveEndpoint(String endpointUri, String upgradeUri, boolean allowSelfSigned,
                                  MtlsConfig mtlsConfig) throws IOException {
        if(upgradeUri == null || upgradeUri.isBlank()) return stripTrailingSlash(endpointUri);

        URI endpoint = URI.create(stripTrailingSlash(endpointUri));
        URI upgrade = URI.create(stripTrailingSlash(upgradeUri));
        UpgradeResponse response = sendUpgradeRequest(upgrade, allowSelfSigned, mtlsConfig);

        String altSvc = response.optionalAltSvc()
                .orElseThrow(() -> new IOException("HTTP/3 upgrade response did not include Alt-Svc."));
        int h3Port = parseH3Port(altSvc).orElse(defaultPort(endpoint, 443));
        URI redirect = response.redirect(upgrade).orElse(endpoint);

        String host = redirect.getHost() != null ? redirect.getHost() : endpoint.getHost();
        String path = hasText(redirect.getRawPath()) ? redirect.getRawPath() : endpoint.getRawPath();
        String query = hasText(redirect.getRawQuery()) ? redirect.getRawQuery() : endpoint.getRawQuery();
        String fragment = hasText(redirect.getRawFragment()) ? redirect.getRawFragment() : endpoint.getRawFragment();
        return stripTrailingSlash(buildUri("https", host, h3Port, path, query, fragment).toString());
    }

    private static UpgradeResponse sendUpgradeRequest(URI upgrade, boolean allowSelfSigned,
                                                      MtlsConfig mtlsConfig) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) upgrade.toURL().openConnection();
        connection.setInstanceFollowRedirects(false);
        connection.setConnectTimeout(UPGRADE_TIMEOUT_MS);
        connection.setReadTimeout(UPGRADE_TIMEOUT_MS);
        connection.setRequestMethod("GET");

        if(connection instanceof HttpsURLConnection https && (allowSelfSigned || mtlsConfig != null)) {
            try {
                SSLContext context = mtlsConfig == null
                        ? trustAllSslContext()
                        : TlsMaterial.buildSslContext(mtlsConfig, allowSelfSigned);
                https.setSSLSocketFactory(context.getSocketFactory());
                if (allowSelfSigned) {
                    HostnameVerifier verifier = (hostname, session) -> true;
                    https.setHostnameVerifier(verifier);
                }
            } catch (Exception e) {
                throw new IOException("Failed to create HTTPS upgrade client.", e);
            }
        }

        try {
            int status = connection.getResponseCode();
            return new UpgradeResponse(status, connection.getHeaderField("Alt-Svc"), connection.getHeaderField("Location"));
        } finally {
            connection.disconnect();
        }
    }

    private static SSLContext trustAllSslContext() throws Exception {
        TrustManager[] trustManagers = new TrustManager[]{
                new X509TrustManager() {
                    @Override
                    public void checkClientTrusted(X509Certificate[] chain, String authType) { }

                    @Override
                    public void checkServerTrusted(X509Certificate[] chain, String authType) { }

                    @Override
                    public X509Certificate[] getAcceptedIssuers() {
                        return new X509Certificate[0];
                    }
                }
        };
        SSLContext context = SSLContext.getInstance("TLS");
        context.init(null, trustManagers, new SecureRandom());
        return context;
    }

    private static Optional<Integer> parseH3Port(String altSvc) {
        Matcher matcher = H3_PORT.matcher(altSvc == null ? "" : altSvc);
        if(!matcher.find()) return Optional.empty();
        return Optional.of(Integer.parseInt(matcher.group(1)));
    }

    private static int defaultPort(URI uri, int fallback) {
        if(uri.getPort() > 0) return uri.getPort();
        if("https".equalsIgnoreCase(uri.getScheme())) return 443;
        if("http".equalsIgnoreCase(uri.getScheme())) return 80;
        return fallback;
    }

    private static URI buildUri(String scheme, String host, int port, String path, String query, String fragment) {
        try {
            return new URI(scheme, null, host, port, normalizePath(path), query, fragment);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid HTTP/3 endpoint URI.", e);
        }
    }

    private static String normalizePath(String path) {
        if(path == null || path.isBlank()) return "/";
        return path.startsWith("/") ? path : "/" + path;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static String stripTrailingSlash(String value) {
        if(value == null) return null;
        while(value.length() > 1 && value.endsWith("/")) value = value.substring(0, value.length() - 1);
        return value;
    }

    private record UpgradeResponse(int status, String altSvc, String location) {
        private Optional<String> optionalAltSvc() {
            return Optional.ofNullable(altSvc).filter(Http3UpgradeResolver::hasText);
        }

        private Optional<URI> redirect(URI requestUri) {
            if(status < 300 || status >= 400 || !hasText(location)) return Optional.empty();
            return Optional.of(requestUri.resolve(location));
        }
    }
}
