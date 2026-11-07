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
 * File created by Ryan Fitzgerald (ryan@fortrow.com) on 2026-05-08 18:13:50 EDT
 */
package com.fortrow.jflux.http.api.server;

import com.fortrow.jflux.crypto.builtin.SecureCompare;
import com.fortrow.jflux.files.LogFile;
import com.fortrow.jflux.http.api.base.prereqs.ApiService;
import com.fortrow.jflux.http.api.server.base.APIServer;
import com.fortrow.jflux.http.api.server.config.APIServerConfig;
import com.fortrow.jflux.http.api.server.containers.*;
import com.fortrow.jflux.http.api.server.encryption.APIKeys;
import com.fortrow.jflux.http.api.server.exceptions.APIException;
import com.fortrow.jflux.http.api.server.impl.APIMethods;
import com.fortrow.jflux.http.api.server.resources.APIResource;
import com.fortrow.jflux.http.api.server.types.APIServerType;
import com.fortrow.jflux.http.api.server.types.ContentType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public abstract class API implements APIMethods {

    private APIServer server;
    protected APIServerConfig serverConfig;
    protected ApiService service;
    private LogFile logFile;
    private final ArrayList<ServerRegistration> pendingRegistrations = new ArrayList<>();

    public API(APIServerConfig config, ApiService service) throws APIException {
        this.serverConfig = config;
        this.service = service;
    }

    public void start() throws APIException {

        if(server != null) {
            throw new APIException("API server has already been started.");
        }

        if(service != null) {
            service.configureDatabases(serverConfig);
        }

        server = new APIServer(serverConfig) {

            @Override
            public void newRequestForSession(ClientSession session) {
                if(service != null) service.newRequestForSession(session);
                writeServiceLog("New request for session '" + session.getSession().getSessionId() + "' from client at " + session.getClient().getClientInfo().getRemoteIp());
            }

            @Override
            public void newClientCreated(RemoteClient client) {
                writeServiceLog("New remote client at " + client.getClientInfo().getRemoteIp());
            }

            @Override
            public void newSessionForClient(RemoteClient client, ClientSession session) {
                if(service != null) service.newSessionForClient(client, session, serverConfig);
                writeServiceLog("New session for client at " + client.getClientInfo().getRemoteIp());
            }

            @Override
            public void sessionInvalidated(ClientSession session) {
                if(service != null) service.invalidateSession(session);
            }

            @Override
            public boolean isPastRateLimit(ClientSession clientSession, int perMinute) {
                return API.this.inTimeout(clientSession, perMinute);
            }

            @Override
            public boolean inBlacklist(ClientSession clientSession) {
                return API.this.inBlacklist(clientSession);
            }

            @Override
            public void addToBlacklist(ClientSession clientSession, ApiService.BlacklistEntryType entryType) {
                API.this.addToBlacklist(clientSession, entryType);
            }

            @Override
            public void removeFromBlacklist(ClientSession clientSession) {
                API.this.removeFromBlacklist(clientSession);
            }

            @Override
            public void addToTimeout(ClientSession clientSession, ApiService.BlacklistEntryType entryType) {
                API.this.addToTimeout(clientSession, entryType);
            }

            @Override
            public void removeFromTimeout(ClientSession clientSession) {
                API.this.removeFromTimeout(clientSession);
            }

            @Override
            public void lockApiKey(ClientSession clientSession, String reason) {
                API.this.lockApiKey(clientSession, reason);
            }

            @Override
            public void lockApiKey(APIKeys apiKeys, String reason) {
                API.this.lockApiKey(apiKeys, reason);
            }

            @Override
            public boolean isAuthenticated(RequestHeaders headers, APIResource resource, BodyParameters params, ClientSession clientSession) {
                return API.this.isAuthenticated(headers, resource, params, clientSession);
            }

            @Override
            public APIKeys lookupApiKeys(String apiKey) {
                return API.this.lookupApiKey(apiKey);
            }

            @Override
            public HashMap<String, String> getParameters(String body, ContentType contentType) {
                return API.this.getParameters(body, contentType);
            }

        };

        if(logFile != null) {
            server.setLogFile(logFile);
        }

        for(ServerRegistration registration : pendingRegistrations) {
            registration.apply(server);
        }
        pendingRegistrations.clear();

    }

    public void stop() {
        if(server != null) {
            server.stop();
            server = null;
        }
    }

    private void writeServiceLog(String line) {
        if(service != null) {
            service.writeToLog(line);
        }
    }

    public APIKeys generateApiKeys() throws APIException {
        return generateApiKeys(null);
    }

    public APIKeys generateApiKeys(List<IpAddress> allowedIps) throws APIException {
        APIKeys.Keys keys = APIKeys.generateKeys();
        APIKeys apiKeys = new APIKeys(keys, allowedIps) {
            @Override
            public void updateSessionSecret(APIKeys keys, String sessionSecret) throws APIException {
                API.this.updateSessionSecret(keys, sessionSecret);
            }
        };
        if(service != null) service.registerApiKeys(apiKeys);
        return apiKeys;
    }

    public abstract void updateSessionSecret(APIKeys keys, String sessionSecret) throws APIException;

    public void setLogFile(LogFile file) {
        this.logFile = file;
        if(this.server != null) {
            this.server.setLogFile(file);
        }
    }

    public APIResource addResource(String value, String requestMethod, APIResource definition) {
        return addResource(definition, apiServer -> apiServer.addResource(value, requestMethod, definition));
    }

    public APIResource addResource(String value, String requestMethod, String[] parameters, APIResource definition) {
        return addResource(definition, apiServer -> apiServer.addResource(value, requestMethod, parameters, definition));
    }

    public APIResource addResource(String value, String requestMethod, ArrayList<String> parameters, APIResource definition) {
        return addResource(definition, apiServer -> apiServer.addResource(value, requestMethod, parameters, definition));
    }

    public APIResource addResource(String value, String requestMethod, boolean isAsync, ArrayList<String> parameters, APIResource definition) {
        if(serverConfig.getServerType() == APIServerType.ENCRYPTED_SYNC
                || serverConfig.getServerType() == APIServerType.REST_SYNC) {
            return addResource(definition, apiServer -> apiServer.addResource(value, requestMethod, parameters, definition));
        }

        return addResource(definition, apiServer -> apiServer.addResource(value, requestMethod, isAsync, parameters, definition));
    }

    public APIResource addResource(String value, String requestMethod, boolean isAsync, APIResource definition) {
        if(serverConfig.getServerType() == APIServerType.ENCRYPTED_SYNC
                || serverConfig.getServerType() == APIServerType.REST_SYNC) {
            return addResource(definition, apiServer -> apiServer.addResource(value, requestMethod, definition));
        }

        return addResource(definition, apiServer -> apiServer.addResource(value, requestMethod, isAsync, definition));
    }

    public APIResource addResource(String value, String requestMethod, boolean isAsync, String[] parameters, APIResource definition) {
        if(serverConfig.getServerType() == APIServerType.ENCRYPTED_SYNC
                || serverConfig.getServerType() == APIServerType.REST_SYNC) {
            return addResource(definition, apiServer -> apiServer.addResource(value, requestMethod, parameters, definition));
        }

        return addResource(definition, apiServer -> apiServer.addResource(value, requestMethod, isAsync, parameters, definition));
    }

    public HashMap<String, String> getParameters(String body, ContentType contentType) {
        return new HashMap<>();
    }

    public boolean inTimeout(ClientSession clientSession, int perMinute) {
        return service != null && service.inTimeout(clientSession, perMinute);
    }

    public boolean inBlacklist(ClientSession clientSession) {
        return service != null && service.inBlacklist(clientSession);
    }

    public APIKeys lookupApiKey(String apiKey) {
        return service == null ? null : service.lookupApiKeys(apiKey);
    }

    public void addToBlacklist(ClientSession clientSession, ApiService.BlacklistEntryType entryType) {
        if(service != null) {
            service.addToBlacklist(clientSession, entryType);
        }
    }

    public void removeFromBlacklist(ClientSession clientSession) {
        if(service != null) {
            service.removeFromBlacklist(clientSession);
        }
    }

    public void addToTimeout(ClientSession clientSession, ApiService.BlacklistEntryType entryType) {
        if(service != null) {
            service.addToTimeout(clientSession, entryType);
        }
    }

    public void removeFromTimeout(ClientSession clientSession) {
        if(service != null) {
            service.removeFromTimeout(clientSession);
        }
    }

    public void lockApiKey(ClientSession clientSession, String reason) {
        if(service != null) {
            service.lockApiKey(clientSession, reason);
        }
    }

    public void lockApiKey(APIKeys apiKeys, String reason) {
        if(service != null) {
            service.lockApiKey(apiKeys, reason);
        }
    }

    public boolean isAuthenticated(RequestHeaders headers, APIResource resource, BodyParameters params, ClientSession clientSession) {

        if(headers == null || clientSession == null || clientSession.getClient() == null || clientSession.getSession() == null) {
            return false;
        }

        if(serverConfig.getServerType() == APIServerType.REST_SYNC
                || serverConfig.getServerType() == APIServerType.REST_ASYNC) {
            return apiKeyMatches(headers, clientSession);
        }

        if(headers.contains("X-Api-Session-Id")) {

            String apiKey = headers.getString("X-Api-Key");
            String sessionId = headers.getString("X-Api-Session-Id");
            String keySetId = headers.getString("X-Api-Key-Set-Id");
            String sessionAuth = headers.getString("X-Api-Session-Authorization");

            boolean flag = false;

            if(!SecureCompare.equals(apiKey, clientSession.getClient().getApiKey())) {
                flag = true;
            }

            if(!SecureCompare.equals(sessionId, clientSession.getSession().getSessionId())) {
                flag = true;
            }

            if(!SecureCompare.equals(keySetId, clientSession.getSession().getKeySetId())) {
                flag = true;
            }

            if(!clientSession.getSession().checkDynamicSessionAuth(sessionAuth)) {
                flag = true;
            }

            if(flag) {
                lockApiKey(clientSession, "Invalid encrypted session authentication headers.");
                APIServer server = clientSession.getClient().getServer();
                server.invalidateSession(clientSession);
            }

            return !flag;

        } else {

            return apiKeyMatches(headers, clientSession);

        }

    }

    private boolean apiKeyMatches(RequestHeaders headers, ClientSession clientSession) {
        if(headers == null || clientSession == null || clientSession.getClient() == null) {
            return false;
        }
        return SecureCompare.equals(headers.getString("X-Api-Key"), clientSession.getClient().getApiKey());
    }

    public APIServer getServer() {
        return requireStarted();
    }

    public APIServerConfig getServerConfig() {
        return serverConfig;
    }

    public ArrayList<ClientSession> getClientSessions() {
        return requireStarted().getClientSessions();
    }

    private APIResource addResource(APIResource definition, ServerRegistration registration) {
        if(server == null) {
            pendingRegistrations.add(registration);
        } else {
            registration.apply(server);
        }
        return definition;
    }

    private APIServer requireStarted() {
        if(server == null) {
            throw new IllegalStateException("API server has not been started.");
        }
        return server;
    }

    private interface ServerRegistration {
        void apply(APIServer server);
    }

}
