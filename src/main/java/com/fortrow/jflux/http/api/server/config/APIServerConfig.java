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
 * File created by Ryan Fitzgerald (ryan@fortrow.com) on 2026-05-08 19:22:08 EDT
 */
package com.fortrow.jflux.http.api.server.config;

import com.fortrow.jflux.db.connectors.details.DbConnectionDetails;
import com.fortrow.jflux.db.connectors.details.DbConnectionMysqlDetails;
import com.fortrow.jflux.db.connectors.details.DbConnectionRedisDetails;
import com.fortrow.jflux.db.connectors.details.DbConnectionSqlLiteDetails;
import com.fortrow.jflux.db.supporting.DbType;
import com.fortrow.jflux.exceptions.ExceptionHandling;
import com.fortrow.jflux.files.LogFile;
import com.fortrow.jflux.http.api.server.exceptions.APIException;
import com.fortrow.jflux.http.api.server.encryption.EncryptedApiCryptoSuite;
import com.fortrow.jflux.http.api.server.session.SessionAllocationAuthenticator;
import com.fortrow.jflux.http.api.server.types.CORSPolicy;
import com.fortrow.jflux.http.api.server.types.APIServerType;
import com.fortrow.jflux.http.api.types.DatabaseServerType;
import com.fortrow.jflux.ssl.keystore.JavaKeyStore;

import javax.net.ssl.SSLContext;
import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.security.KeyStore;
import java.time.Duration;
import java.util.Map;

public final class APIServerConfig {

    private EncryptedApiCryptoSuite encryptedCryptoSuite = EncryptedApiCryptoSuite.RSA_2048;

    /** Selects the required key-envelope suite for newly initialized or resumed sessions. */
    public APIServerConfig setEncryptedCryptoSuite(EncryptedApiCryptoSuite suite) {
        if (suite == null) throw ExceptionHandling.invalid("Encrypted API crypto suite is required.");
        suite.requireAvailable();
        this.encryptedCryptoSuite = suite;
        return this;
    }

    public EncryptedApiCryptoSuite getEncryptedCryptoSuite() {
        return encryptedCryptoSuite;
    }

    private final int apiPort;
    private boolean useTls = false;
    private HttpVersion httpVersion = HttpVersion.HTTP_1;
    private int upgradePort = -1;
    private int upgradePortHttps = -1;
    private int requestsPerMinute = 60;
    private final String bindAddress;
    private APIServerType serverType = APIServerType.REST_SYNC;
    private String apiServerKeyPassword = null;
    private String apiServerKeystorePath = null;
    private SSLContext apiServerSslContext = null;
    private boolean needClientAuth = false;
    private java.util.function.BiPredicate<String, com.fortrow.jflux.websockets.server.TlsPeerIdentity> peerIdentityAuthorizer;
    private boolean wantClientAuth = false;
    private KeyStore clientTrustStore = null;
    private JavaKeyStore keystore = null;
    private boolean publicApi;
    private String apiPath = "/api";
    private String responsePath = "/response";
    private int minutesTillInvalid = 30;
    private boolean sessionExpirationEnabled = true;
    private int resumeTokenMinutes = 30;
    private boolean activeSessionRecoveryEnabled = false;
    private boolean checkpointRecoveryEnabled = Boolean.getBoolean("jflux.checkpointRecovery.enabled");
    private java.util.function.Predicate<String> checkpointRecoveryAuthorizer = configuredRecoveryGate();
    private static java.util.function.Predicate<String> configuredRecoveryGate() {
        return switch(System.getProperty("jflux.checkpointRecovery.authority","")) {
            // The existing HTTP certificate gate must enclose the complete API path.
            case "certificate-gate" -> key -> true;
            case "t-api" -> {
                String bootstrap=System.getenv("FORTROW_SERVER_API_KEY");
                yield key -> bootstrap!=null && !bootstrap.isBlank() && key!=null && !bootstrap.equals(key);
            }
            default -> null;
        };
    }
    /** Explicit key-scope gate for a TLS-terminating private gateway; never enable shared bootstrap identities. */
    public APIServerConfig setCheckpointRecoveryAuthorizer(java.util.function.Predicate<String> authorizer) { checkpointRecoveryAuthorizer=java.util.Objects.requireNonNull(authorizer);return this; }
    public java.util.function.Predicate<String> getCheckpointRecoveryAuthorizer(){return checkpointRecoveryAuthorizer;}
    /** Opt-in read-only recovery, requiring an enrolled TLS peer authorizer. */
    public APIServerConfig setCheckpointRecoveryEnabled(boolean enabled) { checkpointRecoveryEnabled=enabled; return this; }
    public boolean isCheckpointRecoveryEnabled() { return checkpointRecoveryEnabled; }
    private Path sessionResumeStorePath;
    private Duration allocationChallengeLifetime = Duration.ofMinutes(5);
    /** Retained only for source compatibility with the pre-assertion allocator. */
    @Deprecated
    private Duration allocationTokenLifetime = Duration.ofMinutes(5);
    /** Retained only for source compatibility; signed assertions are now verified by JFlux. */
    @Deprecated
    private SessionAllocationAuthenticator sessionAllocationAuthenticator;
    private boolean useDynamicApiKey = false;
    private CORSPolicy corsPolicy;
    private String[] encryptedSessionPaths = new String[]{"session/init", "session/close", "session/resume", "session/reset", "session/allocate", "session/heartbeat"};
    private LogFile log = new LogFile();
    private DatabaseServerType applicationDbType = null;
    private DatabaseServerType sessionDbType = null;
    private DatabaseConnectionDetailsProvider applicationDbDetailsProvider = null;
    private DatabaseConnectionDetailsProvider sessionDbDetailsProvider = null;
    private int requestThreads = Math.max(4, Runtime.getRuntime().availableProcessors() * 2);
    private int asyncThreads = Math.max(2, Runtime.getRuntime().availableProcessors());
    private int asyncMinPollMs = 100;
    private int asyncMaxPollMs = 1500;
    private int asyncTimeoutMs = 15000;
    private int maxRequestBodyBytes = 4 * 1024 * 1024;
    private boolean webSocketEnabled = false;
    private int webSocketPort = -1;
    private String webSocketBindAddress = "";
    private String webSocketPath = "/ws";
    private int webSocketMaxMessageBytes = 4 * 1024 * 1024;
    private int webSocketMaxPendingRequests = 128;
    private boolean binaryWebSocketEnabled;
    private SSLContext webSocketSslContext;

