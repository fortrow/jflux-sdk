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
 * File created by Ryan Fitzgerald (ryan@fortrow.com) on 2026-05-08 19:22:08 EDT
 * 
 * -------------------------------------------------------------------------

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
import com.fortrow.jflux.http.api.server.types.CORSPolicy;
import com.fortrow.jflux.http.api.server.types.APIServerType;
import com.fortrow.jflux.http.api.types.DatabaseServerType;
import com.fortrow.jflux.ssl.keystore.JavaKeyStore;

import javax.net.ssl.SSLContext;
import java.net.InetSocketAddress;
import java.security.KeyStore;
import java.util.Map;

public final class APIServerConfig {

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
    private boolean wantClientAuth = false;
    private KeyStore clientTrustStore = null;
    private JavaKeyStore keystore = null;
    private boolean publicApi;
    private String apiPath = "/api";
    private String responsePath = "/response";
    private int maxSessions = 100;
    private int minutesTillInvalid = 30;
    private boolean useDynamicApiKey = false;
    private CORSPolicy corsPolicy;
    private String[] encryptedSessionPaths = new String[]{"session/init", "session/close"};
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
        encryptedSessionPaths[0] = sessionInitPath;
        encryptedSessionPaths[1] = sessionClosePath;
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
        this.minutesTillInvalid = minutesTillInvalid;
        return this;
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

    public APIServerConfig setMaxSessions(int val) {
        this.maxSessions = Math.max(1, val);
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

    public String[] getEncryptedSessionPaths() {
        return encryptedSessionPaths;
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

    public int getMaxSessions() {
        return maxSessions;
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
        if(corsPolicy != null) corsPolicy.validateOrThrow();
        if(requestThreads <= 0) throw new APIException("API request thread count must be greater than zero.");
        if(asyncThreads <= 0) throw new APIException("API async thread count must be greater than zero.");
        if(asyncMinPollMs <= 0) throw new APIException("API async minimum poll interval must be greater than zero.");
        if(asyncMaxPollMs < asyncMinPollMs) throw new APIException("API async maximum poll interval cannot be less than the minimum poll interval.");
        if(asyncTimeoutMs < asyncMaxPollMs) throw new APIException("API async timeout cannot be less than the maximum poll interval.");
        if(maxRequestBodyBytes <= 0) throw new APIException("API maximum request body size must be greater than zero.");
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
