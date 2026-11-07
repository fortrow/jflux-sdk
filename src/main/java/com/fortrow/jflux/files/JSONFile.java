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
 */

package com.fortrow.jflux.files;

import com.fortrow.jflux.exceptions.ExceptionHandling;
import com.fortrow.jflux.crypto.builtin.SHA;
import com.fortrow.jflux.exceptions.types.CommonIOException;
import com.fortrow.jflux.json.JSON;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** JSON-backed file with simple dot and array-index path access. */
public class JSONFile {

    private final FileObject file;
    private JSONObject object;
    private boolean lock;
    private String digest;
    private final String filepath;

    public JSONFile(String filepath, boolean lock) throws CommonIOException {
        this.filepath = filepath;
        this.lock = lock;
        this.file = Files.open(filepath);
        this.file.readToEnd();
        String content = this.file.string(StandardCharsets.UTF_8);
        this.object = JSON.getObject(content);
        this.digest = SHA.get384(content);
    }

    public JSONFile(String filepath, JSONObject object) throws CommonIOException {
        if (object == null) throw ExceptionHandling.invalid("JSON object is required.");
        this.filepath = filepath;
        this.file = Files.open(filepath);
        this.object = object;
        save();
    }

    public void setByPath(String path, Object value) {
        if (lock) return;
        List<Token> tokens = tokenize(path);
        if (tokens.isEmpty()) throw ExceptionHandling.invalid("JSON path is required.");
        Object current = object;
        for (int i = 0; i < tokens.size() - 1; i++) current = child(current, tokens.get(i));
        assign(current, tokens.get(tokens.size() - 1), value);
        save();
    }

    public JSONObject getJson() {
        return object;
    }

    public String getFilepath() {
        return filepath;
    }

    public String getSignature() {
        return digest;
    }

    @SuppressWarnings("unchecked")
    public <Any> Any getByPath(String path) {
        Object current = object;
        for (Token token : tokenize(path)) current = child(current, token);
        return (Any) current;
    }

    public boolean isLocked() {
        return lock;
    }

    public void save() {
        String content = JSON.prettyString(object);
        file.overwrite(content.getBytes(StandardCharsets.UTF_8));
        digest = SHA.get384(content);
    }

    private Object child(Object current, Token token) {
        if (token.index != null) {
            if (!(current instanceof List<?> list)) throw ExceptionHandling.invalid("Expected an array before " + token);
            if (token.index < 0 || token.index >= list.size()) {
                String message = "Array index out of bounds: " + token.index;
                ExceptionHandling.report(new IndexOutOfBoundsException(message));
                throw new IndexOutOfBoundsException(message);
            }
            return list.get(token.index);
        }
        if (!(current instanceof Map<?, ?> map)) throw ExceptionHandling.invalid("Expected an object before " + token.key);
        return map.get(token.key);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void assign(Object current, Token token, Object value) {
        if (token.index != null) {
            if (!(current instanceof List list)) throw ExceptionHandling.invalid("Expected an array at " + token);
            if (token.index < 0 || token.index > list.size()) {
                String message = "Array index out of bounds: " + token.index;
                ExceptionHandling.report(new IndexOutOfBoundsException(message));
                throw new IndexOutOfBoundsException(message);
            }
            if (token.index == list.size()) list.add(value); else list.set(token.index, value);
            return;
        }
        if (!(current instanceof Map map)) throw ExceptionHandling.invalid("Expected an object at " + token.key);
        map.put(token.key, value);
    }

    private List<Token> tokenize(String path) {
        if (path == null || path.isBlank()) return List.of();
        ArrayList<Token> result = new ArrayList<>();
        for (String segment : path.split("\\.")) {
            if (segment.isEmpty()) throw ExceptionHandling.invalid("Invalid JSON path: " + path);
            int cursor = 0;
            int bracket = segment.indexOf('[');
            if (bracket < 0) {
                result.add(new Token(segment, null));
                continue;
            }
            if (bracket > 0) result.add(new Token(segment.substring(0, bracket), null));
            while (bracket >= 0) {
                int end = segment.indexOf(']', bracket);
                if (end < 0) throw ExceptionHandling.invalid("Invalid JSON array path: " + path);
                try {
                    result.add(new Token(null, Integer.parseInt(segment.substring(bracket + 1, end))));
                } catch (NumberFormatException e) {
                    throw ExceptionHandling.invalid("Invalid JSON array index: " + segment);
                }
                cursor = end + 1;
                bracket = segment.indexOf('[', cursor);
                if (bracket < 0 && cursor < segment.length()) {
                    result.add(new Token(segment.substring(cursor), null));
                }
            }
        }
        return result;
    }

    private record Token(String key, Integer index) { }
}
