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

import com.fortrow.jflux.exceptions.ExceptionHandling;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Interactive menu selector with Ryel cursor and brand accents.
 */
public final class InteractiveMenu<T> {

    public record Option<T>(String label, String description, T value) {}

    private final List<Option<T>> options = new ArrayList<>();
    private String title = "Select an option:";
    private final PrintStream out;
    private final InputStream in;

    public InteractiveMenu() {
        this(System.in, System.out);
    }

    public InteractiveMenu(InputStream in, PrintStream out) {
        this.in = in != null ? in : System.in;
        this.out = out != null ? out : System.out;
    }

    public static <T> InteractiveMenu<T> create() {
        return new InteractiveMenu<>();
    }

    public InteractiveMenu<T> title(String title) {
        this.title = title;
        return this;
    }

    public InteractiveMenu<T> add(String label, T value) {
        return add(label, null, value);
    }

    public InteractiveMenu<T> add(String label, String description, T value) {
        options.add(new Option<>(label, description, value));
        return this;
    }

    public T prompt() {
        if (options.isEmpty()) {
            throw ExceptionHandling.invalid("InteractiveMenu requires at least one option.");
        }

        out.println(AnsiStyle.bold(AnsiStyle.white(title)));
        for (int i = 0; i < options.size(); i++) {
            Option<T> opt = options.get(i);
            String num = AnsiStyle.amber("  [" + (i + 1) + "] ");
            String label = AnsiStyle.bold(opt.label);
            out.print(num + label);
            if (opt.description != null && !opt.description.isBlank()) {
                out.print(" " + AnsiStyle.dim("— " + opt.description));
            }
            out.println();
        }

        BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
        while (true) {
            out.print(AnsiStyle.mint("❯ ") + AnsiStyle.dim("Enter choice (1-" + options.size() + "): "));
            out.flush();
            try {
                String line = reader.readLine();
                if (line == null) return options.get(0).value;
                line = line.trim();
                if (line.isEmpty()) return options.get(0).value;
                int choice = Integer.parseInt(line) - 1;
                if (choice >= 0 && choice < options.size()) {
                    return options.get(choice).value;
                }
            } catch (Exception ignored) {
            }
            out.println(AnsiStyle.coral("Invalid choice. Please select 1 through " + options.size()));
        }
    }
}
