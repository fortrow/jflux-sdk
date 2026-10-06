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
 * File created by Ryan Fitzgerald (ryan@fortrow.com) on 2026-05-10 22:56:32 EDT
 *
 * -------------------------------------------------------------------------

 */
package com.fortrow.jflux.http.api.client;

import com.fortrow.jflux.crypto.Rand;
import com.fortrow.jflux.crypto.builtin.AES;
import com.fortrow.jflux.crypto.builtin.RSA;
import com.fortrow.jflux.crypto.builtin.SHA;
import com.fortrow.jflux.encoding.BaseX;
import com.fortrow.jflux.encoding.Hex;
import com.fortrow.jflux.exceptions.ExceptionHandling;
import com.fortrow.jflux.http.api.server.config.HttpVersion;
import com.fortrow.jflux.http.api.server.exceptions.APIException;
import com.fortrow.jflux.http.api.server.encryption.APIKeys;
import com.fortrow.jflux.http.api.server.session.SessionAllocationAssertion;
import com.fortrow.jflux.http.api.types.EncryptedResponseType;
import com.fortrow.jflux.output.json.JSON;
import com.fortrow.jflux.todash.__;
import org.apache.http.HttpEntity;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.conn.ssl.TrustSelfSignedStrategy;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.ssl.SSLContextBuilder;
import org.apache.http.ssl.TrustStrategy;
import org.apache.http.util.EntityUtils;
import org.json.simple.JSONObject;

import javax.net.ssl.SSLContext;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.*;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeUnit;

public class EncryptedClient implements AutoCloseable {
    private final java.util.concurrent.locks.ReentrantLock exchangeLock = new java.util.concurrent.locks.ReentrantLock();


    private static final int DEFAULT_ASYNC_TIMEOUT_MS = 15_000;

    private SSLContext sslContext;
    private CloseableHttpClient http1Client;
    private java.net.http.HttpClient http3Client;
    private ClosableHttp3Transport http3Transport;
    private EncryptedWebSocketTransport webSocketTransport;
    private HttpVersion httpVersion;
    private boolean allowSelfSigned;
    private MtlsConfig mtlsConfig;
    private SSLContext webSocketSslContext;
    private Duration webSocketRequestTimeout = EncryptedWebSocketTransport.DEFAULT_REQUEST_TIMEOUT;

    private String endpointUri = "";
    private String http3UpgradeUri;
    private String apiKey = "";
    private String sessionRandom = "";
    private String sessionId = "";

    private String secret = "";
    private ReadOnlySecretStore pskStore;
    private String sessionSecret = "";
    private String cachedRawSessionSecret = "";
    private String activeSecret = "";
    private String pastSecret = "";
    private String nextRandom = "";
    private String nextAuthorization = "";
    private String nextSessionAuthorization = "";
    private String sessionInitPath = "";
    private String sessionClosePath = "";
    private String sessionResumePath = "/session/resume";
    private String sessionResetPath = "/session/reset";
    private String sessionAllocatePath = "/session/allocate";
    private String sessionHeartbeatPath = "/session/heartbeat";
    private String responsePath = "/response";
    private SecretStore sessionSecretStore;
    private SecretStore resumeTokenStore = new StringSecretStore("");
    private int asyncTimeoutMs = DEFAULT_ASYNC_TIMEOUT_MS;
    private String lastSessionInitError = "";

    private String keySetId = "";
    private byte[] keyTransferBytes;
    private PublicKey serverPublic;
    private PrivateKey clientPrivate;
    private long requestSequence = 0;
    private final Thread shutdownHook;
    private volatile boolean closed;

    public EncryptedClient(String apiKey, String secretKey, String sessionSecret, String psk, String endpointUri, HttpVersion httpVersion, boolean allowSelfSigned) {
        this(apiKey, secretKey, new StringSecretStore(sessionSecret), new StringReadOnlySecretStore(psk), endpointUri, httpVersion, allowSelfSigned);
    }

    public EncryptedClient(String apiKey, String secretKey, String sessionSecret, String psk,
                           String endpointUri, HttpVersion httpVersion, boolean allowSelfSigned,
                           MtlsConfig mtlsConfig) {
        this(apiKey, secretKey, new StringSecretStore(sessionSecret), new StringReadOnlySecretStore(psk),
                endpointUri, httpVersion, allowSelfSigned, mtlsConfig);
    }

    public EncryptedClient(String apiKey, String secretKey, String sessionSecret, File pskFile, String endpointUri, HttpVersion httpVersion, boolean allowSelfSigned) {
        this(apiKey, secretKey, new StringSecretStore(sessionSecret), new FileSecretStore(pskFile.toPath()), endpointUri, httpVersion, allowSelfSigned);
    }

    public EncryptedClient(String apiKey, String secretKey, File sessionSecretFile, File pskFile, String endpointUri, HttpVersion httpVersion, boolean allowSelfSigned) {
        this(apiKey, secretKey, new FileSecretStore(sessionSecretFile.toPath()), new FileSecretStore(pskFile.toPath()), endpointUri, httpVersion, allowSelfSigned);
    }

    public EncryptedClient(String apiKey, String secretKey, File sessionSecretFile, String defaultSessionSecret, File pskFile, String endpointUri, HttpVersion httpVersion, boolean allowSelfSigned) {
        this(apiKey, secretKey, new FileSecretStore(sessionSecretFile.toPath(), defaultSessionSecret), new FileSecretStore(pskFile.toPath()), endpointUri, httpVersion, allowSelfSigned);
    }

    public EncryptedClient(String apiKey, String secretKey, SecretStore sessionSecretStore, ReadOnlySecretStore pskStore, String endpointUri, HttpVersion httpVersion, boolean allowSelfSigned) {
        this(apiKey, secretKey, sessionSecretStore, pskStore, endpointUri, httpVersion, allowSelfSigned, null);
    }

    public EncryptedClient(String apiKey, String secretKey, SecretStore sessionSecretStore,
                           ReadOnlySecretStore pskStore, String endpointUri, HttpVersion httpVersion,
                           boolean allowSelfSigned, MtlsConfig mtlsConfig) {
        if(apiKey == null || apiKey.isBlank()) throw ExceptionHandling.invalid("Encrypted API key is required.");
        if(secretKey == null || secretKey.isBlank()) throw ExceptionHandling.invalid("Encrypted API secret is required.");
        if(endpointUri == null || endpointUri.isBlank()) throw ExceptionHandling.invalid("Encrypted API endpoint URI is required.");
        if(httpVersion == null) throw ExceptionHandling.invalid("Encrypted API HTTP version is required.");
        this.apiKey = apiKey;
        this.secret = secretKey;
        this.sessionSecretStore = Objects.requireNonNull(sessionSecretStore, "Session secret store is required.");
        this.pskStore = Objects.requireNonNull(pskStore, "PSK store is required.");
        this.sessionSecret = SHA.get256(currentRawSessionSecret());
        this.endpointUri = stripTrailingSlash(endpointUri);
        this.httpVersion = httpVersion;
        this.allowSelfSigned = allowSelfSigned;
        this.mtlsConfig = mtlsConfig;
        this.shutdownHook = new Thread(this::shutdown, "jflux-encrypted-client-shutdown");
        Runtime.getRuntime().addShutdownHook(shutdownHook);
    }