    public APIServerConfig(int port) {
        this.apiPort = port;
        this.bindAddress = "";
        this.corsPolicy = new CORSPolicy(false);
    }

    public APIServerConfig(String bindAddress, int port) {
        this.bindAddress = bindAddress;
        this.apiPort = port;
        this.corsPolicy = new CORSPolicy(false);
    }

    public APIServerConfig(int port, String apiPath) {
        this.apiPort = port;
        this.bindAddress = "";
        setApiPath(apiPath);
        this.corsPolicy = new CORSPolicy(false);

    }

    public APIServerConfig(String bindAddress, int port, String apiPath) {
        this.bindAddress = bindAddress;
        this.apiPort = port;
        setApiPath(apiPath);
        this.corsPolicy = new CORSPolicy(false);
    }

    public APIServerConfig useHttp1() {
        this.httpVersion = HttpVersion.HTTP_1;
        return this;
    }

    public APIServerConfig useHttp3(JavaKeyStore key) {
        this.httpVersion = HttpVersion.HTTP_3;
        this.useTls = true;
        this.keystore = key;
        this.apiServerSslContext = key == null ? null : key.ssl();
        return this;
    }

    public JavaKeyStore getKeystore() {
        return keystore;
    }

    public APIServerConfig useTls() {
        this.useTls = true;
        return this;
    }

    /** Requests client certificates when this API is served directly over HTTPS or HTTP/3. */
    public APIServerConfig setClientAuth(boolean needClientAuth) {
        this.needClientAuth = needClientAuth;
        this.wantClientAuth = needClientAuth;
        return this;
    }

    /** Requests, but does not require, client certificates when serving HTTPS or HTTP/3. */
    public APIServerConfig setWantClientAuth(boolean wantClientAuth) {
        this.wantClientAuth = wantClientAuth;
        if (wantClientAuth) this.needClientAuth = false;
        return this;
    }

    /** Requires a verified TLS identity and authorizes its binding to each presented API key. */
    public APIServerConfig setPeerIdentityAuthorizer(java.util.function.BiPredicate<String,
            com.fortrow.jflux.websockets.server.TlsPeerIdentity> authorizer) {
        if (authorizer == null) throw com.fortrow.jflux.exceptions.ExceptionHandling.invalid("Peer identity authorizer is required.");
        this.peerIdentityAuthorizer = authorizer;
        return this;
    }

