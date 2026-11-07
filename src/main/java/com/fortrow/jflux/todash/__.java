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
 * File created by Ryan Fitzgerald (ryan@fortrow.com) on 2026-05-04 15:27:04 EDT
 */
package com.fortrow.jflux.todash;

import com.fortrow.jflux.crypto.Rand;
import com.fortrow.jflux.crypto.builtin.SHA;
import com.fortrow.jflux.encoding.BaseX;
import com.fortrow.jflux.encoding.Hex;
import com.fortrow.jflux.exceptions.ExceptionHandling;

import java.lang.reflect.Array;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Scanner;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Pattern;

public final class __ {

    private static final Pattern CAMEL_BOUNDARY = Pattern.compile("([a-z0-9])([A-Z])");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");
    private static final Pattern BASE64_SHAPE = Pattern.compile("[A-Za-z0-9+/]+={0,2}");
    private static final String ANSI_RESET = "\u001B[0m";
    private static final String[] ANSI_COLORS = {
            "\u001B[30m", "\u001B[31m", "\u001B[32m", "\u001B[33m",
            "\u001B[34m", "\u001B[35m", "\u001B[36m", "\u001B[37m"
    };
    private static final Scanner INPUT = new Scanner(System.in, StandardCharsets.UTF_8);

    /** Retained for source compatibility; this utility has no instance state. */
    public __() { }

    // COMPARE

    public static Comparison compare(int input) {
        return new Comparison(input);
    }

    public static Comparison compare(double input) {
        return new Comparison(input);
    }

    public static Comparison compare(float input) {
        return new Comparison(input);
    }

    public static Comparison compare(long input) {
        return new Comparison(input);
    }

    public static class Comparison {

        private double input;

        public Comparison(int input) {
            this.input = input;
        }

        public Comparison(double input) {
            this.input = input;
        }

        public Comparison(float input) {
            this.input = input;
        }

        public Comparison(long input) {
            this.input = input;
        }

        public boolean gt(int input) {
            return this.input > input;
        }

        public boolean gt(double input) {
            return this.input > input;
        }

        public boolean gt(float input) {
            return this.input > input;
        }

        public boolean gt(long input) {
            return this.input > input;
        }

        public boolean lt(int input) {
            return this.input < input;
        }

        public boolean lt(double input) {
            return this.input < input;
        }

        public boolean lt(float input) {
            return this.input < input;
        }

        public boolean lt(long input) {
            return this.input < input;
        }

        public boolean gte(int input) {
            return this.input >= input;
        }

        public boolean gte(double input) {
            return this.input >= input;
        }

        public boolean gte(float input) {
            return this.input >= input;
        }

        public boolean gte(long input) {
            return this.input >= input;
        }

        public boolean lte(int input) {
            return this.input <= input;
        }

        public boolean lte(double input) {
            return this.input <= input;
        }

        public boolean lte(float input) {
            return this.input <= input;
        }

        public boolean lte(long input) {
            return this.input <= input;
        }

        public boolean eq(int input) {
            return this.input == input;
        }

        public boolean eq(double input) {
            return this.input == input;
        }

        public boolean eq(float input) {
            return this.input == input;
        }

        public boolean eq(long input) {
            return this.input == input;
        }

    }

    // NULL VALUES

    public static Object nothing() {
        return null;
    }

    public static boolean unset(Object o) {
        return empty(o);
    }

    public static boolean isset(Object o) {
        return !empty(o);
    }

    public static boolean empty(Object o) {
        // Compatibility contract: object emptiness historically meant null only.
        return o == null;
    }

    private static boolean lodashEmpty(Object o) {
        if (o == null) return true;
        if (o instanceof CharSequence sequence) return sequence.isEmpty();
        if (o instanceof Collection<?> collection) return collection.isEmpty();
        if (o instanceof Map<?, ?> map) return map.isEmpty();
        if (o instanceof Iterable<?> iterable) return !iterable.iterator().hasNext();
        return o.getClass().isArray() && Array.getLength(o) == 0;
    }

    public static boolean empty(String str) {
        return str == null || str.isEmpty();
    }

    // STRINGS

    public static boolean same(String str1, String str2) {
        return Objects.equals(str1, str2);
    }

    public static String trim(String str) {
        return requireString(str).strip();
    }

    public static String trimLeading(String str) {
        return requireString(str).stripLeading();
    }

