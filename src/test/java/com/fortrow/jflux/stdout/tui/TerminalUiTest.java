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

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TerminalUiTest {

    @Test
    void ansiStyleGeneratesTrueColorsAndRyelBrandPalette() {
        String amber = AnsiStyle.amber("Keystone");
        assertTrue(amber.contains("38;2;250;200;20m"));
        assertTrue(amber.contains("Keystone"));

        String mint = AnsiStyle.mint("Active");
        assertTrue(mint.contains("38;2;60;190;150m"));

        String coral = AnsiStyle.coral("Deleted");
        assertTrue(coral.contains("38;2;240;110;80m"));

        assertEquals("Clean Text", AnsiStyle.stripAnsi(AnsiStyle.amber("Clean Text")));
        assertEquals(10, AnsiStyle.visibleLength(AnsiStyle.coral("1234567890")));
    }

    @Test
    void terminalBoxRendersBordersAndTitles() {
        TerminalBox box = TerminalBox.create()
                .title("Ryel Keystone")
                .subtitle("SWE Vibe Coding")
                .style(TerminalBox.Style.ROUNDED)
                .addLine("Local CPU Execution: Active")
                .addLine("Memory: 312MB");

        String rendered = box.renderToString();
        assertTrue(rendered.contains("╭"));
        assertTrue(rendered.contains("Ryel Keystone"));
        assertTrue(rendered.contains("SWE Vibe Coding"));
        assertTrue(rendered.contains("Local CPU Execution: Active"));
        assertTrue(rendered.contains("╰"));
    }

    @Test
    void diffHighlighterColorsGitAdditionsAndDeletions() {
        String rawDiff = """
                diff --git a/app/Pipeline.php b/app/Pipeline.php
                index abc1234..def5678 100644
                --- a/app/Pipeline.php
                +++ b/app/Pipeline.php
                @@ -1,3 +1,4 @@
                 existing code
                -old line
                +new line
                """;

        String highlighted = DiffHighlighter.highlight(rawDiff);
        assertTrue(highlighted.contains("+new line"));
        assertTrue(highlighted.contains("-old line"));
        assertTrue(highlighted.contains("@@ -1,3 +1,4 @@"));

        String summary = DiffHighlighter.summarize(12, 3, 2);
        assertTrue(summary.contains("+12"));
        assertTrue(summary.contains("-3"));
    }

    @Test
    void terminalBannerProducesKeystoneHeader() {
        String banner = TerminalBanner.keystoneBanner("v0.9.4");
        assertTrue(banner.contains("Ryel Keystone"));
        assertTrue(banner.contains("CPU SERVER ENGINE"));
    }

    @Test
    void interactiveMenuPresentsOptionsAndParsesInput() {
        String inputKeys = "2\n";
        ByteArrayInputStream in = new ByteArrayInputStream(inputKeys.getBytes(StandardCharsets.UTF_8));
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        InteractiveMenu<String> menu = new InteractiveMenu<>(in, new PrintStream(out));
        menu.title("Select Action")
                .add("Status", "Check repo status", "status")
                .add("Vibe", "Start conversational vibe coding", "vibe");

        String selected = menu.prompt();
        assertEquals("vibe", selected);
    }
}
