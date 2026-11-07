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

package com.fortrow.jflux.sys;

import com.fortrow.jflux.shell.CommandResult;
import com.fortrow.jflux.shell.Shell;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SysTest {

    @Test
    void platformSnapshotIsStableAndUseful() {
        PlatformUtils.PlatformSnapshot snapshot = PlatformUtils.snapshot();
        assertNotNull(snapshot.name());
        assertNotNull(snapshot.type());
        assertNotNull(PlatformUtils.tempDirectory());
        assertTrue(PlatformUtils.availableProcessors() > 0);
    }

    @Test
    void shellCapturesJavaVersionWithoutADeadlock() {
        Path java = Path.of(System.getProperty("java.home"), "bin", "java");
        CommandResult result = Shell.execute(List.of(java.toString(), "-version"), Duration.ofSeconds(5));
        assertTrue(result.success(), result.output());
        assertFalse(result.stderr().isBlank());
        assertFalse(result.stdoutTruncated());
    }

    @Test
    void packageAndJavaMetadataHelpersRemainSeparate() {
        assertTrue(JavaPackageUtils.isInPackage(SysTest.class, "com.fortrow.jflux.sys", false));
        assertEquals("com/fortrow/jflux/sys", JavaPackageUtils.packagePath(JavaPackageUtils.packageName(SysTest.class)));
        assertNotNull(PackageUtils.which(Path.of(System.getProperty("java.home"), "bin", "java").toString()));
    }

    @Test
    void sourceBuilderUsesLanguageNeutralCommands() {
        InstallerBuilder builder = InstallerBuilder.in(Path.of("target", "source-install-test"))
                .fromGit("https://github.com/example/project.git", "project")
                .buildCommand("./configure")
                .buildCommand("make", "-j2")
                .cleanupCommand("make", "clean");

        assertEquals(List.of("git", "clone", "https://github.com/example/project.git",
                builder.workspace().resolve("project").toString()), builder.setupCommands().getFirst());
        assertEquals(List.of("make", "-j2"), builder.buildCommands().get(1));
        assertEquals(GitUtils.Provider.GITHUB, GitUtils.provider("https://github.com/example/project.git"));
    }
}