    public EncryptedClient setMtlsConfig(MtlsConfig mtlsConfig) {
        this.mtlsConfig = mtlsConfig;
        return this;
    }

    /** Configures the client TLS context used when the endpoint is wss://. */
    public EncryptedClient setWebSocketSslContext(SSLContext sslContext) {
        if (!isWebSocketEndpoint()) throw ExceptionHandling.invalid("A WebSocket SSL context requires a ws:// or wss:// endpoint.");
        if (webSocketTransport != null) throw ExceptionHandling.invalid("WebSocket TLS configuration must be set before build().");
        if (sslContext == null) throw ExceptionHandling.invalid("WebSocket SSL context is required.");
        this.webSocketSslContext = sslContext;
        return this;
    }

    /** Sets the timeout for one encrypted WebSocket request/response exchange. */
    public EncryptedClient setWebSocketRequestTimeout(Duration timeout) {
        if (timeout == null || timeout.isZero() || timeout.isNegative()) {
            throw ExceptionHandling.invalid("WebSocket request timeout must be positive.");
        }
        this.webSocketRequestTimeout = timeout;
        if (webSocketTransport != null) webSocketTransport.setRequestTimeout(timeout);
        return this;
    }

    public EncryptedClient build() {
        exchangeLock.lock();
        try {
            if(closed) throw ExceptionHandling.failure("Encrypted API client has already been closed.", null);
            if(webSocketTransport != null || http1Client != null || http3Transport != null) return this;
            if (isWebSocketEndpoint()) {
                try {
                    webSocketTransport = new EncryptedWebSocketTransport(URI.create(endpointUri))
                            .setRequestTimeout(webSocketRequestTimeout);
                    if (mtlsConfig != null) {
                        if (webSocketSslContext != null) throw ExceptionHandling.invalid("Configure either mTLS material or a custom WebSocket SSL context, not both.");
                        webSocketTransport.setSslContext(TlsMaterial.buildSslContext(mtlsConfig, allowSelfSigned));
                    } else if (webSocketSslContext != null) webSocketTransport.setSslContext(webSocketSslContext);
                    else if (allowSelfSigned) webSocketTransport.allowSelfSigned();
                    if (!webSocketTransport.connect()) {
                        webSocketTransport.close();
                        webSocketTransport = null;
                        throw ExceptionHandling.failure("Unable to connect to the encrypted API WebSocket server.", null);
                    }
                    return this;
                } catch (Exception e) {
                    if (webSocketTransport != null) webSocketTransport.close();
                    webSocketTransport = null;
                    throw ExceptionHandling.failure("Unable to initialize the encrypted API WebSocket client.", e);
                }
            }
            if(httpVersion == HttpVersion.HTTP_1) {
                try {
                    SSLContextBuilder builder = new SSLContextBuilder();
                    if (mtlsConfig != null) {
                        char[] identityPassword = mtlsConfig.identityPassword();
                        try {
                            builder.loadKeyMaterial(loadKeyStore(mtlsConfig.identityStore(),
                                    mtlsConfig.identityStoreType(), identityPassword), identityPassword);
                        } finally {
                            java.util.Arrays.fill(identityPassword, '\0');
                        }
                        if (mtlsConfig.hasTrustStore()) {
                            char[] trustPassword = mtlsConfig.trustStorePassword();
                            try {
                                builder.loadTrustMaterial(loadKeyStore(mtlsConfig.trustStore(),
                                        mtlsConfig.trustStoreType(), trustPassword), null);
                            } finally {
                                java.util.Arrays.fill(trustPassword, '\0');
                            }
                        } else if (allowSelfSigned) {
                            builder.loadTrustMaterial(null, new TrustSelfSignedStrategy());
                        } else {
                            builder.loadTrustMaterial(null, (TrustStrategy) null);
                        }
                    } else if(allowSelfSigned) {
                        builder.loadTrustMaterial(null, new TrustSelfSignedStrategy());
                    } else {
                        builder.loadTrustMaterial(null, (TrustStrategy) null);
                    }
                    sslContext = builder.build();
                    var clientBuilder = HttpClients.custom().setSSLContext(sslContext);
                    if (allowSelfSigned) clientBuilder.setSSLHostnameVerifier(NoopHostnameVerifier.INSTANCE);
                    http1Client = clientBuilder.build();
                } catch (NoSuchAlgorithmException | KeyManagementException | KeyStoreException e) {
                    throw ExceptionHandling.failure("Unable to initialize encrypted API TLS client.", e);
                } catch (java.security.GeneralSecurityException | IOException e) {
                    throw ExceptionHandling.failure("Unable to initialize encrypted API TLS client.", e);
                }
            } else if(httpVersion == HttpVersion.HTTP_3) {
                try {
                    String upgradeSource = http3UpgradeUri;
                    if(upgradeSource == null && endpointUri.startsWith("http://")) upgradeSource = endpointUri;
                    endpointUri = Http3UpgradeResolver.resolveEndpoint(endpointUri, upgradeSource, allowSelfSigned, mtlsConfig);
                } catch (IOException e) {
                    throw ExceptionHandling.failure("Failed to resolve HTTP/3 upgrade endpoint.", e);
                }
                http3Transport = ClosableHttp3Transport.create(endpointUri, allowSelfSigned, mtlsConfig);
                http3Client = http3Transport.client();
            }
            return this;

        } finally { exchangeLock.unlock(); }
    }

    private static KeyStore loadKeyStore(Path path, String type, char[] password)
            throws GeneralSecurityException, IOException {
        KeyStore store = KeyStore.getInstance(type);
        try (var input = Files.newInputStream(path)) {
            store.load(input, password);
        }
        return store;
    }

    public EncryptedClient setResponsePath(String responsePath) {
        this.responsePath = fixPath(responsePath);
        return this;
    }

    public EncryptedClient setResumeTokenStore(SecretStore resumeTokenStore) {
        this.resumeTokenStore = Objects.requireNonNull(resumeTokenStore, "Resume-token store is required.");
        return this;
    }

    public EncryptedClient setResumeTokenFile(File file) {
        if (file == null) throw ExceptionHandling.invalid("Resume-token file is required.");
        return setResumeTokenStore(new FileSecretStore(file.toPath()));
    }

    public EncryptedClient setSessionResumePath(String path) {
        this.sessionResumePath = fixPath(path);
        return this;
    }

    public EncryptedClient setSessionResetPath(String path) {
        this.sessionResetPath = fixPath(path);
        return this;
    }

    public EncryptedClient setSessionAllocatePath(String path) {
        this.sessionAllocatePath = fixPath(path);
        return this;
    }

    public EncryptedClient setHttp3UpgradeUri(String upgradeUri) {
        this.http3UpgradeUri = stripTrailingSlash(upgradeUri);
        return this;
    }

    public EncryptedClient setHttp3UpgradePort(int port) {
        this.http3UpgradeUri = Http3UpgradeResolver.upgradeUri(endpointUri, port, false);
        return this;
    }

    public EncryptedClient setHttp3HttpsUpgradePort(int port) {
        this.http3UpgradeUri = Http3UpgradeResolver.upgradeUri(endpointUri, port, true);
        return this;
    }

    public EncryptedClient setAsyncTimeoutMs(int asyncTimeoutMs) {
        this.asyncTimeoutMs = Math.max(1, asyncTimeoutMs);
        return this;
    }