    public static String trimTrailing(String str) {
        return requireString(str).stripTrailing();
    }

    public static String replaceSpaces(String str, String replacement) {
        return WHITESPACE.matcher(requireString(str)).replaceAll(java.util.regex.Matcher.quoteReplacement(requireString(replacement)));
    }

    public static String replaceTabs(String str, String replacement) {
        return requireString(str).replaceAll("\\t+", java.util.regex.Matcher.quoteReplacement(requireString(replacement)));
    }

    public static String replaceLines(String str, String replacement) {
        return requireString(str).replaceAll("\\R+", java.util.regex.Matcher.quoteReplacement(requireString(replacement)));
    }

    public static String replaceWhitespace(String str, String replacement, boolean tabs, boolean newlines) {
        String value = requireString(str);
        String token = requireString(replacement);
        if (tabs) value = value.replaceAll("\\t+", java.util.regex.Matcher.quoteReplacement(token));
        if (newlines) value = value.replaceAll("\\R+", java.util.regex.Matcher.quoteReplacement(token));
        return value.replaceAll(" +", java.util.regex.Matcher.quoteReplacement(token));
    }

    public static String classCase(String str) {
        if (str == null || str.isEmpty()) return str;
        return snakeToClass(camelToSnake(str));
    }

    public static String camelToSnake(String str) {
        String value = requireString(str);
        return CAMEL_BOUNDARY.matcher(value).replaceAll("$1_$2").toLowerCase(java.util.Locale.ROOT);
    }

    public static String classToSnake(String str) {
        String value = requireString(str);
        if (value.isEmpty()) return value;
        String snake = CAMEL_BOUNDARY.matcher(value).replaceAll("$1_$2").toLowerCase(java.util.Locale.ROOT);
        return snake.charAt(0) == '_' ? snake.substring(1) : snake;
    }

    public static String snakeToCamel(String str) {
        return wordsToCase(requireString(str), false);
    }

    public static String snakeToClass(String str) {
        return wordsToCase(requireString(str), true);
    }

    public static String[] split(String input, String delimiter) {
        return requireString(input).split(requireString(delimiter));
    }

    /** Returns a literal split without regex compilation or regex metacharacter surprises. */
    public static String[] splitLiteral(String input, String delimiter) {
        if (delimiter == null || delimiter.isEmpty()) return new String[]{requireString(input)};
        return requireString(input).split(Pattern.quote(delimiter), -1);
    }

    public static String capitalize(String input) {
        if (input == null || input.isEmpty()) return input;
        return Character.toUpperCase(input.charAt(0)) + input.substring(1).toLowerCase(java.util.Locale.ROOT);
    }

    public static String upperFirst(String input) {
        if (input == null || input.isEmpty()) return input;
        return Character.toUpperCase(input.charAt(0)) + input.substring(1);
    }

    public static String lowerFirst(String input) {
        if (input == null || input.isEmpty()) return input;
        return Character.toLowerCase(input.charAt(0)) + input.substring(1);
    }

    public static String kebabCase(String input) {
        return camelToSnake(requireString(input)).replace('_', '-');
    }

    public static String[] words(String input) {
        if (input == null || input.isBlank()) return new String[0];
        return input.strip().split("[^\\p{L}\\p{Nd}]+");
    }

    // LODASH-LIKE VALUES AND COLLECTIONS

    public static boolean isNil(Object value) {
        return value == null;
    }

    public static boolean isEmpty(Object value) {
        return lodashEmpty(value);
    }

    public static <T> T defaultTo(T value, T fallback) {
        return value == null || (value instanceof Number number && Double.isNaN(number.doubleValue()))
                ? fallback : value;
    }

    public static boolean isTruthy(Object value) {
        if (value == null) return false;
        if (value instanceof Boolean bool) return bool;
        if (value instanceof Number number) {
            double numeric = number.doubleValue();
            return numeric != 0.0d && !Double.isNaN(numeric);
        }
        if (value instanceof CharSequence sequence) return sequence.length() > 0;
        return true;
    }

    public static <T, R> List<R> map(Iterable<? extends T> values,
                                     Function<? super T, ? extends R> mapper) {
        if (mapper == null) throw ExceptionHandling.invalid("Map function is required.");
        ArrayList<R> result = new ArrayList<>();
        if (values != null) for (T value : values) result.add(mapper.apply(value));
        return result;
    }

