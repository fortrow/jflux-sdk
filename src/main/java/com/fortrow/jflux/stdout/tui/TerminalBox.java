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

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Terminal UI box generator supporting custom borders, padding, headers, and Ryel styling.
 */
public final class TerminalBox {

    public enum Style {
        ROUNDED("╭", "─", "╮", "│", "╰", "╯"),
        SHARP("┌", "─", "┐", "│", "└", "┘"),
        DOUBLE("╔", "═", "╗", "║", "╚", "╝"),
        HEAVY("┏", "━", "┓", "┃", "┗", "┛");

        public final String topLeft;
        public final String horizontal;
        public final String topRight;
        public final String vertical;
        public final String bottomLeft;
        public final String bottomRight;

        Style(String tl, String h, String tr, String v, String bl, String br) {
            this.topLeft = tl;
            this.horizontal = h;
            this.topRight = tr;
            this.vertical = v;
            this.bottomLeft = bl;
            this.bottomRight = br;
        }
    }

    private Style style = Style.ROUNDED;
    private String borderColor = AnsiStyle.RYEL_BORDER;
    private String title;
    private String subtitle;
    private int padding = 1;
    private int explicitWidth = 0;
    private final List<String> lines = new ArrayList<>();

    public static TerminalBox create() {
        return new TerminalBox();
    }

    public TerminalBox style(Style style) {
        if (style != null) this.style = style;
        return this;
    }

    public TerminalBox borderColor(String ansiColor) {
        this.borderColor = ansiColor == null ? "" : ansiColor;
        return this;
    }

    public TerminalBox title(String title) {
        this.title = title;
        return this;
    }

    public TerminalBox subtitle(String subtitle) {
        this.subtitle = subtitle;
        return this;
    }

    public TerminalBox padding(int padding) {
        this.padding = Math.max(0, padding);
        return this;
    }

    public TerminalBox width(int width) {
        this.explicitWidth = Math.max(0, width);
        return this;
    }

    public TerminalBox addLine(String line) {
        if (line != null) {
            String[] split = line.split("\r?\n", -1);
            lines.addAll(Arrays.asList(split));
        }
        return this;
    }

    public TerminalBox addLines(List<String> newLines) {
        if (newLines != null) {
            for (String l : newLines) addLine(l);
        }
        return this;
    }

    public String renderToString() {
        int maxContentWidth = 0;
        if (title != null) maxContentWidth = Math.max(maxContentWidth, AnsiStyle.visibleLength(title) + 4);
        if (subtitle != null) maxContentWidth = Math.max(maxContentWidth, AnsiStyle.visibleLength(subtitle) + 4);

        for (String line : lines) {
            maxContentWidth = Math.max(maxContentWidth, AnsiStyle.visibleLength(line));
        }

        int innerWidth = Math.max(maxContentWidth + (padding * 2), explicitWidth - 2);
        if (innerWidth < 10) innerWidth = 10;

        StringBuilder sb = new StringBuilder();
        String c = borderColor;
        String r = AnsiStyle.RESET;

        // Top Border with optional Title
        sb.append(c).append(style.topLeft).append(r);
        if (title != null && !title.isEmpty()) {
            sb.append(c).append(style.horizontal).append(" ").append(r);
            sb.append(title);
            sb.append(c).append(" ").append(r);
            int rem = innerWidth - AnsiStyle.visibleLength(title) - 3;
            if (rem > 0) sb.append(c).append(style.horizontal.repeat(rem)).append(r);
        } else {
            sb.append(c).append(style.horizontal.repeat(innerWidth)).append(r);
        }
        sb.append(c).append(style.topRight).append(r).append("\n");

        // Subtitle line if present
        if (subtitle != null && !subtitle.isEmpty()) {
            sb.append(c).append(style.vertical).append(r);
            sb.append(" ".repeat(padding)).append(subtitle);
            int rem = innerWidth - padding - AnsiStyle.visibleLength(subtitle);
            if (rem > 0) sb.append(" ".repeat(rem));
            sb.append(c).append(style.vertical).append(r).append("\n");

            // Subtitle divider
            sb.append(c).append(style.vertical).append(style.horizontal.repeat(innerWidth)).append(style.vertical).append(r).append("\n");
        }

        // Top padding lines
        for (int p = 0; p < padding; p++) {
            sb.append(c).append(style.vertical).append(r)
              .append(" ".repeat(innerWidth))
              .append(c).append(style.vertical).append(r).append("\n");
        }

        // Content lines
        for (String line : lines) {
            sb.append(c).append(style.vertical).append(r);
            sb.append(" ".repeat(padding));
            sb.append(line);
            int remaining = innerWidth - padding - AnsiStyle.visibleLength(line);
            if (remaining > 0) sb.append(" ".repeat(remaining));
            sb.append(c).append(style.vertical).append(r).append("\n");
        }

        // Bottom padding lines
        for (int p = 0; p < padding; p++) {
            sb.append(c).append(style.vertical).append(r)
              .append(" ".repeat(innerWidth))
              .append(c).append(style.vertical).append(r).append("\n");
        }

        // Bottom Border
        sb.append(c).append(style.bottomLeft).append(style.horizontal.repeat(innerWidth)).append(style.bottomRight).append(r).append("\n");
        return sb.toString();
    }

    public void render(PrintStream out) {
        PrintStream p = out != null ? out : System.out;
        p.print(renderToString());
    }
}