    public String getLastSessionInitError() {
        return lastSessionInitError;
    }

    public boolean closeSession() {
        return closeSession(false);
    }

    public boolean closeSession(boolean createResumeToken) {
        try {
            closeSessionOrThrow(createResumeToken);
            return true;
        } catch (APIException ignored) {
            return false;
        } finally {
            close();
        }
    }

    public void discardSessionState() {
        exchangeLock.lock();
        try {
            clearSessionState();

        } finally { exchangeLock.unlock(); }
    }

    public String currentReconnectSessionSecret() {
        return currentRawSessionSecret();
    }

    public void restoreReconnectSessionSecret(String rawSecret) {
        exchangeLock.lock();
        try {
            persistAuthenticatedSessionSecret(rawSecret);

        } finally { exchangeLock.unlock(); }
    }

    public void checkpointReconnectSessionSecret() {
        exchangeLock.lock();
        try {
            persistAuthenticatedSessionSecret(pastSecret);

        } finally { exchangeLock.unlock(); }
    }

    public EncryptedResponse closeSessionOrThrow() throws APIException {
        exchangeLock.lock();
        try {
            return closeSessionOrThrow(false);

        } finally { exchangeLock.unlock(); }
    }

    public EncryptedResponse closeSessionOrThrow(boolean createResumeToken) throws APIException {
        exchangeLock.lock();
        try {
            if(!hasActiveSession()) throw new APIException("Encrypted session has not been initialized.");
            if(sessionClosePath == null || sessionClosePath.isBlank()) throw new APIException("Encrypted session close path is not configured.");

            JSONObject closeBody = new JSONObject();
            if (createResumeToken) closeBody.put("create_resume_token", true);
            EncryptedResponse response = request(sessionClosePath, closeBody, EncryptedResponseType.SESSION_CLOSE, false);
            if(response == null) throw new APIException("No response was returned from session close.");
            if(response.getStatus() != 200 || !"success".equals(response.getRequestStatus())) {
                throw new APIException("Encrypted session close was not successful. status=" + response.getStatus() + ", body=" + response.getBody());
            }
            if(!response.isVerified()) throw new APIException("Encrypted session close response authorization failed.");

            persistAuthenticatedSessionSecret(this.pastSecret);
            if (createResumeToken) {
                String resumeToken = stringValue(response.getBodyObject().get("resume_token"));
                if (resumeToken.isBlank()) {
                    clearSessionState();
                    throw new APIException("Session close did not return the requested resume token.");
                }
                try {
                    resumeTokenStore.write(resumeToken);
                } catch (RuntimeException ex) {
                    clearSessionState();
                    throw new APIException("Unable to persist the encrypted session resume token.", ex);
                }
            } else {
                clearResumeToken();
            }

            clearSessionState();
            return response;

        } finally { exchangeLock.unlock(); }
    }

    /** Sends an authenticated encrypted heartbeat without closing the session. */
    public EncryptedResponse heartbeat() throws APIException {
        return heartbeat(false);
    }

    /** Requests an early resume token only when the server permits issuance. */
    public EncryptedResponse heartbeat(boolean createResumeToken) throws APIException {
        if(sessionHeartbeatPath == null || sessionHeartbeatPath.isBlank()) {
            throw new APIException("Encrypted session heartbeat path is not configured.");
        }
        JSONObject body = new JSONObject();
        if (createResumeToken) body.put("create_resume_token", true);
        EncryptedResponse response = request(sessionHeartbeatPath, body);
        if (createResumeToken) {
            if (response == null || response.getStatus() != 200 || !response.isVerified())
                throw new APIException("Early resume token request failed.");
            String token = stringValue(response.getBodyObject().get("resume_token"));
            if (token.isBlank()) throw new APIException("Server did not issue an early resume token.");
            resumeTokenStore.write(token);
        }
        return response;
    }

    protected void shutdown() {
        close();
    }

    public EncryptedResponse get(String path, JSONObject body) throws APIException {
        return request(path, RequestType.GET, body);
    }

    public EncryptedResponse post(String path, JSONObject body) throws APIException {
        return request(path, RequestType.POST, body);
    }

    public EncryptedResponse put(String path, JSONObject body) throws APIException {
        return request(path, RequestType.PUT, body);
    }

    public EncryptedResponse patch(String path, JSONObject body) throws APIException {
        return request(path, RequestType.PATCH, body);
    }

    public EncryptedResponse delete(String path, JSONObject body) throws APIException {
        return request(path, RequestType.DELETE, body);
    }

    public EncryptedResponse request(String path, JSONObject body) throws APIException {
        return request(path, RequestType.POST, body);
    }

    public EncryptedResponse request(String path, RequestType resourceMethod, JSONObject body) throws APIException {
        return request(path, resourceMethod, body, EncryptedResponseType.EXECUTE_RESOURCE, true);
    }

    private EncryptedResponse request(String path, JSONObject body, EncryptedResponseType responseType, boolean updateRequestVars) throws APIException {
        return request(path, RequestType.POST, body, responseType, updateRequestVars);
    }

    private EncryptedResponse request(String path, RequestType resourceMethod, JSONObject body, EncryptedResponseType responseType, boolean updateRequestVars) throws APIException {
        exchangeLock.lock();
        try {

            if(closed) throw new APIException("Encrypted API client is closed.");

            String fixedPath = fixPath(path);
            if(!hasActiveSession()) throw new APIException("Encrypted API session has not been initialized.");
            HashMap<String, String> headers = new HashMap<String, String>();

            String timestamp = "" + Instant.now().getEpochSecond();
            String sequence = String.valueOf(++requestSequence);
            JSONObject requestBody = body == null ? new JSONObject() : new JSONObject(body);
            boolean sessionCloseRequest = isSessionClosePath(fixedPath);
            if(sessionCloseRequest) authorizeSessionClose(requestBody, sequence);
            String plaintext = JSON.getString(requestBody);
            String contentType = "application/json";
            String logicalMethod = normalizeResourceMethod(resourceMethod);

            headers.put("Content-Type", contentType);
            headers.put("Content-Transfer-Encoding", "x-encrypted-base64");

            headers.put("X-Api-Key", apiKey);
            headers.put("X-Api-Session-Id", sessionId);
            headers.put("X-Api-Session-Authorization", nextSessionAuthorization);
            headers.put("X-Api-Authorization", nextAuthorization);
            headers.put("X-Api-Random-Bytes", nextRandom);
            headers.put("X-Api-Key-Set-Id", keySetId);
            headers.put("X-Request-Sequence", sequence);
            headers.put("X-Request-Timestamp", timestamp);
            if(!sessionCloseRequest) headers.put("X-Api-Resource-Method", logicalMethod);

            String signedResourceMethod = sessionCloseRequest ? "POST" : logicalMethod;
            byte[] aad = APIKeys.requestAad("POST", fixedPath, timestamp, sequence, apiKey, sessionId, keySetId, nextRandom, contentType, signedResourceMethod);
            byte[] encrypted = AES.gcm(plaintext.getBytes(StandardCharsets.UTF_8), Hex.fromHex(activeSecret), Hex.fromHex(nextRandom), aad, true);
            if (encrypted == null) {
                throw new APIException("Failed to encrypt request payload.");
            }
            String encryptedRequest = BaseX.encode64(encrypted);
            String signature = APIKeys.requestSignature(secret, activeSecret, apiKey, sessionId, "POST", fixedPath, timestamp, sequence, keySetId, nextRandom, contentType, encryptedRequest, signedResourceMethod);
            headers.put("X-Request-Signature", signature);

            return post(fixedPath, encryptedRequest, headers, aad, responseType, updateRequestVars);


        } finally { exchangeLock.unlock(); }
    }

