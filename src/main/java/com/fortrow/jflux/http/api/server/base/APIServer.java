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
 * File created by Ryan Fitzgerald (ryan@fortrow.com) on 2026-05-10 22:53:45 EDT
 * 
 * -------------------------------------------------------------------------

 */
package com.fortrow.jflux.http.api.server.base;

import com.fortrow.jflux.crypto.builtin.SecureCompare;
import com.fortrow.jflux.exceptions.ExceptionHandling;
import com.fortrow.jflux.encoding.BaseX;
import com.fortrow.jflux.files.LogFile;
import com.fortrow.jflux.http.api.base.endpoints.InboundRequest;
import com.fortrow.jflux.http.api.base.prereqs.ApiService;
import com.fortrow.jflux.http.api.server.config.APIServerConfig;
import com.fortrow.jflux.http.api.server.containers.*;
import com.fortrow.jflux.http.api.server.encryption.APIKeys;
import com.fortrow.jflux.http.api.server.exceptions.APIException;
import com.fortrow.jflux.http.api.server.impl.APIEvents;
import com.fortrow.jflux.http.api.server.impl.ClientFilter;
import com.fortrow.jflux.http.api.server.resources.APIResource;
import com.fortrow.jflux.http.api.server.resources.APIResources;
import com.fortrow.jflux.http.api.server.session.SessionAllocationAssertion;
import com.fortrow.jflux.http.api.server.session.SessionAllocationManager;
import com.fortrow.jflux.http.api.server.session.SessionCapacity;
import com.fortrow.jflux.http.api.server.session.SessionResumeTokenStore;
import com.fortrow.jflux.http.api.server.types.APIServerType;
import com.fortrow.jflux.http.api.server.types.ContentType;
import com.fortrow.jflux.http.api.types.EncryptedRequestType;
import com.fortrow.jflux.output.json.JSON;
import com.fortrow.jflux.threading.ThreadExecutor;
import com.fortrow.jflux.todash.__;
import com.sun.net.httpserver.*;
import org.json.simple.JSONObject;
import tech.kwik.core.log.SysOutLogger;
import tech.kwik.flupke.server.HttpRequestHandler;
import tech.kwik.flupke.server.HttpServerRequest;
import tech.kwik.flupke.server.HttpServerResponse;
import tech.kwik.core.QuicConnection;
import tech.kwik.core.server.ServerConnectionConfig;
import tech.kwik.core.server.ServerConnector;
import tech.kwik.flupke.server.Http3ApplicationProtocolFactory;

import javax.net.ssl.*;
import java.io.*;
import java.net.InetAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.net.http.HttpHeaders;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static com.fortrow.jflux.http.api.server.base.APIServer.RequestParser.queryToMap;
import static tech.kwik.flupke.server.Http3ApplicationProtocolFactory.HTTP3_PROTOCOL_ID;

public abstract class APIServer implements ClientFilter, APIEvents {

    private final APIServerConfig config;
    private APIResources resources = new APIResources();
    private final ConcurrentHashMap<String, AsyncRequest> asyncResponses = new ConcurrentHashMap<>();
    private final ThreadExecutor requestExecutor;
    private final ThreadExecutor asyncExecutor;
    private final ArrayList<RemoteClient> clients = new ArrayList<RemoteClient>();
    private final ArrayList<ClientSession> clientSessions = new ArrayList<ClientSession>();
    private final ArrayList<APISession> pastSessions = new ArrayList<APISession>();
    private final SessionCapacity sessionCapacity;
    private final SessionAllocationManager sessionAllocationManager = new SessionAllocationManager();
    private final SessionResumeTokenStore resumeTokenStore;
    private final boolean isPublicApi;
    private LogFile log;
    private HttpServer httpServer;
    private HttpsServer httpsServer;
    private HttpServer upgradeServer;
    private HttpsServer httpsUpgradeServer;
    private ServerConnector http3ServerConnector;

    public APIServer(APIServerConfig config) throws APIException {

        config.validateOrThrow();

        this.config = config;
        this.requestExecutor = new ThreadExecutor(config.getRequestThreads());
        this.asyncExecutor = new ThreadExecutor(config.getAsyncThreads());
        this.isPublicApi = this.config.isPublicApi();
        this.log = this.config.getLog();
        this.resumeTokenStore = new SessionResumeTokenStore(config.getSessionResumeStorePath());
        this.sessionCapacity = new SessionCapacity(config.getSessionResumeStorePath());

        if(config.getApiPath().isEmpty()) {
            throw new APIException("Server could not be started successfully. " +
                    "The API path is not set.");
        }

        if(config.getServerType() == APIServerType.ENCRYPTED_SYNC
                || config.getServerType() == APIServerType.ENCRYPTED_ASYNC
        ) {
            if(config.getEncryptedSessionPaths()[0].isEmpty()) {
                throw new APIException("Server could not be started successfully. " +
                        "The API path is not set.");
            }
            if(config.getEncryptedSessionPaths()[1].isEmpty()) {
                throw new APIException("Server could not be started successfully. " +
                        "The API path is not set.");
            }
        }

        if(config.isUseTls()) {
            if(config.isUseHttp1()) {
                startHttps1Server();
            } else if(config.isUseHttp3()) {
                startHttps3Server();
                startHttp3UpgradeServers();
            }
        } else {
            startHttp1Server();
        }

    }

    public APIResource addResource(String value, String requestMethod, APIResource definition) {
        definition.setRequestMethod(requestMethod);
        resources.addResource(value, definition);
        return definition;
    }

    public void stop() {
        if(httpServer != null) {
            httpServer.stop(1);
            httpServer = null;
        }
        if(httpsServer != null) {
            httpsServer.stop(1);
            httpsServer = null;
        }
        if(upgradeServer != null) {
            upgradeServer.stop(1);
            upgradeServer = null;
        }
        if(httpsUpgradeServer != null) {
            httpsUpgradeServer.stop(1);
            httpsUpgradeServer = null;
        }
        if(http3ServerConnector != null) {
            try {
                http3ServerConnector.close();
            } catch (Exception ignored) {
            }
            http3ServerConnector = null;
        }
        requestExecutor.close();
        asyncExecutor.close();
    }

    public APIResource addResource(String value, String requestMethod, String[] parameters, APIResource definition) {
        definition.setRequestMethod(requestMethod);
        resources.addResource(value, parameters, definition);
        return definition;
    }

    public APIResource addResource(String value, String requestMethod, ArrayList<String> parameters, APIResource definition) {
        definition.setRequestMethod(requestMethod);
        resources.addResource(value, parameters, definition);
        return definition;
    }

    public APIResource addResource(String value, String requestMethod, boolean isAsync, APIResource definition) {
        definition.setRequestMethod(requestMethod);
        resources.addResource(value, isAsync, definition);
        return definition;
    }

    public APIResource addResource(String value, String requestMethod, boolean isAsync, String[] parameters, APIResource definition) {
        definition.setRequestMethod(requestMethod);
        resources.addResource(value, isAsync, parameters, definition);
        return definition;
    }

    public APIResource addResource(String value, String requestMethod, boolean isAsync, ArrayList<String> parameters, APIResource definition) {
        definition.setRequestMethod(requestMethod);
        resources.addResource(value, isAsync, parameters, definition);
        return definition;
    }

    public void addParameterToResource(String resource, String parameter) {
        resources.getResource(resource).addParameter(parameter);
    }

    public void setApiResources(APIResources apiResources) {
        resources = apiResources;
    }

    public ArrayList<ClientSession> getClientSessions() {
        return clientSessions;
    }

    public APIResources getApiResources() {
        return resources;
    }

    public boolean isEncrypted() {
        return (config.getServerType() == APIServerType.ENCRYPTED_ASYNC
                || config.getServerType() == APIServerType.ENCRYPTED_SYNC
        );
    }

    /** Returns the current per-API-key session capacity. */
    public int getSessionLimit(String apiKey) {
        return sessionCapacity.limitFor(apiKey);
    }

    /**
     * Invalidates every live session and outstanding token for one root API
     * key, then restores that key's capacity to one session.
     */
    public synchronized void resetSessionsForApiKey(String apiKey) throws APIException {
        if (apiKey == null || apiKey.isBlank()) return;
        invalidateSessionsByApiKey(apiKey);
        clients.removeIf(client -> SecureCompare.equals(client.getApiKey(), apiKey));
        resumeTokenStore.invalidateForApiKey(apiKey);
        sessionAllocationManager.invalidateApiKey(apiKey);
        sessionCapacity.reset(apiKey);
    }

    private int activeSessionsForApiKey(String apiKey) {
        int count = 0;
        for (ClientSession session : clientSessions) {
            if (sessionMatchesApiKey(session, apiKey)) count++;
        }
        return count;
    }

    /** Returns unique live-session IPs for an already authenticated limit response. */
    private List<String> sessionIpAddressesForApiKey(String apiKey) {
        LinkedHashSet<String> addresses = new LinkedHashSet<>();
        for (ClientSession session : clientSessions) {
            if (!sessionMatchesApiKey(session, apiKey) || session.getClient() == null
                    || session.getClient().getClientInfo() == null) continue;
            String address = session.getClient().getClientInfo().getRemoteIp();
            if (address != null && !address.isBlank()) addresses.add(address);
        }
        return new ArrayList<>(addresses);
    }

    public void invalidateSession(ClientSession clientSession) {

        for(int i=clientSessions.size() - 1; i>=0; i--) {
            if(clientSessions.get(i).equals(clientSession)) {
                clientSession.getSession().deactivate();
                sessionInvalidated(clientSession);
                pastSessions.add(clientSession.getSession());
                clientSessions.remove(i);
            }
        }

    }

    private void lockSessionApiKey(ClientSession clientSession, String reason) {
        if(clientSession == null) return;
        // A stale ratchet, key-set id, or lost response belongs to one
        // encrypted session. Revoking the root API key here lets a transient
        // reconnect failure permanently destroy every session for that user.
        invalidateSession(clientSession);
        if(log != null) log.write("Invalidated the affected encrypted API session: "
                + (reason == null || reason.isBlank() ? "authentication failure" : reason));
    }

    private void lockApiKeyMaterial(APIKeys apiKeys, String reason) {
        if(apiKeys == null) return;
        // There is no verified session to revoke. Do not delete persistent
        // API-key material based only on an untrusted/stale session id.
        if(log != null) log.write("Rejected a request with an invalid encrypted session identifier.");
    }

    private void invalidateSessionsByApiKey(String apiKey) {
        for(int i=clientSessions.size() - 1; i>=0; i--) {
            ClientSession current = clientSessions.get(i);
            if(sessionMatchesApiKey(current, apiKey)) {
                current.getSession().deactivate();
                sessionInvalidated(current);
                pastSessions.add(current.getSession());
                clientSessions.remove(i);
            }
        }
    }

    private boolean sessionMatchesApiKey(ClientSession session, String apiKey) {
        if(session == null || apiKey == null) return false;
        String current = sessionApiKey(session);
        return SecureCompare.equals(apiKey, current);
    }

    private String sessionApiKey(ClientSession session) {
        if(session == null) return null;
        if(session.getSession() != null && session.getSession().getApiKeys() != null) {
            return session.getSession().getApiKeys().getKey();
        }
        if(session.getClient() != null) return session.getClient().getApiKey();
        return null;
    }
    public APIResponse execute(APIResource resource, InboundRequest request) {
        try {
            return resource.execute(request);
        } catch (Exception ex) {
            ExceptionHandling.report(ex);
            return new APIResponse(request.getSession(), ResponseCode.SERVER_ERROR);
        }
    }

    public APIResponse asyncExecute(APIResource resource, InboundRequest request) {

        try {

            APIResponse mainResponse = getRedirectResponse(request.getSession());
            AsyncRequest asyncRequest = mainResponse.getAsyncRequest();
            APIResourceExecution execution = new APIResourceExecution(resource, request, config.getAsyncTimeoutMs()) {
                @Override
                protected void onComplete(APIResponse response, Throwable failure) {
                    if(failure != null) {
                        log.write("Async resource '" + resource.getName() + "' failed: " + failure.getMessage());
                    }
                }
            };
            asyncRequest.setExecution(execution);
            addAsyncRequest(mainResponse);
            execution.start(asyncExecutor);
            return mainResponse;

        } catch (APIException ex) {
            log.write("Async resource setup failed: " + ex.getMessage());
            System.out.println(ex.getMessage());
        }

        return new APIResponse(request.getSession(), ResponseCode.SERVER_ERROR);

    }

    public APIResponse getRedirectResponse(ClientSession clientSession) throws APIException {
        return new APIResponse(clientSession, ResponseCode.REDIRECT_START);
    }

    public void addAsyncRequest(APIResponse response) {
        if(response == null || response.getAsyncRequest() == null) return;
        this.asyncResponses.put(response.getAsyncRequest().getRequestId(), response.getAsyncRequest());
    }

    public abstract APIKeys lookupApiKeys(String apiKey);

    public BodyParameters getParameters(String body, ContentType contentType, RequestHeaders headers, ClientSession clientSession, boolean isEncrypted) {
        return getParameters(body, contentType, headers, clientSession, isEncrypted, null);
    }