    public java.util.function.BiPredicate<String, com.fortrow.jflux.websockets.server.TlsPeerIdentity> getPeerIdentityAuthorizer() {
        return peerIdentityAuthorizer;
    }

    public boolean isNeedClientAuth() { return needClientAuth; }
    public boolean isWantClientAuth() { return wantClientAuth; }

    /** Sets the trust store used to validate HTTP/3 client certificates. */
    public APIServerConfig setClientTrustStore(KeyStore clientTrustStore) {
        this.clientTrustStore = clientTrustStore;
        return this;
    }

    public KeyStore getClientTrustStore() { return clientTrustStore; }

    public APIServerConfig useDynamicApiKey() {
        return useDynamicApiKey(true);
    }

    public APIServerConfig useDynamicApiKey(boolean val) {
        useDynamicApiKey = val;
        return this;
    }

    public APIServerConfig apiIsPublic() {
        this.publicApi = true;
        return this;
    }

    public APIServerConfig setSessionResourcePaths(String sessionInitPath, String sessionClosePath) {
        return setSessionResourcePaths(sessionInitPath, sessionClosePath, "session/resume", "session/reset", "session/allocate", "session/heartbeat");
    }

    /** Configures all encrypted session lifecycle endpoints. */
    public APIServerConfig setSessionResourcePaths(String sessionInitPath, String sessionClosePath,
                                                   String sessionResumePath, String sessionResetPath) {
        return setSessionResourcePaths(sessionInitPath, sessionClosePath, sessionResumePath, sessionResetPath, "session/allocate", "session/heartbeat");
    }

    /** Configures encrypted lifecycle endpoints, including per-key capacity allocation. */
    public APIServerConfig setSessionResourcePaths(String sessionInitPath, String sessionClosePath,
                                                   String sessionResumePath, String sessionResetPath,
                                                   String sessionAllocatePath) {
        return setSessionResourcePaths(sessionInitPath, sessionClosePath, sessionResumePath, sessionResetPath, sessionAllocatePath, "session/heartbeat");
    }

    /** Configures encrypted lifecycle endpoints, including the authenticated session heartbeat. */
    public APIServerConfig setSessionResourcePaths(String sessionInitPath, String sessionClosePath,
                                                   String sessionResumePath, String sessionResetPath,
                                                   String sessionAllocatePath, String sessionHeartbeatPath) {
        encryptedSessionPaths = new String[]{
                normalizeSessionPath(sessionInitPath, "session init path"),
                normalizeSessionPath(sessionClosePath, "session close path"),
                normalizeSessionPath(sessionResumePath, "session resume path"),
                normalizeSessionPath(sessionResetPath, "session reset path"),
                normalizeSessionPath(sessionAllocatePath, "session allocate path"),
                normalizeSessionPath(sessionHeartbeatPath, "session heartbeat path")
        };
        return this;
    }

    public void setApiServerSslContext(SSLContext apiServerSslContext) {
        this.apiServerSslContext = apiServerSslContext;
    }

    public SSLContext getApiServerSslContext() {
        return apiServerSslContext;
    }

    public APIServerConfig setResponsePath(String responsePath) {
        this.responsePath = responsePath;
        return this;
    }

    public APIServerConfig setRequestsPerMinute(int requestsPerMinute) {
        this.requestsPerMinute = requestsPerMinute;
        return this;
    }

    public APIServerConfig setDatabaseTypes(DatabaseServerType applicationDbType, DatabaseServerType sessionDbType) {
        this.applicationDbType = applicationDbType;
        this.sessionDbType = sessionDbType;
        return this;
    }

    public APIServerConfig setApplicationDbType(DatabaseServerType applicationDbType) {
        this.applicationDbType = applicationDbType;
        return this;
    }

    public APIServerConfig setSessionDbType(DatabaseServerType sessionDbType) {
        this.sessionDbType = sessionDbType;
        return this;
    }

    public APIServerConfig setApplicationDb(DatabaseServerType applicationDbType, DbConnectionDetails details) {
        this.applicationDbType = applicationDbType;
        this.applicationDbDetailsProvider = () -> details;
        return this;
    }

    public APIServerConfig setSessionDb(DatabaseServerType sessionDbType, DbConnectionDetails details) {
        this.sessionDbType = sessionDbType;
        this.sessionDbDetailsProvider = () -> details;
        return this;
    }

