/*
 * -------------------------------------------------------------------------
 *
 * Copyright (c) 2026 Fortrow LLC. All rights reserved.
 *
 * NOTICE: All information contained herein is, and remains the property of
 * Fortrow, LLC. The intellectual and technical concepts contained herein are
 * proprietary to Fortrow, LLC and may be covered by U.S. and Foreign Patents,
 * patents in process, and are protected by trade secret or copyright law.
 *
 * Dissemination of this information or reproduction of this material is
 * strictly forbidden unless prior written permission is obtained from
 * Fortrow, LLC.
 *
 * -------------------------------------------------------------------------
 */
package com.fortrow.jflux.stdout;

import com.fortrow.jflux.exceptions.ExceptionHandling;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Small terminal-output and prompt facade for CLI applications.
 *
 * <p>It centralizes ANSI styling, safe line prompts, confirmations, numbered
 * choices, cursor helpers, and password input so individual applications do
 * not each need their own terminal conventions.</p>
 */
public final class Terminal {

    private static final String ESC = "\033[";
    private final PrintStream out;
    private final InputStream sourceInput;
    private final BufferedReader input;
    private final boolean ansi;

    public Terminal() {
        this(System.in, System.out);
    }

    public Terminal(InputStream input, PrintStream out) {
        if (input == null) throw ExceptionHandling.invalid("Terminal input is required.");
        if (out == null) throw ExceptionHandling.invalid("Terminal output is required.");
        this.sourceInput = input;
        this.input = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8));
        this.out = out;
        this.ansi = System.console() != null
                && !"dumb".equalsIgnoreCase(System.getenv().getOrDefault("TERM", ""))
                && (out == System.out || out == System.err);
    }

    public Terminal(InputStream input, PrintStream out, boolean ansi) {
        if (input == null) throw ExceptionHandling.invalid("Terminal input is required.");
        if (out == null) throw ExceptionHandling.invalid("Terminal output is required.");
        this.sourceInput = input;
        this.input = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8));
        this.out = out;
        this.ansi = ansi;
    }

    public boolean supportsAnsi() {
        return ansi;
    }

    public Terminal print(Object value) {
        out.print(value == null ? "null" : value);
        return this;
    }

    public Terminal println(Object value) {
        out.println(value == null ? "null" : value);
        return this;
    }

    public Terminal println() {
        out.println();
        return this;
    }

    public Terminal flush() {
        out.flush();
        return this;
    }

    public Terminal writeRaw(String value) {
        if (value != null) out.print(value);
        return this;
    }

    public String style(String value, Color color) {
        if (value == null) return "";
        if (!ansi || color == null) return value;
        return ESC + color.code + "m" + value + ESC + "0m";
    }

    public Terminal print(String value, Color color) {
        out.print(style(value, color));
        return this;
    }

    public Terminal println(String value, Color color) {
        out.println(style(value, color));
        return this;
    }

    public Terminal heading(String value) {
        return println(value, Color.CYAN).println();
    }

    public Terminal divider(int width) {
        int safeWidth = Math.max(1, width);
        return println("─".repeat(safeWidth), Color.DIM);
    }

    public Terminal clearLine() {
        if (ansi) out.print("\r" + ESC + "2K");
        return this;
    }

    public Terminal clearScreen() {
        if (ansi) out.print(ESC + "2J" + ESC + "H");
        return this;
    }

    public Terminal hideCursor() {
        if (ansi) out.print(ESC + "?25l");
        return this;
    }

    public Terminal showCursor() {
        if (ansi) out.print(ESC + "?25h");
        return this;
    }

    public Terminal cursorUp(int lines) {
        if (ansi && lines > 0) out.print(ESC + lines + "A");
        return this;
    }

    public Terminal cursorDown(int lines) {
        if (ansi && lines > 0) out.print(ESC + lines + "B");
        return this;
    }

    /** Reads a line, returning the default when the user submits an empty line. */
    public String prompt(String label, String defaultValue) {
        print(label == null ? "" : label);
        flush();
        String value = readLine();
        return value.isBlank() && defaultValue != null ? defaultValue : value;
    }

    public String prompt(String label) {
        return prompt(label, "");
    }

    /** Reads a yes/no answer, applying the default for an empty response. */
    public boolean confirm(String label, boolean defaultValue) {
        String suffix = defaultValue ? " [Y/n] " : " [y/N] ";
        while (true) {
            String value = prompt((label == null ? "Confirm?" : label) + suffix).trim().toLowerCase();
            if (value.isEmpty()) return defaultValue;
            if (value.equals("y") || value.equals("yes")) return true;
            if (value.equals("n") || value.equals("no")) return false;
            println("Please answer yes or no.", Color.YELLOW);
        }
    }

    /** Reads a password without echo when a JVM console is available. */
    public char[] password(String label) {
        java.io.Console console = System.console();
        if (console != null && sourceInput == System.in) return console.readPassword(label == null ? "" : label);
        // BufferedReader cannot disable echo portably; callers should use a real Console for secrets.
        return prompt(label == null ? "" : label).toCharArray();
    }

    /** Displays a numbered list and returns the selected zero-based index. */
    public int select(String label, List<String> choices) {
        if (choices == null || choices.isEmpty()) throw ExceptionHandling.invalid("At least one terminal choice is required.");
        if (label != null && !label.isBlank()) println(label, Color.CYAN);
        for (int i = 0; i < choices.size(); i++) println("  " + (i + 1) + ") " + (choices.get(i) == null ? "" : choices.get(i)));
        while (true) {
            String value = prompt("Select [1-" + choices.size() + "]: ").trim();
            try {
                int selected = Integer.parseInt(value) - 1;
                if (selected >= 0 && selected < choices.size()) return selected;
            } catch (NumberFormatException ignored) {
                // Keep the prompt interactive for invalid user input.
            }
            println("Choose one of the listed numbers.", Color.YELLOW);
        }
    }

    public <T> T choose(String label, List<T> choices) {
        if (choices == null || choices.isEmpty()) throw ExceptionHandling.invalid("At least one terminal choice is required.");
        List<String> labels = choices.stream().map(String::valueOf).toList();
        return choices.get(select(label, labels));
    }

    private String readLine() {
        try {
            String value = input.readLine();
            return value == null ? "" : value;
        } catch (IOException e) {
            throw ExceptionHandling.failure("Unable to read terminal input.", e);
        }
    }

    /** Basic ANSI colors used by JFlux terminal helpers. */
    public enum Color {
        RESET(0),
        BOLD(1),
        DIM(2),
        RED(31),
        GREEN(32),
        YELLOW(33),
        BLUE(34),
        MAGENTA(35),
        CYAN(36),
        WHITE(37);

        private final int code;

        Color(int code) {
            this.code = code;
        }
    }
}
