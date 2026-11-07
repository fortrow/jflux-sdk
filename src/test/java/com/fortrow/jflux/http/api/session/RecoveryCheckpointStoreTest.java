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

package com.fortrow.jflux.http.api.session;
import com.fortrow.jflux.http.api.client.FileSecretStore;
import java.nio.file.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
class RecoveryCheckpointStoreTest {
 @TempDir Path dir;
 @Test void forwardCheckpointIsAtomicPrivateAndLeavesNoTemporaryFiles()throws Exception {
  Path p=dir.resolve("session");var store=new FileSecretStore(p);store.write("a".repeat(64));store.write("b".repeat(64));
  assertEquals("b".repeat(64),store.read());
  assertEquals("rw-------",java.nio.file.attribute.PosixFilePermissions.toString(Files.getPosixFilePermissions(p)));
  try(var files=Files.list(dir)){assertEquals(1,files.count());}
 }
 @Test void symlinkCannotRedirectCheckpointMutation()throws Exception {
  Path target=dir.resolve("target"),link=dir.resolve("session");Files.writeString(target,"original");Files.createSymbolicLink(link,target);
  assertThrows(IllegalArgumentException.class,()->new FileSecretStore(link).write("x".repeat(64)));
  assertEquals("original",Files.readString(target));
 }
 @Test void invalidValueDoesNotTruncateLastCheckpoint()throws Exception {
  var store=new FileSecretStore(dir.resolve("session"));store.write("a".repeat(64));
  assertThrows(IllegalArgumentException.class,()->store.write(""));assertEquals("a".repeat(64),store.read());
 }
}