    public APIServerConfig setApplicationDb(DatabaseServerType applicationDbType, DatabaseConnectionDetailsProvider provider) {
        this.applicationDbType = applicationDbType;
        this.applicationDbDetailsProvider = provider;
        return this;
    }

    public APIServerConfig setSessionDb(DatabaseServerType sessionDbType, DatabaseConnectionDetailsProvider provider) {
        this.sessionDbType = sessionDbType;
        this.sessionDbDetailsProvider = provider;
        return this;
    }

    public APIServerConfig setApplicationDb(DatabaseServerType applicationDbType, Map<String, Object> details) {
        this.applicationDbType = applicationDbType;
        this.applicationDbDetailsProvider = () -> detailsFromMap(applicationDbType, details);
        return this;
    }

    public APIServerConfig setSessionDb(DatabaseServerType sessionDbType, Map<String, Object> details) {
        this.sessionDbType = sessionDbType;
        this.sessionDbDetailsProvider = () -> detailsFromMap(sessionDbType, details);
        return this;
    }

    public APIServerConfig setApplicationDbDetails(DatabaseConnectionDetailsProvider provider) {
        this.applicationDbDetailsProvider = provider;
        return this;
    }

    public APIServerConfig setSessionDbDetails(DatabaseConnectionDetailsProvider provider) {
        this.sessionDbDetailsProvider = provider;
        return this;
    }

    public APIServerConfig setApiServerKeyPassword(String keyPassword) {
        this.apiServerKeyPassword = keyPassword;
        return this;
    }

    public APIServerConfig setServerType(APIServerType serverType) {
        this.serverType = serverType;
        if(serverType == APIServerType.ENCRYPTED_SYNC || serverType == APIServerType.ENCRYPTED_ASYNC) this.publicApi = false;
        return this;
    }

    public APIServerConfig setRateLimit(int requestsPerSecond) {
        this.requestsPerMinute = requestsPerSecond;
        return this;
    }

    public APIServerConfig setMinutesTillInvalid(int minutesTillInvalid) {
        this.minutesTillInvalid = Math.max(1, minutesTillInvalid);
        return this;
    }

    /** Enables or disables expiration based on the last request time. */
    public APIServerConfig setSessionExpirationEnabled(boolean enabled) {
        this.sessionExpirationEnabled = enabled;
        return this;
    }

    public APIServerConfig useSessionExpiration(boolean enabled) {
        return setSessionExpirationEnabled(enabled);
    }

    /** Sets the inactivity period before a live session expires. */
    public APIServerConfig setSessionExpirationMinutes(int minutes) {
        return setMinutesTillInvalid(minutes);
    }

    /** Sets the lifetime of a closed session resume token. */
    public APIServerConfig setResumeTokenMinutes(int minutes) {
        this.resumeTokenMinutes = Math.max(1, minutes);
        return this;
    }

    /** Allows explicit resume-token requests on authenticated heartbeat without closing the session. */
    public APIServerConfig setActiveSessionRecoveryEnabled(boolean enabled) {
        this.activeSessionRecoveryEnabled = enabled;
        return this;
    }

    public boolean isActiveSessionRecoveryEnabled() { return activeSessionRecoveryEnabled; }

    /** Sets how long a manual allocation challenge remains usable. */
    public APIServerConfig setAllocationChallengeMinutes(int minutes) {
        this.allocationChallengeLifetime = Duration.ofMinutes(Math.max(1, minutes));
        return this;
    }

    /**
     * @deprecated Allocation tokens are no longer minted. The external
     * authentication backend signs a short-lived allocation assertion instead.
     */
    @Deprecated
    public APIServerConfig setAllocationTokenMinutes(int minutes) {
        this.allocationTokenLifetime = Duration.ofMinutes(Math.max(1, minutes));
        return this;
    }

    /**
     * @deprecated JFlux does not receive username, password, or MFA values.
     * Keep this setter only so applications can migrate without a source break;
     * it is not consulted by the encrypted allocation endpoint.
     */
    @Deprecated
    public APIServerConfig setSessionAllocationAuthenticator(SessionAllocationAuthenticator authenticator) {
        this.sessionAllocationAuthenticator = authenticator;
        return this;
    }

    /** Configures an optional durable server-side resume-token metadata file. */
    public APIServerConfig setSessionResumeStorePath(Path path) {
        this.sessionResumeStorePath = path == null ? null : path.toAbsolutePath().normalize();
        return this;
    }

