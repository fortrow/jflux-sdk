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
 * File created by Ryan Fitzgerald (ryan@fortrow.com) on 2026-05-10 22:51:50 EDT
 */
package com.fortrow.jflux.http.api.server.containers;

import com.fortrow.jflux.http.api.server.types.ContentType;
import com.fortrow.jflux.output.json.JSON;
import com.fortrow.jflux.todash.__;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public class BodyParameters {

    private String text;
    private byte[] binaryBody;
    private HashMap<String, Object> values = new HashMap<String, Object>();
    private HashMap<String, Class<?>> expected = new HashMap<String, Class<?>>();
    private boolean valid = true;

    public BodyParameters(String body, ContentType contentType, RequestHeaders headers, ClientSession session, boolean isEncrypted) {
        this(body, contentType, headers, session, isEncrypted, null);
    }

    public BodyParameters(String body, ContentType contentType, RequestHeaders headers, ClientSession session, boolean isEncrypted, byte[] aad) {
        if (contentType == ContentType.BINARY) {
            if (!isEncrypted || session == null) throw com.fortrow.jflux.exceptions.ExceptionHandling.invalid("Binary payload requires authenticated encryption.");
            binaryBody = new EncryptedPayload("", session.getSession().getApiKeys(), aad)
                    .decryptBytes(com.fortrow.jflux.encoding.BaseX.bytesFrom64(body));
            text = "";
        } else if(isEncrypted) decrypt(body, session, aad);
        else this.text = body == null ? "" : body;
        parse(contentType, headers);
    }

    public boolean checkAllPresent() {
        return true;
    }

    /** Returns whether the body matched its declared content type. */
    public boolean isValid() { return valid; }

    /** Validates required endpoint parameters without allocating a second map. */
    public boolean checkAllPresent(Collection<String> required) {
        if(required == null || required.isEmpty()) return true;
        for(String key : required) {
            if(key == null || key.isBlank() || !values.containsKey(key) || values.get(key) == null) return false;
        }
        return true;
    }

    public boolean present(String key) { return __.isset(values.get(key)); }

    public String text() { return this.text; }

    public byte[] bytes() { return binaryBody == null ? new byte[0] : binaryBody.clone(); }

    public String json() {
        return JSON.getString(new JSONObject(values));
    }

    public void putAll(Map<String, ?> input) {
        if(input == null) return;
        for(Map.Entry<String, ?> entry : input.entrySet()) {
            values.put(entry.getKey(), entry.getValue());
        }
    }

    public void decrypt(String body, ClientSession session) {
        decrypt(body, session, null);
    }

    public void decrypt(String body, ClientSession session, byte[] aad) {
        EncryptedPayload payload = new EncryptedPayload(body, session.getSession().getApiKeys(), aad);
        String text = payload.decrypt();
        this.text = text == null ? "" : text;
    }

    public JSONObject getJsonObj(String key) {
        Object obj = values.get(key);
        if(obj instanceof JSONObject) return (JSONObject) obj;
        if(obj instanceof Map) return new JSONObject((Map<?, ?>) obj);
        return new JSONObject();
    }

    public JSONArray getJsonArr(String key) {
        Object obj = values.get(key);
        return obj instanceof JSONArray ? (JSONArray) obj : new JSONArray();
    }

    public Object get(String key) { return values.get(key); }
    public int getInt(String key) { return Integer.parseInt(String.valueOf(values.get(key))); }
    public String getString(String key) {
        Object value = values.get(key);
        return value == null ? null : value.toString();
    }
    public double getDouble(String key) { return Double.parseDouble(String.valueOf(values.get(key))); }

    public boolean getBoolean(String key) throws Exception {
        String val = values.get(key).toString().toUpperCase();
        if(val.equals("TRUE") || val.equals("1")) return true;
        else if(val.equals("FALSE") || val.equals("0")) return false;
        throw new Exception();
    }

    public String[] getArr(String key, String delimiter) { return __.split(values.get(key).toString(), delimiter); }

    public LocalDateTime getDateTimeFromTimestamp(String key) {
        return LocalDateTime.ofEpochSecond(Long.valueOf(values.get(key).toString()), 0, ZoneOffset.UTC);
    }

    public int getInt(JSONObject inputObj, String key) { return Integer.parseInt((String) inputObj.get(key)); }
    public String getString(JSONObject inputObj, String key) { return (String) inputObj.get(key); }
    public double getDouble(JSONObject inputObj, String key) { return Double.parseDouble((String) inputObj.get(key)); }

    public boolean getBoolean(JSONObject inputObj, String key) throws Exception {
        String val = ((String) inputObj.get(key)).toUpperCase();
        if(val.equals("TRUE") || val.equals("1")) return true;
        else if(val.equals("FALSE") || val.equals("0")) return false;
        throw new Exception();
    }

    public String[] getArr(String key, String delimiter, JSONObject inputObj) {
        return __.split(((String) inputObj.get(key)), delimiter);
    }

    public LocalDateTime getDateTimeFromTimestamp(JSONObject inputObj, String key) {
        return LocalDateTime.ofEpochSecond(Long.valueOf(((String) inputObj.get(key))), 0, ZoneOffset.UTC);
    }

    private void parse(ContentType contentType, RequestHeaders headers) {
        if(contentType == null) contentType = ContentType.TEXT;
        switch (contentType) {
            case JSON:
                String verified = JSON.verify(text);
                if("false".equals(verified)) {
                    valid = false;
                    values.clear();
                } else {
                    values = JSON.getObject(verified);
                }
                break;
            case URL_ENCODED:
                putAll(parseQuery(text));
                break;
            case GET_URL:
                putAll(parseQuery(extractQuery(text)));
                break;
            case FORM_DATA:
                putAll(parseMultipart(text, boundary(headers)));
                break;
            case TEXT:
            default:
                break;
        }
    }

    private static HashMap<String, Object> parseQuery(String query) {
        HashMap<String, Object> result = new HashMap<>();
        if(query == null || query.isBlank()) return result;
        String normalized = query.startsWith("?") ? query.substring(1) : query;
        for(String param : normalized.split("&")) {
            if(param.isBlank()) continue;
            String[] entry = param.split("=", 2);
            String key = decode(entry[0]);
            String value = entry.length > 1 ? decode(entry[1]) : "";
            result.put(key, value);
        }
        return result;
    }

    private static String extractQuery(String input) {
        if(input == null) return "";
        int index = input.indexOf('?');
        return index < 0 ? "" : input.substring(index + 1);
    }

    private static HashMap<String, Object> parseMultipart(String body, String boundary) {
        HashMap<String, Object> result = new HashMap<>();
        if(body == null || body.isEmpty() || boundary == null || boundary.isBlank()) return result;
        String delimiter = "--" + boundary;
        String[] parts = body.split(java.util.regex.Pattern.quote(delimiter));
        for(String part : parts) {
            String normalized = trimPart(part);
            if(normalized.isBlank() || normalized.equals("--")) continue;
            int separator = normalized.indexOf("\r\n\r\n");
            int separatorLength = 4;
            if(separator < 0) {
                separator = normalized.indexOf("\n\n");
                separatorLength = 2;
            }
            if(separator < 0) continue;
            String headerText = normalized.substring(0, separator);
            String value = normalized.substring(separator + separatorLength);
            if(value.endsWith("\r\n")) value = value.substring(0, value.length() - 2);
            if(value.endsWith("\n")) value = value.substring(0, value.length() - 1);
            if(value.endsWith("--")) value = value.substring(0, value.length() - 2);
            String name = multipartName(headerText);
            if(name != null && !name.isBlank()) result.put(name, value);
        }
        return result;
    }

    private static String boundary(RequestHeaders headers) {
        if(headers == null || !headers.contains("Content-Type")) return null;
        String contentType = headers.getString("Content-Type");
        for(String part : contentType.split(";")) {
            String trimmed = part.trim();
            if(trimmed.toLowerCase().startsWith("boundary=")) {
                String boundary = trimmed.substring("boundary=".length()).trim();
                if(boundary.startsWith("\"") && boundary.endsWith("\"") && boundary.length() >= 2) {
                    boundary = boundary.substring(1, boundary.length() - 1);
                }
                return boundary;
            }
        }
        return null;
    }

    private static String multipartName(String headers) {
        for(String line : headers.split("\r?\n")) {
            String lower = line.toLowerCase();
            if(!lower.startsWith("content-disposition:")) continue;
            for(String attr : line.split(";")) {
                String trimmed = attr.trim();
                if(trimmed.startsWith("name=")) {
                    String value = trimmed.substring("name=".length()).trim();
                    if(value.startsWith("\"") && value.endsWith("\"") && value.length() >= 2) {
                        value = value.substring(1, value.length() - 1);
                    }
                    return value;
                }
            }
        }
        return null;
    }

    private static String trimPart(String part) {
        if(part == null) return "";
        String result = part;
        while(result.startsWith("\r\n")) result = result.substring(2);
        while(result.startsWith("\n")) result = result.substring(1);
        return result;
    }

    private static String decode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }
}