    /** Sends arbitrary encrypted resource bytes over WSS; session controls remain text. */
    public EncryptedResponse requestBytes(String path, RequestType resourceMethod, byte[] plaintext) throws APIException {
        exchangeLock.lock();
        try {

            if(closed) throw new APIException("Encrypted API client is closed.");
            if (!"wss".equalsIgnoreCase(URI.create(endpointUri).getScheme()) || allowSelfSigned || webSocketTransport == null || mtlsConfig == null)
                throw new APIException("Binary API requires a built WSS client with validating mTLS configuration.");
            if (plaintext == null || plaintext.length > com.fortrow.jflux.http.api.server.containers.EncryptedPayload.MAX_BINARY_PLAINTEXT_BYTES)
                throw new APIException("Binary request size is invalid.");

            String fixedPath = fixPath(path);
            if(!hasActiveSession()) throw new APIException("Encrypted API session has not been initialized.");
            HashMap<String, String> headers = new HashMap<String, String>();

            String timestamp = "" + Instant.now().getEpochSecond();
            String sequence = String.valueOf(++requestSequence);
            boolean sessionCloseRequest = isSessionClosePath(fixedPath);
            if (sessionCloseRequest || fixedPath.equals(fixPath(sessionInitPath))) throw new APIException("Binary session controls are unsupported.");
            String contentType = "application/octet-stream";
            String logicalMethod = normalizeResourceMethod(resourceMethod);

            headers.put("Content-Type", contentType);
            headers.put("Content-Transfer-Encoding", "x-encrypted-base64");

            headers.put("X-Api-Key", apiKey);
            headers.put("X-Api-Session-Id", sessionId);
            headers.put("X-Api-Session-Authorization", nextSessionAuthorization);
            headers.put("X-Api-Authorization", nextAuthorization);
            headers.put("X-Api-Random-Bytes", nextRandom);
            headers.put("X-Api-Key-Set-Id", keySetId);
            headers.put("X-Request-Sequence", sequence);
            headers.put("X-Request-Timestamp", timestamp);
            if(!sessionCloseRequest) headers.put("X-Api-Resource-Method", logicalMethod);

            String signedResourceMethod = sessionCloseRequest ? "POST" : logicalMethod;
            byte[] aad = APIKeys.requestAad("POST", fixedPath, timestamp, sequence, apiKey, sessionId, keySetId, nextRandom, contentType, signedResourceMethod);
            byte[] encrypted = AES.gcm(plaintext, Hex.fromHex(activeSecret), Hex.fromHex(nextRandom), aad, true);
            if (encrypted == null) {
                throw new APIException("Failed to encrypt request payload.");
            }
            String encryptedRequest = BaseX.encode64(encrypted);
            String signature = APIKeys.requestSignature(secret, activeSecret, apiKey, sessionId, "POST", fixedPath, timestamp, sequence, keySetId, nextRandom, contentType, encryptedRequest, signedResourceMethod);
            headers.put("X-Request-Signature", signature);

            var response = webSocketTransport.executeBinary("POST", fixedPath, headers, encrypted);
            var verified = EncryptedResponse.binary(response.status(), new ResponseHeaders(response.headers()), response.ciphertext(),
                    apiKey, secret, activeSecret, sessionRandom, serverPublic, clientPrivate, aad);
            setRequestVars(verified);
            return verified;


        } finally { exchangeLock.unlock(); }
    }

    public EncryptedResponse postBytes(String path, byte[] plaintext) throws APIException {
        return requestBytes(path, RequestType.POST, plaintext);
    }

    public EncryptedResponse requestAndWait(String path, JSONObject body) throws APIException {
        return awaitAsync(request(path, body));
    }

    public EncryptedResponse requestAndWait(String path, RequestType resourceMethod, JSONObject body) throws APIException {
        return awaitAsync(request(path, resourceMethod, body));
    }

    public EncryptedResponse awaitAsync(EncryptedResponse initialResponse) throws APIException {
        if(initialResponse == null) throw new APIException("Encrypted request returned no response.");
        if(!initialResponse.isRedirect()) return initialResponse;

        JSONObject body = initialResponse.getBodyObject();
        String responseId = stringValue(body.get("response_id"));
        String authSeed = stringValue(body.get("initial_auth"));
        int pollRate = intValue(body.get("poll_rate"), 100);
        long deadline = System.currentTimeMillis() + asyncTimeoutMs;

        while(System.currentTimeMillis() < deadline) {
            sleep(pollRate);
            JSONObject poll = new JSONObject();
            poll.put("response_id", responseId);
            poll.put("authorization", asyncAuthorization(authSeed));

            EncryptedResponse pollResponse = request(responsePath, poll);
            JSONObject pollBody = pollResponse.getBodyObject();

            if(pollResponse.isRedirect()) {
                responseId = stringValue(pollBody.getOrDefault("response_id", responseId));
                authSeed = stringValue(pollBody.getOrDefault("next_auth", authSeed));
                pollRate = intValue(pollBody.get("poll_rate"), pollRate);
                continue;
            }

            if(pollResponse.isTooManyRequests()) {
                authSeed = stringValue(pollBody.getOrDefault("next_auth", authSeed));
                pollRate = intValue(pollBody.get("poll_rate"), pollRate);
                continue;
            }

            return pollResponse;
        }

        throw new APIException("Timed out waiting for encrypted async response.");
    }

    /** Requests a one-time challenge for the external authentication backend. */
    public JSONObject requestAllocationChallenge() throws APIException {
        return allocationControl(new JSONObject());
    }

    /**
     * Submits the external authentication backend's signed assertion. Every
     * accepted assertion increases this API key's capacity by exactly one.
     */
    public JSONObject allocateSession(SessionAllocationAssertion assertion) throws APIException {
        if (assertion == null) throw new APIException("Session allocation assertion is required.");
        return allocationControl(assertion.toJson());
    }

    /** Convenience overload for clients that receive the assertion as flat fields. */
    public JSONObject allocateSession(String challengeId, String challenge,
                                     long issuedAtMs, long expiresAtMs, String signature) throws APIException {
        return allocateSession(new SessionAllocationAssertion(
                apiKey, challengeId, challenge, issuedAtMs, expiresAtMs, signature));
    }

    /** @deprecated JFlux no longer receives username/password/MFA values. */
    @Deprecated
    public JSONObject requestAllocationChallenge(String ignoredUsername) throws APIException {
        return requestAllocationChallenge();
    }

    /** @deprecated Authenticate users in the external web-authentication backend and submit a signed assertion. */
    @Deprecated
    public JSONObject authenticateAllocation(String username, String challengeId, String challenge,
                                             String challengeResponse, String mfaResponse) throws APIException {
        throw new APIException("JFlux does not receive username, password, or MFA values. Submit a signed session allocation assertion.");
    }

