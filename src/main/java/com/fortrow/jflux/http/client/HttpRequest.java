package com.fortrow.jflux.http.client;

import com.fortrow.jflux.exceptions.ExceptionHandling;
import com.fortrow.jflux.http.api.server.types.RequestType;
import com.fortrow.jflux.output.json.JSON;
import org.json.simple.JSONObject;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.StringJoiner;

/** Small JDK HTTP request wrapper retained for simple non-session calls. */
public class HttpRequest {

    private final HttpClient client = HttpClient.newHttpClient();
    private final RequestType requestType;
    private final String endpoint;
    private final HashMap<String, String> headers = new HashMap<>();
    private String path;
    private String body;
    private boolean urlEncoded;

    public HttpRequest(String host, int port, String path, RequestType type) {
        if(host == null || host.isBlank()) throw ExceptionHandling.invalid("HTTP host is required.");
        if(port < 1 || port > 65_535) throw ExceptionHandling.invalid("HTTP port must be between 1 and 65535.");
        if(type == null) throw ExceptionHandling.invalid("HTTP request method is required.");
        this.path = normalizePath(path);
        this.requestType = type;
        this.endpoint = "https://" + host.trim() + ":" + port;
        try {
            URI.create(endpoint + this.path);
        } catch (IllegalArgumentException ex) {
            throw ExceptionHandling.invalid("HTTP endpoint is invalid.", ex);
        }
    }

    public void appendHeaders(String key, String value) {
        if(key == null || key.isBlank()) throw ExceptionHandling.invalid("HTTP header name is required.");
        if(value == null) throw ExceptionHandling.invalid("HTTP header value is required.");
        headers.put(key, value);
    }

    public void setBody(JSONObject json, boolean urlEncoded) {
        this.urlEncoded = urlEncoded;
        this.body = json == null ? null : (urlEncoded ? encodeUrl(json) : JSON.string(json));
    }

    public void setBody(HashMap<String, String> params, boolean urlEncoded) {
        this.urlEncoded = urlEncoded;
        this.body = params == null ? null : (urlEncoded ? encodeUrl(params) : JSON.string(params));
    }

    public HttpResponse<String> execute() {
        try {
            String target = endpoint + path;
            String requestBody = body;
            if(urlEncoded && (requestType == RequestType.GET || requestType == RequestType.HEAD) && body != null && !body.isBlank()) {
                target += (target.contains("?") ? "&" : "?") + body;
                requestBody = null;
            }

            java.net.http.HttpRequest.Builder builder = java.net.http.HttpRequest.newBuilder()
                    .uri(URI.create(target))
                    .timeout(Duration.ofSeconds(30));
            for(Map.Entry<String, String> header : headers.entrySet()) builder.header(header.getKey(), header.getValue());
            builder.header("X-Request-Timestamp", String.valueOf(System.currentTimeMillis()));
            if(requestBody != null) {
                if(!headers.containsKey("Content-Type")) builder.header("Content-Type", urlEncoded ? "application/x-www-form-urlencoded" : "application/json");
                builder.method(requestType.name(), java.net.http.HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8));
            } else {
                builder.method(requestType.name(), java.net.http.HttpRequest.BodyPublishers.noBody());
            }
            return client.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw ExceptionHandling.failure("HTTP request was interrupted.", ex);
        } catch (Exception ex) {
            throw ExceptionHandling.failure("HTTP request failed.", ex);
        }
    }

    private String encodeUrl(Map<?, ?> params) {
        StringJoiner encoded = new StringJoiner("&");
        for(Map.Entry<?, ?> entry : params.entrySet()) {
            encoded.add(encodeValue(String.valueOf(entry.getKey())) + "=" + encodeValue(entry.getValue() == null ? "" : String.valueOf(entry.getValue())));
        }
        return encoded.toString();
    }

    private String encodeValue(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static String normalizePath(String input) {
        if(input == null || input.isBlank()) return "/";
        String normalized = input.trim();
        return normalized.startsWith("/") ? normalized : "/" + normalized;
    }
}