    public static <T> List<T> filter(Iterable<? extends T> values, Predicate<? super T> predicate) {
        if (predicate == null) throw ExceptionHandling.invalid("Filter predicate is required.");
        ArrayList<T> result = new ArrayList<>();
        if (values != null) for (T value : values) if (predicate.test(value)) result.add(value);
        return result;
    }

    public static <T> List<T> compact(Iterable<? extends T> values) {
        ArrayList<T> result = new ArrayList<>();
        if (values != null) for (T value : values) if (isTruthy(value)) result.add(value);
        return result;
    }

    public static <T> T find(Iterable<? extends T> values, Predicate<? super T> predicate) {
        if (predicate == null) throw ExceptionHandling.invalid("Find predicate is required.");
        if (values != null) for (T value : values) if (predicate.test(value)) return value;
        return null;
    }

    public static <T> boolean every(Iterable<? extends T> values, Predicate<? super T> predicate) {
        if (predicate == null) throw ExceptionHandling.invalid("Every predicate is required.");
        if (values != null) for (T value : values) if (!predicate.test(value)) return false;
        return true;
    }

    public static <T> boolean some(Iterable<? extends T> values, Predicate<? super T> predicate) {
        if (predicate == null) throw ExceptionHandling.invalid("Some predicate is required.");
        if (values != null) for (T value : values) if (predicate.test(value)) return true;
        return false;
    }

    public static <T> List<T> uniq(Iterable<? extends T> values) {
        if (values == null) return new ArrayList<>();
        return new ArrayList<>(new LinkedHashSet<>(toList(values)));
    }

    public static <T> List<T> flatten(Iterable<? extends Iterable<? extends T>> values) {
        ArrayList<T> result = new ArrayList<>();
        if (values != null) for (Iterable<? extends T> group : values) if (group != null) for (T value : group) result.add(value);
        return result;
    }

    public static <T> List<List<T>> chunk(List<? extends T> values, int size) {
        if (size < 1) throw ExceptionHandling.invalid("Chunk size must be positive.");
        ArrayList<List<T>> result = new ArrayList<>();
        if (values == null) return result;
        for (int start = 0; start < values.size(); start += size) {
            result.add(new ArrayList<>(values.subList(start, Math.min(start + size, values.size()))));
        }
        return result;
    }

    public static <T> List<T> take(List<? extends T> values, int count) {
        if (values == null || count <= 0) return new ArrayList<>();
        return new ArrayList<>(values.subList(0, Math.min(count, values.size())));
    }

    public static <T> List<T> drop(List<? extends T> values, int count) {
        if (values == null) return new ArrayList<>();
        int start = Math.max(0, Math.min(count, values.size()));
        return new ArrayList<>(values.subList(start, values.size()));
    }

    public static <T> T first(List<? extends T> values) {
        return values == null || values.isEmpty() ? null : values.getFirst();
    }

    public static <T> T last(List<? extends T> values) {
        return values == null || values.isEmpty() ? null : values.getLast();
    }

    public static String join(Iterable<?> values, String separator) {
        String delimiter = separator == null ? "," : separator;
        if (values == null) return "";
        StringBuilder result = new StringBuilder();
        for (Object value : values) {
            if (!result.isEmpty()) result.append(delimiter);
            result.append(value == null ? "" : value);
        }
        return result.toString();
    }

    public static <T, K> Map<K, List<T>> groupBy(Iterable<? extends T> values,
                                                  Function<? super T, ? extends K> keyFunction) {
        if (keyFunction == null) throw ExceptionHandling.invalid("Group key function is required.");
        LinkedHashMap<K, List<T>> result = new LinkedHashMap<>();
        if (values != null) for (T value : values) result.computeIfAbsent(keyFunction.apply(value), ignored -> new ArrayList<>()).add(value);
        return result;
    }

    public static <T, K> Map<K, T> keyBy(Iterable<? extends T> values,
                                         Function<? super T, ? extends K> keyFunction) {
        if (keyFunction == null) throw ExceptionHandling.invalid("Key function is required.");
        LinkedHashMap<K, T> result = new LinkedHashMap<>();
        if (values != null) for (T value : values) result.put(keyFunction.apply(value), value);
        return result;
    }

