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
