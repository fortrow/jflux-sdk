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
package com.fortrow.jflux.console;

import com.fortrow.jflux.exceptions.ExceptionHandling;
import com.fortrow.jflux.output.json.JSON;
import com.fortrow.jflux.sys.PlatformUtils;
import com.fortrow.jflux.shell.CommandResult;
import com.fortrow.jflux.shell.Shell;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * A small terminal JSON writer/editor.
 *
 * <p>The editor keeps JSON data structured while editing. It never asks the
 * user to hand-count braces or quote keys: the rendered view owns the
 * indentation, separators, quotes, brackets, and braces. On a real Unix TTY
 * it switches to raw input mode so arrow keys work one keystroke at a time;
 * streams and redirected input use the same key protocol without terminal
 * control sequences.</p>
 */
public class ObjectiveConsole {

    private static final String ESC = "\033[";
    private static final String RESET = "\033[0m";
    private static final String SELECTED = ESC + "7m";
    private static final String DEFAULT_OBJECT_KEY = "new_field";

    private final String command;
    private Object document;
    private final ArrayList<Field> fields = new ArrayList<>();
    private boolean color = System.console() != null;
    private int selectedIndex;
    private int cursor;
    private String status = "";

    /** Creates an editor from JSON, or an empty object when the command is not JSON. */
    public ObjectiveConsole(String command) {
        this.command = command == null ? "" : command;
        this.document = initialDocument(this.command);
        rebuildFields(null);
    }

    /** Creates an editor from JSON text. */
    public static ObjectiveConsole create(String command) {
        return new ObjectiveConsole(command);
    }

    /** Creates an empty object editor. */
    public static ObjectiveConsole create() {
        return new ObjectiveConsole("{}");
    }

    /** Returns the original command supplied to the compatibility constructor. */
    public String getCommand() {
        return command;
    }

    /** Enables or disables ANSI selection highlighting in {@link #render()}. */
    public ObjectiveConsole color(boolean enabled) {
        this.color = enabled;
        return this;
    }

    /** Returns the current compact JSON document. */
    public String json() {
        return JSON.string(document);
    }

    /** Compatibility alias for {@link #json()}. */
    public String getJson() {
        return json();
    }

    /** Returns the current JSON document in the terminal editor layout. */
    public String render() {
        ensureFields();
        StringBuilder output = new StringBuilder(256);
        renderNode(document, output, 0, List.of());
        return output.toString();
    }

    /** Returns the current layout without ANSI highlighting. */
    public String renderPlain() {
        boolean previous = color;
        color = false;
        try {
            return render();
        } finally {
            color = previous;
        }
    }

    /** Edits JSON using the process terminal. */
    public String edit() {
        return edit(System.in, System.out);
    }