    /** @deprecated Allocation accepts one signed assertion and always adds one session slot. */
    @Deprecated
    public JSONObject allocateSessions(String oauthToken, int additionalSessions) throws APIException {
        throw new APIException("OAuth-style allocation tokens are no longer supported. Submit a signed session allocation assertion.");
    }

    /** Invalidates this API key's sessions/tokens and restores its capacity to one. */
    public JSONObject resetSessions() throws APIException {
        JSONObject response = allocationControl(new JSONObject(), sessionResetPath);
        if ("success".equals(stringValue(response.get("request_status")))) clearResumeToken();
        return response;
    }

    public static boolean isSessionLimitReached(JSONObject response) {
        return response != null && "JFLUX_SESSION_LIMIT_REACHED".equals(stringValue(response.get("error_code")));
    }

    private JSONObject allocationControl(JSONObject body) throws APIException {
        return allocationControl(body, sessionAllocatePath);
    }

    private JSONObject allocationControl(JSONObject body, String path) throws APIException {
        EncryptedResponse response = postBootstrap(fixPath(path), body, EncryptedResponseType.SESSION_RESUME);
        JSONObject result = response == null ? new JSONObject() : response.getBodyObject();
        if (result.isEmpty() && response != null && response.getData() != null) result = response.getData();
        return result;
    }


    private static String normalizeResourceMethod(RequestType resourceMethod) {
        return resourceMethod == null ? RequestType.POST.name() : resourceMethod.name();
    }

    private String fixPath(String input) {
        if(input == null || input.isBlank()) return "/";
        input = input.trim().replaceAll("\\s+", "");
        input = input.replaceAll("\\.", "/");
        if(input.charAt(0) != '/') input = "/" + input;
        while(input.length() > 1 && input.charAt(input.length() - 1) == '/') input = input.substring(0, input.length() - 1);
        return input;
    }

    public boolean beginSession(String sessionInitPath, String sessionClosePath) {
        exchangeLock.lock();
        try {

            return beginSession(sessionInitPath, sessionClosePath, "/session/resume", "/session/reset", "/session/allocate");

        } finally { exchangeLock.unlock(); }
    }

    public boolean beginSession(String sessionInitPath, String sessionClosePath,
                                String sessionResumePath, String sessionResetPath,
                                String sessionAllocatePath) {
        exchangeLock.lock();
        try {
            return beginSession(sessionInitPath, sessionClosePath, sessionResumePath, sessionResetPath, sessionAllocatePath, "/session/heartbeat");

        } finally { exchangeLock.unlock(); }
    }

    public boolean beginSession(String sessionInitPath, String sessionClosePath,
                                String sessionResumePath, String sessionResetPath,
                                String sessionAllocatePath, String sessionHeartbeatPath) {
        exchangeLock.lock();
        try {

            if(sessionInitPath == null || sessionInitPath.isBlank()) throw ExceptionHandling.invalid("Encrypted session init path is required.");
            if(sessionClosePath == null || sessionClosePath.isBlank()) throw ExceptionHandling.invalid("Encrypted session close path is required.");

            this.sessionInitPath = fixPath(sessionInitPath);
            this.sessionClosePath = fixPath(sessionClosePath);
            this.sessionResumePath = fixPath(sessionResumePath);
            this.sessionResetPath = fixPath(sessionResetPath);
            this.sessionAllocatePath = fixPath(sessionAllocatePath);
            this.sessionHeartbeatPath = fixPath(sessionHeartbeatPath);
            this.sessionSecret = SHA.get256(currentRawSessionSecret());

            if (!readOptionalSecret(resumeTokenStore).isBlank() && resume()) return true;
            return init();


        } finally { exchangeLock.unlock(); }
    }

    public void beginSessionOrThrow(String sessionInitPath, String sessionClosePath) throws APIException {
        if(!beginSession(sessionInitPath, sessionClosePath)) {
            String detail = lastSessionInitError == null || lastSessionInitError.isBlank() ? "No session-init detail was captured." : lastSessionInitError;
            throw new APIException("Failed to begin encrypted API session: " + detail);
        }
    }

    public void beginSessionOrThrow(String sessionInitPath, String sessionClosePath,
                                    String sessionResumePath, String sessionResetPath,
                                    String sessionAllocatePath) throws APIException {
        if(!beginSession(sessionInitPath, sessionClosePath, sessionResumePath, sessionResetPath, sessionAllocatePath)) {
            String detail = lastSessionInitError == null || lastSessionInitError.isBlank() ? "No session detail was captured." : lastSessionInitError;
            throw new APIException("Failed to begin encrypted API session: " + detail);
        }
    }

    public void beginSessionOrThrow(String sessionInitPath, String sessionClosePath,
                                    String sessionResumePath, String sessionResetPath,
                                    String sessionAllocatePath, String sessionHeartbeatPath) throws APIException {
        if(!beginSession(sessionInitPath, sessionClosePath, sessionResumePath, sessionResetPath, sessionAllocatePath, sessionHeartbeatPath)) {
            String detail = lastSessionInitError == null || lastSessionInitError.isBlank() ? "No session detail was captured." : lastSessionInitError;
            throw new APIException("Failed to begin encrypted API session: " + detail);
        }
    }

    private EncryptedResponse get(String path, HashMap<String, String> requestHeaders) throws APIException {
        return get(path, requestHeaders, EncryptedResponseType.SESSION_INIT);
    }

    private EncryptedResponse get(String path, HashMap<String, String> requestHeaders, EncryptedResponseType responseType) throws APIException {

        try {

            if (isWebSocketEndpoint()) {
                if (webSocketTransport == null) throw new APIException("Encrypted WebSocket client has not been built.");
                EncryptedWebSocketTransport.Response response = webSocketTransport.execute("GET", path, requestHeaders, "");
                return new EncryptedResponse(response.status(), new ResponseHeaders(response.headers()), response.body(),
                        this.apiKey, this.secret, this.activeSecret, this.sessionRandom, serverPublic, clientPrivate, responseType, true);
            }

            ExecutesClientRequest httpRequest;
            if(httpVersion == HttpVersion.HTTP_1) {
                if(http1Client == null) throw new APIException("Encrypted HTTP/1 client has not been built.");
                httpRequest = new Http1Request(http1Client, this.endpointUri + path, RequestType.GET, requestHeaders);
            } else if(httpVersion == HttpVersion.HTTP_3) {
                if(http3Client == null) throw new APIException("Encrypted HTTP/3 client has not been built.");
                httpRequest = new Http3Request(http3Client, this.endpointUri + path, RequestType.GET, requestHeaders);
            } else throw new APIException("Encrypted HTTP version is not supported.");

            httpRequest.execute();

            return new EncryptedResponse(httpRequest.getStatus(), httpRequest.getHeaders(), httpRequest.getBody(), this.apiKey, this.secret, this.activeSecret, this.sessionRandom, serverPublic, clientPrivate, responseType, true);

        } catch (APIException e) {
            throw e;
        } catch (Exception e) {
            throw new APIException("Encrypted API bootstrap transport failed.", e);
        }

    }

