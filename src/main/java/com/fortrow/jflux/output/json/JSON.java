package com.fortrow.jflux.output.json;

import com.fortrow.jflux.exceptions.ExceptionHandling;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONValue;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import java.util.Map;

/** JSON-Simple-first JSON parsing and serialization facade. */
public final class JSON {

    private static final Gson GSON = new Gson();
    private static final Gson PRETTY_GSON = new GsonBuilder().setPrettyPrinting().create();

    private JSON() { }

    /** Parses JSON into JSON-Simple values: JSONObject, JSONArray, String, Number, Boolean, or null. */
    @SuppressWarnings("unchecked")
    public static <T> T parse(String json) {
        if (json == null || json.isBlank()) throw ExceptionHandling.invalid("JSON input is required.");
        try {
            return (T) new JSONParser().parse(json);
        } catch (ParseException | RuntimeException e) {
            throw ExceptionHandling.invalid("Invalid JSON input.", e);
        }
    }

    /** Gson compatibility overload for typed Java object conversion. */
    public static <T> T parse(String json, Class<T> type) {
        if (json == null || json.isBlank()) throw ExceptionHandling.invalid("JSON input is required.");
        if (type == null) throw ExceptionHandling.invalid("JSON target type is required.");
        try {
            return GSON.fromJson(json, type);
        } catch (RuntimeException e) {
            throw ExceptionHandling.invalid("Unable to convert JSON to the requested type.", e);
        }
    }

    /** Serializes JSON-Simple values, maps, collections, arrays, and primitives without Gson. */
    public static String string(Object value) {
        if (value == null) return "null";
        if (value instanceof JSONObject object) return object.toJSONString();
        if (value instanceof JSONArray array) return array.toJSONString();
        if (value instanceof Map<?, ?> map) return JSONValue.toJSONString(map);
        if (value instanceof Iterable<?> || value.getClass().isArray()) return JSONValue.toJSONString(value);
        if (value instanceof String || value instanceof Number || value instanceof Boolean || value instanceof Character) {
            return JSONValue.toJSONString(value);
        }
        return GSON.toJson(value);
    }

    /** Pretty-prints through the cached Gson formatter for compatibility with existing console output. */
    public static String prettyString(Object value) {
        return PRETTY_GSON.toJson(value);
    }

    /** Parses a JSON object, returning an empty object for legacy malformed-input behavior. */
    public static JSONObject getObject(String body) {
        try {
            Object parsed = parseLegacy(body);
            return parsed instanceof JSONObject object ? object : new JSONObject();
        } catch (RuntimeException ignored) {
            return new JSONObject();
        }
    }

    /** Parses a JSON array, returning an empty array for legacy malformed-input behavior. */
    public static JSONArray getArray(String body) {
        try {
            Object parsed = parseLegacy(body);
            return parsed instanceof JSONArray array ? array : new JSONArray();
        } catch (RuntimeException ignored) {
            return new JSONArray();
        }
    }

    public static String getString(JSONObject object) {
        return getString((Object) object);
    }

    public static String getString(JSONArray array) {
        return getString((Object) array);
    }

    /** Serializes any JSON-Simple value using compact JSON. */
    public static String getString(Object value) {
        try {
            return string(value);
        } catch (RuntimeException e) {
            ExceptionHandling.report(e);
            return value instanceof Map<?, ?> ? "{}" : "null";
        }
    }

    public static String getString(JSONObject object, boolean format) {
        return getString((Object) object, format);
    }

    public static String getString(JSONArray array, boolean format) {
        return getString((Object) array, format);
    }

    public static String getString(Object value, boolean format) {
        if (!format) return getString(value);
        try {
            return PRETTY_GSON.toJson(value);
        } catch (RuntimeException e) {
            ExceptionHandling.report(e);
            return value instanceof Map<?, ?> ? "{}" : "null";
        }
    }

    /** Returns whether the input is valid JSON, including scalar JSON values. */
    public static boolean isValid(String json) {
        if (json == null || json.isBlank()) return false;
        try {
            new JSONParser().parse(json);
            return true;
        } catch (ParseException | RuntimeException ignored) {
            return false;
        }
    }

    /** Returns a compact validated JSON object, or the historical {@code "false"} sentinel. */
    public static String verify(String json) {
        if (json == null || json.isBlank()) return "false";
        Object parsed = parseLegacy(json);
        return parsed instanceof JSONObject ? string(parsed) : "false";
    }

    private static Object parseLegacy(String body) {
        if (body == null || body.isBlank()) return null;
        try {
            return new JSONParser().parse(body);
        } catch (ParseException | RuntimeException e) {
            return null;
        }
    }
}