    /**
     * Edits JSON using caller-owned streams.
     *
     * <p>Enter commits the current field and moves forward. Tab commits and
     * moves forward, creating a new sibling at the end or a new child when a
     * container is selected. Up/Down changes fields, Left/Right moves inside
     * the selected text, and Escape cancels the session.</p>
     */
    public String edit(InputStream input, PrintStream output) {
        if (input == null) throw ExceptionHandling.invalid("ObjectiveConsole input is required.");
        if (output == null) throw ExceptionHandling.invalid("ObjectiveConsole output is required.");

        String original = json();
        boolean interactive = input == System.in && output == System.out && System.console() != null;
        TerminalMode terminal = interactive ? TerminalMode.enter() : TerminalMode.none();
        BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8));
        try {
            if (interactive) drawFrame(output);
            while (true) {
                Key key = readKey(reader);
                if (key.type == KeyType.END_OF_INPUT) return finish(output, interactive);
                if (key.type == KeyType.CANCEL) {
                    restore(original);
                    return finish(output, interactive);
                }
                if (key.type == KeyType.ESCAPE) {
                    restore(original);
                    return finish(output, interactive);
                }

                boolean done = handle(key);
                if (done) return finish(output, interactive);
                if (interactive) drawFrame(output);
            }
        } catch (IOException e) {
            throw ExceptionHandling.failure("Unable to read ObjectiveConsole input.", e);
        } finally {
            terminal.close();
        }
    }

    private String finish(PrintStream output, boolean interactive) {
        if (interactive) {
            output.print(ESC + "0m\n");
            output.flush();
        } else {
            output.println(renderPlain());
        }
        return json();
    }

    private boolean handle(Key key) {
        Field field = currentField();
        switch (key.type) {
            case ENTER -> {
                if (!commitCurrent()) return false;
                advance(false);
            }
            case TAB -> {
                if (field.kind == FieldKind.CONTAINER) {
                    insertChild(field);
                } else if (commitCurrent()) {
                    advance(true);
                }
            }
            case UP -> {
                if (commitCurrent()) select(selectedIndex - 1);
            }
            case DOWN -> {
                if (commitCurrent()) select(selectedIndex + 1);
            }
            case LEFT -> moveCursor(-1);
            case RIGHT -> moveCursor(1);
            case HOME -> cursor = 0;
            case END -> {
                if (key.control) return commitAndFinish();
                cursor = field.draft.length();
            }
            case DELETE -> deleteAtCursor();
            case BACKSPACE -> backspace();
            case CLEAR -> {
                field.draft = "";
                cursor = 0;
            }
            case SAVE -> {
                if (commitCurrent()) status = "Saved in memory. Press Ctrl+D to finish.";
            }
            case INSERT_OBJECT -> insertContainer(false);
            case INSERT_ARRAY -> insertContainer(true);
            case ESCAPE -> {
                return true;
            }
            case CHARACTER -> insert(key.value);
            default -> {
                // Unknown control sequences are intentionally ignored.
            }
        }
        return false;
    }

    private boolean commitAndFinish() {
        return commitCurrent();
    }

    private void advance(boolean tab) {
        if (fields.isEmpty()) {
            rebuildFields(null);
            return;
        }
        if (selectedIndex + 1 < fields.size()) {
            select(selectedIndex + 1);
            return;
        }
        appendSibling(currentField());
    }

    private void insertChild(Field field) {
        Object container = valueAt(field.path);
        if (container instanceof Map<?, ?> map) {
            @SuppressWarnings("unchecked") Map<Object, Object> object = (Map<Object, Object>) map;
            String key = uniqueKey(object);
            object.put(key, "");
            rebuildFields(new Identity(FieldKind.KEY, append(field.path, key)));
            status = "Added object field '" + key + "'.";
        } else if (container instanceof List<?> list) {
            @SuppressWarnings("unchecked") List<Object> array = (List<Object>) list;
            array.add("");
            rebuildFields(new Identity(FieldKind.VALUE, append(field.path, array.size() - 1)));
            status = "Added array item.";
        } else {
            status = "Select an object or array to add a child.";
        }
    }

    private void appendSibling(Field field) {
        if (field.path.isEmpty()) {
            if (document instanceof Map<?, ?> map) {
                @SuppressWarnings("unchecked") Map<Object, Object> object = (Map<Object, Object>) map;
                String key = uniqueKey(object);
                object.put(key, "");
                rebuildFields(new Identity(FieldKind.KEY, List.of(key)));
                return;
            }
            if (document instanceof List<?> list) {
                @SuppressWarnings("unchecked") List<Object> array = (List<Object>) list;
                array.add("");
                rebuildFields(new Identity(FieldKind.VALUE, List.of(array.size() - 1)));
                return;
            }
            document = new JSONObject();
            insertChild(new Field(FieldKind.CONTAINER, List.of(), document));
            return;
        }

        Object parent = valueAt(field.path.subList(0, field.path.size() - 1));
        if (parent instanceof Map<?, ?> map) {
            @SuppressWarnings("unchecked") Map<Object, Object> object = (Map<Object, Object>) map;
            String key = uniqueKey(object);
            object.put(key, "");
            rebuildFields(new Identity(FieldKind.KEY, append(field.path.subList(0, field.path.size() - 1), key)));
        } else if (parent instanceof List<?> list) {
            @SuppressWarnings("unchecked") List<Object> array = (List<Object>) list;
            array.add("");
            rebuildFields(new Identity(FieldKind.VALUE,
                    append(field.path.subList(0, field.path.size() - 1), array.size() - 1)));
        }
    }

    private void insertContainer(boolean array) {
        Field field = currentField();
        if (field.kind == FieldKind.CONTAINER) {
            insertChild(field);
            return;
        }
        Object replacement = array ? new JSONArray() : new JSONObject();
        putValue(field.path, replacement);
        rebuildFields(new Identity(FieldKind.CONTAINER, field.path));
        status = array ? "Inserted an array." : "Inserted an object.";
    }

    private boolean commitCurrent() {
        Field field = currentField();
        if (field.kind == FieldKind.CONTAINER) return true;
        String identity = new Identity(field.kind, field.path).key();
        try {
            if (field.kind == FieldKind.KEY) commitKey(field);
            else putValue(field.path, parseDraft(field));
            status = "";
            rebuildFields(new Identity(field.kind, field.path));
            selectIdentity(identity);
            return true;
        } catch (RuntimeException e) {
            status = e.getMessage() == null ? "Invalid value." : e.getMessage();
            return false;
        }
    }

    private void commitKey(Field field) {
        if (field.draft.isBlank()) throw new IllegalArgumentException("JSON keys cannot be blank.");
        if (field.path.isEmpty()) throw new IllegalArgumentException("The JSON root has no key.");
        Object parent = valueAt(field.path.subList(0, field.path.size() - 1));
        if (!(parent instanceof Map<?, ?> map)) throw new IllegalArgumentException("Only object keys are editable.");
        @SuppressWarnings("unchecked") Map<Object, Object> object = (Map<Object, Object>) map;
        Object oldKey = field.path.get(field.path.size() - 1);
        if (Objects.equals(oldKey, field.draft)) return;
        if (object.containsKey(field.draft)) throw new IllegalArgumentException("That JSON key already exists.");
        Object value = object.remove(oldKey);
        object.put(field.draft, value);
    }

    private Object parseDraft(Field field) {
        Object original = valueAt(field.path);
        String draft = field.draft;
        if (original instanceof String) return draft;
        if (original instanceof Boolean) {
            if ("true".equalsIgnoreCase(draft)) return true;
            if ("false".equalsIgnoreCase(draft)) return false;
            throw new IllegalArgumentException("Boolean values must be true or false.");
        }
        if (original instanceof Number) return number(draft);
        if (original == null) {
            if (draft.isBlank() || "null".equalsIgnoreCase(draft)) return null;
            return draft;
        }
        return draft;
    }

    private Number number(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Number values cannot be blank.");
        try {
            BigDecimal decimal = new BigDecimal(value);
            if (decimal.scale() <= 0) return decimal.longValueExact();
            return decimal.doubleValue();
        } catch (NumberFormatException | ArithmeticException e) {
            throw new IllegalArgumentException("Invalid JSON number.", e);
        }
    }

    private void restore(String original) {
        document = initialDocument(original);
        rebuildFields(null);
    }

    private void insert(char value) {
        Field field = currentField();
        if (field.kind == FieldKind.CONTAINER) return;
        field.draft = field.draft.substring(0, cursor) + value + field.draft.substring(cursor);
        cursor++;
        status = "";
    }

    private void backspace() {
        Field field = currentField();
        if (field.kind == FieldKind.CONTAINER || cursor == 0) return;
        field.draft = field.draft.substring(0, cursor - 1) + field.draft.substring(cursor);
        cursor--;
    }

    private void deleteAtCursor() {
        Field field = currentField();
        if (field.kind == FieldKind.CONTAINER || cursor >= field.draft.length()) return;
        field.draft = field.draft.substring(0, cursor) + field.draft.substring(cursor + 1);
    }

    private void moveCursor(int delta) {
        Field field = currentField();
        if (field.kind == FieldKind.CONTAINER) return;
        cursor = Math.max(0, Math.min(field.draft.length(), cursor + delta));
    }

    private void select(int index) {
        if (fields.isEmpty()) return;
        selectedIndex = Math.max(0, Math.min(index, fields.size() - 1));
        Field field = currentField();
        cursor = field.kind == FieldKind.CONTAINER ? 0 : field.draft.length();
        status = "";
    }

    private void selectIdentity(String key) {
        for (int i = 0; i < fields.size(); i++) {
            if (fields.get(i).identity().key().equals(key)) {
                selectedIndex = i;
                cursor = fields.get(i).kind == FieldKind.CONTAINER ? 0 : fields.get(i).draft.length();
                return;
            }
        }
        select(selectedIndex);
    }

    private Field currentField() {
        ensureFields();
        return fields.get(Math.max(0, Math.min(selectedIndex, fields.size() - 1)));
    }

    private void ensureFields() {
        if (fields.isEmpty()) rebuildFields(null);
    }

    private void rebuildFields(Identity preferred) {
        String previous = preferred == null && !fields.isEmpty()
                ? fields.get(Math.min(selectedIndex, fields.size() - 1)).identity().key()
                : preferred == null ? null : preferred.key();
        fields.clear();
        collect(document, List.of());
        if (fields.isEmpty()) {
            FieldKind kind = isContainer(document) ? FieldKind.CONTAINER : FieldKind.VALUE;
            fields.add(new Field(kind, List.of(), document));
        }
        if (previous != null) selectIdentity(previous);
        else {
            selectedIndex = Math.min(selectedIndex, fields.size() - 1);
            cursor = currentField().kind == FieldKind.CONTAINER ? 0 : currentField().draft.length();
        }
    }

    private void collect(Object value, List<Object> path) {
        if (value instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                List<Object> childPath = append(path, entry.getKey());
                fields.add(new Field(FieldKind.KEY, childPath, entry.getKey()));
                collectChild(entry.getValue(), childPath);
            }
        } else if (value instanceof List<?> list) {
            for (int i = 0; i < list.size(); i++) collectChild(list.get(i), append(path, i));
        }
    }

    private void collectChild(Object child, List<Object> path) {
        if (isContainer(child)) {
            fields.add(new Field(FieldKind.CONTAINER, path, child));
            collect(child, path);
        } else {
            fields.add(new Field(FieldKind.VALUE, path, child));
        }
    }

    private void renderNode(Object value, StringBuilder output, int indent, List<Object> path) {
        if (value instanceof Map<?, ?> map) {
            appendContainerStart(output, '{', path);
            if (!map.isEmpty()) output.append('\n');
            int index = 0;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                output.append("  ".repeat(indent + 1));
                List<Object> childPath = append(path, entry.getKey());
                output.append(renderKey(String.valueOf(entry.getKey()), childPath));
                output.append(": ");
                renderNodeOrValue(entry.getValue(), output, indent + 1, childPath);
                if (++index < map.size()) output.append(',');
                output.append('\n');
            }
            if (!map.isEmpty()) output.append("  ".repeat(indent));
            output.append('}');
        } else if (value instanceof List<?> list) {
            appendContainerStart(output, '[', path);
            if (!list.isEmpty()) output.append('\n');
            for (int i = 0; i < list.size(); i++) {
                output.append("  ".repeat(indent + 1));
                renderNodeOrValue(list.get(i), output, indent + 1, append(path, i));
                if (i + 1 < list.size()) output.append(',');
                output.append('\n');
            }
            if (!list.isEmpty()) output.append("  ".repeat(indent));
            output.append(']');
        } else {
            appendPrimitive(value, output, path);
        }
    }

    private void renderNodeOrValue(Object value, StringBuilder output, int indent, List<Object> path) {
        if (isContainer(value)) renderNode(value, output, indent, path);
        else appendPrimitive(value, output, path);
    }

    private void appendContainerStart(StringBuilder output, char open, List<Object> path) {
        if (selected(FieldKind.CONTAINER, path)) output.append(style(String.valueOf(open)));
        else output.append(open);
    }

    private String renderKey(String key, List<Object> path) {
        Field field = selectedField(FieldKind.KEY, path);
        return field == null ? JSON.string(key) : style(editable(field.draft, true, field));
    }

    private void appendPrimitive(Object value, StringBuilder output, List<Object> path) {
        Field field = selectedField(FieldKind.VALUE, path);
        if (field == null) output.append(JSON.string(value));
        else output.append(style(editable(field.draft, value instanceof String, field)));
    }

    private String editable(String text, boolean quoted, Field field) {
        String value = text == null ? "" : text;
        if (!color) return JSON.string(quoted ? value : valueForRender(field, valueAt(field.path)));
        int position = Math.max(0, Math.min(field.draft.length(), cursor));
        if (quoted) {
            String before = JSON.string(value.substring(0, Math.min(position, value.length())));
            String after = JSON.string(value.substring(Math.min(position, value.length())));
            return before.substring(0, before.length() - 1) + "▌" + after.substring(1);
        }
        return value.substring(0, Math.min(position, value.length()))
                + "▌" + value.substring(Math.min(position, value.length()));
    }

    private Object valueForRender(Field field, Object committed) {
        if (committed instanceof String) return field.draft;
        if (committed instanceof Boolean) return Boolean.parseBoolean(field.draft);
        if (committed instanceof Number) {
            try {
                return number(field.draft);
            } catch (RuntimeException ignored) {
                return field.draft;
            }
        }
        if (committed == null) return field.draft.isBlank() ? null : field.draft;
        return field.draft;
    }

    private boolean selected(FieldKind kind, List<Object> path) {
        return selectedField(kind, path) != null;
    }

    private Field selectedField(FieldKind kind, List<Object> path) {
        if (fields.isEmpty()) return null;
        Field field = currentField();
        return field.kind == kind && field.path.equals(path) ? field : null;
    }

    private String style(String text) {
        return color ? SELECTED + text + RESET : text;
    }

    private void drawFrame(PrintStream output) {
        output.print(ESC + "2J" + ESC + "H");
        output.println(render());
        output.println();
        output.print("↑/↓ field  ←/→ cursor  Enter/Tab next  Ctrl+O object  Ctrl+A array  Ctrl+D done  Esc cancel");
        if (!status.isBlank()) output.print("  [" + status + "]");
        output.flush();
    }

    private Key readKey(BufferedReader reader) throws IOException {
        int value = reader.read();
        if (value < 0) return Key.end();
        if (value == 3) return Key.cancel();
        if (value == 4) return Key.control(KeyType.END);
        if (value == 9) return Key.of(KeyType.TAB);
        if (value == 10 || value == 13) return Key.of(KeyType.ENTER);
        if (value == 8 || value == 127) return Key.of(KeyType.BACKSPACE);
        if (value == 12) return Key.of(KeyType.CLEAR);
        if (value == 15) return Key.of(KeyType.INSERT_OBJECT);
        if (value == 1) return Key.of(KeyType.INSERT_ARRAY);
        if (value == 19) return Key.of(KeyType.SAVE);
        if (value == 21) return Key.of(KeyType.CLEAR);
        if (value != 27) return Key.character((char) value);

        int prefix = readEscapePart(reader);
        if (prefix < 0) return Key.of(KeyType.ESCAPE);
        if (prefix != '[' && prefix != 'O') return Key.of(KeyType.ESCAPE);
        int code = readEscapePart(reader);
        if (code < 0) return Key.of(KeyType.ESCAPE);
        if (code == 'A') return Key.of(KeyType.UP);
        if (code == 'B') return Key.of(KeyType.DOWN);
        if (code == 'C') return Key.of(KeyType.RIGHT);
        if (code == 'D') return Key.of(KeyType.LEFT);
        if (code == 'H') return Key.of(KeyType.HOME);
        if (code == 'F') return Key.of(KeyType.END);
        if (code == '2') {
            if (reader.read() == '~') return Key.of(KeyType.INSERT_OBJECT);
        }
        if (code == '3') {
            if (reader.read() == '~') return Key.of(KeyType.DELETE);
        }
        return Key.of(KeyType.ESCAPE);
    }

    private int readEscapePart(BufferedReader reader) throws IOException {
        long deadline = System.nanoTime() + 100_000_000L;
        while (!reader.ready() && System.nanoTime() < deadline) {
            try {
                Thread.sleep(1L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return -1;
            }
        }
        return reader.ready() ? reader.read() : -1;
    }

    private Object valueAt(List<Object> path) {
        Object current = document;
        for (Object segment : path) {
            if (current instanceof Map<?, ?> map) current = map.get(segment);
            else if (current instanceof List<?> list && segment instanceof Integer index && index >= 0 && index < list.size()) {
                current = list.get(index);
            } else return null;
        }
        return current;
    }

    private void putValue(List<Object> path, Object value) {
        if (path.isEmpty()) {
            document = value;
            return;
        }
        Object parent = valueAt(path.subList(0, path.size() - 1));
        Object segment = path.get(path.size() - 1);
        if (parent instanceof Map<?, ?> map) {
            @SuppressWarnings("unchecked") Map<Object, Object> object = (Map<Object, Object>) map;
            object.put(segment, value);
        } else if (parent instanceof List<?> list && segment instanceof Integer index) {
            @SuppressWarnings("unchecked") List<Object> array = (List<Object>) list;
            array.set(index, value);
        } else {
            throw new IllegalArgumentException("Unable to update the selected JSON value.");
        }
    }

    private String uniqueKey(Map<Object, Object> object) {
        if (!object.containsKey(DEFAULT_OBJECT_KEY)) return DEFAULT_OBJECT_KEY;
        int suffix = 2;
        while (object.containsKey(DEFAULT_OBJECT_KEY + "_" + suffix)) suffix++;
        return DEFAULT_OBJECT_KEY + "_" + suffix;
    }

    private static Object initialDocument(String source) {
        if (source != null && !source.isBlank() && JSON.isValid(source)) return JSON.parse(source);
        return new JSONObject();
    }

    private static boolean isContainer(Object value) {
        return value instanceof Map<?, ?> || value instanceof List<?>;
    }

    private static String draft(Object value) {
        if (value instanceof String string) return string;
        if (value == null) return "null";
        return String.valueOf(value);
    }

    private static List<Object> append(List<Object> path, Object value) {
        ArrayList<Object> result = new ArrayList<>(path.size() + 1);
        result.addAll(path);
        result.add(value);
        return List.copyOf(result);
    }

    private enum FieldKind {
        KEY,
        VALUE,
        CONTAINER
    }

    private static final class Field {
        private final FieldKind kind;
        private final List<Object> path;
        private String draft;

        private Field(FieldKind kind, List<Object> path, Object value) {
            this.kind = kind;
            this.path = List.copyOf(path);
            this.draft = kind == FieldKind.VALUE || kind == FieldKind.KEY ? draft(value) : "";
        }

        private Identity identity() {
            return new Identity(kind, path);
        }
    }

    private record Identity(FieldKind kind, List<Object> path) {
        private String key() {
            return kind + ":" + path;
        }
    }

    private enum KeyType {
        CHARACTER,
        ENTER,
        TAB,
        UP,
        DOWN,
        LEFT,
        RIGHT,
        HOME,
        END,
        DELETE,
        BACKSPACE,
        CLEAR,
        SAVE,
        INSERT_OBJECT,
        INSERT_ARRAY,
        ESCAPE,
        CANCEL,
        END_OF_INPUT
    }

    private static final class Key {
        private final KeyType type;
        private final char value;
        private final boolean control;

        private Key(KeyType type, char value, boolean control) {
            this.type = type;
            this.value = value;
            this.control = control;
        }

        private static Key of(KeyType type) {
            return new Key(type, '\0', false);
        }

        private static Key character(char value) {
            return new Key(KeyType.CHARACTER, value, false);
        }

        private static Key control(KeyType type) {
            return new Key(type, '\0', true);
        }

        private static Key end() {
            return of(KeyType.END_OF_INPUT);
        }

        private static Key cancel() {
            return of(KeyType.CANCEL);
        }
    }

    /** Uses the existing shell boundary to enter and restore Unix raw mode. */
    private static final class TerminalMode implements AutoCloseable {
        private static final Duration TIMEOUT = Duration.ofMillis(500);
        private final String state;

        private TerminalMode(String state) {
            this.state = state;
        }

        private static TerminalMode none() {
            return new TerminalMode("");
        }

        private static TerminalMode enter() {
            if (!PlatformUtils.isUnix()) return none();
            CommandResult current = Shell.execute(List.of("sh", "-c", "stty -g < /dev/tty"), TIMEOUT, 4096);
            if (!current.success() || current.stdout().isBlank()) return none();
            CommandResult changed = Shell.execute(
                    List.of("sh", "-c", "stty -icanon -echo min 1 time 0 < /dev/tty"), TIMEOUT, 4096);
            return changed.success() ? new TerminalMode(current.stdout().trim()) : none();
        }

        @Override
        public void close() {
            if (state.isBlank()) return;
            Shell.execute(List.of("sh", "-c", "stty " + state + " < /dev/tty"), TIMEOUT, 4096);
        }
    }
}
