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

package com.fortrow.jflux.http.api.server.session;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SessionCapacityTest {

    @Test
    void eachApiKeyStartsAtOneAndAllocationsAreIndependent() throws Throwable {
        SessionCapacity capacity = new SessionCapacity();

        assertEquals(1, capacity.limitFor("key-a"));
        assertEquals(1, capacity.limitFor("key-b"));
        assertEquals(2, capacity.allocate("key-a"));
        assertEquals(3, capacity.allocate("key-a"));
        assertEquals(3, capacity.limitFor("key-a"));
        assertEquals(1, capacity.limitFor("key-b"));
    }

    @Test
    void allocationRejectsMultiSlotRequests() {
        SessionCapacity capacity = new SessionCapacity();
        assertThrows(Throwable.class, () -> capacity.allocate("key-a", 2));
    }

    @Test
    void resetReturnsTheKeyToOne() throws Throwable {
        SessionCapacity capacity = new SessionCapacity();
        capacity.allocate("key-a");
        capacity.allocate("key-a");
        capacity.allocate("key-a");
        capacity.allocate("key-a");
        capacity.reset("key-a");
        assertEquals(1, capacity.limitFor("key-a"));
    }

    @Test
    void durableCapacityReloadsWithTheResumeStore() throws Throwable {
        Path resumePath = Files.createTempFile("jflux-capacity", ".json");
        Path capacityPath = resumePath.resolveSibling(resumePath.getFileName() + ".capacity");
        try {
            SessionCapacity first = new SessionCapacity(resumePath);
            first.allocate("key-a");
            first.allocate("key-a");
            SessionCapacity second = new SessionCapacity(resumePath);
            assertEquals(3, second.limitFor("key-a"));
        } finally {
            Files.deleteIfExists(resumePath);
            Files.deleteIfExists(capacityPath);
        }
    }
}