    public APIServerConfig setSessionResumeStorePath(String path) {
        if(path == null || path.isBlank()) {
            this.sessionResumeStorePath = null;
            return this;
        }
        try {
            return setSessionResumeStorePath(Path.of(path));
        } catch (RuntimeException ex) {
            throw ExceptionHandling.invalid("Session resume store path is invalid.", ex);
        }
    }

    public APIServerConfig setCorsPolicy(CORSPolicy policy) {
        if(policy == null) throw new IllegalArgumentException("CORS policy cannot be null.");
        policy.validateOrThrow();
        corsPolicy = policy;
        return this;
    }

    public APIServerConfig setUpgradePort(int port) {
        this.upgradePort = port;
        return this;
    }

    public APIServerConfig setHttpUpgradePort(int port) {
        return setUpgradePort(port);
    }

    public APIServerConfig setHttpsUpgradePort(int port) {
        this.upgradePortHttps = port;
        return this;
    }

    public APIServerConfig setUpgradePortHttps(int port) {
        return setHttpsUpgradePort(port);
    }

    public APIServerConfig setApiPath(String apiPath) {
        if(apiPath == null || apiPath.isBlank()) throw ExceptionHandling.invalid("API path is required.");
        String normalized = apiPath.trim();
        if(!normalized.startsWith("/")) normalized = "/" + normalized;
        while(normalized.length() > 1 && normalized.endsWith("/")) normalized = normalized.substring(0, normalized.length() - 1);
        this.apiPath = normalized;
        return this;

    }

    public APIServerConfig setLogFile(String filepath) {
        this.log = new LogFile(filepath);
        return this;
    }

    public APIServerConfig setApiServerKeystorePath(String keystorePath) {
        this.apiServerKeystorePath = keystorePath;
        return this;
    }

    /**
     * @deprecated Global max-session limits are ignored since 1.1.1. Use
     * {@code session/allocate} for each API key instead.
     */
    @Deprecated(since = "1.1.1")
    public APIServerConfig setMaxSessions(int val) {
        return this;
    }

    public APIServerConfig setRequestThreads(int requestThreads) {
        this.requestThreads = Math.max(1, requestThreads);
        return this;
    }

    public APIServerConfig setAsyncThreads(int asyncThreads) {
        this.asyncThreads = Math.max(1, asyncThreads);
        return this;
    }

    public APIServerConfig setAsyncPolling(int minPollMs, int maxPollMs, int timeoutMs) {
        this.asyncMinPollMs = Math.max(1, minPollMs);
        this.asyncMaxPollMs = Math.max(this.asyncMinPollMs, maxPollMs);
        this.asyncTimeoutMs = Math.max(this.asyncMaxPollMs, timeoutMs);
        return this;
    }

    /** Sets the maximum request body retained in memory by the HTTP handlers. */
    public APIServerConfig setMaxRequestBodyBytes(int maxRequestBodyBytes) {
        this.maxRequestBodyBytes = Math.max(1, maxRequestBodyBytes);
        return this;
    }

    /** Enables the encrypted API WebSocket transport on a separate listener. */
    public APIServerConfig useWebSocket(int port) {
        return setWebSocketPort(port).setWebSocketEnabled(true);
    }

    /** Enables or disables the encrypted API WebSocket transport. */
    public APIServerConfig setWebSocketEnabled(boolean enabled) {
        this.webSocketEnabled = enabled;
        return this;
    }

    /** Sets the WebSocket listener port. Use zero to request an ephemeral port. */
    public APIServerConfig setWebSocketPort(int port) {
        this.webSocketPort = port;
        return this;
    }

    /** Sets an optional WebSocket bind host; the API bind host is used by default. */
    public APIServerConfig setWebSocketBindAddress(String bindAddress) {
        this.webSocketBindAddress = bindAddress == null ? "" : bindAddress.trim();
        return this;
    }

    /** Sets the WebSocket handshake path, independent of the HTTP API path. */
    public APIServerConfig setWebSocketPath(String path) {
        if (path == null || path.isBlank()) throw ExceptionHandling.invalid("WebSocket path is required.");
        String normalized = path.trim();
        if (!normalized.startsWith("/")) normalized = "/" + normalized;
        while (normalized.length() > 1 && normalized.endsWith("/")) normalized = normalized.substring(0, normalized.length() - 1);
        if (normalized.contains("..")) throw ExceptionHandling.invalid("WebSocket path is invalid.");
        this.webSocketPath = normalized;
        return this;
    }