    public static <T, K> Map<K, Integer> countBy(Iterable<? extends T> values,
                                                  Function<? super T, ? extends K> keyFunction) {
        if (keyFunction == null) throw ExceptionHandling.invalid("Count key function is required.");
        LinkedHashMap<K, Integer> result = new LinkedHashMap<>();
        if (values != null) for (T value : values) result.merge(keyFunction.apply(value), 1, Integer::sum);
        return result;
    }

    public static <T> List<T> sortBy(Iterable<? extends T> values, Comparator<? super T> comparator) {
        if (comparator == null) throw ExceptionHandling.invalid("Sort comparator is required.");
        ArrayList<T> result = toList(values);
        result.sort(comparator);
        return result;
    }

    public static <T, K> List<T> sortBy(Iterable<? extends T> values,
                                        Function<? super T, ? extends K> keyFunction,
                                        Comparator<? super K> comparator) {
        if (keyFunction == null || comparator == null) throw ExceptionHandling.invalid("Sort key and comparator are required.");
        return sortBy(values, Comparator.comparing(keyFunction, comparator));
    }

    public static <T> List<T> reverse(List<? extends T> values) {
        ArrayList<T> result = new ArrayList<>();
        if (values != null) result.addAll(values);
        Collections.reverse(result);
        return result;
    }

    public static <K, V> V get(Map<? super K, ? extends V> map, K key, V fallback) {
        if (map == null || !map.containsKey(key)) return fallback;
        return map.get(key);
    }

    public static <K, V> boolean has(Map<K, V> map, K key) {
        return map != null && map.containsKey(key);
    }

    public static int size(Object value) {
        if (value == null) return 0;
        if (value instanceof CharSequence sequence) return sequence.length();
        if (value instanceof Collection<?> collection) return collection.size();
        if (value instanceof Map<?, ?> map) return map.size();
        if (value.getClass().isArray()) return Array.getLength(value);
        if (value instanceof Iterable<?> iterable) {
            int count = 0;
            for (Object ignored : iterable) count++;
            return count;
        }
        return 0;
    }

    public static boolean includes(Iterable<?> values, Object target) {
        if (values == null) return false;
        for (Object value : values) if (Objects.equals(value, target)) return true;
        return false;
    }

    public static boolean includes(String value, String target) {
        return value != null && target != null && value.contains(target);
    }

    public static <T> List<T> without(Iterable<? extends T> values, Object... targets) {
        Set<Object> excluded = new HashSet<>();
        if (targets != null) Collections.addAll(excluded, targets);
        ArrayList<T> result = new ArrayList<>();
        if (values != null) for (T value : values) if (!excluded.contains(value)) result.add(value);
        return result;
    }

    public static <K, V> Map<K, V> pick(Map<K, V> map, Iterable<? extends K> keys) {
        LinkedHashMap<K, V> result = new LinkedHashMap<>();
        if (map != null && keys != null) for (K key : keys) if (map.containsKey(key)) result.put(key, map.get(key));
        return result;
    }

    public static <K, V> Map<K, V> omit(Map<K, V> map, Iterable<? extends K> keys) {
        LinkedHashMap<K, V> result = map == null ? new LinkedHashMap<>() : new LinkedHashMap<>(map);
        if (keys != null) for (K key : keys) result.remove(key);
        return result;
    }

    public static int[] range(int end) {
        return range(0, end, 1);
    }

    public static int[] range(int start, int end, int step) {
        if (step == 0) throw ExceptionHandling.invalid("Range step cannot be zero.");
        int distance = step > 0 ? Math.max(0, (end - start + step - 1) / step)
                : Math.max(0, (start - end - step - 1) / -step);
        int[] result = new int[distance];
        for (int i = 0, value = start; i < distance; i++, value += step) result[i] = value;
        return result;
    }

    public static int clamp(int value, int lower, int upper) {
        if (lower > upper) throw ExceptionHandling.invalid("Clamp lower bound cannot exceed upper bound.");
        return Math.max(lower, Math.min(value, upper));
    }

    public static String hex(byte[] arr) {
        return Hex.getHex(arr);
    }

    public static String b64(byte[] arr) {
        return BaseX.encode64(arr);
    }

    // EXIT

    public static void exit(int code) {
        System.exit(code);
    }

    public static void exit(int code, String message) {
        error(message);
        exit(code);
    }