    public BodyParameters getParameters(String body, ContentType contentType, RequestHeaders headers, ClientSession clientSession, boolean isEncrypted, byte[] aad) {
        try {
            BodyParameters parameters = new BodyParameters(body, contentType, headers, clientSession, isEncrypted, aad);
            if(contentType == ContentType.TEXT) {
                parameters.putAll(getParameters(parameters.text(), contentType));
            }
            return parameters;
        } catch (SecurityException ex) {
            if(isEncrypted) lockSessionApiKey(clientSession, "Encrypted request payload authentication failed.");
            throw ex;
        }
    }

    public HashMap<String, String> getParameters(String body, ContentType contentType) {
        return new HashMap<>();
    }

    public BodyParameters getParameters(URI body, ContentType contentType, RequestHeaders headers, ClientSession clientSession, boolean isEncrypted) {
        return getParameters(body == null ? "" : body.toString(), contentType, headers, clientSession, isEncrypted, null);
    }

    public BodyParameters getParameters(URI body, ContentType contentType, RequestHeaders headers, ClientSession clientSession, boolean isEncrypted, byte[] aad) {
        return getParameters(body == null ? "" : body.toString(), contentType, headers, clientSession, isEncrypted, aad);
    }

    private HashMap<String, String> formEncoded(String body, ContentType type, RequestHeaders headers, ClientSession clientSession, boolean isEncrypted) {
        if(type == ContentType.URL_ENCODED && !isEncrypted) {
            return queryToMap(decodeFormData(body));
        } else if(type == ContentType.URL_ENCODED) {
            HashMap<String, String> encryptedMap = queryToMap(decodeFormData(body));
        }
        return new HashMap<>();
    }

    private static String decodeFormData(String body) {
        try {
            String prevUrl = "";
            String decodeUrl = body;
            while(!prevUrl.equals(decodeUrl)) {
                prevUrl = decodeUrl;
                decodeUrl = URLDecoder.decode(decodeUrl, StandardCharsets.UTF_8);
            }
            return decodeUrl;
        } catch (Exception ignored) { return null; }
    }

    private void startHttp3UpgradeServers() throws APIException {
        if(config.getUpgradePort() > 0) startUpgradeServer();
        if(config.getUpgradePortHttps() > 0) startHttpsUpgradeServer();
    }

    private void startUpgradeServer() throws APIException {
        try {
            upgradeServer = HttpServer.create(config.getUpgradeBindAddress(), 0);
            upgradeServer.createContext(config.getApiPath(), new APIUpgradeHandler());
            upgradeServer.setExecutor(requestExecutor);
            upgradeServer.start();
        } catch (IOException e) {
            throw new APIException(e.getMessage());
        }
    }

    private void startHttpsUpgradeServer() throws APIException {
        try {
            httpsUpgradeServer = HttpsServer.create(config.getHttpsUpgradeBindAddress(), 0);
            configureHttpsServer(httpsUpgradeServer);
            httpsUpgradeServer.createContext(config.getApiPath(), new APIUpgradeHandler());
            httpsUpgradeServer.setExecutor(requestExecutor);
            httpsUpgradeServer.start();
        } catch (IOException e) {
            throw new APIException(e.getMessage());
        }
    }

    private void startHttps3Server() throws APIException {

        ServerConnectionConfig serverConnectionConfig = ServerConnectionConfig.builder()
                .maxIdleTimeoutInSeconds(30)
                .maxUnidirectionalStreamBufferSize(1_000_000)
                .maxBidirectionalStreamBufferSize(1_000_000)
                .maxConnectionBufferSize(10_000_000)
                .maxOpenPeerInitiatedUnidirectionalStreams(10)
                .maxOpenPeerInitiatedBidirectionalStreams(100)
                .retryRequired(true)
                .connectionIdLength(8)
                .build();

        ServerConnector serverConnector = null;
        try {
            SysOutLogger log = new SysOutLogger();
            ServerConnector.Builder connectorBuilder = ServerConnector.builder()
                    .withPort(this.config.getApiPort())
                    .withKeyStore(config.getKeystore().getNative(), "key-alias", "password".toCharArray())
                    .withSupportedVersions(List.of(QuicConnection.QuicVersion.V1))
                    .withConfiguration(serverConnectionConfig)
                    .withLogger(log);
            if (config.isNeedClientAuth() || config.isWantClientAuth()) {
                connectorBuilder.withClientAuth(http3ClientTrustManager(), config.isNeedClientAuth());
            }
            serverConnector = connectorBuilder.build();
        } catch (Exception e) {
                ExceptionHandling.report(e);
            throw new APIException(e.getMessage());
        }

        HttpRequestHandler handler = new APIServerHandler();
        Http3ApplicationProtocolFactory http3ApplicationProtocolFactory = new Http3ApplicationProtocolFactory(handler);
        serverConnector.registerApplicationProtocol(HTTP3_PROTOCOL_ID, http3ApplicationProtocolFactory);
        serverConnector.start();
        http3ServerConnector = serverConnector;

    }

    private X509TrustManager http3ClientTrustManager() throws GeneralSecurityException {
        TrustManagerFactory factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        factory.init(config.getClientTrustStore());
        for (TrustManager trustManager : factory.getTrustManagers()) {
            if (trustManager instanceof X509TrustManager x509TrustManager) return x509TrustManager;
        }
        throw new GeneralSecurityException("HTTP/3 client trust store did not provide an X509 trust manager.");
    }

    private class APIUpgradeHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String requestURI = exchange.getRequestURI().toString();
            String redirectUrl = http3RedirectUrl(exchange, requestURI);

