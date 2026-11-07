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

package com.fortrow.jflux.http.api.client;
import java.util.*;import org.junit.jupiter.api.Test;import static org.junit.jupiter.api.Assertions.*;
class SessionMetadataTest {
 @Test void explicitReportsAreBoundedImmutableAndRejectSecretsOrControlCharacters(){
  var supplied=new HashMap<String,String>();supplied.put("machine_hostname","workstation");supplied.put("client_application","fortrow-cli");var saved=SessionMetadata.validate(supplied);supplied.put("machine_hostname","changed");assertEquals("workstation",saved.get("machine_hostname"));assertThrows(UnsupportedOperationException.class,()->saved.put("x","y"));
  assertThrows(RuntimeException.class,()->SessionMetadata.validate(Map.of("api_secret","secret")));
  assertThrows(RuntimeException.class,()->SessionMetadata.validate(Map.of("machine_hostname","bad\nname")));
  assertThrows(RuntimeException.class,()->SessionMetadata.validate(Map.of("client_application","x".repeat(121))));
  assertThrows(RuntimeException.class,()->SessionMetadata.validate(Map.of("memory_bytes","9223372036854775808")));
  assertThrows(RuntimeException.class,()->SessionMetadata.validate(Map.of("cpu_count","0")));
  assertEquals(Map.of(),SessionMetadata.validate(Map.of()));
 }
 @Test void localOptInIdentifiesApplicationWithoutInventingHardware(){
  var metadata=SessionMetadata.local("fortrow-cli","cli","test");assertEquals("fortrow-cli",metadata.get("client_application"));assertTrue(metadata.containsKey("os_name"));assertFalse(metadata.containsKey("device_model"));assertFalse(metadata.containsKey("memory_bytes"));
 }
}