    // CONSOLE

    public static void log(String message) {
        System.out.println(message);
    }

    public static void error(String message) {
        System.err.println(message);
    }

    public static Console console() { return new Console(); }

    public enum TextColor {
        BLACK,
        RED,
        GREEN,
        YELLOW,
        BLUE,
        PURPLE,
        CYAN,
        WHITE
    }

    public static class Console {

        public void print(String message) {
            System.out.print(String.valueOf(message));
        }

        public void println(String message) {
            System.out.println(String.valueOf(message));
        }

        public void error(String message) {
            System.err.println(String.valueOf(message));
        }

        public void print(String message, TextColor color) {
            System.out.print(getAnsiColor(color) + message + ansiReset());
        }

        public void println(String message, TextColor color) {
            System.out.println(getAnsiColor(color) + message + ansiReset());
        }

        public LineCollection refreshable() {
            return new LineCollection();
        }

        public LineCollection collection() {
            return new LineCollection();
        }

        public int interactiveList(String message, String...options) {
            println(message);
            if (options == null || options.length == 0) return -1;
            for(int i=0; i< options.length; i++) {
                int optNum = i + 1;
                String opt = options[i];
                print("[ " + optNum + " ] ➤ ", TextColor.GREEN);
                println(opt);
            }
            String res = collect("Enter the number of your selection");
            return Integer.parseInt(res);
        }

        public String collect(String prompt) {
            print(prompt + " ➤ ", TextColor.PURPLE);
            return readLine();
        }

        public String collect(String prompt, String defaultEntry) {
            print(prompt, TextColor.PURPLE);
            print(" [" + defaultEntry + "]");
            print(" ➤ ", TextColor.PURPLE);
            String input = readLine();
            if(input.trim().isEmpty()) return defaultEntry;
            return input;
        }

        public String collectSecure(String prompt) {
            print(prompt, TextColor.PURPLE);
            print(" ➤ ", TextColor.PURPLE);
            java.io.Console terminal = System.console();
            if (terminal != null) {
                char[] password = terminal.readPassword();
                return password == null ? "" : new String(password);
            }
            return readLine();
        }

        public boolean confirm(String prompt) {
            print(prompt, TextColor.PURPLE);
            print(" [y/n]");
            print(" ➤ ", TextColor.PURPLE);
            String line = readLine().strip().toUpperCase(java.util.Locale.ROOT);
            return line.equals("Y") || line.equals("YES");
        }

        private String readLine() {
            synchronized (INPUT) {
                if (!INPUT.hasNextLine()) return "";
                return INPUT.nextLine();
            }
        }

    }

    public static class ProgressBar extends Line {
        private int value = 0;
        public ProgressBar() { super(""); }
        public void update(String message) { this.content = message == null ? "" : message; }
        public void update(String message, TextColor color) { update(message); this.color = color == null ? TextColor.WHITE : color; }
        public void increment(int value) {
            this.value = Math.max(0, Math.min(100, value));
            if(this.value == 100) this.color = TextColor.GREEN;
        }
        public String get() {
            int width = 20;
            int filled = this.value * width / 100;
            return getAnsiColor(this.color) + "[" + "#".repeat(filled) + "-".repeat(width - filled)
                    + "] " + value + "% " + content + ansiReset();
        }
    }

    public static class DynamicLine extends Line {
        public DynamicLine(String message) { super(message); }
        public DynamicLine(String message, TextColor color) { super(message, color); }
        public void increment(int value) { }
        public void update(String message) {
            this.content = message;
        }
        public void update(String message, TextColor color) {
            this.content = message;
            this.color = color;
        }
        public String get() {
            return getAnsiColor(this.color) + this.content + ansiReset();
        }
    }

    public abstract static class Line {
        protected volatile String content = "";
        protected volatile TextColor color = TextColor.WHITE;
        public Line() { }
        public Line(String message) {
            this.content = message == null ? "" : message;
        }
        public Line(String message, TextColor color) {
            this.content = message == null ? "" : message;
            this.color = color == null ? TextColor.WHITE : color;
        }
        public abstract void update(String message);
        public abstract void update(String message, TextColor color);
        public abstract void increment(int value);
        public abstract String get();
    }

    public static class LineCollection {

        private final ArrayList<Line> lines = new ArrayList<>();

