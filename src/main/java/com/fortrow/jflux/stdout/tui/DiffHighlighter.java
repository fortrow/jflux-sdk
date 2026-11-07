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

import java.util.ArrayList;
import java.util.List;

/**
 * Git diff and code changes syntax highlighter using Ryel brand colors.
 */
public final class DiffHighlighter {

    private DiffHighlighter() {
    }

    public static String highlight(String rawDiff) {
        if (rawDiff == null || rawDiff.isEmpty()) return "";

        StringBuilder sb = new StringBuilder();
        String[] lines = rawDiff.split("\r?\n", -1);

        for (String line : lines) {
            if (line.startsWith("+++") || line.startsWith("---")) {
                sb.append(AnsiStyle.bold(AnsiStyle.white(line))).append("\n");
            } else if (line.startsWith("diff --git") || line.startsWith("index ")) {
                sb.append(AnsiStyle.dim(line)).append("\n");
            } else if (line.startsWith("@@")) {
                sb.append(AnsiStyle.amber(line)).append("\n");
            } else if (line.startsWith("+")) {
                sb.append(AnsiStyle.mint(line)).append("\n");
            } else if (line.startsWith("-")) {
                sb.append(AnsiStyle.coral(line)).append("\n");
            } else {
                sb.append(AnsiStyle.zinc(line)).append("\n");
            }
        }

        return sb.toString();
    }

    public static String summarize(int additions, int deletions, int filesChanged) {
        return AnsiStyle.bold(filesChanged + " file" + (filesChanged == 1 ? "" : "s") + " changed") +
                ", " +
                AnsiStyle.mint("+" + additions) +
                ", " +
                AnsiStyle.coral("-" + deletions);
    }
}
