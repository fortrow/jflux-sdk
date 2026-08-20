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
 * File created by Ryan Fitzgerald (ryan@fortrow.com) on 2026-05-04 15:27:04 EDT
 * 
 * -------------------------------------------------------------------------

 */
package com.fortrow.jflux.http.api.client;

import com.fortrow.jflux.exceptions.ExceptionHandling;
import com.fortrow.jflux.http.api.server.config.HttpVersion;
import com.fortrow.jflux.http.api.server.exceptions.APIException;
import com.fortrow.jflux.output.json.JSON;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpEntityEnclosingRequestBase;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpHead;
import org.apache.http.client.methods.HttpOptions;
import org.apache.http.client.methods.HttpPatch;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.client.methods.HttpTrace;
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
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpRequest;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Scanner;

public class RESTClient implements AutoCloseable {

    private static final int DEFAULT_ASYNC_TIMEOUT_MS = 15_000;

    private SSLContext sslContext;
    private CloseableHttpClient http1Client;
    private java.net.http.HttpClient http3Client;
    private ClosableHttp3Transport http3Transport;
    private HttpVersion httpVersion;
    private boolean allowSelfSigned;
    private MtlsConfig mtlsConfig;
    private String apiKey;
    private String endpointUri;
    private String http3UpgradeUri;
    private String responsePath = "/response";
    private int asyncTimeoutMs = DEFAULT_ASYNC_TIMEOUT_MS;

    public RESTClient(String apiKey, String endpointUri, HttpVersion httpVersion, boolean allowSelfSigned) {
        if(apiKey == null || apiKey.isBlank()) throw ExceptionHandling.invalid("REST API key is required.");
        if(endpointUri == null || endpointUri.isBlank()) throw ExceptionHandling.invalid("REST endpoint URI is required.");
        if(httpVersion == null) throw ExceptionHandling.invalid("REST HTTP version is required.");
        this.apiKey = apiKey;
        this.endpointUri = stripTrailingSlash(endpointUri);
        this.httpVersion = httpVersion;
        this.allowSelfSigned = allowSelfSigned;
    }

    public RESTClient setMtlsConfig(MtlsConfig mtlsConfig) {
        this.mtlsConfig = mtlsConfig;
        return this;
    }

