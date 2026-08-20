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
 * File created by Ryan Fitzgerald (ryan@fortrow.com) on 2026-05-11 20:39:09 EDT
 * 
 * -------------------------------------------------------------------------

 */
package com.fortrow.jflux.http.api.base.prereqs;

import com.fortrow.jflux.crypto.Rand;
import com.fortrow.jflux.crypto.builtin.AES;
import com.fortrow.jflux.crypto.builtin.HKDF;
import com.fortrow.jflux.crypto.builtin.SHA;
import com.fortrow.jflux.crypto.builtin.SecureCompare;
import com.fortrow.jflux.db.Db;
import com.fortrow.jflux.db.connectors.details.DbConnectionDetails;
import com.fortrow.jflux.db.supporting.QueryResults;
import com.fortrow.jflux.db.supporting.TableSchema;
import com.fortrow.jflux.db.supporting.sql.Record;
import com.fortrow.jflux.http.api.base.endpoints.ApiResource;
import com.fortrow.jflux.http.api.base.endpoints.InboundRequest;
import com.fortrow.jflux.http.api.base.endpoints.expectations.ApiExpectationResult;
import com.fortrow.jflux.http.api.base.endpoints.permissions.ApiEndpointPermissions;
import com.fortrow.jflux.http.api.base.endpoints.permissions.ApiUserPermissions;
import com.fortrow.jflux.http.api.base.models.ApiModel;
import com.fortrow.jflux.http.api.base.models.AppModelCollection;
import com.fortrow.jflux.encoding.BaseX;
import com.fortrow.jflux.encoding.Hex;
import com.fortrow.jflux.files.LogFile;
import com.fortrow.jflux.http.api.server.config.APIServerConfig;
import com.fortrow.jflux.http.api.server.containers.APIResponse;
import com.fortrow.jflux.http.api.server.containers.BodyParameters;
import com.fortrow.jflux.http.api.server.containers.ClientSession;
import com.fortrow.jflux.http.api.server.containers.IpAddress;
import com.fortrow.jflux.http.api.server.containers.RemoteClient;
import com.fortrow.jflux.http.api.server.encryption.APIKeys;
import com.fortrow.jflux.http.api.server.containers.ResponseCode;
import com.fortrow.jflux.http.api.server.exceptions.APIException;
import com.fortrow.jflux.http.api.types.DatabaseServerType;
import com.fortrow.jflux.json.JSON;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public class ApiService {

    private static final long DEFAULT_TIMEOUT_MILLIS = 60_000L;
    private String sessionsTable;
    private String sessionKeysTable;
    private String apiKeysTable;
    private String apiPermissionsTable;
    private String blacklistTable;
    private String timeoutsTable;

    private final List<BlacklistEntry> blacklist = Collections.synchronizedList(new ArrayList<>());
    private final List<TimeoutEntry> timeouts = Collections.synchronizedList(new ArrayList<>());
    private final Map<String, ApiUserPermissions> apiUserPermissions = Collections.synchronizedMap(new HashMap<>());
    private String appName;
    private String appKey;
    private Db appDb = null;
    private AppModelCollection models = new AppModelCollection();
    private Db sessionDb = null;
    private DatabaseServerType applicationDbType = null;
    private DatabaseServerType sessionDbType = DatabaseServerType.PROCESS_MEMORY;
    private LogFile log = null;

    public ApiService(String appName, byte[] appKey) {
        if(appKey == null || appKey.length == 0) throw new IllegalArgumentException("API service app key is required.");
        this.appName = appName;
        initStoreNames(appName);
        this.appKey = BaseX.encode64(appKey);
    }

    public static byte[] generateAppKey(String appName, String nonce) {
        String normalizedName = normalizeAppName(appName);
        if(nonce == null || nonce.isBlank()) throw new IllegalArgumentException("API service app key nonce is required.");
        byte[] inputKeyMaterial = nonceMaterial(nonce);
        byte[] salt = Hex.fromHex(SHA.get256(normalizedName));
        byte[] info = ("jflux:http-api:app-key:v1:" + normalizedName).getBytes(StandardCharsets.UTF_8);
        return HKDF.deriveKey(inputKeyMaterial, salt, info, 32);
    }

    private static byte[] nonceMaterial(String nonce) {
        String trimmed = nonce.trim();
        if(trimmed.matches("(?i)[0-9a-f]+") && trimmed.length() % 2 == 0) {
            return Hex.fromHex(trimmed);
        }
        return trimmed.getBytes(StandardCharsets.UTF_8);
    }


    private void initStoreNames(String appName) {
        String prefix = normalizeAppName(appName) + "_api_";
        this.sessionsTable = prefix + "sessions";
        this.sessionKeysTable = prefix + "session_keys";
        this.apiKeysTable = prefix + "api_keys";
        this.apiPermissionsTable = prefix + "api_permissions";
        this.blacklistTable = prefix + "blacklist";
        this.timeoutsTable = prefix + "timeouts";
    }

    private static String normalizeAppName(String appName) {
        String normalized = appName == null ? "" : appName.trim().toLowerCase().replaceAll("[^a-z0-9_]", "_");
        normalized = normalized.replaceAll("_+", "_").replaceAll("^_", "").replaceAll("_$", "");
        return normalized.isBlank() ? "jflux" : normalized;
    }

    public void configureDatabases(APIServerConfig config) throws APIException {
        if(config == null) return;
        this.applicationDbType = config.getApplicationDbType();
        this.sessionDbType = config.getSessionDbType();

        DbConnectionDetails applicationDetails = config.getApplicationDbConnectionDetails();
        if(applicationDbType != null && applicationDbType != DatabaseServerType.PROCESS_MEMORY && applicationDetails != null) {
            this.appDb = new Db(applicationDetails);
        }

        DbConnectionDetails sessionDetails = config.getSessionDbConnectionDetails();
        if(sessionDbType != DatabaseServerType.PROCESS_MEMORY) {
            this.sessionDb = new Db(sessionDetails);
            createSessionTables();
        }
    }

    public void setLogFile(LogFile file) {
        this.log = file;
    }

    public void setApplicationDb(Db applicationDb) {
        appDb = applicationDb;
    }

    public void setSessionDb(Db sessionDb) {
        this.sessionDb = sessionDb;
    }

    public Db getApplicationDb() {
        return appDb;
    }

    public void setModels(AppModelCollection models) {
        this.models = models == null ? new AppModelCollection() : models;
        this.models.bind(this);
    }

    public AppModelCollection getModels() {
        return models;
    }

    public void migrateModel(ApiModel model) {
        if(model == null || appDb == null) return;
        try {
            appDb.query().createTable(model.getTableName(), model.tableSchema()).run();
        } catch (Exception e) {
            writeToLog("Failed to migrate API model '" + model.getModelName() + "': " + e.getMessage());
        }
    }

    public APIResponse executeModelResource(ApiModel model, InboundRequest request, String method) throws Exception {
        if(model == null) return response(request, ResponseCode.REQUEST_FAILED, "API model resource is not configured.");
        if(appDb == null) return response(request, ResponseCode.SERVER_ERROR, "Application database is not configured for API model resources.");

        String normalizedMethod = method == null ? "GET" : method.toUpperCase();
        switch (normalizedMethod) {
            case "GET":
                return modelIndex(model, request);
            case "POST":
                return modelCreate(model, request);
            case "PUT":
            case "PATCH":
                return modelUpdate(model, request);
            case "DELETE":
                return modelDelete(model, request);
            default:
                return response(request, ResponseCode.REQUEST_FAILED, normalizedMethod + " is not supported by model resources.");
        }
    }

    private APIResponse modelIndex(ApiModel model, InboundRequest request) {
        ApiUserPermissions userPermissions = permissionsForRequest(request);
        boolean authenticated = authenticated(request, userPermissions);
        String id = requestValue(request, model.primaryKeyName());
        if(id.isBlank()) id = requestValue(request, "id");

        QueryResults results;
        if(id.isBlank()) {
            results = appDb.query().table(model.getTableName()).select(model.readableColumns(authenticated, userPermissions)).allRows().get();
        } else {
            results = appDb.query().table(model.getTableName()).select(model.readableColumns(authenticated, userPermissions)).where(model.primaryKeyName(), "=", id).get();
        }

        JSONArray records = new JSONArray();
        if(results != null) {
            for(Record record : results.getList()) records.add(model.rowJson(record, authenticated, userPermissions));
        }

        JSONObject payload = new JSONObject();
        payload.put("request_status", "success");
        payload.put("model", model.getModelName());
        payload.put("records", records);
        payload.put("count", records.size());
        return new APIResponse(request == null ? null : request.getSession(), payload, ResponseCode.SUCCESSFUL, false);
    }

    private APIResponse modelCreate(ApiModel model, InboundRequest request) {
        ApiUserPermissions userPermissions = permissionsForRequest(request);
        boolean authenticated = authenticated(request, userPermissions);
        HashMap<String, Object> values = model.createValues(request == null ? null : request.getParams(), authenticated, userPermissions);
        if(values.isEmpty()) return response(request, ResponseCode.REQUEST_FAILED, "No writable model fields were provided.");

        appDb.query().table(model.getTableName()).insert(values).run();
        JSONObject payload = new JSONObject();
        payload.put("request_status", "success");
        payload.put("model", model.getModelName());
        payload.put("record", new JSONObject(values));
        return new APIResponse(request == null ? null : request.getSession(), payload, ResponseCode.SUCCESSFUL, false);
    }

    private APIResponse modelUpdate(ApiModel model, InboundRequest request) {
        String id = requestValue(request, model.primaryKeyName());
        if(id.isBlank()) id = requestValue(request, "id");
        if(id.isBlank()) return response(request, ResponseCode.REQUEST_FAILED, "Model update requires an id.");

        ApiUserPermissions userPermissions = permissionsForRequest(request);
        boolean authenticated = authenticated(request, userPermissions);
        HashMap<String, Object> values = model.writableValues(request == null ? null : request.getParams(), false, authenticated, userPermissions);
        values.remove(model.primaryKeyName());
        if(values.isEmpty()) return response(request, ResponseCode.REQUEST_FAILED, "No writable model fields were provided.");

        appDb.query().table(model.getTableName()).update(values).where(model.primaryKeyName(), "=", id).run();
        JSONObject payload = new JSONObject();
        payload.put("request_status", "success");
        payload.put("model", model.getModelName());
        payload.put("id", id);
        payload.put("updated", new JSONObject(values));
        return new APIResponse(request == null ? null : request.getSession(), payload, ResponseCode.SUCCESSFUL, false);
    }

    private APIResponse modelDelete(ApiModel model, InboundRequest request) {
        String id = requestValue(request, model.primaryKeyName());
        if(id.isBlank()) id = requestValue(request, "id");
        if(id.isBlank()) return response(request, ResponseCode.REQUEST_FAILED, "Model delete requires an id.");

        appDb.query().table(model.getTableName()).where(model.primaryKeyName(), "=", id).delete();
        JSONObject payload = new JSONObject();
        payload.put("request_status", "success");
        payload.put("model", model.getModelName());
        payload.put("id", id);
        return new APIResponse(request == null ? null : request.getSession(), payload, ResponseCode.SUCCESSFUL, false);
    }

    private APIResponse response(InboundRequest request, ResponseCode code, String message) {
        return new APIResponse(request == null ? null : request.getSession(), code, message);
    }

    private boolean authenticated(InboundRequest request) {
        return authenticated(request, permissionsForRequest(request));
    }

    private boolean authenticated(InboundRequest request, ApiUserPermissions userPermissions) {
        if(request == null) return false;
        ClientSession session = request.getSession();
        if(session != null && session.getClient() != null) return true;
        if(session != null && session.getSession() != null && session.getSession().getApiKeys() != null) return true;

        String apiKey = ApiEndpointPermissions.apiKeyFrom(request);
        if(apiKey == null || apiKey.isBlank()) return false;
        return userPermissions != null || lookupApiKeys(apiKey) != null;
    }

    private ApiUserPermissions permissionsForRequest(InboundRequest request) {
        String apiKey = ApiEndpointPermissions.apiKeyFrom(request);
        return apiKey == null || apiKey.isBlank() ? null : lookupApiUserPermissions(apiKey);
    }

    private String requestValue(InboundRequest request, String key) {
        if(request == null || key == null || key.isBlank()) return "";
        if(request.getPathVariables() != null && request.getPathVariables().containsKey(key)) {
            return stringValue(request.getPathVariables().get(key));
        }
        BodyParameters params = request.getParams();
        if(params != null && params.present(key)) return stringValue(params.get(key));
        return "";
    }

    public APIKeys lookupApiKeys(String apiKey) {
        if(apiKey == null || apiKey.isBlank()) return null;
        if(apiKeyLocked(apiKey)) return null;
        if(!usesPersistentSessions()) return null;
        try {
            JSONObject payload = readApiKeyPayload(apiKey);
            if(payload == null || payload.isEmpty()) return null;
            String storedApiKey = stringValue(payload.get("api_key"));
            if(!SecureCompare.equals(storedApiKey, apiKey)) return null;
            return managedApiKeys(
                    storedApiKey,
                    stringValue(payload.get("secret")),
                    stringValue(payload.get("session_secret")),
                    stringValue(payload.get("psk")),
                    allowedIpsFromJson(payload.get("allowed_ips"))
            );
        } catch (APIException e) {
            writeToLog("Failed to lookup API key: " + e.getMessage());
            return null;
        }
    }

    public APIKeys registerApiKeys(APIKeys keys) throws APIException {
        if(keys == null) throw new APIException("API keys are required.");
        return registerApiKeys(
                keys.getKey(),
                keys.getSecret(),
                keys.getRawSessionSecret(),
                keys.getPsk(),
                keys.getAllowedIps()
        );
    }

    public APIKeys registerApiKeys(APIKeys.Keys keys) throws APIException {
        if(keys == null) throw new APIException("API keys are required.");
        return registerApiKeys(keys.getKey(), keys.getSecret(), keys.getSessionSecret(), keys.getPsk());
    }

    public APIKeys registerApiKeys(String apiKey, String secret, String sessionSecret, String psk) throws APIException {
        return registerApiKeys(apiKey, secret, sessionSecret, psk, null);
    }

    public APIKeys registerApiKeys(String apiKey, String secret, String sessionSecret, String psk, List<IpAddress> allowedIps) throws APIException {
        requireApiKeyMaterial(apiKey, secret, sessionSecret, psk);
        if(apiKeyLocked(apiKey)) throw new APIException("API key is locked and cannot be reused.");
        APIKeys managed = managedApiKeys(apiKey, secret, sessionSecret, psk, allowedIps);
        if(usesPersistentSessions()) saveApiKeyRecord(apiKey, secret, sessionSecret, psk, allowedIps);
        return managed;
    }

    public void lockApiKey(ClientSession clientSession, String reason) {
        if(clientSession == null) return;
        String apiKey = null;
        if(clientSession.getSession() != null && clientSession.getSession().getApiKeys() != null) {
            apiKey = clientSession.getSession().getApiKeys().getKey();
        }
        if((apiKey == null || apiKey.isBlank()) && clientSession.getClient() != null) {
            apiKey = clientSession.getClient().getApiKey();
        }
        lockApiKey(apiKey, reason);
    }

    public void lockApiKey(APIKeys apiKeys, String reason) {
        if(apiKeys == null) return;
        lockApiKey(apiKeys.getKey(), reason);
    }

    public void lockApiKey(String apiKey, String reason) {
        if(apiKey == null || apiKey.isBlank()) return;
        BlacklistEntry entry = new BlacklistEntry(BlacklistEntryType.API_KEY, apiKey);
        synchronized (blacklist) {
            boolean exists = false;
            for(BlacklistEntry current : blacklist) {
                if(current.sameTarget(entry)) {
                    exists = true;
                    break;
                }
            }
            if(!exists) blacklist.add(0, entry);
        }
        if(usesPersistentSessions()) {
            saveBlacklist(entry);
            try {
                String id = apiKeyRecordId(apiKey);
                deleteRecord(apiKeysTable, id);
                deleteRecord(sessionKeysTable, id);
            } catch (APIException e) {
                writeToLog("Failed to remove locked API key material: " + e.getMessage());
            }
        }
        String safeReason = reason == null || reason.isBlank() ? "No reason provided." : reason;
        writeToLog("Locked API key '" + SHA.getSha1(apiKey) + "': " + safeReason);
    }

    public void updateSessionSecret(APIKeys keys, String sessionSecret) throws APIException {
        if(keys == null) return;
        if(usesPersistentSessions()) {
            JSONObject obj = new JSONObject();
            obj.put("api_key", apiKeyRecordId(keys.getKey()));
            obj.put("session_secret", SHA.get256(sessionSecret));
            obj.put("updated_at_ms", nowMs());
            saveRecord(sessionKeysTable, apiKeyRecordId(keys.getKey()), obj);
            if(hasApiKeyMaterial(keys)) {
                saveApiKeyRecord(keys.getKey(), keys.getSecret(), sessionSecret, keys.getPsk(), keys.getAllowedIps());
            }
        }
    }

    public String getAppKey() {
        return appKey;
    }

    public String getSession() {
        return "";
    }

    public String createSession() {
        return "";
    }

    public void writeToLog(String line) {
        if(log != null) log.write(line);
    }

    public APIResponse validateEndpointRequest(ApiResource resource, InboundRequest request) {
        ApiExpectationResult expectationResult = validateEndpointExpectations(resource, request);
        if(!expectationResult.isValid()) {
            writeToLog("Endpoint expectation failed for '" + endpointName(resource) + "': " + expectationResult.getMessage());
            return new APIResponse(request == null ? null : request.getSession(), ResponseCode.REQUEST_FAILED, expectationResult.getMessage());
        }

        if(!isEndpointPermitted(resource, request)) {
            String message = resource == null || resource.getPermissions() == null
                    ? "API key does not have permission to access this endpoint."
                    : resource.getPermissions().failureMessage();
            writeToLog("Endpoint permission denied for '" + endpointName(resource) + "'.");
            return new APIResponse(request == null ? null : request.getSession(), ResponseCode.FORBIDDEN, message);
        }

        return null;
    }

    public ApiExpectationResult validateEndpointExpectations(ApiResource resource, InboundRequest request) {
        if(resource == null || resource.getExpectations() == null) return ApiExpectationResult.ok();
        return resource.getExpectations().validate(request);
    }

    public boolean isEndpointPermitted(ApiResource resource, InboundRequest request) {
        if(resource == null || resource.getPermissions() == null) return true;
        ApiEndpointPermissions endpointPermissions = resource.getPermissions();
        if(endpointPermissions.isEmpty()) return true;
        return endpointPermissions.isPermitted(request, lookupApiUserPermissions(ApiEndpointPermissions.apiKeyFrom(request)));
    }

    public ApiUserPermissions lookupApiUserPermissions(String apiKey) {
        if(apiKey == null || apiKey.isBlank()) return null;
        ApiUserPermissions permissions = apiUserPermissions.get(apiKey);
        if(permissions != null) return permissions;
        if(!usesPersistentSessions()) return null;

        try {
            JSONObject payload = getRecord(apiPermissionsTable, apiKeyRecordId(apiKey));
            if(payload == null || payload.isEmpty()) return null;
            permissions = permissionsFromJson(apiKey, payload);
            apiUserPermissions.put(apiKey, permissions);
            return permissions;
        } catch (APIException e) {
            writeToLog("Failed to lookup API user permissions: " + e.getMessage());
            return null;
        }
    }

    public ApiUserPermissions registerApiUserPermissions(ApiUserPermissions permissions) throws APIException {
        if(permissions == null || permissions.getApiKey().isBlank()) throw new APIException("API user permissions require an API key.");
        apiUserPermissions.put(permissions.getApiKey(), permissions);
        if(usesPersistentSessions()) saveApiUserPermissions(permissions);
        return permissions;
    }

    public ApiUserPermissions grantApiPermissions(String apiKey, String... permissionNames) throws APIException {
        ApiUserPermissions permissions = lookupApiUserPermissions(apiKey);
        if(permissions == null) permissions = new ApiUserPermissions(apiKey);
        permissions.grant(permissionNames);
        return registerApiUserPermissions(permissions);
    }

    public ApiUserPermissions grantApiKeyPermission(String apiKey, String table, String id) throws APIException {
        ApiUserPermissions permissions = lookupApiUserPermissions(apiKey);
        if(permissions == null) permissions = new ApiUserPermissions(apiKey);
        permissions.grantKey(table, id);
        return registerApiUserPermissions(permissions);
    }

    public void revokeApiUserPermissions(String apiKey) {
        if(apiKey == null || apiKey.isBlank()) return;
        apiUserPermissions.remove(apiKey);
        if(usesPersistentSessions()) {
            try {
                deleteRecord(apiPermissionsTable, apiKeyRecordId(apiKey));
            } catch (APIException e) {
                writeToLog("Failed to revoke API user permissions: " + e.getMessage());
            }
        }
    }

    public void newSessionForClient(RemoteClient client, ClientSession session, APIServerConfig config) {
        if(!usesPersistentSessions()) return;
        saveSession(session, true);
    }

    public void newRequestForSession(ClientSession session) {
        if(!usesPersistentSessions()) return;
        saveSession(session, true);
    }

    public void invalidateSession(ClientSession session) {
        if(!usesPersistentSessions()) return;
        saveSession(session, false);
    }

    private String endpointName(ApiResource resource) {
        if(resource == null) return "unknown";
        return resource.getPath() + "#" + resource.getType();
    }

    private void saveApiUserPermissions(ApiUserPermissions permissions) throws APIException {
        String id = apiKeyRecordId(permissions.getApiKey());
        JSONObject obj = permissionsJson(id, permissions);
        if(!saveRecord(apiPermissionsTable, id, obj)) {
            throw new APIException("Failed to persist API user permissions.");
        }
    }

    private JSONObject permissionsJson(String id, ApiUserPermissions permissions) {
        JSONObject obj = new JSONObject();
        obj.put("id", id);
        obj.put("api_key", id);
        obj.put("permissions", jsonArray(permissions.getPermissions()));
        obj.put("permitted_keys", jsonArray(permissions.getPermittedKeys()));
        obj.put("updated_at_ms", nowMs());
        return obj;
    }

    private ApiUserPermissions permissionsFromJson(String apiKey, JSONObject payload) {
        ApiUserPermissions permissions = new ApiUserPermissions(apiKey);
        for(Object value : jsonArrayValue(payload.get("permissions"))) {
            permissions.grant(stringValue(value));
        }
        for(Object value : jsonArrayValue(payload.get("permitted_keys"))) {
            permissions.grantKey(stringValue(value));
        }
        return permissions;
    }

    private static JSONArray jsonArray(Set<String> values) {
        JSONArray arr = new JSONArray();
        if(values == null) return arr;
        for(String value : values) {
            if(value != null && !value.isBlank()) arr.add(value);
        }
        return arr;
    }

    private static JSONArray jsonArrayValue(Object value) {
        if(value instanceof JSONArray) return (JSONArray) value;
        if(value instanceof String && !((String) value).isBlank()) return JSON.getArray((String) value);
        return new JSONArray();
    }

    private static class BlacklistEntry {

        private final BlacklistEntryType entryType;
        private final String value;

        public BlacklistEntry(BlacklistEntryType entryType, String value) {
            if(entryType == null) throw new IllegalArgumentException("Blacklist entry type is required.");
            if(value == null || value.isBlank()) throw new IllegalArgumentException("Blacklist entry value is required.");
            this.entryType = entryType;
            this.value = value;
        }

        public BlacklistEntry(BlacklistEntryType entryType, ClientSession clientSession) {
            this(entryType, valueFor(entryType, clientSession));
        }

        public boolean matches(ClientSession clientSession) {
            if(clientSession == null || clientSession.getClient() == null) return false;
            switch(entryType) {
                case API_KEY:
                    return SecureCompare.equals(this.value, clientSession.getClient().getApiKey());
                case IP_ADDRESS:
                    return this.value.equals(clientSession.getClient().getClientInfo().getRemoteIp());
            }
            return false;
        }

        public boolean sameTarget(BlacklistEntry other) {
            return other != null
                    && this.entryType == other.entryType
                    && SecureCompare.equals(this.value, other.value);
        }

        private static String valueFor(BlacklistEntryType entryType, ClientSession clientSession) {
            if(clientSession == null || clientSession.getClient() == null) {
                throw new IllegalArgumentException("Client session is required.");
            }
            switch(entryType) {
                case API_KEY:
                    return clientSession.getClient().getApiKey();
                case IP_ADDRESS:
                    return clientSession.getClient().getClientInfo().getRemoteIp();
                default:
                    throw new IllegalArgumentException("Unsupported blacklist entry type.");
            }
        }

        public String getValue() {
            return value;
        }

        public BlacklistEntryType getEntryType() {
            return entryType;
        }

        public String id() {
            return targetId(entryType, value);
        }

    }

    private static class TimeoutEntry {

        private final BlacklistEntry target;
        private final long expiresAtNanos;
        private final long expiresAtMs;

        public TimeoutEntry(BlacklistEntryType entryType, ClientSession clientSession, long timeoutMillis) {
            this.target = new BlacklistEntry(entryType, clientSession);
            long safeTimeoutMillis = Math.max(1L, timeoutMillis);
            this.expiresAtNanos = expiresAtNanos(safeTimeoutMillis);
            this.expiresAtMs = nowMs() + safeTimeoutMillis;
        }

        public boolean matches(ClientSession clientSession) {
            return target.matches(clientSession);
        }

        public boolean sameTarget(TimeoutEntry other) {
            return other != null && target.sameTarget(other.target);
        }

        public boolean isActive(long nowNanos) {
            return nowNanos < expiresAtNanos;
        }

        public boolean isPersistedActive(long nowMs) {
            return nowMs < expiresAtMs;
        }

        public String id() {
            return target.id();
        }

        public JSONObject json() {
            JSONObject obj = new JSONObject();
            obj.put("id", id());
            obj.put("entry_type", target.getEntryType().name());
            obj.put("entry_value", target.getEntryType() == BlacklistEntryType.API_KEY ? id() : target.getValue());
            obj.put("expires_at_ms", expiresAtMs);
            obj.put("created_at_ms", nowMs());
            return obj;
        }

        private static long expiresAtNanos(long timeoutMillis) {
            long timeoutNanos = TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
            long nowNanos = System.nanoTime();
            if(Long.MAX_VALUE - nowNanos < timeoutNanos) return Long.MAX_VALUE;
            return nowNanos + timeoutNanos;
        }

    }

    public boolean inTimeout(ClientSession clientSession, int perMinute) {
        if(clientSession == null) return false;
        if(usesPersistentSessions()) {
            if(persistentTimeoutActive(clientSession, BlacklistEntryType.API_KEY)) return true;
            if(persistentTimeoutActive(clientSession, BlacklistEntryType.IP_ADDRESS)) return true;
        }
        long nowNanos = System.nanoTime();
        synchronized (timeouts) {
            for(int i = timeouts.size() - 1; i >= 0; i--) {
                TimeoutEntry timeout = timeouts.get(i);
                if(!timeout.isActive(nowNanos)) {
                    timeouts.remove(i);
                    continue;
                }
                if(timeout.matches(clientSession)) return true;
            }
        }
        return clientSession.getSession().getRecentRequests() >= perMinute;
    }

    public boolean inBlacklist(ClientSession clientSession) {
        if(usesPersistentSessions()) {
            if(persistentBlacklistContains(clientSession, BlacklistEntryType.API_KEY)) return true;
            if(persistentBlacklistContains(clientSession, BlacklistEntryType.IP_ADDRESS)) return true;
        }
        synchronized (blacklist) {
            for(int i=0; i<blacklist.size(); i++) {
                if(blacklist.get(i).matches(clientSession)) return true;
            }
        }
        return false;
    }

    public void addToBlacklist(ClientSession clientSession, BlacklistEntryType entryType) {
        BlacklistEntry entry = new BlacklistEntry(entryType, clientSession);
        synchronized (blacklist) {
            for(BlacklistEntry current : blacklist) {
                if(current.sameTarget(entry)) return;
            }
            blacklist.add(0, entry);
        }
        if(usesPersistentSessions()) saveBlacklist(entry);
    }

    public void removeFromBlacklist(ClientSession clientSession) {
        synchronized (blacklist) {
            for(int i= blacklist.size() - 1; i>=0; i--) {
                if(blacklist.get(i).matches(clientSession)) {
                    blacklist.remove(i);
                }
            }
        }
        if(usesPersistentSessions()) {
            deleteTarget(blacklistTable, BlacklistEntryType.API_KEY, clientSession);
            deleteTarget(blacklistTable, BlacklistEntryType.IP_ADDRESS, clientSession);
        }
    }

    public void addToTimeout(ClientSession clientSession, BlacklistEntryType entryType) {
        addToTimeout(clientSession, entryType, DEFAULT_TIMEOUT_MILLIS);
    }

    public void addToTimeout(ClientSession clientSession, BlacklistEntryType entryType, long timeoutMillis) {
        TimeoutEntry entry = new TimeoutEntry(entryType, clientSession, timeoutMillis);
        synchronized (timeouts) {
            for(int i = timeouts.size() - 1; i >= 0; i--) {
                if(timeouts.get(i).sameTarget(entry)) {
                    timeouts.remove(i);
                }
            }
            timeouts.add(0, entry);
        }
        if(usesPersistentSessions()) saveRecord(timeoutsTable, entry.id(), entry.json());
    }

    public void removeFromTimeout(ClientSession clientSession) {
        synchronized (timeouts) {
            for(int i = timeouts.size() - 1; i >= 0; i--) {
                if(timeouts.get(i).matches(clientSession)) {
                    timeouts.remove(i);
                }
            }
        }
        if(usesPersistentSessions()) {
            deleteTarget(timeoutsTable, BlacklistEntryType.API_KEY, clientSession);
            deleteTarget(timeoutsTable, BlacklistEntryType.IP_ADDRESS, clientSession);
        }
    }

    private void createSessionTables() throws APIException {
        if(!usesPersistentSessions()) return;
        if(sessionDbType == DatabaseServerType.REDIS) return;
        try {
            sessionDb.query().createTable(
                    apiKeysTable,
                    new TableSchema("id", "varchar(96)", false, true, null),
                    TableSchema.col("iv", "text", false),
                    TableSchema.col("value", "text", false),
                    TableSchema.col("updated_at_ms", "integer", false),
                    TableSchema.col("payload", "text", false)
            ).run();
            sessionDb.query().createTable(
                    apiPermissionsTable,
                    new TableSchema("id", "varchar(96)", false, true, null),
                    TableSchema.col("api_key", "text", false),
                    TableSchema.col("permissions", "text", false),
                    TableSchema.col("permitted_keys", "text", false),
                    TableSchema.col("updated_at_ms", "integer", false),
                    TableSchema.col("payload", "text", false)
            ).run();
            sessionDb.query().createTable(
                    sessionsTable,
                    TableSchema.id(),
                    TableSchema.col("api_key", "text", false),
                    TableSchema.col("remote_ip", "text", false),
                    TableSchema.col("key_set_id", "text", true),
                    TableSchema.col("active", "integer", false),
                    TableSchema.col("started_at_ms", "integer", false),
                    TableSchema.col("last_request_at_ms", "integer", false),
                    TableSchema.col("request_count", "integer", false),
                    TableSchema.col("payload", "text", false)
            ).run();
            sessionDb.query().createTable(
                    sessionKeysTable,
                    new TableSchema("id", "varchar(96)", false, true, null),
                    TableSchema.col("session_secret", "text", false),
                    TableSchema.col("updated_at_ms", "integer", false),
                    TableSchema.col("payload", "text", false)
            ).run();
            sessionDb.query().createTable(
                    blacklistTable,
                    TableSchema.id(),
                    TableSchema.col("entry_type", "text", false),
                    TableSchema.col("entry_value", "text", false),
                    TableSchema.col("created_at_ms", "integer", false),
                    TableSchema.col("payload", "text", false)
            ).run();
            sessionDb.query().createTable(
                    timeoutsTable,
                    TableSchema.id(),
                    TableSchema.col("entry_type", "text", false),
                    TableSchema.col("entry_value", "text", false),
                    TableSchema.col("expires_at_ms", "integer", false),
                    TableSchema.col("created_at_ms", "integer", false),
                    TableSchema.col("payload", "text", false)
            ).run();
        } catch (Exception e) {
            throw new APIException("Failed to create API session tables: " + e.getMessage());
        }
    }

    private void saveSession(ClientSession session, boolean active) {
        if(session == null || session.getSession() == null || session.getClient() == null) return;
        JSONObject obj = sessionJson(session, active);
        saveRecord(sessionsTable, session.getSession().getSessionId(), obj);
    }

    private JSONObject sessionJson(ClientSession session, boolean active) {
        JSONObject obj = new JSONObject();
        obj.put("id", session.getSession().getSessionId());
        obj.put("api_key", safeApiKeyRecordId(session.getSession().getApiKeys().getKey()));
        obj.put("remote_ip", session.getClient().getClientInfo().getRemoteIp());
        obj.put("key_set_id", safeKeySetId(session));
        obj.put("active", active);
        obj.put("started_at_ms", toMs(session.getSession().getStartedDateTime()));
        obj.put("last_request_at_ms", toMs(session.getSession().getLastRequestDateTime()));
        obj.put("request_count", session.getSession().getRequestCount());
        return obj;
    }

    private String safeKeySetId(ClientSession session) {
        try {
            return session.getSession().getKeySetId();
        } catch (Exception ignored) {
            return "";
        }
    }

    private void saveBlacklist(BlacklistEntry entry) {
        JSONObject obj = new JSONObject();
        obj.put("id", entry.id());
        obj.put("entry_type", entry.getEntryType().name());
        obj.put("entry_value", persistentEntryValue(entry));
        obj.put("created_at_ms", nowMs());
        saveRecord(blacklistTable, entry.id(), obj);
    }

    private boolean persistentBlacklistContains(ClientSession session, BlacklistEntryType entryType) {
        JSONObject obj = getTarget(blacklistTable, entryType, session);
        return obj != null && !obj.isEmpty();
    }

    private boolean persistentTimeoutActive(ClientSession session, BlacklistEntryType entryType) {
        String id = targetId(entryType, session);
        JSONObject obj = getRecord(timeoutsTable, id);
        if(obj == null || obj.isEmpty()) return false;
        long expiresAt = longValue(obj.get("expires_at_ms"));
        if(expiresAt > nowMs()) return true;
        deleteRecord(timeoutsTable, id);
        return false;
    }

    private JSONObject getTarget(String table, BlacklistEntryType entryType, ClientSession session) {
        return getRecord(table, targetId(entryType, session));
    }

    private void deleteTarget(String table, BlacklistEntryType entryType, ClientSession session) {
        deleteRecord(table, targetId(entryType, session));
    }

    private boolean saveRecord(String table, String id, JSONObject payload) {
        if(!usesPersistentSessions() || id == null || id.isBlank()) return false;
        try {
            JSONObject row = new JSONObject();
            row.putAll(payload);
            row.put("id", id);
            if(sessionDbType == DatabaseServerType.REDIS) {
                sessionDb.redis().put(redisKey(table, id), row).run();
                return true;
            }
            deleteRecord(table, id);
            insertSqlRecord(table, id, row);
            return true;
        } catch (Exception e) {
            writeToLog("Failed to persist API session record: " + e.getMessage());
            return false;
        }
    }

    private JSONObject getRecord(String table, String id) {
        if(!usesPersistentSessions() || id == null || id.isBlank()) return null;
        try {
            if(sessionDbType == DatabaseServerType.REDIS) {
                QueryResults results = sessionDb.redis().getEntry(redisKey(table, id)).get();
                if(results == null || results.size() == 0) return null;
                Record record = results.next();
                JSONObject obj = new JSONObject();
                for(Object[] pair : record.toArray()) obj.put(pair[0].toString(), pair[1]);
                return obj;
            }
            QueryResults results = sessionDb.query().table(table).select("payload").where("id", "=", id).get();
            if(results == null || results.size() == 0) return null;
            String payload = results.next().val(0);
            return JSON.getObject(payload);
        } catch (Exception e) {
            writeToLog("Failed to read API session record: " + e.getMessage());
            return null;
        }
    }

    private void deleteRecord(String table, String id) {
        if(!usesPersistentSessions() || id == null || id.isBlank()) return;
        try {
            if(sessionDbType == DatabaseServerType.REDIS) {
                sessionDb.redis().delete(redisKey(table, id)).run();
                return;
            }
            sessionDb.query().table(table).where("id", "=", id).delete();
        } catch (Exception e) {
            writeToLog("Failed to delete API session record: " + e.getMessage());
        }
    }

    private void insertSqlRecord(String table, String id, JSONObject payload) {
        String payloadText = JSON.getString(payload);
        if(apiKeysTable.equals(table)) {
            sessionDb.query().table(table)
                    .insert("id", id)
                    .insert("iv", stringValue(payload.get("iv")))
                    .insert("value", stringValue(payload.get("value")))
                    .insert("updated_at_ms", longValue(payload.get("updated_at_ms")))
                    .insert("payload", payloadText)
                    .run();
        } else if(apiPermissionsTable.equals(table)) {
            sessionDb.query().table(table)
                    .insert("id", id)
                    .insert("api_key", stringValue(payload.get("api_key")))
                    .insert("permissions", JSON.getString((JSONArray) payload.get("permissions")))
                    .insert("permitted_keys", JSON.getString((JSONArray) payload.get("permitted_keys")))
                    .insert("updated_at_ms", longValue(payload.get("updated_at_ms")))
                    .insert("payload", payloadText)
                    .run();
        } else if(sessionsTable.equals(table)) {
            sessionDb.query().table(table)
                    .insert("id", id)
                    .insert("api_key", stringValue(payload.get("api_key")))
                    .insert("remote_ip", stringValue(payload.get("remote_ip")))
                    .insert("key_set_id", stringValue(payload.get("key_set_id")))
                    .insert("active", boolValue(payload.get("active")) ? 1 : 0)
                    .insert("started_at_ms", longValue(payload.get("started_at_ms")))
                    .insert("last_request_at_ms", longValue(payload.get("last_request_at_ms")))
                    .insert("request_count", intValue(payload.get("request_count")))
                    .insert("payload", payloadText)
                    .run();
        } else if(sessionKeysTable.equals(table)) {
            sessionDb.query().table(table)
                    .insert("id", id)
                    .insert("session_secret", stringValue(payload.get("session_secret")))
                    .insert("updated_at_ms", longValue(payload.get("updated_at_ms")))
                    .insert("payload", payloadText)
                    .run();
        } else if(blacklistTable.equals(table)) {
            sessionDb.query().table(table)
                    .insert("id", id)
                    .insert("entry_type", stringValue(payload.get("entry_type")))
                    .insert("entry_value", stringValue(payload.get("entry_value")))
                    .insert("created_at_ms", longValue(payload.get("created_at_ms")))
                    .insert("payload", payloadText)
                    .run();
        } else if(timeoutsTable.equals(table)) {
            sessionDb.query().table(table)
                    .insert("id", id)
                    .insert("entry_type", stringValue(payload.get("entry_type")))
                    .insert("entry_value", stringValue(payload.get("entry_value")))
                    .insert("expires_at_ms", longValue(payload.get("expires_at_ms")))
                    .insert("created_at_ms", longValue(payload.get("created_at_ms")))
                    .insert("payload", payloadText)
                    .run();
        }
    }



    private boolean apiKeyLocked(String apiKey) {
        if(apiKey == null || apiKey.isBlank()) return false;
        BlacklistEntry probe = new BlacklistEntry(BlacklistEntryType.API_KEY, apiKey);
        synchronized (blacklist) {
            for(BlacklistEntry current : blacklist) {
                if(current.sameTarget(probe)) return true;
            }
        }
        if(usesPersistentSessions()) {
            JSONObject obj = getRecord(blacklistTable, probe.id());
            return obj != null && !obj.isEmpty();
        }
        return false;
    }

    private String safeApiKeyRecordId(String apiKey) {
        try {
            return apiKeyRecordId(apiKey);
        } catch (APIException e) {
            writeToLog("Failed to derive API key id for session metadata: " + e.getMessage());
            return SHA.getSha1(stringValue(apiKey));
        }
    }

    private static String persistentEntryValue(BlacklistEntry entry) {
        if(entry.getEntryType() == BlacklistEntryType.API_KEY) return entry.id();
        return entry.getValue();
    }

    private APIKeys managedApiKeys(String apiKey, String secret, String sessionSecret, String psk, List<IpAddress> allowedIps) {
        return new APIKeys(apiKey, secret, sessionSecret, psk, allowedIps) {
            @Override
            public void updateSessionSecret(APIKeys keys, String sessionSecret) throws APIException {
                ApiService.this.updateSessionSecret(keys, sessionSecret);
            }
        };
    }

    private void saveApiKeyRecord(String apiKey, String secret, String sessionSecret, String psk, List<IpAddress> allowedIps) throws APIException {
        String id = apiKeyRecordId(apiKey);
        JSONObject payload = new JSONObject();
        payload.put("api_key", apiKey);
        payload.put("secret", secret);
        payload.put("session_secret", sessionSecret);
        payload.put("psk", psk);
        payload.put("allowed_ips", allowedIpsJson(allowedIps));

        JSONObject encrypted = encryptApiKeyPayload(id, payload);
        encrypted.put("updated_at_ms", nowMs());
        if(!saveRecord(apiKeysTable, id, encrypted)) {
            throw new APIException("Failed to persist encrypted API key record.");
        }
    }

    private JSONObject readApiKeyPayload(String apiKey) throws APIException {
        String id = apiKeyRecordId(apiKey);
        JSONObject encrypted = getRecord(apiKeysTable, id);
        if(encrypted == null || encrypted.isEmpty()) return null;
        return decryptApiKeyPayload(id, encrypted);
    }

    private JSONObject encryptApiKeyPayload(String id, JSONObject payload) throws APIException {
        byte[] iv = Rand.generateIv(96);
        byte[] encrypted = AES.gcm(
                JSON.getString(payload).getBytes(StandardCharsets.UTF_8),
                apiKeyStorageKey(id),
                iv,
                apiKeyStorageAad(id),
                true
        );
        if(encrypted == null) throw new APIException("Failed to encrypt API key record.");
        JSONObject envelope = new JSONObject();
        envelope.put("version", 1);
        envelope.put("alg", "AES-128-GCM");
        envelope.put("iv", Hex.getHex(iv));
        envelope.put("value", BaseX.encode64(encrypted));
        return envelope;
    }

    private JSONObject decryptApiKeyPayload(String id, JSONObject envelope) throws APIException {
        byte[] ciphertext = BaseX.bytesFrom64(stringValue(envelope.get("value")));
        byte[] iv = Hex.fromHex(stringValue(envelope.get("iv")));
        if(ciphertext == null || iv == null || iv.length == 0) throw new APIException("Invalid encrypted API key record.");
        byte[] plaintext = AES.gcm(ciphertext, apiKeyStorageKey(id), iv, apiKeyStorageAad(id), false);
        if(plaintext == null) throw new APIException("Failed to decrypt API key record.");
        return JSON.getObject(new String(plaintext, StandardCharsets.UTF_8));
    }

    private String apiKeyRecordId(String apiKey) throws APIException {
        if(apiKey == null || apiKey.isBlank()) throw new APIException("API key is required.");
        String id = SHA.getHmac256(apiKey, appKeyBytes());
        if(id == null || id.isBlank()) throw new APIException("Failed to derive API key record id.");
        return id;
    }

    private byte[] apiKeyStorageKey(String id) throws APIException {
        byte[] salt = Hex.fromHex(SHA.get256(normalizeAppName(appName) + ":" + id));
        byte[] info = ("jflux:http-api:api-key-store:v1:" + normalizeAppName(appName)).getBytes(StandardCharsets.UTF_8);
        return HKDF.deriveKey(appKeyBytes(), salt, info, 16);
    }

    private byte[] apiKeyStorageAad(String id) {
        return ("jflux:http-api:api-key-store:v1:" + normalizeAppName(appName) + ":" + id).getBytes(StandardCharsets.UTF_8);
    }

    private byte[] appKeyBytes() throws APIException {
        byte[] key = BaseX.bytesFrom64(appKey);
        if(key == null || key.length == 0) throw new APIException("API service app key is unavailable.");
        return key;
    }

    private static JSONArray allowedIpsJson(List<IpAddress> allowedIps) {
        JSONArray arr = new JSONArray();
        if(allowedIps == null) return arr;
        for(IpAddress ip : allowedIps) {
            if(ip != null && ip.getAddress() != null) arr.add(ip.getAddress());
        }
        return arr;
    }

    private static List<IpAddress> allowedIpsFromJson(Object value) {
        if(!(value instanceof JSONArray)) return null;
        JSONArray arr = (JSONArray) value;
        if(arr.isEmpty()) return null;
        List<IpAddress> allowedIps = new ArrayList<>();
        for(Object ipValue : arr) {
            try {
                String ip = stringValue(ipValue);
                if(!ip.isBlank()) allowedIps.add(new IpAddress(ip));
            } catch (ParseException ignored) { }
        }
        return allowedIps.isEmpty() ? null : allowedIps;
    }

    private static void requireApiKeyMaterial(String apiKey, String secret, String sessionSecret, String psk) throws APIException {
        if(apiKey == null || apiKey.isBlank()) throw new APIException("API key is required.");
        if(secret == null || secret.isBlank()) throw new APIException("API secret is required.");
        if(sessionSecret == null || sessionSecret.isBlank()) throw new APIException("API session secret is required.");
        if(psk == null || psk.isBlank()) throw new APIException("API PSK is required.");
    }

    private static boolean hasApiKeyMaterial(APIKeys keys) {
        return keys != null
                && keys.getKey() != null && !keys.getKey().isBlank()
                && keys.getSecret() != null && !keys.getSecret().isBlank()
                && keys.getRawSessionSecret() != null && !keys.getRawSessionSecret().isBlank()
                && keys.getPsk() != null && !keys.getPsk().isBlank();
    }

    private boolean usesPersistentSessions() {
        return sessionDbType != null && sessionDbType != DatabaseServerType.PROCESS_MEMORY && sessionDb != null;
    }

    private static String targetId(BlacklistEntryType entryType, ClientSession session) {
        return targetId(entryType, BlacklistEntry.valueFor(entryType, session));
    }

    private static String targetId(BlacklistEntryType entryType, String value) {
        return SHA.getSha1(entryType.name() + ":" + value);
    }

    private String redisKey(String table, String id) {
        return table + ":" + id;
    }

    private static long nowMs() {
        return System.currentTimeMillis();
    }

    private static long toMs(LocalDateTime value) {
        if(value == null) return nowMs();
        return value.toInstant(ZoneOffset.UTC).toEpochMilli();
    }

    private static String stringValue(Object value) {
        return value == null ? "" : value.toString();
    }

    private static boolean boolValue(Object value) {
        if(value instanceof Boolean) return (Boolean) value;
        if(value instanceof Number) return ((Number) value).intValue() != 0;
        return Boolean.parseBoolean(stringValue(value));
    }

    private static int intValue(Object value) {
        if(value instanceof Number) return ((Number) value).intValue();
        try {
            return Integer.parseInt(stringValue(value));
        } catch (Exception ignored) {
            return 0;
        }
    }

    private static long longValue(Object value) {
        if(value instanceof Number) return ((Number) value).longValue();
        try {
            return Long.parseLong(stringValue(value));
        } catch (Exception ignored) {
            return 0L;
        }
    }

    public enum BlacklistEntryType {
        IP_ADDRESS,
        API_KEY
    }

}
