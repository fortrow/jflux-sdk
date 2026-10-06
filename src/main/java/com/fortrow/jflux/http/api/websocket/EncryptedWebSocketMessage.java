package com.fortrow.jflux.http.api.websocket;

import com.fortrow.jflux.exceptions.ExceptionHandling;
import com.fortrow.jflux.output.json.JSON;
import org.json.simple.JSONObject;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Compact JSON frames used by the encrypted API WebSocket transport.
 *
 * <p>The frame carries an ordinary HTTP method, relative API path, headers,
 * and body. The encrypted API still signs and encrypts its body exactly as it
 * does over HTTP; this class only moves that request through a WebSocket.</p>
 */
public final class EncryptedWebSocketMessage {

    public static final String REQUEST = "request";
    public static final String RESPONSE = "response";

    private EncryptedWebSocketMessage() { }

    public record Request(String requestId, String method, String path,
                          Map<String, String> headers, String body) { }

    public record Response(String requestId, int status,
                           Map<String, String> headers, String body) { }

    public static String request(String requestId, String method, String path,
                                 Map<String, String> headers, String body) {
        JSONObject object = new JSONObject();
        object.put("type", REQUEST);
        object.put("request_id", requireText(requestId, "WebSocket request ID"));
        object.put("method", requireText(method, "WebSocket request method"));
        object.put("path", requirePath(path));
        object.put("headers", new JSONObject(headers == null ? Map.of() : headers));
        object.put("body", body == null ? "" : body);
        return JSON.getString(object);
    }

    public static String response(String requestId, int status,
                                  Map<String, String> headers, String body) {
        JSONObject object = new JSONObject();
        object.put("type", RESPONSE);
        object.put("request_id", requireText(requestId, "WebSocket response ID"));
        object.put("status", status);
        object.put("headers", new JSONObject(headers == null ? Map.of() : headers));
        object.put("body", body == null ? "" : body);
        return JSON.getString(object);
    }

    public static Request parseRequest(String encoded) {
        JSONObject object = parseObject(encoded);
        if (!REQUEST.equals(text(object.get("type")))) throw ExceptionHandling.invalid("WebSocket frame is not a request.");
        return new Request(
                requireText(text(object.get("request_id")), "WebSocket request ID"),
                requireText(text(object.get("method")), "WebSocket request method").toUpperCase(Locale.ROOT),
                requirePath(text(object.get("path"))),
                parseHeaders(object.get("headers")),
                textOrEmpty(object.get("body"))
        );
    }

    public static Response parseResponse(String encoded) {
        JSONObject object = parseObject(encoded);
        if (!RESPONSE.equals(text(object.get("type")))) throw ExceptionHandling.invalid("WebSocket frame is not a response.");
        Object status = object.get("status");
        if (!(status instanceof Number number)) throw ExceptionHandling.invalid("WebSocket response status is invalid.");
        if (number.intValue() < 100 || number.intValue() > 599) throw ExceptionHandling.invalid("WebSocket response status is invalid.");
        return new Response(
                requireText(text(object.get("request_id")), "WebSocket response ID"),
                number.intValue(),
                parseHeaders(object.get("headers")),
                textOrEmpty(object.get("body"))
        );
    }

    public static String error(String requestId, int status, String message) {
        JSONObject body = new JSONObject();
        body.put("request_status", "error");
        body.put("message", message == null ? "WebSocket request failed." : message);
        return response(requestId == null || requestId.isBlank() ? "unknown" : requestId,
                status, Map.of("Content-Type", "application/json"), JSON.getString(body));
    }

    private static JSONObject parseObject(String encoded) {
        Object parsed = JSON.parse(encoded);
        if (!(parsed instanceof JSONObject object)) throw ExceptionHandling.invalid("WebSocket frame must be a JSON object.");
        return object;
    }

    private static Map<String, String> parseHeaders(Object value) {
        if (value == null) return Map.of();
        if (!(value instanceof Map<?, ?> source)) throw ExceptionHandling.invalid("WebSocket frame headers are invalid.");
        Map<String, String> headers = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : source.entrySet()) {
            if (!(entry.getKey() instanceof String key) || key.isBlank()) {
                throw ExceptionHandling.invalid("WebSocket frame contains an invalid header name.");
            }
            if (!(entry.getValue() instanceof String headerValue)) {
                throw ExceptionHandling.invalid("WebSocket frame contains an invalid header value.");
            }
            headers.put(key, headerValue);
        }
        return Map.copyOf(headers);
    }

    private static String text(Object value) { return value == null ? "" : value.toString(); }

    private static String textOrEmpty(Object value) {
        if (value == null) return "";
        if (!(value instanceof String)) throw ExceptionHandling.invalid("WebSocket frame body is invalid.");
        return (String) value;
    }

    private static String requireText(String value, String label) {
        if (value == null || value.isBlank()) throw ExceptionHandling.invalid(label + " is required.");
        return value;
    }

    private static String requirePath(String path) {
        String normalized = requireText(path, "WebSocket request path").trim();
        if (!normalized.startsWith("/") || normalized.contains("..") || normalized.contains("#") || normalized.contains("\\")) {
            throw ExceptionHandling.invalid("WebSocket request path is invalid.");
        }
        return normalized;
    }
}