    /** Sets the maximum UTF-8 WebSocket frame size accepted by the API transport. */
    public APIServerConfig setWebSocketMaxMessageBytes(int maxMessageBytes) {
        this.webSocketMaxMessageBytes = Math.max(1, maxMessageBytes);
        return this;
    }

    /** Configures WSS. If omitted, the API server TLS context is reused when available. */
    public APIServerConfig setWebSocketSslContext(SSLContext sslContext) {
        this.webSocketSslContext = sslContext;
        return this;
    }

    public String[] getEncryptedSessionPaths() {
        return java.util.Arrays.copyOf(encryptedSessionPaths, encryptedSessionPaths.length);
    }

    public InetSocketAddress getBindAddress() {
        return (bindAddress.isEmpty()) ?
                new InetSocketAddress(this.apiPort) :
                new InetSocketAddress(this.bindAddress, this.apiPort);
    }

    public InetSocketAddress getUpgradeBindAddress() {
        if(this.upgradePort <= 0) return null;
        return (bindAddress.isEmpty()) ?
                new InetSocketAddress(this.upgradePort) :
                new InetSocketAddress(this.bindAddress, this.upgradePort);
    }

    public InetSocketAddress getHttpsUpgradeBindAddress() {
        if(this.upgradePortHttps <= 0) return null;
        return (bindAddress.isEmpty()) ?
                new InetSocketAddress(this.upgradePortHttps) :
                new InetSocketAddress(this.bindAddress, this.upgradePortHttps);
    }

    public String getBindHost() {
        return bindAddress;
    }

    public String getApiServerKeystorePath() {
        return apiServerKeystorePath;
    }

    public String getApiServerKeyPassword() {
        return apiServerKeyPassword;
    }

    public LogFile getLog() {
        return log;
    }

    public boolean isAsync() {
        return serverType == APIServerType.REST_ASYNC || serverType == APIServerType.ENCRYPTED_ASYNC;
    }

    public boolean doUseDynamicApiKey() {
        return useDynamicApiKey;
    }

    public boolean isPublicApi() {
        return publicApi;
    }

    public int getMinutesTillInvalid() {
        return minutesTillInvalid;
    }

    public boolean isSessionExpirationEnabled() {
        return sessionExpirationEnabled;
    }

    public int getResumeTokenMinutes() {
        return resumeTokenMinutes;
    }

    public Duration getAllocationChallengeLifetime() {
        return allocationChallengeLifetime;
    }

    /** @deprecated Allocation tokens are no longer used. */
    @Deprecated
    public Duration getAllocationTokenLifetime() {
        return allocationTokenLifetime;
    }

    /** @deprecated The encrypted API now verifies signed assertions directly. */
    @Deprecated
    public SessionAllocationAuthenticator getSessionAllocationAuthenticator() {
        return sessionAllocationAuthenticator;
    }

    public Path getSessionResumeStorePath() {
        return sessionResumeStorePath;
    }

    public CORSPolicy getCorsPolicy() {
        return this.corsPolicy;
    }

    public int getUpgradePort() {
        return upgradePort;
    }

    public int getUpgradePortHttps() {
        return upgradePortHttps;
    }

    public int getHttpsUpgradePort() {
        return upgradePortHttps;
    }

    public int getApiPort() {
        return apiPort;
    }

    public int getRequestsPerMinute() {
        return requestsPerMinute;
    }

    public int getRateLimit() {
        return requestsPerMinute;
    }

    /** @deprecated Global max-session limits are no longer enforced. */
    @Deprecated(since = "1.1.1")
    public int getMaxSessions() {
        return 1;
    }

    public int getRequestThreads() {
        return requestThreads;
    }

    public int getAsyncThreads() {
        return asyncThreads;
    }

    public int getAsyncMinPollMs() {
        return asyncMinPollMs;
    }

    public int getAsyncMaxPollMs() {
        return asyncMaxPollMs;
    }

    public int getAsyncTimeoutMs() {
        return asyncTimeoutMs;
    }

    public int getMaxRequestBodyBytes() {
        return maxRequestBodyBytes;
    }

    public boolean isWebSocketEnabled() { return webSocketEnabled; }

    public int getWebSocketPort() { return webSocketPort; }

    public InetSocketAddress getWebSocketBindAddress() {
        String host = webSocketBindAddress.isBlank() ? bindAddress : webSocketBindAddress;
        return host == null || host.isBlank()
                ? new InetSocketAddress(webSocketPort)
                : new InetSocketAddress(host, webSocketPort);
    }

