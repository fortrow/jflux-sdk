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
package com.fortrow.jflux.stdout.tui;

import java.util.regex.Pattern;

/**
 * Rich ANSI styling, 24-bit TrueColor, and Ryel Brand Color Theme definitions.
 */
public final class AnsiStyle {

    public static final String ESC = "\033[";
    public static final String RESET = "\033[0m";
    public static final String BOLD = "\033[1m";
    public static final String DIM = "\033[2m";
    public static final String ITALIC = "\033[3m";
    public static final String UNDERLINE = "\033[4m";
    public static final String INVERSE = "\033[7m";
    public static final String STRIKE = "\033[9m";

    // Ryel Brand Primary Palette
    public static final String RYEL_AMBER = rgb(250, 200, 20);   // #fac814
    public static final String RYEL_MINT = rgb(60, 190, 150);    // #3cbe96
    public static final String RYEL_CORAL = rgb(240, 110, 80);   // #f06e50
    public static final String RYEL_WHITE = rgb(255, 255, 255);  // #ffffff
    public static final String RYEL_BLACK = rgb(9, 9, 11);       // #09090b
    public static final String RYEL_ZINC = rgb(113, 113, 122);   // #71717a
    public static final String RYEL_BORDER = rgb(228, 228, 231); // #e4e4e7
    public static final String RYEL_MUTED = rgb(161, 161, 170);  // #a1a1aa

    // Ryel Brand Backgrounds
    public static final String BG_RYEL_BLACK = bgRgb(9, 9, 11);
    public static final String BG_RYEL_AMBER = bgRgb(250, 200, 20);
    public static final String BG_RYEL_MINT = bgRgb(60, 190, 150);
    public static final String BG_RYEL_CORAL = bgRgb(240, 110, 80);
    public static final String BG_RYEL_ZINC = bgRgb(39, 39, 42);

    private static final Pattern ANSI_PATTERN = Pattern.compile("\\033\\[[0-9;]*[a-zA-Z]");

    private AnsiStyle() {
    }

    public static String rgb(int r, int g, int b) {
        return ESC + "38;2;" + clamp(r) + ";" + clamp(g) + ";" + clamp(b) + "m";
    }

    public static String bgRgb(int r, int g, int b) {
        return ESC + "48;2;" + clamp(r) + ";" + clamp(g) + ";" + clamp(b) + "m";
    }

    public static String colorize(String text, String ansiCode) {
        if (text == null) return "";
        if (ansiCode == null || ansiCode.isEmpty()) return text;
        return ansiCode + text + RESET;
    }

    public static String amber(String text) {
        return colorize(text, RYEL_AMBER);
    }

    public static String mint(String text) {
        return colorize(text, RYEL_MINT);
    }

    public static String coral(String text) {
        return colorize(text, RYEL_CORAL);
    }

    public static String white(String text) {
        return colorize(text, RYEL_WHITE);
    }

    public static String zinc(String text) {
        return colorize(text, RYEL_ZINC);
    }

    public static String muted(String text) {
        return colorize(text, RYEL_MUTED);
    }

    public static String bold(String text) {
        return colorize(text, BOLD);
    }

    public static String dim(String text) {
        return colorize(text, DIM);
    }

    public static String italic(String text) {
        return colorize(text, ITALIC);
    }

    public static String badge(String text, String fgCode, String bgCode) {
        if (text == null) return "";
        return bgCode + fgCode + BOLD + " " + text + " " + RESET;
    }

    public static String stripAnsi(String text) {
        if (text == null) return "";
        return ANSI_PATTERN.matcher(text).replaceAll("");
    }

    public static int visibleLength(String text) {
        if (text == null) return 0;
        return stripAnsi(text).length();
    }

    private static int clamp(int val) {
        return Math.max(0, Math.min(255, val));
    }
}