    private EncryptedResponse postBootstrap(String path, JSONObject body, EncryptedResponseType responseType) throws APIException {
        HashMap<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "application/json");
        headers.put("X-Api-Key", apiKey);
        String randomBytes = getRandomBytes(44);
        headers.put("X-Api-Random-Bytes", randomBytes);
        headers.put("X-Api-Session-Authorization", sessionSecret);
        return postBootstrap(path, body, responseType, headers);
    }

    private EncryptedResponse postBootstrap(String path, JSONObject body, EncryptedResponseType responseType,
                                            HashMap<String, String> headers) throws APIException {
        try {
            headers.putIfAbsent("X-Request-Timestamp", Long.toString(System.currentTimeMillis() / 1000));
            if (fixPath(path).equals(sessionResetPath)) {
                String timestamp = headers.get("X-Request-Timestamp");
                String randomBytes = headers.get("X-Api-Random-Bytes");
                headers.put("X-Api-Reset-Authorization",
                        APIKeys.sessionResetAuthorization(secret, apiKey, timestamp, randomBytes));
            }
            String requestBody = JSON.getString(body == null ? new JSONObject() : body);
            if (isWebSocketEndpoint()) {
                if (webSocketTransport == null) throw new APIException("Encrypted WebSocket client has not been built.");
                EncryptedWebSocketTransport.Response response = webSocketTransport.execute("POST", path, headers, requestBody);
                return new EncryptedResponse(response.status(), new ResponseHeaders(response.headers()), response.body(),
                        apiKey, secret, activeSecret, sessionRandom, serverPublic, clientPrivate, responseType, null);
            }
            HttpEntity entity = new StringEntity(requestBody);
            ExecutesClientRequest httpRequest;
            if(httpVersion == HttpVersion.HTTP_1) {
                if(http1Client == null) throw new APIException("Encrypted HTTP/1 client has not been built.");
                httpRequest = new Http1Request(http1Client, this.endpointUri + path, RequestType.POST, headers, entity);
            } else if(httpVersion == HttpVersion.HTTP_3) {
                if(http3Client == null) throw new APIException("Encrypted HTTP/3 client has not been built.");
                httpRequest = new Http3Request(http3Client, this.endpointUri + path, RequestType.POST, headers, requestBody);
            } else throw new APIException("Encrypted HTTP version is not supported.");
            httpRequest.execute();
            return new EncryptedResponse(httpRequest.getStatus(), httpRequest.getHeaders(), httpRequest.getBody(),
                    apiKey, secret, activeSecret, sessionRandom, serverPublic, clientPrivate, responseType, null);
        } catch (APIException e) {
            throw e;
        } catch (Exception e) {
            throw new APIException("Encrypted API bootstrap transport failed.", e);
        }
    }

    private EncryptedResponse post(String path, String encryptedRequest, HashMap<String, String> requestHeaders, byte[] aad, EncryptedResponseType responseType, boolean updateRequestVars) throws APIException {

        try {

            if (isWebSocketEndpoint()) {
                if (webSocketTransport == null) throw new APIException("Encrypted WebSocket client has not been built.");
                EncryptedWebSocketTransport.Response response = webSocketTransport.execute("POST", path, requestHeaders, encryptedRequest);
                EncryptedResponse encryptedResponse = new EncryptedResponse(response.status(), new ResponseHeaders(response.headers()), response.body(),
                        this.apiKey, this.secret, this.activeSecret, this.sessionRandom, serverPublic, clientPrivate, responseType, aad);
                if (updateRequestVars) setRequestVars(encryptedResponse);
                return encryptedResponse;
            }

            HttpEntity entity = new StringEntity(encryptedRequest);

            ExecutesClientRequest httpRequest;
            if(httpVersion == HttpVersion.HTTP_1) {
                if(http1Client == null) throw new APIException("Encrypted HTTP/1 client has not been built.");
                httpRequest = new Http1Request(http1Client, this.endpointUri + path, RequestType.POST, requestHeaders, entity);
            } else if(httpVersion == HttpVersion.HTTP_3) {
                if(http3Client == null) throw new APIException("Encrypted HTTP/3 client has not been built.");
                httpRequest = new Http3Request(http3Client, this.endpointUri + path, RequestType.POST, requestHeaders, encryptedRequest);
            } else throw new APIException("Encrypted HTTP version is not supported.");

            httpRequest.execute();

            EncryptedResponse resp = new EncryptedResponse(httpRequest.getStatus(), httpRequest.getHeaders(), httpRequest.getBody(), this.apiKey, this.secret, this.activeSecret, this.sessionRandom, serverPublic, clientPrivate, responseType, aad);
            if(updateRequestVars) setRequestVars(resp);
            return resp;

        } catch (APIException e) {
            throw e;
        } catch (Exception e) {
            throw new APIException("Encrypted API request transport failed.", e);
        }

    }

    private static String rootMessage(Throwable throwable) {
        Throwable cursor = throwable;
        while(cursor != null && cursor.getCause() != null) cursor = cursor.getCause();
        String message = cursor == null ? null : cursor.getMessage();
        if(message == null || message.isBlank()) message = cursor == null ? String.valueOf(throwable) : cursor.getClass().getSimpleName();
        return message;
    }

    private boolean init() {

        String randomBytesHex = getRandomBytes(44);
        HashMap<String, String> headers = new HashMap<String, String>();
        headers.put("Content-Type", "application/json");
        headers.put("X-Api-Key", apiKey);
        headers.put("X-Api-Random-Bytes", randomBytesHex);
        headers.put("X-Request-Timestamp", Long.toString(System.currentTimeMillis() / 1000));
        headers.put("X-Api-Session-Authorization", sessionSecret);

        sessionRandom = randomBytesHex.substring(0, 64);

        lastSessionInitError = "";
        EncryptedResponse response;
        try {
            response = get(this.sessionInitPath, headers, EncryptedResponseType.SESSION_INIT);
        } catch (APIException e) {
            lastSessionInitError = e.getMessage();
            return false;
        }

        return finishBootstrap(response, "Session init");

    }

    private boolean resume() {
        String token = readOptionalSecret(resumeTokenStore);
        if (token.isBlank()) return false;
        String randomBytesHex = getRandomBytes(44);
        sessionRandom = randomBytesHex.substring(0, 64);
        JSONObject body = new JSONObject();
        body.put("resume_token", token);
        HashMap<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "application/json");
        headers.put("X-Api-Key", apiKey);
        headers.put("X-Api-Random-Bytes", randomBytesHex);
        headers.put("X-Request-Timestamp", Long.toString(System.currentTimeMillis() / 1000));
        headers.put("X-Api-Session-Authorization", sessionSecret);
        lastSessionInitError = "";
        EncryptedResponse response;
        try {
            response = postBootstrap(sessionResumePath, body, EncryptedResponseType.SESSION_RESUME, headers);
        } catch (APIException e) {
            lastSessionInitError = e.getMessage();
            return false;
        }
        if (!finishBootstrap(response, "Session resume")) return false;
        clearResumeToken();
        return true;
    }

    private boolean finishBootstrap(EncryptedResponse response, String label) {
        if(response == null) {
            lastSessionInitError = "No response was returned from " + label.toLowerCase() + ".";
            return false;
        }
        if(response.getData() == null || !response.getData().containsKey("request_status")) {
            lastSessionInitError = label + " response did not include request_status. status=" + response.getStatus() + ", body=" + response.getBody();
            return false;
        }
        if(!"success".equals(String.valueOf(response.getData().get("request_status")))) {
            lastSessionInitError = label + " was not successful. status=" + response.getStatus() + ", body=" + response.getBody();
            return false;
        }
        if(!response.getData().containsKey("iv")) {
            lastSessionInitError = label + " response did not include iv. status=" + response.getStatus() + ", body=" + response.getBody();
            return false;
        }
        try {
            if(!setKeyVars(response)) {
                lastSessionInitError = label + " key transfer failed.";
                return false;
            }
        } catch (RuntimeException ex) {
            lastSessionInitError = label + " key transfer failed: " + rootMessage(ex);
            new APIException(lastSessionInitError, ex).tryReport();
            return false;
        }
        if(!response.verifyLate(apiKey, secret, sessionRandom, clientPrivate, serverPublic)) {
            lastSessionInitError = label + " response authorization failed.";
            return false;
        }
        this.activeSecret = response.nextSecretKey();
        this.pastSecret = response.currentSecretKey();
        this.nextRandom = response.nextRandom();
        this.nextAuthorization = response.nextAuthorization();
        this.nextSessionAuthorization = response.nextSessionAuthorization(sessionId);
        return true;
    }

    @Override
    public void close() {
        exchangeLock.lock();
        try {
            if(closed) return;
            closed = true;
            if(http3Transport != null) {
                http3Transport.close();
                http3Transport = null;
                http3Client = null;
            }
            if (webSocketTransport != null) {
                webSocketTransport.close();
                webSocketTransport = null;
            }
            if(http1Client instanceof java.io.Closeable closeable) {
                try {
                    closeable.close();
                } catch (IOException ignored) {
                }
                http1Client = null;
            }
            sslContext = null;
            try {
                Runtime.getRuntime().removeShutdownHook(shutdownHook);
            } catch (IllegalStateException ignored) {
                // JVM shutdown is already in progress.
            }

        } finally { exchangeLock.unlock(); }
    }

    private boolean isWebSocketEndpoint() {
        return endpointUri.regionMatches(true, 0, "ws://", 0, 5)
                || endpointUri.regionMatches(true, 0, "wss://", 0, 6);
    }

    private boolean setKeyVars(EncryptedResponse response) {

        String ciphertext = (String) response.getData().get("response");
        String iv = (String) response.getData().get("iv");
        JSONObject json = null;
        String psk = readSecret(pskStore, "pre-shared key");
        if(psk.length() != 64) throw new RuntimeException("Invalid length for Pre-Shared Key");
        byte[] key = Hex.fromHex(psk);
        if(!__.isset(key)) throw new RuntimeException("Invalid Pre-Shared Key");
        String resp = new String(Objects.requireNonNull(AES.gcm(BaseX.bytesFrom64(ciphertext), key, Hex.fromHex(iv), null, false)));
        json = JSON.getObject(resp);
        if(!__.isset(json)) throw new RuntimeException("Failed to parse request JSON.");
        this.sessionId = (String) json.get("session_id");
        this.serverPublic = RSA.publicKeyFromBytes(BaseX.bytesFrom64((String) json.get("server_public_key")));
        this.clientPrivate = RSA.privateKeyFromBytes(BaseX.bytesFrom64((String) json.get("client_private_key")));
        this.keySetId = (String) json.get("key_set_id");
        this.activeSecret = (String) json.get("session_secret");
        this.keyTransferBytes = Hex.fromHex((String) json.get("random_bytes"));

        return true;

    }

    private boolean setRequestVars(EncryptedResponse response) {

        if(!response.isVerified()) return false;

        String nextServerSecret = response.currentSecretKey();
        if(!__.isset(nextServerSecret)) return false;

        String lastAcceptedServerSecret = this.pastSecret;
        this.activeSecret = response.nextSecretKey();
        this.pastSecret = nextServerSecret;
        this.nextRandom = response.nextRandom();
        this.nextAuthorization = response.nextAuthorization();
        this.nextSessionAuthorization = response.nextSessionAuthorization(sessionId);
        persistAuthenticatedSessionSecret(lastAcceptedServerSecret);

        return true;

    }


    private void authorizeSessionClose(JSONObject body, String sequence) throws APIException {
        if(!hasActiveSession()) throw new APIException("Encrypted session has not been initialized.");
        body.put("session_id", sessionId);
        body.put("key_set_id", keySetId);
        body.put("authorization", APIKeys.sessionCloseAuthorization(secret, activeSecret, apiKey, sessionId, keySetId, sequence));
    }

    private boolean isSessionClosePath(String fixedPath) {
        return sessionClosePath != null && !sessionClosePath.isBlank() && fixedPath.equals(fixPath(sessionClosePath));
    }

    private boolean hasActiveSession() {
        return __.isset(sessionId)
                && __.isset(keySetId)
                && __.isset(activeSecret)
                && __.isset(nextRandom)
                && __.isset(nextAuthorization)
                && __.isset(nextSessionAuthorization);
    }

    private void clearSessionState() {
        sessionRandom = "";
        sessionId = "";
        activeSecret = "";
        pastSecret = "";
        nextRandom = "";
        nextAuthorization = "";
        nextSessionAuthorization = "";
        keySetId = "";
        keyTransferBytes = null;
        serverPublic = null;
        clientPrivate = null;
        requestSequence = 0;
    }


    private String asyncAuthorization(String authSeed) throws APIException {
        if(keyTransferBytes == null || keyTransferBytes.length == 0) {
            throw new APIException("Encrypted async polling requires an initialized session.");
        }
        return SHA.getHmac384Hex(authSeed, keyTransferBytes);
    }

    private void persistAuthenticatedSessionSecret(String rawSecret) {
        if(rawSecret == null || rawSecret.isBlank()) return;
        cacheRawSessionSecret(rawSecret);
        sessionSecretStore.write(rawSecret);
        this.sessionSecret = SHA.get256(rawSecret);
    }

    private void clearResumeToken() {
        try {
            resumeTokenStore.clear();
        } catch (RuntimeException ex) {
            new APIException("Unable to clear the encrypted session resume token.", ex).tryReport();
        }
    }

    private String currentRawSessionSecret() {
        try {
            return cacheRawSessionSecret(readSecret(this.sessionSecretStore, "session secret"));
        } catch (RuntimeException e) {
            if(__.isset(cachedRawSessionSecret)) return cachedRawSessionSecret;
            throw e;
        }
    }

    private String cacheRawSessionSecret(String rawSecret) {
        if(rawSecret == null || rawSecret.isBlank()) return cachedRawSessionSecret;
        cachedRawSessionSecret = rawSecret.trim();
        return cachedRawSessionSecret;
    }

    private static String readSecret(ReadOnlySecretStore store, String label) {
        if(store == null) throw ExceptionHandling.invalid(label + " store is required.");
        String value = store.read();
        if(value == null || value.isBlank()) throw ExceptionHandling.invalid(label + " is required.");
        return value.trim();
    }

    private static String readOptionalSecret(ReadOnlySecretStore store) {
        if (store == null) return "";
        try {
            String value = store.read();
            return value == null ? "" : value.trim();
        } catch (RuntimeException ignored) {
            return "";
        }
    }

    private static String stripTrailingSlash(String input) {
        if(input == null) return "";
        while(input.length() > 1 && input.endsWith("/")) input = input.substring(0, input.length() - 1);
        return input;
    }

    private static String stringValue(Object value) {
        return value == null ? "" : value.toString();
    }

    private static int intValue(Object value, int fallback) {
        if(value == null) return fallback;
        if(value instanceof Number number) return number.intValue();
        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static void sleep(int millis) throws APIException {
        try {
            TimeUnit.MILLISECONDS.sleep(Math.max(1, millis));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new APIException("Interrupted while waiting for encrypted async response.");
        }
    }

    private String getRandomBytes(int len) {
        return Hex.getHex(Rand.secure(len));
    }

    private byte[] addBytes(byte[] arr1, byte[] arr2) {

        if(arr1.length == arr2.length) {

            byte[] outArr = new byte[arr1.length];

            for(int i=0; i<outArr.length; i++) {
                outArr[i] = (byte) (arr1[i] + arr2[i]);
            }

            return outArr;

        }

        return null;

    }

    private byte[] subtractBytes(byte[] inputArr, byte[] byArr) {

        if(inputArr.length == byArr.length) {

            byte[] outArr = new byte[inputArr.length];

            for(int i=0; i<outArr.length; i++) {
                outArr[i] = (byte) (inputArr[i] - byArr[i]);
            }

            return outArr;

        }

        return null;

    }

    private static class Http1Request implements ExecutesClientRequest {

        private final CloseableHttpClient httpClient;
        private RequestType requestMethod;

        private int status = -1;
        private String body = null;
        private ResponseHeaders headers = null;

        private HttpGet get;
        private HttpPost post;

        public Http1Request(CloseableHttpClient client, String endpointUri, RequestType requestMethod, HashMap<String, String> requestHeaders) {

            this.httpClient = client;
            this.requestMethod = requestMethod;

            if(requestMethod == RequestType.GET) {
                get = new HttpGet(endpointUri);
                for(Map.Entry<String, String> entry : requestHeaders.entrySet()) {
                    String key = entry.getKey();
                    String value = entry.getValue();
                    get.setHeader(key, value);
                }
                get.setHeader("X-Request-Timestamp", "" + Instant.now().getEpochSecond());
            } else if(requestMethod == RequestType.POST) {
                post = new HttpPost(endpointUri);
                for(Map.Entry<String, String> entry : requestHeaders.entrySet()) {
                    String key = entry.getKey();
                    String value = entry.getValue();
                    post.setHeader(key, value);
                }
                if(!post.containsHeader("X-Request-Timestamp")) post.setHeader("X-Request-Timestamp", "" + Instant.now().getEpochSecond());
            }

        }

        public Http1Request(CloseableHttpClient client, String endpointUri, RequestType requestMethod, HashMap<String, String> requestHeaders, HttpEntity body) {

            this.httpClient = client;
            this.requestMethod = requestMethod;

            if(requestMethod == RequestType.POST) {
                post = new HttpPost(endpointUri);
                for(Map.Entry<String, String> entry : requestHeaders.entrySet()) {
                    String key = entry.getKey();
                    String value = entry.getValue();
                    post.setHeader(key, value);
                }
                if(!post.containsHeader("Content-Type")) post.setHeader("Content-Type", "application/json");
                if(!post.containsHeader("X-Request-Timestamp")) post.setHeader("X-Request-Timestamp", "" + Instant.now().getEpochSecond());
                post.setEntity(body);
            }

        }


        @Override
        public ResponseHeaders getHeaders() {
            return this.headers;
        }

        @Override
        public int getStatus() {
            return this.status;
        }

        @Override
        public String getBody() {
            return this.body;
        }

        @Override
        public void execute() {
            try {
                if (requestMethod == RequestType.GET) {
                    try (CloseableHttpResponse response = this.httpClient.execute(get)) {
                        status = response.getStatusLine().getStatusCode();
                        headers = new ResponseHeaders(response.getAllHeaders());
                        body = bodyFromEntity(response.getEntity());
                    }
                } else if(requestMethod == RequestType.POST) {
                    try (CloseableHttpResponse response = this.httpClient.execute(post)) {
                        status = response.getStatusLine().getStatusCode();
                        headers = new ResponseHeaders(response.getAllHeaders());
                        body = bodyFromEntity(response.getEntity());
                    }
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        private static String bodyFromEntity(HttpEntity entity) throws IOException {
            return entity == null ? "" : EntityUtils.toString(entity, StandardCharsets.UTF_8);
        }
    }

    private static class Http3Request implements ExecutesClientRequest {

        private java.net.http.HttpClient httpClient;
        private HttpRequest request;
        private java.net.http.HttpResponse<String> response;
        private RequestType requestMethod;

        private int status = -1;
        private String body = null;
        private ResponseHeaders headers = null;

        public Http3Request(java.net.http.HttpClient client, String endpointUri, RequestType requestMethod, HashMap<String, String> requestHeaders) {

            this.httpClient = client;
            this.requestMethod = requestMethod;

            if(requestMethod == RequestType.GET) {
                java.net.http.HttpRequest.Builder builder = HttpRequest.newBuilder()
                                    .uri(URI.create(endpointUri))
                                    .GET()
                                    .timeout(Duration.ofSeconds(5));
                for(Map.Entry<String, String> entry : requestHeaders.entrySet()) {
                    String key = entry.getKey();
                    String value = entry.getValue();
                    builder.setHeader(key, value);
                }
                builder.setHeader("X-Request-Timestamp", "" + Instant.now().getEpochSecond());
                request = builder.build();
            } else if(requestMethod == RequestType.POST) {
                java.net.http.HttpRequest.Builder builder = HttpRequest.newBuilder()
                        .uri(URI.create(endpointUri))
                        .POST(HttpRequest.BodyPublishers.ofString(""))
                        .timeout(Duration.ofSeconds(5));
                for(Map.Entry<String, String> entry : requestHeaders.entrySet()) {
                    String key = entry.getKey();
                    String value = entry.getValue();
                    builder.setHeader(key, value);
                }
                builder.setHeader("X-Request-Timestamp", "" + Instant.now().getEpochSecond());
                request = builder.build();
            }

        }

        public Http3Request(java.net.http.HttpClient client, String endpointUri, RequestType requestMethod, HashMap<String, String> requestHeaders, String body) {

            this.httpClient = client;

            if(requestMethod == RequestType.POST) {
                java.net.http.HttpRequest.Builder builder = HttpRequest.newBuilder()
                        .uri(URI.create(endpointUri))
                        .POST(HttpRequest.BodyPublishers.ofString(body))
                        .timeout(Duration.ofSeconds(5));
                for(Map.Entry<String, String> entry : requestHeaders.entrySet()) {
                    String key = entry.getKey();
                    String value = entry.getValue();
                    builder.setHeader(key, value);
                }
                builder.setHeader("X-Request-Timestamp", "" + Instant.now().getEpochSecond());
                request = builder.build();
            }

        }


        @Override
        public ResponseHeaders getHeaders() {
            return this.headers;
        }

        @Override
        public int getStatus() {
            return this.status;
        }

        @Override
        public String getBody() {
            return this.body;
        }

        @Override
        public void execute() {
            try {

                response = httpClient.send(request, java.net.http.HttpResponse.BodyHandlers.ofString());
                status = response.statusCode();
                headers = new ResponseHeaders(response.headers());
                this.body = response.body() == null ? "" : response.body();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Encrypted HTTP/3 request interrupted.", e);
            } catch (RuntimeException e) {
                throw e;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    private interface ExecutesClientRequest {
        ResponseHeaders getHeaders();
        int getStatus();
        String getBody();
        void execute();
    }

}