    public String getWebSocketBindHost() { return webSocketBindAddress.isBlank() ? bindAddress : webSocketBindAddress; }

    public String getWebSocketPath() { return webSocketPath; }

    public int getWebSocketMaxMessageBytes() { return webSocketMaxMessageBytes; }

    /** Bounds queued/running API WebSocket exchanges before virtual-thread submission. */
    public APIServerConfig setWebSocketMaxPendingRequests(int maximum) {
        if (maximum < 1 || maximum > 4096)
            throw com.fortrow.jflux.exceptions.ExceptionHandling.invalid("WebSocket pending request bound must be between 1 and 4096.");
        this.webSocketMaxPendingRequests = maximum;
        return this;
    }

    public int getWebSocketMaxPendingRequests() { return webSocketMaxPendingRequests; }

    /** Opts into encrypted byte resources; requires mandatory mTLS and peer/key authorization. */
    public APIServerConfig setBinaryWebSocketEnabled(boolean enabled) { binaryWebSocketEnabled = enabled; return this; }
    public boolean isBinaryWebSocketEnabled() { return binaryWebSocketEnabled; }



    public SSLContext getWebSocketSslContext() { return webSocketSslContext; }

    private static String normalizeSessionPath(String value, String label) {
        if(value == null || value.isBlank()) throw ExceptionHandling.invalid(label + " is required.");
        String normalized = value.trim();
        while(normalized.startsWith("/")) normalized = normalized.substring(1);
        while(normalized.endsWith("/") && !normalized.isEmpty()) normalized = normalized.substring(0, normalized.length() - 1);
        if(normalized.isBlank() || normalized.contains("..")) throw ExceptionHandling.invalid(label + " is invalid.");
        return normalized;
    }

    public String getResponsePath() {
        return responsePath;
    }

    public String getApiPath() {
        return this.apiPath;
    }

    public APIServerType getServerType() {
        return serverType;
    }

    public boolean isUseHttp1() {
        return httpVersion == HttpVersion.HTTP_1;
    }

    public boolean isUseHttp3() {
        return httpVersion == HttpVersion.HTTP_3;
    }

    public boolean isUseTls() {
        return useTls;
    }

    public DatabaseServerType getApplicationDbType() {
        return applicationDbType;
    }

    public DatabaseServerType getSessionDbType() {
        return sessionDbType;
    }

    public DbConnectionDetails getApplicationDbConnectionDetails() throws APIException {
        return connectionDetails(applicationDbType, applicationDbDetailsProvider, false);
    }

    public DbConnectionDetails getSessionDbConnectionDetails() throws APIException {
        return connectionDetails(sessionDbType, sessionDbDetailsProvider, true);
    }

    public void validateOrThrow() throws APIException {
        if (encryptedCryptoSuite.isPostQuantum() && (!useDynamicApiKey
                || (serverType != APIServerType.ENCRYPTED_SYNC && serverType != APIServerType.ENCRYPTED_ASYNC)))
            throw new APIException("Post-quantum API crypto requires an encrypted server with dynamic API keys enabled.");
        if(corsPolicy != null) corsPolicy.validateOrThrow();
        if (binaryWebSocketEnabled && (!webSocketEnabled || !needClientAuth || peerIdentityAuthorizer == null
                || (webSocketSslContext == null && apiServerSslContext == null && !useTls)
                || serverType != APIServerType.ENCRYPTED_SYNC))
            throw new APIException("Binary WebSocket resources require encrypted sync, WSS, mandatory mTLS, and peer identity authorization.");
        if(requestThreads <= 0) throw new APIException("API request thread count must be greater than zero.");
        if(asyncThreads <= 0) throw new APIException("API async thread count must be greater than zero.");
        if(asyncMinPollMs <= 0) throw new APIException("API async minimum poll interval must be greater than zero.");
        if(asyncMaxPollMs < asyncMinPollMs) throw new APIException("API async maximum poll interval cannot be less than the minimum poll interval.");
        if(asyncTimeoutMs < asyncMaxPollMs) throw new APIException("API async timeout cannot be less than the maximum poll interval.");
        if(maxRequestBodyBytes <= 0) throw new APIException("API maximum request body size must be greater than zero.");
        if(webSocketPort < -1 || webSocketPort > 65535) throw new APIException("API WebSocket port must be between 0 and 65535.");
        if(webSocketEnabled && webSocketPort < 0) throw new APIException("Enabled API WebSocket transport requires a WebSocket port.");
        if(webSocketMaxMessageBytes <= 0) throw new APIException("API WebSocket maximum message size must be greater than zero.");
        if(minutesTillInvalid <= 0) throw new APIException("API session expiration minutes must be greater than zero.");
        if(resumeTokenMinutes <= 0) throw new APIException("API resume-token lifetime must be greater than zero.");
        if(upgradePortHttps > 0 && !isUseHttp3()) throw new APIException("HTTPS upgrade port is only supported for HTTP/3 servers.");
        if(upgradePortHttps > 0 && apiServerSslContext == null) throw new APIException("HTTPS upgrade port requires an SSL context.");
        validateDatabaseConfig(applicationDbType, applicationDbDetailsProvider, false, "application");
        validateDatabaseConfig(sessionDbType, sessionDbDetailsProvider, true, "session");
    }

