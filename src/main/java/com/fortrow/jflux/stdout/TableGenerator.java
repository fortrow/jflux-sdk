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
package com.fortrow.jflux.stdout;

import com.fortrow.jflux.shell.CommandResult;
import com.fortrow.jflux.shell.Shell;
import com.fortrow.jflux.sys.PlatformUtils;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TableGenerator {

    private static final int DEFAULT_TERMINAL_WIDTH = 120;
    private static final int MIN_TERMINAL_WIDTH = 40;
    private static final int MIN_COLUMN_WIDTH = 4;
    private static final int PADDING_SIZE = 1;
    private static volatile int detectedTerminalWidth;

    private final String newLine = "\n";
    private final String tableJointSymbol = "+";
    private final String tableVerticalSymbol = "|";
    private final String tableHorizontalSymbol = "-";

    public String generateTable(List<String> headersList, List<List<String>> rowsList, int... overRiddenHeaderHeight) {
        if(headersList == null || headersList.isEmpty()) return newLine;

        StringBuilder stringBuilder = new StringBuilder();
        Map<Integer, Integer> columnWidths = getColumnWidths(headersList, rowsList);

        stringBuilder.append(newLine);
        createRowLine(stringBuilder, headersList.size(), columnWidths);
        stringBuilder.append(newLine);
        createWrappedRow(stringBuilder, headersList, columnWidths);
        createRowLine(stringBuilder, headersList.size(), columnWidths);

        if(rowsList != null) {
            for(List<String> row : rowsList) {
                stringBuilder.append(newLine);
                createWrappedRow(stringBuilder, normalizeRow(row, headersList.size()), columnWidths);
            }
        }

        createRowLine(stringBuilder, headersList.size(), columnWidths);
        stringBuilder.append(newLine);
        return stringBuilder.toString();
    }

    private void createWrappedRow(StringBuilder stringBuilder, List<String> row, Map<Integer, Integer> columnWidths) {
        List<List<String>> wrappedCells = new ArrayList<>();
        int rowHeight = 1;

        for(int i = 0; i < columnWidths.size(); i++) {
            List<String> wrapped = wrap(safeCell(row.get(i)), columnWidths.get(i));
            wrappedCells.add(wrapped);
            rowHeight = Math.max(rowHeight, wrapped.size());
        }

        for(int line = 0; line < rowHeight; line++) {
            if(line > 0) stringBuilder.append(newLine);
            for(int cellIndex = 0; cellIndex < wrappedCells.size(); cellIndex++) {
                if(cellIndex == 0) stringBuilder.append(tableVerticalSymbol);
                String value = line < wrappedCells.get(cellIndex).size() ? wrappedCells.get(cellIndex).get(line) : "";
                fillCell(stringBuilder, value, columnWidths.get(cellIndex));
                stringBuilder.append(tableVerticalSymbol);
            }
        }
        stringBuilder.append(newLine);
    }

    private List<String> normalizeRow(List<String> row, int size) {
        List<String> normalized = new ArrayList<>();
        for(int i = 0; i < size; i++) {
            normalized.add(row != null && i < row.size() ? row.get(i) : "");
        }
        return normalized;
    }

    private void fillCell(StringBuilder stringBuilder, String cell, int width) {
        fillSpace(stringBuilder, PADDING_SIZE);
        stringBuilder.append(cell);
        fillSpace(stringBuilder, Math.max(0, width - visibleLength(cell)));
        fillSpace(stringBuilder, PADDING_SIZE);
    }

    private void fillSpace(StringBuilder stringBuilder, int length) {
        for(int i = 0; i < length; i++) stringBuilder.append(" ");
    }

    private void createRowLine(StringBuilder stringBuilder, int columnCount, Map<Integer, Integer> columnWidths) {
        for(int i = 0; i < columnCount; i++) {
            if(i == 0) stringBuilder.append(tableJointSymbol);
            for(int j = 0; j < columnWidths.get(i) + PADDING_SIZE * 2; j++) {
                stringBuilder.append(tableHorizontalSymbol);
            }
            stringBuilder.append(tableJointSymbol);
        }
    }

    private Map<Integer, Integer> getColumnWidths(List<String> headersList, List<List<String>> rowsList) {
        int columnCount = headersList.size();
        Map<Integer, Integer> naturalWidths = getNaturalWidths(headersList, rowsList);
        int terminalWidth = detectTerminalWidth();

        int borderAndPaddingWidth = columnCount + 1 + (columnCount * PADDING_SIZE * 2);
        int contentBudget = Math.max(columnCount * MIN_COLUMN_WIDTH, terminalWidth - borderAndPaddingWidth);

        Map<Integer, Integer> widths = new HashMap<>();
        int baseWidth = Math.max(MIN_COLUMN_WIDTH, contentBudget / columnCount);
        int used = 0;

        for(int i = 0; i < columnCount; i++) {
            int width = Math.min(naturalWidths.get(i), baseWidth);
            width = Math.max(MIN_COLUMN_WIDTH, width);
            widths.put(i, width);
            used += width;
        }

        int remaining = contentBudget - used;
        while(remaining > 0) {
            List<Integer> indexes = indexesByNeed(widths, naturalWidths);
            for(Integer index : indexes) {
                if(remaining <= 0) break;
                widths.put(index, widths.get(index) + 1);
                remaining--;
            }
        }

        return widths;
    }

    private List<Integer> indexesByNeed(Map<Integer, Integer> widths, Map<Integer, Integer> naturalWidths) {
        List<Integer> indexes = new ArrayList<>(naturalWidths.keySet());
        indexes.sort((left, right) -> {
            int leftNeed = naturalWidths.get(left) - widths.get(left);
            int rightNeed = naturalWidths.get(right) - widths.get(right);
            return Integer.compare(rightNeed, leftNeed);
        });
        return indexes;
    }

    private Map<Integer, Integer> getNaturalWidths(List<String> headersList, List<List<String>> rowsList) {
        Map<Integer, Integer> widths = new HashMap<>();
        for(int i = 0; i < headersList.size(); i++) {
            widths.put(i, Math.max(MIN_COLUMN_WIDTH, longestLine(headersList.get(i))));
        }

        if(rowsList != null) {
            for(List<String> row : rowsList) {
                for(int i = 0; i < headersList.size(); i++) {
                    String value = row != null && i < row.size() ? row.get(i) : "";
                    widths.put(i, Math.max(widths.get(i), longestLine(value)));
                }
            }
        }

        return widths;
    }

    private List<String> wrap(String value, int width) {
        if(value == null || value.isEmpty()) return new ArrayList<>(Collections.singletonList(""));

        List<String> lines = new ArrayList<>();
        String[] sourceLines = value.replace("\r", "").split("\n", -1);
        for(String sourceLine : sourceLines) {
            wrapSingleLine(sourceLine, width, lines);
        }
        return lines.isEmpty() ? new ArrayList<>(Collections.singletonList("")) : lines;
    }

    private void wrapSingleLine(String sourceLine, int width, List<String> lines) {
        if(sourceLine == null || sourceLine.isEmpty()) {
            lines.add("");
            return;
        }

        String[] words = sourceLine.trim().split("\s+");
        StringBuilder current = new StringBuilder();

        for(String word : words) {
            if(word.isEmpty()) continue;

            while(visibleLength(word) > width) {
                if(current.length() > 0) {
                    lines.add(current.toString());
                    current.setLength(0);
                }
                lines.add(word.substring(0, width));
                word = word.substring(width);
            }

            int separator = current.length() == 0 ? 0 : 1;
            if(visibleLength(current.toString()) + separator + visibleLength(word) > width) {
                if(current.length() > 0) {
                    lines.add(current.toString());
                    current.setLength(0);
                }
            }

            if(current.length() > 0) current.append(' ');
            current.append(word);
        }

        if(current.length() > 0) lines.add(current.toString());
    }

    private int longestLine(String value) {
        int longest = 0;
        String[] lines = safeCell(value).replace("\r", "").split("\n", -1);
        for(String line : lines) longest = Math.max(longest, visibleLength(line));
        return longest;
    }

    private String safeCell(String value) {
        return value == null ? "" : value;
    }

    private int visibleLength(String value) {
        return value == null ? 0 : value.length();
    }

    private int detectTerminalWidth() {
        int configured = parsePositiveInt(System.getenv("JFLUX_TABLE_WIDTH"));
        if(configured > 0) return Math.max(MIN_TERMINAL_WIDTH, configured);

        int cached = detectedTerminalWidth;
        if(cached > 0) return cached;

        int ttyColumns = ttyColumns();
        if(ttyColumns > 0) return cacheWidth(ttyColumns);

        int columns = parsePositiveInt(System.getenv("COLUMNS"));
        if(columns > 0) return cacheWidth(columns);

        int tputColumns = tputColumns();
        if(tputColumns > 0) return cacheWidth(tputColumns);

        return cacheWidth(DEFAULT_TERMINAL_WIDTH);
    }

    private int ttyColumns() {
        if (PlatformUtils.isWindows()) return -1;
        CommandResult result = Shell.execute(
                List.of("sh", "-c", "stty size < /dev/tty 2>/dev/null | awk '{print $2}'"),
                Duration.ofMillis(250), 1024);
        return result.success() ? parsePositiveInt(result.stdout().strip()) : -1;
    }

    private int tputColumns() {
        if (PlatformUtils.isWindows()) return -1;
        CommandResult result = Shell.execute(
                List.of("sh", "-c", "tput cols 2>/dev/null"),
                Duration.ofMillis(250), 1024);
        return result.success() ? parsePositiveInt(result.stdout().strip()) : -1;
    }

    private int cacheWidth(int width) {
        int normalized = Math.max(MIN_TERMINAL_WIDTH, width);
        detectedTerminalWidth = normalized;
        return normalized;
    }

    private int parsePositiveInt(String value) {
        try {
            if(value == null || value.isBlank()) return -1;
            int parsed = Integer.parseInt(value.trim());
            return parsed > 0 ? parsed : -1;
        } catch (Exception ignored) {
            return -1;
        }
    }
}