    public RESTClient build() {
        if(http1Client != null || http3Transport != null) return this;
        if(httpVersion == HttpVersion.HTTP_1) {
            try {
                if (mtlsConfig != null) {
                    sslContext = TlsMaterial.buildSslContext(mtlsConfig, allowSelfSigned);
                } else if(allowSelfSigned) {
                    sslContext = new SSLContextBuilder()
                            .loadTrustMaterial(null, new TrustSelfSignedStrategy())
                            .build();
                } else {
                    sslContext = new SSLContextBuilder()
                            .loadTrustMaterial(null, (TrustStrategy) null)
                            .build();
                }

                if(allowSelfSigned) http1Client = HttpClients.custom()
                        .setSSLContext(sslContext)
                        .setSSLHostnameVerifier(NoopHostnameVerifier.INSTANCE)
                        .build();
                else http1Client = HttpClients.custom()
                        .setSSLContext(sslContext)
                        .build();
            } catch (GeneralSecurityException | IOException e) {
                throw ExceptionHandling.failure("Unable to initialize API TLS client.", e);
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

    public RESTClient setResponsePath(String responsePath) {
        this.responsePath = fixPath(responsePath);
        return this;
    }

    public RESTClient setHttp3UpgradeUri(String upgradeUri) {
        this.http3UpgradeUri = stripTrailingSlash(upgradeUri);
        return this;
    }

    public RESTClient setHttp3UpgradePort(int port) {
        this.http3UpgradeUri = Http3UpgradeResolver.upgradeUri(endpointUri, port, false);
        return this;
    }

    public RESTClient setHttp3HttpsUpgradePort(int port) {
        this.http3UpgradeUri = Http3UpgradeResolver.upgradeUri(endpointUri, port, true);
        return this;
    }

    public RESTClient setAsyncTimeoutMs(int asyncTimeoutMs) {
        this.asyncTimeoutMs = Math.max(1, asyncTimeoutMs);
        return this;
    }

    @Override
    public void close() {
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
    }

    public Response get(String path, JSONObject query) throws APIException {
        return request(path, RequestType.GET, query);
    }

    public Response head(String path, JSONObject query) throws APIException {
        return request(path, RequestType.HEAD, query);
    }

    public Response post(String path, JSONObject body) throws APIException {
        return request(path, RequestType.POST, body);
    }

    public Response put(String path, JSONObject body) throws APIException {
        return request(path, RequestType.PUT, body);
    }

    public Response patch(String path, JSONObject body) throws APIException {
        return request(path, RequestType.PATCH, body);
    }

    public Response delete(String path, JSONObject body) throws APIException {
        return request(path, RequestType.DELETE, body);
    }

    public Response options(String path) throws APIException {
        return request(path, RequestType.OPTIONS, null);
    }

    public Response trace(String path) throws APIException {
        return request(path, RequestType.TRACE, null);
    }

    public Response request(String path, RequestType method, JSONObject payload) throws APIException {
        String fixedPath = fixPath(path);
        String requestPath = appendQueryIfNeeded(fixedPath, method, payload);
        String body = bodyFor(method, payload);
        HashMap<String, String> headers = baseHeaders(method, body);
        return execute(requestPath, method, body, headers);
    }

    public Response requestAndWait(String path, RequestType method, JSONObject payload) throws APIException {
        return awaitAsync(request(path, method, payload));
    }

    public Response awaitAsync(Response initialResponse) throws APIException {
        if(initialResponse == null) throw new APIException("REST request returned no response.");
        if(!initialResponse.isRedirect()) return initialResponse;

        JSONObject obj = initialResponse.getData();
        String responseId = stringValue(obj.get("response_id"));
        String authorization = stringValue(obj.get("initial_auth"));
        if(responseId.isBlank() || authorization.isBlank()) throw new APIException("REST async response did not include authorization state.");
        int pollRate = intValue(obj.get("poll_rate"), 100);
        long deadline = System.currentTimeMillis() + asyncTimeoutMs;

        while(System.currentTimeMillis() < deadline) {
            sleep(pollRate);
            JSONObject poll = new JSONObject();
            poll.put("response_id", responseId);
            poll.put("authorization", authorization);

            Response pollResponse = request(responsePath, RequestType.POST, poll);
            JSONObject pollData = pollResponse.getData();
            String status = pollResponse.getRequestStatus();

            if("redirect".equals(status)) {
                responseId = stringValue(pollData.getOrDefault("response_id", responseId));
                authorization = stringValue(pollData.getOrDefault("next_auth", authorization));
                pollRate = intValue(pollData.get("poll_rate"), pollRate);
                continue;
            }

            if("too_many_requests".equals(status)) {
                authorization = stringValue(pollData.getOrDefault("next_auth", authorization));
                pollRate = intValue(pollData.get("poll_rate"), pollRate);
                continue;
            }

            return pollResponse;
        }

        throw new APIException("Timed out waiting for REST async response.");
    }

    private Response execute(String path, RequestType method, String body, HashMap<String, String> headers) throws APIException {
        if(method == null) throw new APIException("REST request method is required.");
        try {
            if(httpVersion == HttpVersion.HTTP_1) {
                if(http1Client == null) throw new APIException("REST HTTP/1 client has not been built.");
                return executeHttp1(path, method, body, headers);
            } else if(httpVersion == HttpVersion.HTTP_3) {
                if(http3Client == null) throw new APIException("REST HTTP/3 client has not been built.");
                return executeHttp3(path, method, body, headers);
            }
        } catch (APIException e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new APIException("REST client request was interrupted.", e);
        } catch (Exception e) {
            throw new APIException("REST client request failed.", e);
        }
        throw new APIException("REST HTTP version is not supported.");
    }

    private Response executeHttp1(String path, RequestType method, String body, HashMap<String, String> headers) throws IOException {
        HttpRequestBase request = http1Request(endpointUri + path, method, body);
        for(Map.Entry<String, String> entry : headers.entrySet()) {
            request.setHeader(entry.getKey(), entry.getValue());
        }
        try (CloseableHttpResponse response = http1Client.execute(request)) {
            return new Response(response.getStatusLine().getStatusCode(), new ResponseHeaders(response.getAllHeaders()), bodyFromEntity(response.getEntity()));
        } finally {
            request.releaseConnection();
        }
    }

    private Response executeHttp3(String path, RequestType method, String body, HashMap<String, String> headers) throws IOException, InterruptedException {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(endpointUri + path))
                .timeout(Duration.ofSeconds(30));

        if(hasBody(method) && body != null) builder.method(method.name(), HttpRequest.BodyPublishers.ofString(body));
        else builder.method(method.name(), HttpRequest.BodyPublishers.noBody());

        for(Map.Entry<String, String> entry : headers.entrySet()) {
            builder.setHeader(entry.getKey(), entry.getValue());
        }

        java.net.http.HttpResponse<String> response = http3Client.send(builder.build(), java.net.http.HttpResponse.BodyHandlers.ofString());
        return new Response(response.statusCode(), new ResponseHeaders(response.headers()), response.body());
    }

    private HttpRequestBase http1Request(String uri, RequestType method, String body) {
        return switch (method) {
            case GET -> new HttpGet(uri);
            case HEAD -> new HttpHead(uri);
            case POST -> withBody(new HttpPost(uri), body);
            case PUT -> withBody(new HttpPut(uri), body);
            case DELETE -> body == null || body.isBlank() ? new HttpDelete(uri) : withBody(new HttpDeleteWithBody(uri), body);
            case OPTIONS -> new HttpOptions(uri);
            case TRACE -> new HttpTrace(uri);
            case PATCH -> withBody(new HttpPatch(uri), body);
        };
    }

    private HttpRequestBase withBody(HttpEntityEnclosingRequestBase request, String body) {
        request.setEntity(new StringEntity(body == null ? "" : body, StandardCharsets.UTF_8));
        return request;
    }

    private HashMap<String, String> baseHeaders(RequestType method, String body) {
        HashMap<String, String> headers = new HashMap<>();
        headers.put("X-Api-Key", apiKey);
        headers.put("X-Request-Timestamp", String.valueOf(Instant.now().getEpochSecond()));
        if(hasBody(method) && body != null) headers.put("Content-Type", "application/json");
        return headers;
    }

    private String bodyFor(RequestType method, JSONObject payload) {
        if(!hasBody(method) || payload == null) return null;
        return JSON.getString(payload);
    }

    private String appendQueryIfNeeded(String path, RequestType method, JSONObject payload) {
        if(method != RequestType.GET && method != RequestType.HEAD) return path;
        if(payload == null || payload.isEmpty()) return path;
        String query = queryString(payload);
        if(query.isEmpty()) return path;
        return path + (path.contains("?") ? "&" : "?") + query;
    }

    private String queryString(JSONObject payload) {
        StringBuilder out = new StringBuilder();
        for(Object keyObj : payload.keySet()) {
            String key = String.valueOf(keyObj);
            Object valueObj = payload.get(keyObj);
            String value = valueObj == null ? "" : String.valueOf(valueObj);
            if(!out.isEmpty()) out.append('&');
            out.append(encode(key)).append('=').append(encode(value));
        }
        return out.toString();
    }

    private String bodyFromEntity(HttpEntity entity) throws IOException {
        if(entity == null) return "";
        return EntityUtils.toString(entity, StandardCharsets.UTF_8);
    }

    private static boolean hasBody(RequestType method) {
        return method == RequestType.POST || method == RequestType.PUT || method == RequestType.PATCH || method == RequestType.DELETE;
    }

    private static String fixPath(String input) {
        if(input == null || input.isBlank()) return "/";
        input = input.trim().replace('.', '/');
        if(input.charAt(0) != '/') input = "/" + input;
        while(input.length() > 1 && input.endsWith("/")) input = input.substring(0, input.length() - 1);
        return input;
    }

    private static String stripTrailingSlash(String input) {
        if(input == null) return "";
        while(input.length() > 1 && input.endsWith("/")) input = input.substring(0, input.length() - 1);
        return input;
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
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
            Thread.sleep(Math.max(1, millis));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new APIException("Interrupted while waiting for REST async response.");
        }
    }

    private static final class HttpDeleteWithBody extends HttpEntityEnclosingRequestBase {
        private HttpDeleteWithBody(String uri) {
            setURI(URI.create(uri));
        }

        @Override
        public String getMethod() {
            return "DELETE";
        }
    }

    public static class Response {
        private final int status;
        private final ResponseHeaders headers;
        private final String body;
        private final JSONObject data;

        public Response(int status, ResponseHeaders headers, String body) {
            this.status = status;
            this.headers = headers;
            this.body = body == null ? "" : body;
            this.data = this.body.isBlank() ? new JSONObject() : JSON.getObject(this.body);
        }

        public int getStatus() {
            return status;
        }

        public ResponseHeaders getHeaders() {
            return headers;
        }

        public String getBody() {
            return body;
        }

        public JSONObject getData() {
            return data;
        }

        public String getRequestStatus() {
            Object value = data.get("request_status");
            return value == null ? "" : value.toString();
        }

        public boolean isRedirect() {
            return "redirect".equals(getRequestStatus());
        }
    }
}