    public boolean validate() {
        try {
            validateOrThrow();
            return true;
        } catch (APIException e) {
            return false;
        }
    }

    private void validateDatabaseConfig(DatabaseServerType type, DatabaseConnectionDetailsProvider provider, boolean required, String label) throws APIException {
        if(type == null) {
            if(required) throw new APIException("API " + label + " database type is required.");
            if(provider != null) throw new APIException("API " + label + " database details cannot be set without a database type.");
            return;
        }

        DbConnectionDetails details = connectionDetails(type, provider, required);
        if(!detailsMatch(type, details)) {
            throw new APIException("API " + label + " database details do not match database type " + type + ".");
        }
    }

    private DbConnectionDetails connectionDetails(DatabaseServerType type, DatabaseConnectionDetailsProvider provider, boolean required) throws APIException {
        if(type == null) {
            if(required) throw new APIException("API database type is required.");
            return null;
        }
        if(type == DatabaseServerType.PROCESS_MEMORY) return null;
        if(provider == null) {
            if(required) throw new APIException("API database connection details provider is required for " + type + ".");
            throw new APIException("API database connection details are required for " + type + ".");
        }
        DbConnectionDetails details = provider.getConnectionDetails();
        if(details == null) throw new APIException("API database connection details are required for " + type + ".");
        return details;
    }

    private boolean detailsMatch(DatabaseServerType type, DbConnectionDetails details) {
        if(type == DatabaseServerType.PROCESS_MEMORY) return details == null;
        if(details == null) return false;
        DbType detailsType = details.getType();
        switch(type) {
            case MYSQL:
                return detailsType == DbType.MYSQL;
            case SQLITE:
                return detailsType == DbType.SQLITE;
            case REDIS:
                return detailsType == DbType.REDIS;
            default:
                return false;
        }
    }

    private DbConnectionDetails detailsFromMap(DatabaseServerType type, Map<String, Object> values) throws APIException {
        if(type == null) throw new APIException("API database type is required.");
        if(type == DatabaseServerType.PROCESS_MEMORY) return null;
        if(values == null) throw new APIException("API database connection details are required for " + type + ".");
        switch(type) {
            case MYSQL:
                return new DbConnectionMysqlDetails(
                        requiredString(values, "name"),
                        requiredString(values, "host"),
                        requiredInt(values, "port"),
                        requiredString(values, "username"),
                        requiredString(values, "password")
                );
            case SQLITE:
                return new DbConnectionSqlLiteDetails(requiredString(values, "filepath"));
            case REDIS:
                return new DbConnectionRedisDetails(
                        requiredString(values, "dbNameLabel"),
                        requiredString(values, "host"),
                        requiredInt(values, "port"),
                        requiredInt(values, "dbIndex")
                );
            default:
                throw new APIException("Unsupported API database type " + type + ".");
        }
    }

    private String requiredString(Map<String, Object> values, String key) throws APIException {
        Object value = values.get(key);
        if(value == null || value.toString().isBlank()) {
            throw new APIException("API database connection detail '" + key + "' is required.");
        }
        return value.toString();
    }

    private int requiredInt(Map<String, Object> values, String key) throws APIException {
        Object value = values.get(key);
        if(value instanceof Number) return ((Number) value).intValue();
        if(value instanceof String) {
            try {
                return Integer.parseInt((String) value);
            } catch (NumberFormatException e) {
                throw new APIException("API database connection detail '" + key + "' must be an integer.");
            }
        }
        throw new APIException("API database connection detail '" + key + "' must be an integer.");
    }

}
