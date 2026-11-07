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

import com.fortrow.jflux.output.json.JSON;
import com.fortrow.jflux.stdout.Terminal;
import org.json.simple.JSONObject;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConsoleStdoutTest {

    @Test
    void rendersStructuredJsonWithQuotesBracketsAndIndentation() {
        ObjectiveConsole console = ObjectiveConsole.create("{\"name\":\"Ada\",\"items\":[true,2]}").color(false);

        String rendered = console.renderPlain();
        assertTrue(rendered.startsWith("{"));
        assertTrue(rendered.contains("  \"name\": \"Ada\""));
        assertTrue(rendered.contains("  \"items\": ["));
        assertTrue(rendered.contains("    true"));
        assertTrue(rendered.contains("]"));
        assertTrue(rendered.endsWith("}"));
    }

    @Test
    void arrowNavigationAndControlEditingCommitTheSelectedValue() {
        ObjectiveConsole console = ObjectiveConsole.create("{\"name\":\"Ada\"}").color(false);
        String keys = "\033[B\u0015Grace\u0004";
        String result = console.edit(new ByteArrayInputStream(keys.getBytes(StandardCharsets.UTF_8)),
                new PrintStream(new ByteArrayOutputStream()));

        JSONObject object = JSON.getObject(result);
        assertEquals("Grace", object.get("name"));
    }

    @Test
    void tabAddsAFieldAndControlShortcutsCreateContainers() {
        ObjectiveConsole objectEditor = ObjectiveConsole.create("{\"name\":\"Ada\"}").color(false);
        String objectResult = objectEditor.edit(input("\033[B\t\u0004"), silentOutput());
        assertEquals(2, JSON.getObject(objectResult).size());

        ObjectiveConsole arrayEditor = ObjectiveConsole.create("{\"items\":\"\"}").color(false);
        String arrayResult = arrayEditor.edit(input("\033[B\u0001\u0004"), silentOutput());
        JSONObject arrayObject = JSON.getObject(arrayResult);
        assertTrue(arrayObject.get("items") instanceof List<?>);
    }

    @Test
    void progressMovesToStdoutAndSupportsConcurrentTaskUpdates() {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        com.fortrow.jflux.stdout.ProgressTaskList progress =
                new com.fortrow.jflux.stdout.ProgressTaskList(new PrintStream(bytes));
        com.fortrow.jflux.stdout.ProgressTaskList.Task task = progress.append("download", "Downloading");
        task.progress(42).updateLabel("Downloading archive");
        task.complete();
        progress.awaitCompletion();
        progress.close();

        assertTrue(task.isSuccessful());
        assertTrue(task.isDone());
        assertTrue(bytes.toString(StandardCharsets.UTF_8).contains("Completed"));
    }

    @Test
    void terminalProvidesPromptConfirmationAndSelectionHelpers() {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        Terminal terminal = new Terminal(
                input("Ada\ny\n2\n"), new PrintStream(bytes), false);

        assertEquals("Ada", terminal.prompt("Name: "));
        assertTrue(terminal.confirm("Continue?", false));
        assertEquals(1, terminal.select("Choose", List.of("one", "two")));
        assertFalse(terminal.supportsAnsi());
        assertEquals("Ada", terminal.style("Ada", Terminal.Color.GREEN));
    }

    @Test
    void oldProgressImportStillWorksAsDeprecatedFacade() {
        try (ProgressTaskList progress = new ProgressTaskList(new PrintStream(new ByteArrayOutputStream()))) {
            ProgressTaskList.Task task = progress.append("legacy", "Legacy");
            task.complete();
            assertTrue(task.isDone());
        }
    }

    private static ByteArrayInputStream input(String keys) {
        return new ByteArrayInputStream(keys.getBytes(StandardCharsets.UTF_8));
    }

    private static PrintStream silentOutput() {
        return new PrintStream(new ByteArrayOutputStream());
    }
}
