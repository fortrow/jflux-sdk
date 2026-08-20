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

    private static final int DEFAULT_ASYNC_TIMEOUT_MS = 15_000;

    private SSLContext sslContext;
    private CloseableHttpClient http1Client;
    private java.net.http.HttpClient http3Client;
    private ClosableHttp3Transport http3Transport;
    private HttpVersion httpVersion;
    private boolean allowSelfSigned;
    private MtlsConfig mtlsConfig;

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
    private String responsePath = "/response";
    private SecretStore sessionSecretStore;
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

    public EncryptedClient build() {
        if(closed) throw ExceptionHandling.failure("Encrypted API client has already been closed.", null);
        if(http1Client != null || http3Transport != null) return this;
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
        try {
            closeSessionOrThrow();
            return true;
        } catch (APIException ignored) {
            return false;
        } finally {
            close();
        }
    }

    public void discardSessionState() {
        clearSessionState();
    }

    public String currentReconnectSessionSecret() {
        return currentRawSessionSecret();
    }

    public void restoreReconnectSessionSecret(String rawSecret) {
        persistAuthenticatedSessionSecret(rawSecret);
    }

    public void checkpointReconnectSessionSecret() {
        persistAuthenticatedSessionSecret(pastSecret);
    }

    public EncryptedResponse closeSessionOrThrow() throws APIException {
        if(!hasActiveSession()) throw new APIException("Encrypted session has not been initialized.");
        if(sessionClosePath == null || sessionClosePath.isBlank()) throw new APIException("Encrypted session close path is not configured.");

        EncryptedResponse response = request(sessionClosePath, new JSONObject(), EncryptedResponseType.SESSION_CLOSE, false);
        if(response == null) throw new APIException("No response was returned from session close.");
        if(response.getStatus() != 200 || !"success".equals(response.getRequestStatus())) {
            throw new APIException("Encrypted session close was not successful. status=" + response.getStatus() + ", body=" + response.getBody());
        }
        if(!response.isVerified()) throw new APIException("Encrypted session close response authorization failed.");

        persistAuthenticatedSessionSecret(this.pastSecret);
        clearSessionState();
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

        if(sessionInitPath == null || sessionInitPath.isBlank()) throw ExceptionHandling.invalid("Encrypted session init path is required.");
        if(sessionClosePath == null || sessionClosePath.isBlank()) throw ExceptionHandling.invalid("Encrypted session close path is required.");

        this.sessionInitPath = fixPath(sessionInitPath);
        this.sessionClosePath = fixPath(sessionClosePath);
        this.sessionSecret = SHA.get256(currentRawSessionSecret());

        return init();

    }

    public void beginSessionOrThrow(String sessionInitPath, String sessionClosePath) throws APIException {
        if(!beginSession(sessionInitPath, sessionClosePath)) {
            String detail = lastSessionInitError == null || lastSessionInitError.isBlank() ? "No session-init detail was captured." : lastSessionInitError;
            throw new APIException("Failed to begin encrypted API session: " + detail);
        }
    }

    private EncryptedResponse get(String path, HashMap<String, String> requestHeaders) throws APIException {

        try {

            ExecutesClientRequest httpRequest;
            if(httpVersion == HttpVersion.HTTP_1) {
                if(http1Client == null) throw new APIException("Encrypted HTTP/1 client has not been built.");
                httpRequest = new Http1Request(http1Client, this.endpointUri + path, RequestType.GET, requestHeaders);
            } else if(httpVersion == HttpVersion.HTTP_3) {
                if(http3Client == null) throw new APIException("Encrypted HTTP/3 client has not been built.");
                httpRequest = new Http3Request(http3Client, this.endpointUri + path, RequestType.GET, requestHeaders);
            } else throw new APIException("Encrypted HTTP version is not supported.");

            httpRequest.execute();

            return new EncryptedResponse(httpRequest.getStatus(), httpRequest.getHeaders(), httpRequest.getBody(), this.apiKey, this.secret, this.activeSecret, this.sessionRandom, serverPublic, clientPrivate, EncryptedResponseType.SESSION_INIT, true);

        } catch (APIException e) {
            throw e;
        } catch (Exception e) {
            throw new APIException("Encrypted API session-init transport failed.", e);
        }

    }

    private EncryptedResponse post(String path, String encryptedRequest, HashMap<String, String> requestHeaders, byte[] aad, EncryptedResponseType responseType, boolean updateRequestVars) throws APIException {

        try {

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
        headers.put("X-Api-Session-Authorization", sessionSecret);

        sessionRandom = randomBytesHex.substring(0, 64);

        lastSessionInitError = "";
        EncryptedResponse response;
        try {
            response = get(this.sessionInitPath, headers);
        } catch (APIException e) {
            lastSessionInitError = e.getMessage();
            return false;
        }

        if(response == null) {
            lastSessionInitError = "No response was returned from session init.";
            return false;
        }
        if(response.getData() == null || !response.getData().containsKey("request_status")) {
            lastSessionInitError = "Session init response did not include request_status. status=" + response.getStatus() + ", body=" + response.getBody();
            return false;
        }
        if(!"success".equals((String) response.getData().get("request_status"))) {
            lastSessionInitError = "Session init was not successful. status=" + response.getStatus() + ", body=" + response.getBody();
            return false;
        }
        if(!response.getData().containsKey("iv")) {
            lastSessionInitError = "Session init response did not include iv. status=" + response.getStatus() + ", body=" + response.getBody();
            return false;
        }
        if(!setKeyVars(response)) {
            lastSessionInitError = "Session init key transfer failed.";
            return false;
        }
        if(!response.verifyLate(apiKey, secret, sessionRandom, clientPrivate, serverPublic)) {
            lastSessionInitError = "Session init response authorization failed.";
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
        if(closed) return;
        closed = true;
        if(http3Transport != null) {
            http3Transport.close();
            http3Transport = null;
            http3Client = null;
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