        public LineCollection append(String message) {
            this.lines.add(new DynamicLine(message));
            return this;
        }

        public LineCollection append(String message, TextColor color) {
            this.lines.add(new DynamicLine(message, color));
            return this;
        }

        public LineCollection progress() {
            this.lines.add(new ProgressBar());
            return this;
        }

        public LineCollection update(String message, int index) {
            this.lines.get(index).update(message);
            return this;
        }

        public LineCollection update(String message, TextColor color, int index) {
            this.lines.get(index).update(message, color);
            return this;
        }

        public LineCollection increment(int value, int index) {
            this.lines.get(index).increment(value);
            return this;
        }

        public int size() {
            return lines.size();
        }

        public Line get(int index) {
            return lines.get(index);
        }

        public List<String> render() {
            ArrayList<String> result = new ArrayList<>(lines.size());
            for (Line line : lines) result.add(line.get());
            return result;
        }

    }

    private static String getAnsiColor(TextColor color) {
        if (color == null) return ANSI_COLORS[TextColor.WHITE.ordinal()];
        return ANSI_COLORS[color.ordinal()];
    }

    private static String ansiReset() {
        return ANSI_RESET;
    }

    //legacy
    public static String str(Object input) {
        return String.valueOf(input);
    }

    public static int num(Object input) throws Exception {
        try {
            return Integer.parseInt(str(input));
        } catch (NumberFormatException e) {
            throw ExceptionHandling.invalid("Integer parse error.", e);
        }
    }

    public static double dbl(Object input) throws Exception {
        try {
            return Double.parseDouble(str(input));
        } catch (NumberFormatException e) {
            throw ExceptionHandling.invalid("Double parse error.", e);
        }
    }

    public static void printPrettyLn(String output) {
        console().println(output, TextColor.CYAN);
    }

    public static void printPrettyLn(String output, TextColor color) {
        console().println(output, color);
    }

    public static String[] arr(String...vals) {
        return vals;
    }

    public static byte[] bites(String str) {
        if (str == null) return null;
        if (Hex.isValid(str) && !str.isEmpty()) return Hex.fromHex(str);
        boolean encodedHint = looksLikeBase64(str);
        if (encodedHint) {
            try {
                return Base64.getDecoder().decode(str);
            } catch (IllegalArgumentException ignored) {
                // Fall back to UTF-8 for ordinary text.
            }
        }
        return str.getBytes(StandardCharsets.UTF_8);
    }

    public static String id() {
        return id(String.valueOf(System.nanoTime()), hex(Rand.secure(32)), Rand.str(32, ""));
    }

    public static String id(String... seeds) {
        StringBuilder full = new StringBuilder();
        if (seeds != null) for (String seed : seeds) {
            if (!full.isEmpty()) full.append('_');
            full.append(String.valueOf(seed));
        }
        full.append(System.currentTimeMillis());
        return Rand.randomCase(b64(SHA.Bites.getSha1(full.toString())).replace("/", "").replace("=", "").replace("+", ""));
    }

    private static String requireString(String value) {
        if (value == null) throw ExceptionHandling.invalid("String input is required.");
        return value;
    }

    private static boolean looksLikeBase64(String value) {
        if (value.length() < 8 || !BASE64_SHAPE.matcher(value).matches()) return false;
        boolean upper = false;
        boolean lower = false;
        boolean digit = false;
        for (int i = 0; i < value.length(); i++) {
            char character = value.charAt(i);
            upper |= Character.isUpperCase(character);
            lower |= Character.isLowerCase(character);
            digit |= Character.isDigit(character);
        }
        return upper && lower && digit;
    }

    private static String wordsToCase(String value, boolean upperFirst) {
        if (value.isEmpty()) return value;
        String[] parts = value.split("_+");
        StringBuilder result = new StringBuilder(value.length());
        for (int i = 0; i < parts.length; i++) {
            if (parts[i].isEmpty()) continue;
            String word = parts[i].toLowerCase(java.util.Locale.ROOT);
            if (upperFirst || i > 0) word = Character.toUpperCase(word.charAt(0)) + word.substring(1);
            result.append(word);
        }
        return result.toString();
    }

    private static <T> ArrayList<T> toList(Iterable<? extends T> values) {
        ArrayList<T> result = new ArrayList<>();
        if (values != null) for (T value : values) result.add(value);
        return result;
    }

}