            exchange.getResponseHeaders().set("Alt-Svc", http3AltSvcHeader());
            exchange.getResponseHeaders().set("Location", redirectUrl);
            exchange.getResponseHeaders().set("Cache-Control", "no-store");
            exchange.sendResponseHeaders(308, -1);
            exchange.close();
        }
    }

    private String http3RedirectUrl(HttpExchange exchange, String requestURI) {
        String host = hostWithoutPort(exchange.getRequestHeaders().getFirst("Host"));
        if(host == null || host.isBlank()) host = config.getBindHost();
        if(host == null || host.isBlank()) host = "localhost";
        return "https://" + authority(host, config.getApiPort(), "https") + requestURI;
    }

    private String http3AltSvcHeader() {
        return "h3=\":" + config.getApiPort() + "\"; ma=86400";
    }

    private static String authority(String host, int port, String scheme) {
        boolean defaultPort = ("https".equalsIgnoreCase(scheme) && port == 443)
                || ("http".equalsIgnoreCase(scheme) && port == 80);
        return defaultPort ? host : host + ":" + port;
    }

    private static String hostWithoutPort(String hostHeader) {
        if(hostHeader == null || hostHeader.isBlank()) return hostHeader;
        String host = hostHeader.trim();
        if(host.startsWith("[")) {
            int closeBracket = host.indexOf(']');
            return closeBracket >= 0 ? host.substring(0, closeBracket + 1) : host;
        }
        int colon = host.indexOf(':');
        return colon >= 0 ? host.substring(0, colon) : host;
    }

    private void startHttp1Server() throws APIException {
        try {
            httpServer = HttpServer.create(config.getBindAddress(), 0);
            httpServer.createContext(config.getApiPath(), new APIServerHandler());
            httpServer.setExecutor(requestExecutor);
            httpServer.start();
        } catch (IOException e) {
            throw new APIException(e.getMessage());
        }
    }

    private void startHttps1Server() {
        try {
            httpsServer = HttpsServer.create(config.getBindAddress(), 0);
            configureHttpsServer(httpsServer);
            httpsServer.createContext(config.getApiPath(), new APIServerHandler());
            httpsServer.setExecutor(requestExecutor);
            httpsServer.start();
        } catch (IOException e)  {
                ExceptionHandling.report(e);
        }
    }

    private void configureHttpsServer(HttpsServer server) {
        assert __.isset(config.getApiServerSslContext());
        SSLContext context = config.getApiServerSslContext();
        server.setHttpsConfigurator(new HttpsConfigurator(context) {
            public void configure(HttpsParameters params) {
                try {
                    SSLEngine engine = this.getSSLContext().createSSLEngine();
                    params.setCipherSuites(engine.getEnabledCipherSuites());
                    params.setProtocols(engine.getEnabledProtocols());

                    SSLParameters defaultSSLParameters = this.getSSLContext().getDefaultSSLParameters();
                    params.setSSLParameters(defaultSSLParameters);
                    params.setWantClientAuth(config.isWantClientAuth());
                    params.setNeedClientAuth(config.isNeedClientAuth());
                } catch (Exception ex) {
                    ExceptionHandling.report(ex);
                }
            }
        });
    }

    public ArrayList<RemoteClient> getClients() {
        return clients;
    }

    public void setLogFile(LogFile file) {
        this.log = file;
    }

    public APIServerConfig getConfig() {
        return config;
    }

    private boolean isAsyncResponseResource(String resource) {
        String responsePath = config.getResponsePath();
        if(responsePath == null || responsePath.isBlank()) return false;
        return resourceFromPath(responsePath).equals(resource);
    }

    private String resourceFromPath(String path) {
        if (path == null || path.isBlank()) return "";
        String normalized = path.trim();
        String apiPath = config.getApiPath();
        if (apiPath != null && normalized.startsWith(apiPath)) normalized = normalized.substring(apiPath.length());
        while (normalized.startsWith("/")) normalized = normalized.substring(1);
        while (normalized.endsWith("/") && !normalized.isEmpty()) normalized = normalized.substring(0, normalized.length() - 1);
        return normalized;
    }

    static class RequestParser {

        public static boolean isEmpty(String body) {
            return body == null || body.isBlank();
        }

        public static boolean verifyJson(String body) {

            return !JSON.verify(body).equals("false");

        }

        public static JSONObject parseJson(String body, boolean wasVerified) {

            if(wasVerified) {

                String res = JSON.verify(body);

                if(res.equals("false")) {
                    return new JSONObject();
                } else {
                    return JSON.getObject(res);
                }

            } else {

                return new JSONObject();

            }

        }

        public static HashMap<String, String> queryToMap(String query) {
            if(query == null) {
                return null;
            }
            HashMap<String, String> result = new HashMap<>();
            for (String param : query.split("&")) {
                String[] entry = param.split("=");
                if (entry.length > 1) {
                    result.put(entry[0], entry[1]);
                }else{
                    result.put(entry[0], "");
                }
            }
            return result;
        }

        public static boolean verifyResource(APIResources resources, String[] sessionPaths, String responsePath, String resource) {

            if(responsePath != null && !responsePath.isBlank()) {
                if(responsePath.startsWith("/")) responsePath = responsePath.substring(1);
                if(responsePath.endsWith("/")) responsePath = responsePath.substring(0, responsePath.length() - 1);
                if(responsePath.equals(resource)) return true;
            }
            return resources.inList(resource) || Arrays.asList(sessionPaths).contains(resource);

        }

        public static boolean verifyResource(APIResources resources, String[] sessionPaths, String resource) {
            return verifyResource(resources, sessionPaths, null, resource);
        }

    }

    private boolean isBefore(Instant input, int secondsFromNow) {
        if(input == null) return false;
        Instant instant = Instant.now().plusSeconds(secondsFromNow);
        return input.isBefore(instant);
    }

    private boolean isAfter(Instant input, int secondsFromNow) {
        if(input == null) return false;
        Instant instant = Instant.now().minusSeconds(secondsFromNow);
        return input.isAfter(instant);
    }

    private class APIServerHandler implements HttpHandler, HttpRequestHandler {

        private final class RequestBodyTooLargeException extends APIException {
            private RequestBodyTooLargeException() {
                super("Request body exceeds the configured maximum size.");
            }
        }


        @Override
        public void handleRequest(HttpServerRequest t, HttpServerResponse r) throws IOException {

            try {

                final Headers headersObj = convertHeaders(t.headers());
                final RequestHeaders headers = new RequestHeaders(headersObj);
                final String inboundRequestMethod = t.method();

                InetAddress inetAddress = t.clientAddress();
                final String hostname = inetAddress.getHostName();
                final String ipAddr = inetAddress.getHostAddress();

                final String resource = resourceFromPath(t.path());

                if (!RequestParser.verifyResource(getApiResources(), isEncrypted() ? config.getEncryptedSessionPaths() : new String[]{}, config.getResponsePath(), resource)) {
                    log.write("Invalid resource '" + resource + "' requested.");
                    failRequest(r, "Invalid resource.");
                    return;
                }

                boolean isSessionResource = Arrays.asList(config.getEncryptedSessionPaths()).contains(resource);
                boolean isResponseResource = isAsyncResponseResource(resource);
                String requestMethod = isResponseResource ? "POST" : (isSessionResource ? getSessionRequestMethod(resource) : (isEncrypted() ? "POST" : inboundRequestMethod));

                if (inboundRequestMethod.equals("OPTIONS")) {
                    if (config.getCorsPolicy().isPermitted()) {
                        sendResponse(r, 200, "", ContentType.TEXT);
                        return;
                    } else {
                        log.write("Request blocked by CORS policy.");
                        unauthorized(r);
                        return;
                    }
                }

                if (!inboundRequestMethod.equals(requestMethod)) {
                    log.write("Invalid '" + requestMethod + "' request method used.");
                    failRequest(r, "Invalid request method.");
                    return;
                }

                if (!checkRequestHeaders(headers, resource)) {
                    log.write("Invalid headers in request.");
                    unauthorized(r);
                    return;
                }

                Instant requestTimestamp = headers.getInstantFromTimestamp("X-Request-Timestamp");
                if(isEncrypted() && (!isAfter(requestTimestamp, 30) || !isBefore(requestTimestamp, 30))) {
                    unauthorized(r);
                    log.write("Invalid timestamps in request headers.");
                    return;
                }

                String resourceMethod = resolveResourceMethod(headers, inboundRequestMethod, isSessionResource, isResponseResource);
                APIResource resolvedResource = resolveApiResource(resource, resourceMethod, isSessionResource, isResponseResource);
                if(!isSessionResource && !isResponseResource && resolvedResource == null) {
                    log.write("Invalid resource method '" + resourceMethod + "' for resource '" + resource + "'.");
                    failRequest(r, "Invalid resource method.");
                    return;
                }

                APIKeys apiKeys = null;

                if (!isPublicApi && !headers.contains("X-Api-Key") && !isSessionBootstrapResource(resource)) {
                    log.write("Missing 'X-Api-Key' header.");
                    unauthorized(r);
                    return;
                }

                final String reqBody;
                try {
                    reqBody = bodyFromStream(t.body());
                } catch (RequestBodyTooLargeException ex) {
                    log.write("Rejected request body larger than configured maximum.");
                    tooLarge(r);
                    return;
                }

                if (headers.contains("X-Api-Key")) {
                    apiKeys = lookupApiKeys(headers.getString("X-Api-Key"));
                }

                if (!isPublicApi && apiKeys == null) {
                    log.write("API Keys in request could not be validated.");
                    unauthorized(r);
                    return;
                }

                if(!isPublicApi && !apiKeys.ipIsAllowed(ipAddr)) {
                    log.write("Unallowed IP address in request for API key: '" + apiKeys.getKey() + "'.");
                    unauthorized(r);
                    return;
                }

                invalidateOldSessions();

                if (isSessionResetResource(resource) || isSessionAllocateResource(resource)) {
                    APIResponse controlResponse;
                    try {
                        controlResponse = isSessionResetResource(resource)
                                ? handleSessionReset(apiKeys, headers)
                                : handleSessionAllocate(apiKeys, headers, reqBody);
                    } catch (APIException ex) {
                        ExceptionHandling.report(ex);
                        serverError(r);
                        return;
                    }
                    sendControlResponse(r, controlResponse);
                    return;
                }

                boolean earlyResumeAuthorized = false;
                if (isSessionResumeResource(resource)) {
                    JSONObject resumeRequest = parseControlObject(reqBody);
                    String resumeToken = controlString(resumeRequest, "resume_token");
                    String resumedSessionId = resumeTokenStore.sessionForToken(apiKeys.getKey(), apiKeys.getSecret(), resumeToken);
                    earlyResumeAuthorized = resumedSessionId != null
                            && resumeTokenStore.isEarlyToken(apiKeys.getKey(), apiKeys.getSecret(), resumedSessionId, resumeToken);
                    if (!earlyResumeAuthorized && !rootSessionAuthorizationValid(headers, apiKeys)) {
                        unauthorized(r);
                        return;
                    }
                    SessionResumeTokenStore.ConsumeResult resumeResult;
                    try {
                        resumeResult = resumeTokenStore.consume(apiKeys.getKey(), apiKeys.getSecret(), controlString(resumeRequest, "resume_token"));
                    } catch (APIException ex) {
                        ExceptionHandling.report(ex);
                        serverError(r);
                        return;
                    }
                    if (resumeResult != SessionResumeTokenStore.ConsumeResult.VALID) {
                        JSONObject resumeFailure = new JSONObject();
                        resumeFailure.put("request_status", "unauthorized");
                        resumeFailure.put("message", "The session resume token is invalid, expired, or has already been used.");
                        sendResponse(r, 401, JSON.getString(resumeFailure), ContentType.JSON);
                        return;
                    }
                    if (resumedSessionId != null) {
                        for (ClientSession existing : new ArrayList<>(clientSessions)) {
                            if (sessionMatchesApiKey(existing, apiKeys.getKey())
                                    && resumedSessionId.equals(existing.getSession().getSessionId())) {
                                invalidateSession(existing);
                            }
                        }
                        invalidateResumeTokens(apiKeys.getKey(), resumedSessionId);
                    }
                }

                int clientIndex = (config.isPublicApi()) ? -1 : findClient(apiKeys);

                ClientSession clientSession = null;
                RemoteClient client = null;

                if (clientIndex < 0 && !isPublicApi) {

                    if(isEncrypted() && headers.contains("X-Api-Session-Id") && !isSessionInitOrResumeResource(resource)) {
                        log.write("Session not found for API key; client must reinitialize the encrypted session.");
                        unauthorized(r);
                        return;
                    }

                    try {

                        clientSession = appendNewClient(apiKeys, new ClientInfo(
                                headers, ipAddr, hostname
                        ));

                        client = clientSession.getClient();
                        clients.add(client);
                        clientSessions.add(clientSession);
                        newSessionForClient(client, clientSession);
                        apiKeys = clientSession.getSession().getApiKeys();


                        if (!verifyApiAuthorization(headers, apiKeys, clientSession, true)) {
                            // Reject only this newly-created session. A bad or
                            // stale reconnect root must not destroy the API
                            // credential needed for a recovery attempt.
                            invalidateSession(clientSession);
                            log.write("API Authorization failed to validate.");
                            unauthorized(r);
                            return;
                        }

                        if (!sessionAllowed(clientSession, apiKeys.getKey())) {
                            log.write("API Authorization failed to validate.");
                            invalidateSession(clientSession);
                            sendControlResponse(r, APIResponse.sessionLimitReached(null, sessionCapacity.limitFor(apiKeys.getKey()),
                                    activeSessionsForApiKey(apiKeys.getKey()), resumeTokenStore.outstandingForApiKey(apiKeys.getKey()),
                                    sessionIpAddressesForApiKey(apiKeys.getKey())));
                            return;
                        }

                    } catch (APIException e) {
                        log.write("Server Error: " + e.getMessage());
                ExceptionHandling.report(e);
                        serverError(r);
                        return;
                    } catch (Exception e) {
                        log.write("Server Error: " + e.getMessage());
                ExceptionHandling.report(e);
                        serverError(r);
                        return;
                    }

                } else if(!isPublicApi) {

                    client = clients.get(clientIndex);

                    if(isEncrypted() && isSessionInitOrResumeResource(resource)) {

                        try {
                            clientSession = appendNewSession(client, apiKeys, new ClientInfo(
                                    headers, ipAddr, hostname
                            ));
                        } catch (APIException e) {
                            log.write("Server Error: " + e.getMessage());
                ExceptionHandling.report(e);
                            serverError(r);
                            return;
                        }
                        clientSessions.add(clientSession);
                        newSessionForClient(client, clientSession);
                        apiKeys = clientSession.getSession().getApiKeys();

                        if (!verifyApiAuthorization(headers, apiKeys, clientSession, true)) {
                            // Reject only this newly-created session. A bad or
                            // stale reconnect root must not destroy the API
                            // credential needed for a recovery attempt.
                            invalidateSession(clientSession);
                            log.write("API Authorization failed to validate.");
                            unauthorized(r);
                            return;
                        }

                        if (!sessionAllowed(clientSession, apiKeys.getKey())) {
                            log.write("API Authorization failed to validate.");
                            invalidateSession(clientSession);
                            sendControlResponse(r, APIResponse.sessionLimitReached(null, sessionCapacity.limitFor(apiKeys.getKey()),
                                    activeSessionsForApiKey(apiKeys.getKey()), resumeTokenStore.outstandingForApiKey(apiKeys.getKey()),
                                    sessionIpAddressesForApiKey(apiKeys.getKey())));
                            return;
                        }

                    } else {

                        int clientSessionIndex = -1;

                        if (headers.contains("X-Api-Session-Id")) {

                            clientSessionIndex = findClientSession(client, headers.getString("X-Api-Session-Id"));

                        } else {

                            clientSessionIndex = findClientSession(client, apiKeys);

                        }

                        if (clientSessionIndex < 0) {
                            log.write("Invalid session information present in request.");
                            if(headers.contains("X-Api-Session-Id")) lockApiKeyMaterial(apiKeys, "Invalid session identifier.");
                            unauthorized(r);
                            return;
                        } else {
                            clientSession = clientSessions.get(clientSessionIndex);
                        }

                        apiKeys = clientSession.getSession().getApiKeys();

                        if (!verifyApiAuthorization(headers, apiKeys, clientSession, false)) {
                            log.write("API Authorization failed to validate.");
                            lockSessionApiKey(clientSession, "API authorization failed to validate.");
                            unauthorized(r);
                            return;
                        }

                        if (!sessionAllowed(clientSession, apiKeys.getKey())) {
                            log.write("API Authorization failed to validate.");
                            invalidateSession(clientSession);
                            unauthorized(r);
                            return;
                        }

                    }

                }

                if (!isPublicApi && clientSession.getClient().observeRequestMetadata(headers, hostname)) {
                    log.write("Client request metadata changed for API key '" + clientSession.getClient().getApiKey() + "'.");
                }

                if(!isPublicApi) clientSession.newRequest();
                if(!isPublicApi) newRequestForSession(clientSession);

                BodyParameters parameters = null;
                boolean requestIsEncrypted = isEncryptedTransfer(headers);
                byte[] requestAad = null;
                boolean isSessionInitResource = isSessionBootstrapResource(resource);

                if (isEncrypted() && !isJsonContentType(headers)) {
                    log.write("Encrypted requests require JSON Content-Type.");
                    unauthorized(r);
                    return;
                }

                if (isEncrypted() && !isSessionInitResource) {
                    if (!requestIsEncrypted) {
                        log.write("Encrypted request missing encrypted transfer encoding.");
                        unauthorized(r);
                        return;
                    }
                    requestAad = buildRequestAad(headers, inboundRequestMethod, t.path(), clientSession, resourceMethod);
                    if (requestAad == null || !verifyRequestSignature(headers, inboundRequestMethod, t.path(), reqBody, clientSession.getSession().getApiKeys(), clientSession, resourceMethod)) {
                        log.write("Request signature failed to validate.");
                        lockSessionApiKey(clientSession, "Request signature or sequence failed to validate.");
                        unauthorized(r);
                        return;
                    }
                    clientSession.getSession().setCurrentRequestAad(requestAad);
                }

                if (!isEncrypted() && requestMethod.equals("GET") && !isSessionInitResource) {
                    parameters = getParameters(t.path(), ContentType.GET_URL, headers, clientSession, requestIsEncrypted, requestAad);
                } else {
                    if (headers.contains("Content-Type")) {

                        switch (contentType(headers)) {
                            case JSON:
                                parameters = getParameters(reqBody, ContentType.JSON, headers, clientSession, requestIsEncrypted, requestAad);
                                break;
                            case URL_ENCODED:
                                parameters = getParameters(reqBody, ContentType.URL_ENCODED, headers, clientSession, requestIsEncrypted, requestAad);
                                break;
                            case FORM_DATA:
                                parameters = getParameters(reqBody, ContentType.FORM_DATA, headers, clientSession, requestIsEncrypted, requestAad);
                                break;
                            default:
                                parameters = getParameters(reqBody, ContentType.TEXT, headers, clientSession, requestIsEncrypted, requestAad);
                                break;
                        }

                    } else parameters = getParameters(reqBody, ContentType.TEXT, headers, clientSession, requestIsEncrypted, requestAad);

                }

                boolean shouldCheckParams = !isSessionResource && !isAsyncResponseResource(resource) && resolvedResource != null && !resolvedResource.getParameters().isEmpty();

                if (shouldCheckParams) {
                    if (parameters == null || !parameters.isValid()) {
                        failRequest(r, "Invalid parameters.");
                        return;
                    }
                    if (!parameters.checkAllPresent(resolvedResource.getParameters())) {
                        failRequest(r, "Invalid parameters.");
                        return;
                    }
                }

                if (!isPublicApi) {
                    if (client.inBlacklist()) {
                        authFailure(r);
                        return;
                    }
                }

                if (!isPublicApi) {
                    if(client.isLimited(config.getRequestsPerMinute())) {
                        addToTimeout(clientSession, ApiService.BlacklistEntryType.API_KEY);
                        rateLimited(r);
                        return;
                    }
                    removeFromTimeout(clientSession);
                }

                APIResponse response = null;
                APISession.State sessionStateBeforeResponse = isEncrypted() && clientSession != null && clientSession.getSession() != null
                        ? clientSession.getSession().snapshot()
                        : null;
                APIKeys.State keyStateBeforeResponse = isEncrypted() && clientSession != null && clientSession.getSession() != null && clientSession.getSession().getApiKeys() != null
                        ? clientSession.getSession().getApiKeys().snapshot()
                        : null;

                try {
                    response = processRequest(headers, clientSession, resource, resourceMethod, parameters, t.path(), earlyResumeAuthorized);
                } catch (Exception e) {
                ExceptionHandling.report(e);
                    log.write("Server Error: " + e.getMessage());
                    serverError(r);
                    return;
                }

                try {

                    ResponseCode responseCode = response.getResponseCode();
                    String responseBody = response.getResponse();
                    ContentType contentType = response.getContentType();

                    switch (responseCode) {

                        case REQUEST_FAILED:
                            failRequest(r, responseBody);
                            break;
                        case FORBIDDEN:
                            authFailure(r);
                            break;
                        case TOO_MANY_REQUESTS:
                            if(!sendResponse(r, 429, responseBody, contentType, clientSession)) rollbackEncryptedRequest(clientSession, sessionStateBeforeResponse, keyStateBeforeResponse);
                            break;
                        case SESSION_LIMIT_REACHED:
                            if(!sendResponse(r, 429, responseBody, contentType, clientSession)) rollbackEncryptedRequest(clientSession, sessionStateBeforeResponse, keyStateBeforeResponse);
                            break;
                        case REQUEST_TIMEOUT:
                            if(!sendResponse(r, 408, responseBody, contentType, clientSession)) rollbackEncryptedRequest(clientSession, sessionStateBeforeResponse, keyStateBeforeResponse);
                            break;
                        case REDIRECT_START:
                        case REDIRECT_CONTINUE:
                            if(!sendResponse(r, 200, responseBody, contentType, clientSession)) rollbackEncryptedRequest(clientSession, sessionStateBeforeResponse, keyStateBeforeResponse);
                            break;
                        case UNAUTHORIZED:
                            unauthorized(r);
                            break;
                        case SERVER_ERROR:
                            if(!sendServerError(r, responseBody, contentType, clientSession)) rollbackEncryptedRequest(clientSession, sessionStateBeforeResponse, keyStateBeforeResponse);
                            break;
                        case SUCCESSFUL:
                            if(!sendResponse(r, 200, responseBody, contentType, clientSession)) rollbackEncryptedRequest(clientSession, sessionStateBeforeResponse, keyStateBeforeResponse);
                            break;

                    }

                    if (responseCode == ResponseCode.SUCCESSFUL && isSessionCloseResource(resource)) {
                        invalidateSession(clientSession);
                    }

                } catch (Exception ex) {

                    log.write("Server Error: " + ex.getMessage());
                    ExceptionHandling.report(ex);
                    serverError(r);

                }

            } catch (Exception e) {
                log.write("Server Error: " + e.getMessage());
                ExceptionHandling.report(e);
                serverError(r);
            }

        }

        @Override
        public void handle(HttpExchange t) throws IOException {

            try {
                
                final Headers headersObj = t.getRequestHeaders();
                final RequestHeaders headers = new RequestHeaders(headersObj);
                final String inboundRequestMethod = t.getRequestMethod().toUpperCase();

                String remoteAddr = t.getRemoteAddress().toString();
                String[] ipParts = remoteAddr.split(":");
                String leftSide = ipParts[0];
                String[] leftParts = leftSide.split("/");
                final String hostname = leftParts[0];
                final String ipAddr = leftParts[1];

                final String resource = resourceFromPath(t.getRequestURI().getPath());

                if (!RequestParser.verifyResource(getApiResources(), isEncrypted() ? config.getEncryptedSessionPaths() : new String[]{}, config.getResponsePath(), resource)) {
                    log.write("Invalid resource '" + resource + "' requested.");
                    failRequest(t, "Invalid resource.");
                    return;
                }

                boolean isSessionResource = Arrays.asList(config.getEncryptedSessionPaths()).contains(resource);
                boolean isResponseResource = isAsyncResponseResource(resource);
                String requestMethod = isResponseResource ? "POST" : (isSessionResource ? getSessionRequestMethod(resource) : (isEncrypted() ? "POST" : inboundRequestMethod));

                if (inboundRequestMethod.equals("OPTIONS")) {
                    if (config.getCorsPolicy().isPermitted()) {
                        sendResponse(t, 200, "", ContentType.TEXT);
                        return;
                    } else {
                        log.write("Request blocked by CORS policy.");
                        unauthorized(t);
                        return;
                    }
                }

                if (!inboundRequestMethod.equals(requestMethod)) {
                    log.write("Invalid '" + requestMethod + "' request method used.");
                    failRequest(t, "Invalid request method.");
                    return;
                }

                if (!checkRequestHeaders(headers, resource)) {
                    log.write("Invalid headers in request.");
                    unauthorized(t);
                    return;
                }

                if(isEncrypted()
                        && (!isAfter(headers.getInstantFromTimestamp("X-Request-Timestamp"), 30) ||
                        !isBefore(headers.getInstantFromTimestamp("X-Request-Timestamp"), 30))
                ) {
                    unauthorized(t);
                    log.write("Invalid timestamps in request headers.");
                    return;
                }

                String resourceMethod = resolveResourceMethod(headers, inboundRequestMethod, isSessionResource, isResponseResource);
                APIResource resolvedResource = resolveApiResource(resource, resourceMethod, isSessionResource, isResponseResource);
                if(!isSessionResource && !isResponseResource && resolvedResource == null) {
                    log.write("Invalid resource method '" + resourceMethod + "' for resource '" + resource + "'.");
                    failRequest(t, "Invalid resource method.");
                    return;
                }

                APIKeys apiKeys = null;

                if ((!isPublicApi && !headers.contains("X-Api-Key")) && !isSessionBootstrapResource(resource)) {
                    log.write("Missing 'X-Api-Key' header.");
                    unauthorized(t);
                    return;
                }

                final String reqBody;
                try {
                    reqBody = bodyFromStream(t.getRequestBody());
                } catch (RequestBodyTooLargeException ex) {
                    log.write("Rejected request body larger than configured maximum.");
                    tooLarge(t);
                    return;
                }

                if (headers.contains("X-Api-Key")) {
                    apiKeys = lookupApiKeys(headers.getString("X-Api-Key"));
                }

                if (!isPublicApi && apiKeys == null) {
                    log.write("API Keys in request could not be validated.");
                    unauthorized(t);
                    return;
                }

                if(!isPublicApi && !apiKeys.ipIsAllowed(ipAddr)) {
                    log.write("Unallowed IP address in request for API key: '" + apiKeys.getKey() + "'.");
                    unauthorized(t);
                    return;
                }

                invalidateOldSessions();

                if (isSessionResetResource(resource) || isSessionAllocateResource(resource)) {
                    APIResponse controlResponse;
                    try {
                        controlResponse = isSessionResetResource(resource)
                                ? handleSessionReset(apiKeys, headers)
                                : handleSessionAllocate(apiKeys, headers, reqBody);
                    } catch (APIException ex) {
                        ExceptionHandling.report(ex);
                        serverError(t);
                        return;
                    }
                    sendControlResponse(t, controlResponse);
                    return;
                }

                boolean earlyResumeAuthorized = false;
                if (isSessionResumeResource(resource)) {
                    JSONObject resumeRequest = parseControlObject(reqBody);
                    String resumeToken = controlString(resumeRequest, "resume_token");
                    String resumedSessionId = resumeTokenStore.sessionForToken(apiKeys.getKey(), apiKeys.getSecret(), resumeToken);
                    earlyResumeAuthorized = resumedSessionId != null
                            && resumeTokenStore.isEarlyToken(apiKeys.getKey(), apiKeys.getSecret(), resumedSessionId, resumeToken);
                    if (!earlyResumeAuthorized && !rootSessionAuthorizationValid(headers, apiKeys)) {
                        unauthorized(t);
                        return;
                    }
                    SessionResumeTokenStore.ConsumeResult resumeResult;
                    try {
                        resumeResult = resumeTokenStore.consume(apiKeys.getKey(), apiKeys.getSecret(), controlString(resumeRequest, "resume_token"));
                    } catch (APIException ex) {
                        ExceptionHandling.report(ex);
                        serverError(t);
                        return;
                    }
                    if (resumeResult != SessionResumeTokenStore.ConsumeResult.VALID) {
                        JSONObject resumeFailure = new JSONObject();
                        resumeFailure.put("request_status", "unauthorized");
                        resumeFailure.put("message", "The session resume token is invalid, expired, or has already been used.");
                        sendResponse(t, 401, JSON.getString(resumeFailure), ContentType.JSON);
                        return;
                    }
                    if (resumedSessionId != null) {
                        for (ClientSession existing : new ArrayList<>(clientSessions)) {
                            if (sessionMatchesApiKey(existing, apiKeys.getKey())
                                    && resumedSessionId.equals(existing.getSession().getSessionId())) {
                                invalidateSession(existing);
                            }
                        }
                        invalidateResumeTokens(apiKeys.getKey(), resumedSessionId);
                    }
                }

                int clientIndex = (config.isPublicApi()) ? -1 : findClient(apiKeys);

                ClientSession clientSession = null;
                RemoteClient client = null;

                if (clientIndex < 0 && !isPublicApi) {

                    if(isEncrypted() && headers.contains("X-Api-Session-Id") && !isSessionInitOrResumeResource(resource)) {
                        log.write("Session not found for API key; client must reinitialize the encrypted session.");
                        unauthorized(t);
                        return;
                    }

                    try {

                        clientSession = appendNewClient(apiKeys, new ClientInfo(
                                headers, ipAddr, hostname
                        ));

                        client = clientSession.getClient();
                        clients.add(client);
                        clientSessions.add(clientSession);
                        newSessionForClient(client, clientSession);
                        apiKeys = clientSession.getSession().getApiKeys();


                        if (!verifyApiAuthorization(headers, apiKeys, clientSession, true)) {
                            // Reject only this newly-created session. A bad or
                            // stale reconnect root must not destroy the API
                            // credential needed for a recovery attempt.
                            invalidateSession(clientSession);
                            log.write("API Authorization failed to validate.");
                            unauthorized(t);
                            return;
                        }

                        if (!sessionAllowed(clientSession, apiKeys.getKey())) {
                            log.write("API Authorization failed to validate.");
                            invalidateSession(clientSession);
                            sendControlResponse(t, APIResponse.sessionLimitReached(null, sessionCapacity.limitFor(apiKeys.getKey()),
                                    activeSessionsForApiKey(apiKeys.getKey()), resumeTokenStore.outstandingForApiKey(apiKeys.getKey()),
                                    sessionIpAddressesForApiKey(apiKeys.getKey())));
                            return;
                        }

                    } catch (APIException e) {
                        log.write("Server Error: " + e.getMessage());
                ExceptionHandling.report(e);
                        serverError(t);
                        return;
                    } catch (Exception e) {
                        log.write("Server Error: " + e.getMessage());
                ExceptionHandling.report(e);
                        serverError(t);
                        return;
                    }

                } else if(!isPublicApi) {

                    client = clients.get(clientIndex);

                    if(isEncrypted() && isSessionInitOrResumeResource(resource)) {

                        try {
                            clientSession = appendNewSession(client, apiKeys, new ClientInfo(
                                    headers, ipAddr, hostname
                            ));
                        } catch (APIException e) {
                            log.write("Server Error: " + e.getMessage());
            ExceptionHandling.report(e);
                            serverError(t);
                            return;
                        }
                        clientSessions.add(clientSession);
                        newSessionForClient(client, clientSession);
                        apiKeys = clientSession.getSession().getApiKeys();

                        if (!verifyApiAuthorization(headers, apiKeys, clientSession, true)) {
                            // Reject only this newly-created session. A bad or
                            // stale reconnect root must not destroy the API
                            // credential needed for a recovery attempt.
                            invalidateSession(clientSession);
                            log.write("API Authorization failed to validate.");
                            unauthorized(t);
                            return;
                        }

                        if (!sessionAllowed(clientSession, apiKeys.getKey())) {
                            log.write("API Authorization failed to validate.");
                            invalidateSession(clientSession);
                            sendControlResponse(t, APIResponse.sessionLimitReached(null, sessionCapacity.limitFor(apiKeys.getKey()),
                                    activeSessionsForApiKey(apiKeys.getKey()), resumeTokenStore.outstandingForApiKey(apiKeys.getKey()),
                                    sessionIpAddressesForApiKey(apiKeys.getKey())));
                            return;
                        }

                    } else {

                        int clientSessionIndex = -1;

                        if (headers.contains("X-Api-Session-Id")) {

                            clientSessionIndex = findClientSession(client, headers.getString("X-Api-Session-Id"));

                        } else {

                            clientSessionIndex = findClientSession(client, apiKeys);

                        }

                        if (clientSessionIndex < 0) {
                            log.write("Invalid session information present in request.");
                            if(headers.contains("X-Api-Session-Id")) lockApiKeyMaterial(apiKeys, "Invalid session identifier.");
                            unauthorized(t);
                            return;
                        } else {
                            clientSession = clientSessions.get(clientSessionIndex);
                        }

                        apiKeys = clientSession.getSession().getApiKeys();

                        if (!verifyApiAuthorization(headers, apiKeys, clientSession, false)) {
                            log.write("API Authorization failed to validate.");
                            lockSessionApiKey(clientSession, "API authorization failed to validate.");
                            unauthorized(t);
                            return;
                        }

                        if (!sessionAllowed(clientSession, apiKeys.getKey())) {
                            log.write("API Authorization failed to validate.");
                            invalidateSession(clientSession);
                            unauthorized(t);
                            return;
                        }

                    }

                }

                if (!isPublicApi && clientSession.getClient().observeRequestMetadata(headers, hostname)) {
                    log.write("Client request metadata changed for API key '" + clientSession.getClient().getApiKey() + "'.");
                }

                if(!isPublicApi) clientSession.newRequest();
                if(!isPublicApi) newRequestForSession(clientSession);

                BodyParameters parameters = null;
                boolean requestIsEncrypted = isEncryptedTransfer(headers);
                byte[] requestAad = null;
                boolean isSessionInitResource = isSessionBootstrapResource(resource);

                if (isEncrypted() && !isJsonContentType(headers)) {
                    log.write("Encrypted requests require JSON Content-Type.");
                    unauthorized(t);
                    return;
                }

                if (isEncrypted() && !isSessionInitResource) {
                    if (!requestIsEncrypted) {
                        log.write("Encrypted request missing encrypted transfer encoding.");
                        unauthorized(t);
                        return;
                    }
                    requestAad = buildRequestAad(headers, inboundRequestMethod, t.getRequestURI().getPath(), clientSession, resourceMethod);
                    if (requestAad == null || !verifyRequestSignature(headers, inboundRequestMethod, t.getRequestURI().getPath(), reqBody, clientSession.getSession().getApiKeys(), clientSession, resourceMethod)) {
                        log.write("Request signature failed to validate.");
                        lockSessionApiKey(clientSession, "Request signature or sequence failed to validate.");
                        unauthorized(t);
                        return;
                    }
                    clientSession.getSession().setCurrentRequestAad(requestAad);
                }

                if (!isEncrypted() && requestMethod.equals("GET") && !isSessionInitResource) {
                    parameters = getParameters(t.getRequestURI(), ContentType.GET_URL, headers, clientSession, requestIsEncrypted, requestAad);
                } else {
                    if (headers.contains("Content-Type")) {

                        switch (contentType(headers)) {
                            case JSON:
                                parameters = getParameters(reqBody, ContentType.JSON, headers, clientSession, requestIsEncrypted, requestAad);
                                break;
                            case URL_ENCODED:
                                parameters = getParameters(reqBody, ContentType.URL_ENCODED, headers, clientSession, requestIsEncrypted, requestAad);
                                break;
                            case FORM_DATA:
                                parameters = getParameters(reqBody, ContentType.FORM_DATA, headers, clientSession, requestIsEncrypted, requestAad);
                                break;
                            default:
                                parameters = getParameters(reqBody, ContentType.TEXT, headers, clientSession, requestIsEncrypted, requestAad);
                                break;
                        }

                    } else parameters = getParameters(reqBody, ContentType.TEXT, headers, clientSession, requestIsEncrypted, requestAad);

                }

                boolean shouldCheckParams = !isSessionResource && !isAsyncResponseResource(resource) && resolvedResource != null && !resolvedResource.getParameters().isEmpty();

                if (shouldCheckParams) {
                    if (parameters == null || !parameters.isValid()) {
                        failRequest(t, "Invalid parameters.");
                        return;
                    }
                    if (!parameters.checkAllPresent(resolvedResource.getParameters())) {
                        failRequest(t, "Invalid parameters.");
                        return;
                    }
                }

                if (!isPublicApi) {
                    if (client.inBlacklist()) {
                        authFailure(t);
                        return;
                    }
                }

                if (!isPublicApi) {
                    if(client.isLimited(config.getRequestsPerMinute())) {
                        addToTimeout(clientSession, ApiService.BlacklistEntryType.API_KEY);
                        rateLimited(t);
                        return;
                    }
                    removeFromTimeout(clientSession);
                }

                APIResponse response = null;
                APISession.State sessionStateBeforeResponse = isEncrypted() && clientSession != null && clientSession.getSession() != null
                        ? clientSession.getSession().snapshot()
                        : null;
                APIKeys.State keyStateBeforeResponse = isEncrypted() && clientSession != null && clientSession.getSession() != null && clientSession.getSession().getApiKeys() != null
                        ? clientSession.getSession().getApiKeys().snapshot()
                        : null;

                try {
                    response = processRequest(headers, clientSession, resource, resourceMethod, parameters, t.getRequestURI().getPath(), earlyResumeAuthorized);
                } catch (Exception e) {
            ExceptionHandling.report(e);
                    log.write("Server Error: " + e.getMessage());
                    serverError(t);
                    return;
                }

                try {

                    ResponseCode responseCode = response.getResponseCode();
                    String responseBody = response.getResponse();
                    ContentType contentType = response.getContentType();

                    switch (responseCode) {

                        case REQUEST_FAILED:
                            failRequest(t, responseBody);
                            break;
                        case FORBIDDEN:
                            authFailure(t);
                            break;
                        case TOO_MANY_REQUESTS:
                            if(!sendResponse(t, 429, responseBody, contentType, clientSession)) rollbackEncryptedRequest(clientSession, sessionStateBeforeResponse, keyStateBeforeResponse);
                            break;
                        case SESSION_LIMIT_REACHED:
                            if(!sendResponse(t, 429, responseBody, contentType, clientSession)) rollbackEncryptedRequest(clientSession, sessionStateBeforeResponse, keyStateBeforeResponse);
                            break;
                        case REQUEST_TIMEOUT:
                            if(!sendResponse(t, 408, responseBody, contentType, clientSession)) rollbackEncryptedRequest(clientSession, sessionStateBeforeResponse, keyStateBeforeResponse);
                            break;
                        case REDIRECT_START:
                        case REDIRECT_CONTINUE:
                            if(!sendResponse(t, 200, responseBody, contentType, clientSession)) rollbackEncryptedRequest(clientSession, sessionStateBeforeResponse, keyStateBeforeResponse);
                            break;
                        case UNAUTHORIZED:
                            unauthorized(t);
                            break;
                        case SERVER_ERROR:
                            if(!sendServerError(t, responseBody, contentType, clientSession)) rollbackEncryptedRequest(clientSession, sessionStateBeforeResponse, keyStateBeforeResponse);
                            break;
                        case SUCCESSFUL:
                            if(!sendResponse(t, 200, responseBody, contentType, clientSession)) rollbackEncryptedRequest(clientSession, sessionStateBeforeResponse, keyStateBeforeResponse);
                            break;

                    }

                    if (responseCode == ResponseCode.SUCCESSFUL && isSessionCloseResource(resource)) {
                        invalidateSession(clientSession);
                    }

                } catch (Exception ex) {

                    log.write("Server Error: " + ex.getMessage());
                    ExceptionHandling.report(ex);
                    serverError(t);

                }

            } catch (Exception e) {
                log.write("Server Error: " + e.getMessage());
                ExceptionHandling.report(e);
                serverError(t);
            }

        }

        private void invalidateOldSessions() {
            for(int i=clientSessions.size() - 1; i>=0; i--) {
                if(clientSessions.get(i).getSession().invalidateByTime()) {
                    clientSessions.get(i).getSession().deactivate();
                    sessionInvalidated(clientSessions.get(i));
                    pastSessions.add(clientSessions.get(i).getSession());
                    clientSessions.remove(i);
                }
            }
        }

        private Headers convertHeaders(HttpHeaders h) {
            Map<String, List<String>> headerMap = h.map();
            Headers headers = new Headers();
            for (Map.Entry<String, List<String>> entry : headerMap.entrySet()) {
                String key = entry.getKey();
                for (String value : entry.getValue()) {
                    headers.add(key, value);
                }
            }
            return headers;
        }


        private String resolveResourceMethod(RequestHeaders headers, String inboundRequestMethod, boolean isSessionResource, boolean isResponseResource) {
            if(!isEncrypted() || isSessionResource) return inboundRequestMethod == null ? "" : inboundRequestMethod.toUpperCase();
            if(!headers.contains("X-Api-Resource-Method")) return null;
            String method = headers.getString("X-Api-Resource-Method");
            if(method == null) return null;
            method = method.trim().toUpperCase();
            return isSupportedResourceMethod(method) ? method : null;
        }

        private APIResource resolveApiResource(String resource, String resourceMethod, boolean isSessionResource, boolean isResponseResource) {
            if(isSessionResource || isResponseResource) return null;
            if(resourceMethod == null || resourceMethod.isBlank()) return null;
            return resources.getResource(resource, resourceMethod);
        }

        private boolean isSupportedResourceMethod(String method) {
            return switch (method) {
                case "GET", "POST", "PUT", "PATCH", "DELETE", "HEAD", "OPTIONS", "TRACE" -> true;
                default -> false;
            };
        }

        private String getSessionRequestMethod(String resource) {
            String[] paths = config.getEncryptedSessionPaths();
            if(__.same(resource, paths[0])) return "GET";
            return "POST";
        }

        private boolean isSessionInitResource(String resource) {
            return isEncrypted() && __.same(resource, config.getEncryptedSessionPaths()[0]);
        }

        private boolean isSessionResumeResource(String resource) {
            return isEncrypted() && __.same(resource, config.getEncryptedSessionPaths()[2]);
        }

        private boolean isSessionResetResource(String resource) {
            return isEncrypted() && __.same(resource, config.getEncryptedSessionPaths()[3]);
        }

        private boolean isSessionAllocateResource(String resource) {
            return isEncrypted() && __.same(resource, config.getEncryptedSessionPaths()[4]);
        }

        private boolean isSessionHeartbeatResource(String resource) {
            return isEncrypted() && __.same(resource, config.getEncryptedSessionPaths()[5]);
        }

        private boolean isSessionCloseResource(String resource) {
            return isEncrypted() && __.same(resource, config.getEncryptedSessionPaths()[1]);
        }

        private boolean isSessionBootstrapResource(String resource) {
            return isSessionInitResource(resource) || isSessionResumeResource(resource)
                    || isSessionResetResource(resource) || isSessionAllocateResource(resource);
        }

        private boolean isSessionInitOrResumeResource(String resource) {
            return isSessionInitResource(resource) || isSessionResumeResource(resource);
        }

        private boolean rootSessionAuthorizationValid(RequestHeaders headers, APIKeys apiKeys) {
            if (headers == null || apiKeys == null) return false;
            return SecureCompare.equals(apiKeys.getSessionSecret(), headers.getString("X-Api-Session-Authorization"));
        }

        private JSONObject parseControlObject(String body) {
            if (body == null || body.isBlank()) return new JSONObject();
            JSONObject object = JSON.getObject(body);
            return object == null ? new JSONObject() : object;
        }

        private String controlString(JSONObject object, String key) {
            Object value = object == null ? null : object.get(key);
            return value == null ? "" : value.toString().trim();
        }

        private APIResponse handleSessionReset(APIKeys apiKeys, RequestHeaders headers) throws APIException {
            if (!rootSessionAuthorizationValid(headers, apiKeys)
                    && !resetSessionAuthorizationValid(headers, apiKeys)) {
                JSONObject response = new JSONObject();
                response.put("request_status", "unauthorized");
                response.put("error_code", "JFLUX_SESSION_RESET_UNAUTHORIZED");
                response.put("message", "The session reset authorization was invalid or expired.");
                return new APIResponse(null, response, ResponseCode.UNAUTHORIZED, false);
            }
            resetSessionsForApiKey(apiKeys.getKey());
            JSONObject response = new JSONObject();
            response.put("request_status", "success");
            response.put("message", "All sessions and outstanding resume tokens were invalidated.");
            response.put("session_limit", 1);
            return new APIResponse(null, response, ResponseCode.SUCCESSFUL, false);
        }

        private boolean resetSessionAuthorizationValid(RequestHeaders headers, APIKeys apiKeys) {
            if (headers == null || apiKeys == null) return false;
            String timestamp = headers.getString("X-Request-Timestamp");
            String randomBytes = headers.getString("X-Api-Random-Bytes");
            String authorization = headers.getString("X-Api-Reset-Authorization");
            if (timestamp == null || !timestamp.matches("[0-9]{1,20}")
                    || randomBytes == null || !randomBytes.matches("(?i)[0-9a-f]{88}")) {
                return false;
            }
            return APIKeys.verifySessionResetAuthorization(
                    authorization, apiKeys.getSecret(), apiKeys.getKey(), timestamp, randomBytes);
        }

        private APIResponse handleSessionAllocate(APIKeys apiKeys, RequestHeaders headers, String body) throws APIException {
            if (!rootSessionAuthorizationValid(headers, apiKeys)) {
                return new APIResponse(null, ResponseCode.UNAUTHORIZED);
            }
            if (apiKeys.getRawSessionAllocationSecret() == null || apiKeys.getRawSessionAllocationSecret().isBlank()) {
                JSONObject response = new JSONObject();
                response.put("request_status", "allocation_not_configured");
                response.put("message", "A private session allocation signing secret is not configured for this API key.");
                return new APIResponse(null, response, ResponseCode.FORBIDDEN, false);
            }

            JSONObject request = parseControlObject(body);
            if (!controlString(request, "oauth_token").isBlank()
                    || request.containsKey("additional_sessions") || request.containsKey("sessions")) {
                return new APIResponse(null, ResponseCode.REQUEST_FAILED,
                        "OAuth-style allocation tokens and configurable allocation amounts are not supported. Submit one signed allocation assertion.");
            }
            String challengeId = controlString(request, "challenge_id");
            String signature = controlString(request, "signature");
            if (!challengeId.isBlank() || !signature.isBlank()) {
                SessionAllocationAssertion assertion = SessionAllocationAssertion.fromJson(request);
                if (!sessionAllocationManager.verifyAndConsume(
                        apiKeys.getKey(), apiKeys.getRawSessionAllocationSecret(), assertion)) {
                    JSONObject response = new JSONObject();
                    response.put("request_status", "unauthorized");
                    response.put("message", "The session allocation assertion is invalid, expired, or has already been used.");
                    return new APIResponse(null, response, ResponseCode.UNAUTHORIZED, false);
                }
                int limit = sessionCapacity.allocate(apiKeys.getKey());
                JSONObject response = new JSONObject();
                response.put("request_status", "success");
                response.put("allocated_sessions", 1);
                response.put("session_limit", limit);
                return new APIResponse(null, response, ResponseCode.SUCCESSFUL, false);
            }

            SessionAllocationManager.Challenge challenge = sessionAllocationManager.issueChallenge(
                    apiKeys.getKey(), config.getAllocationChallengeLifetime()
            );
            JSONObject response = new JSONObject();
            response.put("request_status", "authentication_challenge");
            response.put("challenge_id", challenge.id());
            response.put("challenge", challenge.value());
            response.put("expires_at_ms", challenge.expiresAt().toEpochMilli());
            response.put("signature_algorithm", "HMAC-SHA384");
            return new APIResponse(null, response, ResponseCode.SUCCESSFUL, false);
        }

        private ClientSession appendNewClient(APIKeys keys, ClientInfo info) throws APIException {

            return ClientSession.createClient(info, keys, APIServer.this, isEncrypted());

        }

        private ClientSession appendNewSession(RemoteClient client, APIKeys keys, ClientInfo info) throws APIException {

            APISession session = new APISession(keys.copyForSession(), info.getRemoteIp(), config.getServerType(),
                    config.getMinutesTillInvalid(), config.isSessionExpirationEnabled(), isEncrypted());
            return new ClientSession(info, client, session);

        }

        private void invalidateSessionsByLimitUsingIp(RemoteClient client) {
            int count = 0;
            for(int i=clientSessions.size() - 1; i>=0; i--) {
                if(__.same(client.getClientInfo().getRemoteIp(), clientSessions.get(i).getClient().getClientInfo().getRemoteIp())) {
                    count++;
                    if(count > sessionCapacity.limitFor(client.getApiKey())) {
                        sessionInvalidated(clientSessions.get(i));
                        pastSessions.add(clientSessions.get(i).getSession());
                        clientSessions.remove(i);
                    }
                }
            }
        }

        private boolean verifyApiAuthorization(RequestHeaders headers, APIKeys apiKeys, ClientSession session, boolean isNewSession) {

            if(config.doUseDynamicApiKey()) {

                String randomBytes = headers.getString("X-Api-Random-Bytes");
                String apiAuthorization = (!isNewSession) ? headers.getString("X-Api-Authorization") : null;
                try {
                    boolean verified = (isNewSession) ? apiKeys.initialInbound(session, randomBytes) : apiKeys.inbound(session, apiAuthorization, randomBytes);
                    if(!verified) {
                        String phase = isNewSession ? "initial" : "inbound";
                        int randomLength = randomBytes == null ? -1 : randomBytes.length();
                        log.write("API " + phase + " authorization returned false. random_header_length=" + randomLength);
                    }
                    return verified;
                } catch (APIException ex) {
                    String phase = isNewSession ? "initial" : "inbound";
                    int randomLength = randomBytes == null ? -1 : randomBytes.length();
                    log.write("API " + phase + " authorization failed: " + ex.getMessage() + " random_header_length=" + randomLength);
                    ExceptionHandling.report(ex);
                    return false;
                }

            } else { return true; }

        }

        private boolean verifyRequestSignature(RequestHeaders headers, String method, String urlPath, String body, APIKeys apiKeys, ClientSession clientSession, String resourceMethod) {

            String timestamp = headers.getString("X-Request-Timestamp");
            String sequence = headers.getString("X-Request-Sequence");
            String nonce = headers.getString("X-Api-Random-Bytes");
            String keySetId = headers.getString("X-Api-Key-Set-Id");
            String contentType = headers.getString("Content-Type");
            String sigReceived = headers.getString("X-Request-Signature");
            String requestSecret = (config.doUseDynamicApiKey()) ? apiKeys.getClientPayloadKey() : apiKeys.getRawSessionSecret();

            if (!__.isset(requestSecret) || !__.isset(sigReceived)) {
                return false;
            }

            boolean verified = APIKeys.verifyRequestSignature(
                    sigReceived,
                    apiKeys.getSecret(),
                    requestSecret,
                    apiKeys.getKey(),
                    clientSession.getSession().getSessionId(),
                    method,
                    fixPath(urlPath),
                    timestamp,
                    sequence,
                    keySetId,
                    nonce,
                    contentType,
                    body,
                    resourceMethod
            );

            if (!verified) {
                return false;
            }

            try {
                return clientSession.getSession().acceptRequestSequence(Long.parseLong(sequence));
            } catch (Exception ignored) {
                return false;
            }

        }

        private byte[] buildRequestAad(RequestHeaders headers, String method, String urlPath, ClientSession clientSession, String resourceMethod) {
            try {
                return APIKeys.requestAad(
                        method,
                        fixPath(urlPath),
                        headers.getString("X-Request-Timestamp"),
                        headers.getString("X-Request-Sequence"),
                        clientSession.getSession().getApiKeys().getKey(),
                        clientSession.getSession().getSessionId(),
                        headers.getString("X-Api-Key-Set-Id"),
                        headers.getString("X-Api-Random-Bytes"),
                        headers.getString("Content-Type"),
                        resourceMethod
                );
            } catch (Exception ignored) {
                return null;
            }
        }

        private boolean isEncryptedTransfer(RequestHeaders headers) {
            return headers != null && "x-encrypted-base64".equalsIgnoreCase(headers.getString("Content-Transfer-Encoding"));
        }

        private boolean isJsonContentType(RequestHeaders headers) {
            return contentType(headers) == ContentType.JSON;
        }

        private ContentType contentType(RequestHeaders headers) {
            if(headers == null || !headers.contains("Content-Type")) return ContentType.TEXT;
            String value = headers.getString("Content-Type");
            if(value == null) return ContentType.TEXT;
            String normalized = value.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
            switch (normalized) {
                case "application/json":
                    return ContentType.JSON;
                case "application/x-www-form-urlencoded":
                    return ContentType.URL_ENCODED;
                case "multipart/form-data":
                    return ContentType.FORM_DATA;
                default:
                    return ContentType.TEXT;
            }
        }

        private String fixPath(String input) {
            if(input == null || input.isBlank()) return "/";
            String path = input.trim().replaceAll("\\s+", "");
            String apiPath = config.getApiPath();
            if(apiPath != null && path.startsWith(apiPath)) path = path.substring(apiPath.length());
            path = path.replace('.', '/');
            if(path.isEmpty()) return "/";
            if(path.charAt(0) != '/') path = "/" + path;
            while(path.length() > 1 && path.endsWith("/")) path = path.substring(0, path.length() - 1);
            return path;
        }

        private boolean sessionAllowed(ClientSession client, String apiKey) {
            if (apiKey == null || apiKey.isBlank()) return false;
            java.util.Set<String> live = new java.util.HashSet<>();
            for (ClientSession current : clientSessions) {
                if (sessionMatchesApiKey(current, apiKey)) live.add(current.getSession().getSessionId());
            }
            return activeSessionsForApiKey(apiKey) + resumeTokenStore.outstandingForApiKey(apiKey, live)
                    <= sessionCapacity.limitFor(apiKey);

        }

        private int getNumSessions(RemoteClient client) {

            int numSessions = 0;

            for(int i=0; i<clientSessions.size(); i++) {

                RemoteClient current = clientSessions.get(i).getClient();
                if(current.equals(client))
                    numSessions++;

            }

            return numSessions;

        }

        private int findClientSession(RemoteClient client, String sessionId) {

            for(int i=0; i<clientSessions.size(); i++) {

                RemoteClient current = clientSessions.get(i).getClient();

                if(current.guessSame(client)) {

                    if(clientSessions.get(i).getSession().getSessionId().equals(sessionId))
                        return i;

                }

            }

            return -1;

        }

        private int findClientSession(RemoteClient client, APIKeys apiKeys) {

            for(int i=0; i<clientSessions.size(); i++) {

                RemoteClient current = clientSessions.get(i).getClient();

                if(current.equals(client)) {

                    if(clientSessions.get(i).getSession().getApiKeys().getKey().equals(apiKeys.getKey()))
                        return i;

                }

            }

            return -1;

        }

        private int findClient(APIKeys apiKeys) {

            for(int i=0; i<clients.size(); i++) {
                RemoteClient curr = clients.get(i);
                if(curr.equals(apiKeys))
                    return i;
            }

            return -1;

        }

        private boolean checkRequestHeaders(RequestHeaders headers, String resource) {

            if (headers.hasAnyDuplicate(
                    "X-Api-Key", "X-Request-Timestamp", "X-Request-Signature",
                    "X-Request-Sequence", "X-Api-Session-Id", "X-Api-Key-Set-Id",
                    "X-Api-Session-Authorization", "X-Api-Authorization", "X-Api-Random-Bytes",
                    "X-Api-Reset-Authorization", "X-Api-Resource-Method",
                    "Content-Transfer-Encoding", "Content-Type"
            )) {
                return false;
            }

            if(config.getServerType() == APIServerType.ENCRYPTED_ASYNC
                    || config.getServerType() == APIServerType.ENCRYPTED_SYNC
            ) {

                if(isSessionBootstrapResource(resource)) {

                    return headers.contains("X-Request-Timestamp")
                            && headers.contains("X-Api-Key")
                            && headers.contains("X-Api-Random-Bytes")
                            && headers.contains("X-Api-Session-Authorization");

                } else if(config.getEncryptedSessionPaths()[1].equals(resource)) {

                    if(!headers.contains("X-Api-Key")
                            || !headers.contains("X-Request-Timestamp")
                            || !headers.contains("X-Request-Signature")
                            || !headers.contains("X-Request-Sequence")
                            || !headers.contains("X-Api-Session-Id")
                            || !headers.contains("X-Api-Key-Set-Id")
                            || !headers.contains("X-Api-Session-Authorization")
                    ) {
                        return false;
                    }

                    return !config.doUseDynamicApiKey() || (headers.contains("X-Api-Authorization")
                            && headers.contains("X-Api-Random-Bytes"));

                } else {

                    if (!headers.contains("X-Api-Key")
                            || !headers.contains("X-Request-Timestamp")
                            || !headers.contains("X-Request-Signature")
                            || !headers.contains("X-Request-Sequence")
                            || !headers.contains("X-Api-Session-Id")
                            || !headers.contains("X-Api-Key-Set-Id")
                            || !headers.contains("X-Api-Session-Authorization")
                            || !headers.contains("X-Api-Resource-Method")
                    ) {
                        return false;
                    }

                    return !config.doUseDynamicApiKey() || (headers.contains("X-Api-Authorization")
                            && headers.contains("X-Api-Random-Bytes"));

                }

            } else if(config.getServerType() == APIServerType.REST_SYNC || config.getServerType() == APIServerType.REST_ASYNC) {

                return config.isPublicApi() || headers.contains("X-Api-Key");

            }

            return true;

        }

        private String bodyFromStream(InputStream bodyInput) throws IOException, RequestBodyTooLargeException {
            if(bodyInput == null) return "";
            int maxBytes = config.getMaxRequestBodyBytes();
            ByteArrayOutputStream output = new ByteArrayOutputStream(Math.min(maxBytes, 8192));
            byte[] buffer = new byte[8192];
            int total = 0;
            int read;
            while((read = bodyInput.read(buffer)) != -1) {
                if(read > maxBytes - total) throw new RequestBodyTooLargeException();
                output.write(buffer, 0, read);
                total += read;
            }
            return output.toString(StandardCharsets.UTF_8);
        }

        private APIResponse processRequest(RequestHeaders headers, ClientSession clientSession, String resourceName, String resourceMethod, BodyParameters params, String urlPath, boolean earlyResumeAuthorized) throws Exception {

            boolean isSessionResource = isEncrypted() && Arrays.asList(config.getEncryptedSessionPaths()).contains(resourceName);
            boolean isResponseResource = isAsyncResponseResource(resourceName);
            APIResource resource = (isSessionResource || isResponseResource) ? null : resources.getResource(resourceName, resourceMethod);

            InboundRequest inboundRequest = new InboundRequest(clientSession, params, headers);
            inboundRequest.setResourceMethod(resourceMethod);

            if(isEncrypted()) {

                if(isSessionResource) {
                    String[] sessionPaths = config.getEncryptedSessionPaths();
                    EncryptedRequestType requestType = sessionPaths[0].equals(resourceName)
                            ? EncryptedRequestType.SESSION_INIT
                            : sessionPaths[2].equals(resourceName)
                            ? EncryptedRequestType.SESSION_RESUME
                            : isSessionHeartbeatResource(resourceName)
                            ? EncryptedRequestType.SESSION_HEARTBEAT
                            : EncryptedRequestType.SESSION_CLOSE;

                    if(requestType == EncryptedRequestType.SESSION_INIT || requestType == EncryptedRequestType.SESSION_RESUME) {
                        String sessionSecret = clientSession.getSession().getApiKeys().getSessionSecret();
                        String headerAuth = headers.getString("X-Api-Session-Authorization");
                        if (!earlyResumeAuthorized && !__.same(sessionSecret, headerAuth)) {
                            // The session root is invalid, but the API key may
                            // still be recoverable with its other root secret.
                            invalidateSession(clientSession);
                            return new APIResponse(clientSession, ResponseCode.UNAUTHORIZED);
                        }
                    } else if(!isAuthenticated(headers, resource, params, clientSession)) {
                        return new APIResponse(clientSession, ResponseCode.UNAUTHORIZED);
                    }

                    return processRequestEncryptedSync(clientSession, resource, params, headers, requestType, resourceMethod);
                }

                if(!isAuthenticated(headers, resource, params, clientSession)) {
                    return new APIResponse(clientSession, ResponseCode.UNAUTHORIZED);
                }

                if(isResponseResource) {
                    return processAsyncPoll(clientSession, params, true);
                }

                if(!headers.getString("X-Api-Key-Set-Id").equals(clientSession.getSession().getKeySetId())) {
                    lockSessionApiKey(clientSession, "Invalid API key-set id.");
                    return new APIResponse(clientSession, ResponseCode.REQUEST_FAILED);
                }
                if (params == null || !params.isValid() || !params.checkAllPresent(resource == null ? List.of() : resource.getParameters())) {
                    return new APIResponse(clientSession, ResponseCode.REQUEST_FAILED);
                }

                if(config.isAsync() && resource.isAsync()) {
                    return processRequestAsync(resource, inboundRequest, true);
                }

                return processRequestEncryptedSync(clientSession, resource, params, headers, EncryptedRequestType.EXECUTE_RESOURCE, resourceMethod);

            } else if(config.getServerType() == APIServerType.REST_SYNC || config.getServerType() == APIServerType.REST_ASYNC) {

                if(isResponseResource) {
                    if(!isPublicApi && !isAuthenticated(headers, resource, params, clientSession)) {
                        return new APIResponse(clientSession, ResponseCode.UNAUTHORIZED);
                    }
                    return processAsyncPoll(clientSession, params, false);
                }

                if(!isPublicApi && !isAuthenticated(headers, resource, params, clientSession)) {
                    return new APIResponse(clientSession, ResponseCode.UNAUTHORIZED);
                }

                if(config.isAsync() && resource.isAsync()) {
                    return processRequestAsync(resource, inboundRequest, false);
                }

                return processRequestSync(resource, inboundRequest);

            }

            return new APIResponse(clientSession, ResponseCode.SERVER_ERROR);

        }

        private APIResponse processRequestSync(APIResource resource, InboundRequest request) {
            APIResponse resp = execute(resource, request);
            return resp;
        }

        private APIResponse processRequestAsync(APIResource resource, InboundRequest request, boolean encrypted) throws Exception {
            APIResponse resp = asyncExecute(resource, request);
            if(encrypted) resp.encrypt();
            return resp;
        }

        private APIResponse processAsyncPoll(ClientSession clientSession, BodyParameters params, boolean encrypted) throws Exception {
            if(params == null || !params.present("response_id") || !params.present("authorization")) {
                return new APIResponse(clientSession, ResponseCode.REQUEST_FAILED);
            }

            String responseId = params.getString("response_id");
            String authorization = params.getString("authorization");
            AsyncRequest asyncRequest = asyncResponses.get(responseId);
            if(asyncRequest == null || !asyncRequest.requestAuthVerifies(authorization, encrypted)) {
                return new APIResponse(clientSession, ResponseCode.UNAUTHORIZED);
            }

            if(asyncRequest.pollTooSoon()) {
                APIResponse response = APIResponse.tooManyRequests(clientSession, asyncRequest.getPollRate(), asyncRequest.getDynamicRequestAuth());
                if(encrypted) response.encrypt();
                return response;
            }

            boolean expired = asyncRequest.isExpired();
            boolean complete = asyncRequest.isRequestComplete();

            if(expired && !complete) {
                asyncResponses.remove(responseId);
                APIResponse response = asyncRequest.timeoutResponse();
                if(encrypted) response.encrypt();
                return response;
            }

            if(complete) {
                asyncResponses.remove(responseId);
                APIResponse response = asyncRequest.getResponse();
                if(response == null) response = new APIResponse(clientSession, ResponseCode.SERVER_ERROR);
                if(encrypted) response.encrypt();
                return response;
            }

            APIResponse response = asyncRequest.getContinue();
            if(encrypted) response.encrypt();
            return response;
        }

        private APIResponse processRequestEncryptedSync(ClientSession clientSession, APIResource resource, BodyParameters params, RequestHeaders headers, EncryptedRequestType type, String resourceMethod) throws Exception {
            if(type == EncryptedRequestType.SESSION_INIT || type == EncryptedRequestType.SESSION_RESUME) {
                APIResponse resp = initializeSession(clientSession);
                resp.encryptWithPsk();
                return resp;
            } else if(type == EncryptedRequestType.SESSION_CLOSE) {
                return sessionClose(params, headers, clientSession);
            } else if(type == EncryptedRequestType.SESSION_HEARTBEAT) {
                return sessionHeartbeat(clientSession, params);
            } else if(type == EncryptedRequestType.EXECUTE_RESOURCE) {

                InboundRequest inboundRequest = new InboundRequest(clientSession, params, headers);
                inboundRequest.setResourceMethod(resourceMethod);

                if (!headers.getString("X-Api-Key-Set-Id").equals(clientSession.getSession().getKeySetId())) {
                    lockSessionApiKey(clientSession, "Invalid API key-set id.");
                    return new APIResponse(clientSession, ResponseCode.REQUEST_FAILED);
                }
                if (params == null || !params.isValid() || !params.checkAllPresent(resource == null ? List.of() : resource.getParameters())) {
                    return new APIResponse(clientSession, ResponseCode.REQUEST_FAILED);
                }

                APIResponse resp = execute(resource, inboundRequest);
                resp.encrypt();
                return resp;

            }

            return new APIResponse(clientSession, ResponseCode.SERVER_ERROR);
        }

        private APIResponse initializeSession(ClientSession clientSession) {

            JSONObject obj = new JSONObject();

            String sessionId = clientSession.getSession().getSessionId();

            obj.put("session_id", sessionId);
            obj.put("early_resume_supported", config.isActiveSessionRecoveryEnabled());
            obj.put("request_status", "success");

            String randomBytes = clientSession.getSession().getRandomBytes();

            String serverPublicKey = BaseX.encode64(
                    clientSession
                            .getSession()
                            .getServerPublicKey()
                            .getPublicKey()
                            .getEncoded());

            String clientPrivateKey = BaseX.encode64(
                    clientSession
                            .getSession()
                            .getClientPrivateKey()
                            .getPrivateKey()
                            .getEncoded());

            String keySetId = clientSession
                    .getSession()
                    .getKeySetId();

            obj.put("random_bytes", randomBytes);
            obj.put("server_public_key", serverPublicKey);
            obj.put("client_private_key", clientPrivateKey);
            obj.put("key_set_id", keySetId);
            obj.put("request_status", "success");

            clientSession.getSession().keysTransferred();

            return new APIResponse(clientSession, obj, ResponseCode.SUCCESSFUL);

        }

        private APIResponse sessionClose(BodyParameters params, RequestHeaders headers, ClientSession clientSession) throws Exception {

            APIKeys apiKeys = clientSession.getSession().getApiKeys();
            String expectedSessionId = clientSession.getSession().getSessionId();
            String expectedKeySetId = clientSession.getSession().getKeySetId();
            String receivedSessionId = paramString(params, "session_id");
            String receivedKeySetId = paramString(params, "key_set_id");
            String receivedAuth = paramString(params, "authorization");
            String sequence = headers.getString("X-Request-Sequence");
            String requestSecret = config.doUseDynamicApiKey() ? apiKeys.getClientPayloadKey() : apiKeys.getRawSessionSecret();

            boolean closeAuthorized = SecureCompare.equals(expectedSessionId, receivedSessionId)
                    && SecureCompare.equals(expectedKeySetId, receivedKeySetId)
                    && APIKeys.verifySessionCloseAuthorization(
                            receivedAuth,
                            apiKeys.getSecret(),
                            requestSecret,
                            apiKeys.getKey(),
                            expectedSessionId,
                            expectedKeySetId,
                            sequence
                    );

            if(closeAuthorized) {
                invalidateResumeTokens(apiKeys.getKey(), expectedSessionId);
                JSONObject obj = new JSONObject();
                obj.put("request_status", "success");
                obj.put("session_id", expectedSessionId);
                if (paramBoolean(params, "create_resume_token") || paramBoolean(params, "resume")) {
                    String resumeToken;
                    try {
                        resumeToken = resumeTokenStore.issue(
                                apiKeys.getKey(), apiKeys.getSecret(), expectedSessionId,
                                java.time.Duration.ofMinutes(config.getResumeTokenMinutes())
                        );
                    } catch (APIException ex) {
                        ex.tryReport();
                        return new APIResponse(clientSession, ResponseCode.SERVER_ERROR);
                    }
                    obj.put("resume_token", resumeToken);
                    obj.put("resume_expires_at_ms", System.currentTimeMillis() + config.getResumeTokenMinutes() * 60_000L);
                }
                APIResponse response = new APIResponse(clientSession, obj, ResponseCode.SUCCESSFUL);
                response.encrypt();
                // The encrypted response must be prepared while the session
                // keys are still available, but an authorized close must no
                // longer count against the API key's active-session limit.
                invalidateSession(clientSession);
                return response;
            }

            lockSessionApiKey(clientSession, "Invalid encrypted session close authorization.");
            return new APIResponse(clientSession, ResponseCode.UNAUTHORIZED);

        }

        private APIResponse sessionHeartbeat(ClientSession clientSession, BodyParameters params) throws Exception {
            JSONObject obj = new JSONObject();
            obj.put("request_status", "success");
            obj.put("session_id", clientSession.getSession().getSessionId());
            obj.put("heartbeat", true);
            if (config.isActiveSessionRecoveryEnabled() && paramBoolean(params, "create_resume_token")) {
                APIKeys keys = clientSession.getSession().getApiKeys();
                try {
                    obj.put("resume_token", resumeTokenStore.issueWhileActive(keys.getKey(), keys.getSecret(),
                            clientSession.getSession().getSessionId(), java.time.Duration.ofMinutes(config.getResumeTokenMinutes())));
                } catch (APIException error) {
                    throw new IOException("Could not issue early resume token.", error);
                }
                obj.put("resume_expires_at_ms", System.currentTimeMillis() + config.getResumeTokenMinutes() * 60_000L);
            }
            APIResponse response = new APIResponse(clientSession, obj, ResponseCode.SUCCESSFUL);
            response.encrypt();
            return response;
        }

        private void invalidateResumeTokens(String apiKey, String sessionId) throws IOException {
            try {
                resumeTokenStore.invalidateForSession(apiKey, sessionId);
            } catch (APIException error) {
                throw new IOException("Could not invalidate resume tokens.", error);
            }
        }

        private String paramString(BodyParameters params, String key) {
            return params != null && params.present(key) ? params.getString(key) : "";
        }

        private boolean paramBoolean(BodyParameters params, String key) {
            if (params == null || !params.present(key)) return false;
            Object value = params.get(key);
            return value instanceof Boolean ? (Boolean) value : "true".equalsIgnoreCase(String.valueOf(value)) || "1".equals(value);
        }

        private void authFailure(HttpExchange t) {

            String retVal = "{\"request_status\":\"forbidden\",\"message\":\"Your network" +
                    " addresses will be blocked after a series of these attempts.\"}";
            sendResponse(t, 401, retVal, ContentType.JSON);

        }

        private void rateLimited(HttpExchange t) {
            String retVal = "{\"request_status\":\"too_many_requests\"}";
            sendResponse(t, 429, retVal, ContentType.JSON);
        }

        private void serverError(HttpExchange t) {

            String retVal = "{\"request_status\":\"server_error\"}";
            sendResponse(t, 500, retVal, ContentType.JSON);

        }

        private void rollbackEncryptedRequest(ClientSession clientSession, APISession.State sessionState, APIKeys.State keyState) {
            if(!isEncrypted() || clientSession == null || clientSession.getSession() == null) return;
            clientSession.getSession().restore(sessionState);
            if(clientSession.getSession().getApiKeys() != null) clientSession.getSession().getApiKeys().restore(keyState);
        }

        private boolean commitEncryptedResponse(ClientSession clientSession) {
            if(!isEncrypted() || clientSession == null || clientSession.getSession() == null) return true;
            if(clientSession.getSession().getApiKeys() == null) return true;
            try {
                clientSession.getSession().getApiKeys().commitSessionSecret();
                return true;
            } catch (Throwable ex) {
                ExceptionHandling.report(ex);
                return false;
            }
        }

        private boolean sendServerError(HttpExchange t, String responseBody, ContentType contentType, ClientSession clientSession) {
            if(responseBody == null || responseBody.isBlank()) {
                serverError(t);
                return false;
            }
            return sendResponse(t, 500, responseBody, contentType == null ? ContentType.JSON : contentType, clientSession);
        }

        private void unauthorized(HttpExchange t) {

            String retVal = "{\"request_status\":\"unauthorized\"}";
            sendResponse(t, 401, retVal, ContentType.JSON);

        }

        private void sendControlResponse(HttpExchange t, APIResponse response) {
            if (response == null) {
                serverError(t);
                return;
            }
            ResponseCode code = response.getResponseCode();
            int status = switch (code) {
                case SESSION_LIMIT_REACHED, TOO_MANY_REQUESTS -> 429;
                case UNAUTHORIZED -> 401;
                case FORBIDDEN -> 403;
                case REQUEST_FAILED -> 400;
                case SERVER_ERROR -> 500;
                default -> 200;
            };
            sendResponse(t, status, response.getResponse(), ContentType.JSON);
        }

        private void tooLarge(HttpExchange t) {
            sendResponse(t, 413, "{\"request_status\":\"request_too_large\"}", ContentType.JSON);
        }

        private void failRequest(HttpExchange t, String reason) {

            JSONObject retObj = new JSONObject();
            retObj.put("request_status", "fail");
            retObj.put("fail_reason", reason);

            String retVal = "{\"request_status\":\"fail\"}";
            int code = 500;

            try {
                retVal = JSON.getString(retObj);
                code = 400;
            } catch (Exception ex) {
                ExceptionHandling.report(ex);
            }

            sendResponse(t, code, retVal, ContentType.JSON);

        }

        private boolean sendResponse(HttpExchange t, int code, String response, ContentType contentType) {

            try {

                if(config.getCorsPolicy().isPermitted()) {
                    t.getResponseHeaders().add("Access-Control-Allow-Origin",
                            config.getCorsPolicy().getCommaSeparated(
                                    config.getCorsPolicy().getOrigins()
                            ));
                    t.getResponseHeaders().add("Access-Control-Allow-Headers",
                            config.getCorsPolicy().getCommaSeparated(
                                    config.getCorsPolicy().getHeaders(config.getServerType())
                            ));
                    t.getResponseHeaders().add("Access-Control-Allow-Methods",
                            config.getCorsPolicy().getCommaSeparated(
                                    config.getCorsPolicy().getMethods()
                            ));
                }

                String contentTypeStr = "";

                switch (contentType) {
                    case JSON:
                        contentTypeStr = "application/json";
                        break;
                    case TEXT:
                    default:
                        contentTypeStr = "text/plain";
                        break;
                }

                t.getResponseHeaders().add("Content-Type", contentTypeStr);
                byte[] responseBytes = (response == null ? "" : response).getBytes(StandardCharsets.UTF_8);
                t.sendResponseHeaders(code, responseBytes.length);

                try (OutputStream os = t.getResponseBody()) {
                    os.write(responseBytes);
                }
                return true;

            } catch (Exception ex) {
                ExceptionHandling.report(ex);
                return false;
            }

        }

        private boolean sendResponse(HttpExchange t, int code, String response, ContentType contentType, ClientSession clientSession) {

            try {

                if (config.getCorsPolicy().isPermitted()) {
                    t.getResponseHeaders().add("Access-Control-Allow-Origin",
                            config.getCorsPolicy().getCommaSeparated(
                                    config.getCorsPolicy().getOrigins()
                            ));
                    t.getResponseHeaders().add("Access-Control-Allow-Headers",
                            config.getCorsPolicy().getCommaSeparated(
                                    config.getCorsPolicy().getHeaders(config.getServerType())
                            ));
                    t.getResponseHeaders().add("Access-Control-Allow-Methods",
                            config.getCorsPolicy().getCommaSeparated(
                                    config.getCorsPolicy().getMethods()
                            ));
                }

                if (isEncrypted()) {

                    t.getResponseHeaders().add(
                            "X-Api-Session-Authorization",
                            clientSession.getSession().getDynamicSessionAuth()
                    );

                    t.getResponseHeaders().add(
                            "X-Api-Random-Bytes",
                            clientSession.getSession().getApiKeys().getServerIv()
                    );

                    if (config.doUseDynamicApiKey()) {

                        if (getNumSessions(clientSession.getClient()) > 1) {

                            t.getResponseHeaders().add(
                                    "X-Api-Authorization",
                                    clientSession.getSession().getApiKeys().getServerEncrypted()
                            );

                            t.getResponseHeaders().add(
                                    "X-Server-Timestamp",
                                    String.valueOf(LocalDateTime.now().toEpochSecond(ZoneOffset.UTC))
                            );

                            t.getResponseHeaders().add(
                                    "X-Client-Timestamp",
                                    String.valueOf(System.currentTimeMillis())
                            );


                        } else {

                            t.getResponseHeaders().add(
                                    "X-Api-Authorization",
                                    clientSession.getSession().getApiKeys().getServerEncrypted()
                            );

                            t.getResponseHeaders().add(
                                    "X-Server-Timestamp",
                                    String.valueOf(LocalDateTime.now().toEpochSecond(ZoneOffset.UTC))
                            );

                            t.getResponseHeaders().add(
                                    "X-Client-Timestamp",
                                    String.valueOf(System.currentTimeMillis())
                            );

                        }

                    }

                }

                String contentTypeStr = "";

                switch (contentType) {
                    case JSON:
                        contentTypeStr = "application/json";
                        break;
                    case TEXT:
                    default:
                        contentTypeStr = "text/plain";
                        break;
                }

                t.getResponseHeaders().add("Content-Type", contentTypeStr);
                byte[] responseBytes = (response == null ? "" : response).getBytes(StandardCharsets.UTF_8);
                t.sendResponseHeaders(code, responseBytes.length);

                try (OutputStream os = t.getResponseBody()) {
                    os.write(responseBytes);
                }
                return commitEncryptedResponse(clientSession);

            } catch (Exception ex) {
                ExceptionHandling.report(ex);
                return false;
            }

        }

        private void authFailure(HttpServerResponse t) {

            String retVal = "{\"request_status\":\"forbidden\",\"message\":\"Your network" +
                    " addresses will be blocked after a series of these attempts.\"}";
            sendResponse(t, 401, retVal, ContentType.JSON);

        }

        private void rateLimited(HttpServerResponse t) {
            String retVal = "{\"request_status\":\"too_many_requests\"}";
            sendResponse(t, 429, retVal, ContentType.JSON);
        }

        private void serverError(HttpServerResponse t) {

            String retVal = "{\"request_status\":\"server_error\"}";
            sendResponse(t, 500, retVal, ContentType.JSON);

        }

        private boolean sendServerError(HttpServerResponse t, String responseBody, ContentType contentType, ClientSession clientSession) {
            if(responseBody == null || responseBody.isBlank()) {
                serverError(t);
                return false;
            }
            return sendResponse(t, 500, responseBody, contentType == null ? ContentType.JSON : contentType, clientSession);
        }

        private void unauthorized(HttpServerResponse t) {

            String retVal = "{\"request_status\":\"unauthorized\"}";
            sendResponse(t, 401, retVal, ContentType.JSON);

        }

        private void sendControlResponse(HttpServerResponse t, APIResponse response) {
            if (response == null) {
                serverError(t);
                return;
            }
            ResponseCode code = response.getResponseCode();
            int status = switch (code) {
                case SESSION_LIMIT_REACHED, TOO_MANY_REQUESTS -> 429;
                case UNAUTHORIZED -> 401;
                case FORBIDDEN -> 403;
                case REQUEST_FAILED -> 400;
                case SERVER_ERROR -> 500;
                default -> 200;
            };
            sendResponse(t, status, response.getResponse(), ContentType.JSON);
        }

        private void tooLarge(HttpServerResponse t) {
            sendResponse(t, 413, "{\"request_status\":\"request_too_large\"}", ContentType.JSON);
        }

        private void failRequest(HttpServerResponse t, String reason) {

            JSONObject retObj = new JSONObject();
            retObj.put("request_status", "fail");
            retObj.put("fail_reason", reason);

            String retVal = "{\"request_status\":\"fail\"}";
            int code = 500;

            try {
                retVal = JSON.getString(retObj);
                code = 400;
            } catch (Exception ex) {
                ExceptionHandling.report(ex);
            }

            sendResponse(t, code, retVal, ContentType.JSON);

        }

        private boolean sendResponse(HttpServerResponse t, int code, String response, ContentType contentType) {

            try {

                if(config.getCorsPolicy().isPermitted()) {
                    t.addHeader("Access-Control-Allow-Origin",
                            config.getCorsPolicy().getCommaSeparated(
                                    config.getCorsPolicy().getOrigins()
                            ));
                    t.addHeader("Access-Control-Allow-Headers",
                            config.getCorsPolicy().getCommaSeparated(
                                    config.getCorsPolicy().getHeaders(config.getServerType())
                            ));
                    t.addHeader("Access-Control-Allow-Methods",
                            config.getCorsPolicy().getCommaSeparated(
                                    config.getCorsPolicy().getMethods()
                            ));
                    t.addHeader("Access-Control-Expose-Headers",
                            config.getCorsPolicy().getCommaSeparated(
                                    config.getCorsPolicy().getExposeHeaders(config.getServerType())
                            ));
                }

                String contentTypeStr = "";

                switch (contentType) {
                    case JSON:
                        contentTypeStr = "application/json";
                        break;
                    case TEXT:
                    default:
                        contentTypeStr = "text/plain";
                        break;
                }

                t.addHeader("Content-Type", contentTypeStr);
                t.setStatus(code);

                byte[] responseBytes = (response == null ? "" : response).getBytes(StandardCharsets.UTF_8);
                try (OutputStream os = t.getOutputStream()) {
                    os.write(responseBytes);
                }
                return true;

            } catch (Exception ex) {
                ExceptionHandling.report(ex);
                return false;
            }

        }

        private boolean sendResponse(HttpServerResponse t, int code, String response, ContentType contentType, ClientSession clientSession) {

            try {

                if (config.getCorsPolicy().isPermitted()) {
                    t.addHeader("Access-Control-Allow-Origin",
                            config.getCorsPolicy().getCommaSeparated(
                                    config.getCorsPolicy().getOrigins()
                            ));
                    t.addHeader("Access-Control-Allow-Headers",
                            config.getCorsPolicy().getCommaSeparated(
                                    config.getCorsPolicy().getHeaders(config.getServerType())
                            ));
                    t.addHeader("Access-Control-Allow-Methods",
                            config.getCorsPolicy().getCommaSeparated(
                                    config.getCorsPolicy().getMethods()
                            ));
                    t.addHeader("Access-Control-Expose-Headers",
                            config.getCorsPolicy().getCommaSeparated(
                                    config.getCorsPolicy().getExposeHeaders(config.getServerType())
                            ));
                }

                if (isEncrypted()) {

                    t.addHeader(
                            "X-Api-Session-Authorization",
                            clientSession.getSession().getDynamicSessionAuth()
                    );

                    t.addHeader(
                            "X-Api-Random-Bytes",
                            clientSession.getSession().getApiKeys().getServerIv()
                    );

                    if (config.doUseDynamicApiKey()) {

                        if (getNumSessions(clientSession.getClient()) > 1) {

                            t.addHeader(
                                    "X-Api-Authorization",
                                    clientSession.getSession().getApiKeys().getServerEncrypted()
                            );

                            t.addHeader(
                                    "X-Server-Timestamp",
                                    String.valueOf(LocalDateTime.now().toEpochSecond(ZoneOffset.UTC))
                            );

                            t.addHeader(
                                    "X-Client-Timestamp",
                                    String.valueOf(System.currentTimeMillis())
                            );


                        } else {

                            t.addHeader(
                                    "X-Api-Authorization",
                                    clientSession.getSession().getApiKeys().getServerEncrypted()
                            );

                            t.addHeader(
                                    "X-Server-Timestamp",
                                    String.valueOf(LocalDateTime.now().toEpochSecond(ZoneOffset.UTC))
                            );

                            t.addHeader(
                                    "X-Client-Timestamp",
                                    String.valueOf(System.currentTimeMillis())
                            );

                        }

                    }

                }

                String contentTypeStr = "";

                switch (contentType) {
                    case JSON:
                        contentTypeStr = "application/json";
                        break;
                    case TEXT:
                    default:
                        contentTypeStr = "text/plain";
                        break;
                }

                t.addHeader("Content-Type", contentTypeStr);
                t.setStatus(code);

                byte[] responseBytes = (response == null ? "" : response).getBytes(StandardCharsets.UTF_8);
                try (OutputStream os = t.getOutputStream()) {
                    os.write(responseBytes);
                }
                return commitEncryptedResponse(clientSession);

            } catch (Exception ex) {
                ExceptionHandling.report(ex);
                return false;
            }

        }

    }

}
