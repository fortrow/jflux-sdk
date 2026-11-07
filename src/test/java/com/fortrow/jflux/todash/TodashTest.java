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

package com.fortrow.jflux.todash;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class TodashTest {

    @Test
    void nullAndEmptyChecksSupportCommonJavaValues() {
        assertTrue(__.isNil(null));
        assertTrue(__.isEmpty(List.of()));
        assertTrue(__.isEmpty(new int[0]));
        assertTrue(__.isEmpty(Map.of()));
        assertFalse(__.isEmpty(List.of("value")));
        assertEquals("fallback", __.defaultTo(null, "fallback"));
    }

    @Test
    void collectionHelpersReturnIndependentResults() {
        assertEquals(List.of(2, 4), __.map(List.of(1, 2), value -> value * 2));
        assertEquals(List.of(2), __.filter(List.of(1, 2, 3), value -> value % 2 == 0));
        assertEquals(List.of(1, 2), __.uniq(List.of(1, 2, 1)));
        assertEquals(List.of(List.of(1, 2), List.of(3)), __.chunk(List.of(1, 2, 3), 2));
        assertEquals(Map.of("odd", 2, "even", 1), __.countBy(List.of(1, 3, 2), value -> value % 2 == 0 ? "even" : "odd"));
    }

    @Test
    void stringAndRangeHelpersAvoidLegacyEdgeCaseFailures() {
        assertEquals("hello_world", __.camelToSnake("helloWorld"));
        assertEquals("HelloWorld", __.snakeToClass("hello_world"));
        assertEquals("hello-world", __.kebabCase("helloWorld"));
        assertArrayEquals(new int[]{3, 1, -1}, __.range(3, -2, -2));
        assertEquals(10, __.clamp(20, 0, 10));
        assertEquals("a|b|c", __.join(List.of("a", "b", "c"), "|"));
    }

    @Test
    void progressLinesRenderUsefulOutput() {
        __.ProgressBar progress = new __.ProgressBar();
        progress.increment(50);
        assertTrue(progress.get().contains("50%"));

        __.LineCollection lines = new __.LineCollection()
                .append("ready")
                .progress();
        lines.increment(100, 1);
        assertEquals(2, lines.size());
        assertTrue(lines.render().get(0).contains("ready"));
        assertTrue(lines.render().get(1).contains("100%"));
    }
}
