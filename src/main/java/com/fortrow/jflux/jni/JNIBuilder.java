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

package com.fortrow.jflux.jni;

import com.fortrow.jflux.exceptions.ExceptionHandling;
import com.fortrow.jflux.shell.CommandResult;
import com.fortrow.jflux.shell.Shell;
import com.fortrow.jflux.sys.PlatformUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Builds a JNI shared library with an argument-list command on Windows,
 * macOS, Linux, or FreeBSD. The builder does not invoke a shell and does not
 * assume a particular C compiler beyond the flags selected for the compiler.
 */
public final class JNIBuilder {

    private static final int DEFAULT_MAX_OUTPUT_BYTES = 2 * 1024 * 1024;

    private final String libraryName;
    private final JNIPlatform platform;
    private final List<Path> sources = new ArrayList<>();
    private final Set<Path> includeDirectories = new LinkedHashSet<>();
    private final Set<Path> javaIncludePaths = new LinkedHashSet<>();
    private final List<String> compilerFlags = new ArrayList<>();
    private final List<String> definitions = new ArrayList<>();
    private final List<String> linkerFlags = new ArrayList<>();
    private Path javaHome = Path.of(PlatformUtils.javaHome()).toAbsolutePath().normalize();
    private Path outputDirectory = Path.of("target", "native").toAbsolutePath().normalize();
    private String compiler;
    private Duration timeout = Duration.ofMinutes(2);
    private int maxOutputBytes = DEFAULT_MAX_OUTPUT_BYTES;

    /**
     * Compatibility constructor for the original empty builder. New code
     * should use {@link #forLibrary(String)} so the output name is explicit.
     */
    @Deprecated
    public JNIBuilder() {
        this("jflux");
    }

    private JNIBuilder(String libraryName) {
        if (libraryName == null || libraryName.isBlank()) {
            throw ExceptionHandling.invalid("JNI library name is required.");
        }
        this.libraryName = libraryName;
        this.platform = JNIPlatform.current();
        javaIncludePaths.addAll(javaIncludeDirectories(javaHome, platform));
        includeDirectories.addAll(javaIncludePaths);
    }

    /** Creates a builder for a logical library name such as {@code telemetry}. */
    public static JNIBuilder forLibrary(String libraryName) {
        return new JNIBuilder(libraryName);
    }

    /** Adds a C or C++ source file to the build. */
    public JNIBuilder source(Path source) {
        if (source == null) throw ExceptionHandling.invalid("JNI source is required.");
        sources.add(source.toAbsolutePath().normalize());
        return this;
    }

    /** Adds multiple source files while preserving insertion order. */
    public JNIBuilder sources(Collection<Path> sourceFiles) {
        if (sourceFiles == null || sourceFiles.stream().anyMatch(source -> source == null)) {
            throw ExceptionHandling.invalid("JNI source files are required.");
        }
        sourceFiles.forEach(this::source);
        return this;
    }

    /** Adds a native include directory. */
    public JNIBuilder includeDirectory(Path directory) {
        if (directory == null) throw ExceptionHandling.invalid("JNI include directory is required.");
        includeDirectories.add(directory.toAbsolutePath().normalize());
        return this;
    }

    /** Adds a compiler flag as one argument. Do not pass shell syntax. */
    public JNIBuilder compilerFlag(String flag) {
        addArgument(compilerFlags, flag, "compiler flag");
        return this;
    }

    /** Adds a linker flag as one argument. Do not pass shell syntax. */
    public JNIBuilder linkerFlag(String flag) {
        addArgument(linkerFlags, flag, "linker flag");
        return this;
    }

    /** Adds a preprocessor definition such as {@code JFLUX_DEBUG} or {@code LEVEL=2}. */
    public JNIBuilder define(String definition) {
        definitions.add(requireArgument(definition, "definition"));
        return this;
    }

    /** Sets the JDK home containing {@code include/jni.h}. */
    public JNIBuilder javaHome(Path home) {
        if (home == null) throw ExceptionHandling.invalid("Java home is required.");
        javaHome = home.toAbsolutePath().normalize();
        includeDirectories.removeAll(javaIncludePaths);
        javaIncludePaths.clear();
        javaIncludePaths.addAll(javaIncludeDirectories(javaHome, platform));
        includeDirectories.addAll(javaIncludePaths);
        return this;
    }

    /** Sets the output directory for the platform library. */
    public JNIBuilder outputDirectory(Path directory) {
        if (directory == null) throw ExceptionHandling.invalid("JNI output directory is required.");
        outputDirectory = directory.toAbsolutePath().normalize();
        return this;
    }

    /** Selects a compiler executable; when omitted, a platform-appropriate executable is discovered. */
    public JNIBuilder compiler(String executable) {
        compiler = requireArgument(executable, "compiler");
        return this;
    }

    /** Sets the maximum time allowed for compilation. */
    public JNIBuilder timeout(Duration buildTimeout) {
        if (buildTimeout == null || buildTimeout.isNegative() || buildTimeout.isZero()) {
            throw ExceptionHandling.invalid("JNI build timeout must be positive.");
        }
        timeout = buildTimeout;
        return this;
    }

    /** Sets the bounded compiler output capture size. */
    public JNIBuilder maxOutputBytes(int maxBytes) {
        if (maxBytes <= 0) throw ExceptionHandling.invalid("JNI compiler output limit must be positive.");
        maxOutputBytes = maxBytes;
        return this;
    }

    /** Returns the command that will be executed by {@link #build()}. */
    public List<String> command() {
        validateSources();
        if (!platform.supportedOperatingSystem()) {
            throw ExceptionHandling.unsupported("JNI builds are not supported on " + platform.identifier());
        }
        String selectedCompiler = resolveCompiler();
        Path output = outputPath();
        List<String> command = new ArrayList<>();
        command.add(selectedCompiler);
        if (isMsvc(selectedCompiler)) {
            command.add("/LD");
            includeDirectories.forEach(path -> command.add("/I" + path));
            definitions.forEach(definition -> command.add("/D" + definition));
            command.addAll(compilerFlags);
            sources.forEach(path -> command.add(path.toString()));
            command.addAll(linkerFlags);
            command.add("/Fe:" + output);
        } else {
            command.add(platform.operatingSystem() == JNIPlatform.OperatingSystem.MACOS ? "-dynamiclib" : "-shared");
            if (platform.operatingSystem() != JNIPlatform.OperatingSystem.WINDOWS) command.add("-fPIC");
            includeDirectories.forEach(path -> {
                command.add("-I");
                command.add(path.toString());
            });
            definitions.forEach(definition -> command.add("-D" + definition));
            command.addAll(compilerFlags);
            command.addAll(sources.stream().map(Path::toString).toList());
            command.addAll(linkerFlags);
            command.add("-o");
            command.add(output.toString());
        }
        return List.copyOf(command);
    }

    /** Executes the compiler and returns its bounded output and generated path. */
    public NativeBuildResult build() {
        List<String> buildCommand = command();
        Path output = outputPath();
        try {
            Files.createDirectories(outputDirectory);
        } catch (IOException error) {
            throw ExceptionHandling.failure("Unable to create JNI output directory: " + outputDirectory, error);
        }
        CommandResult result = Shell.execute(buildCommand, timeout, maxOutputBytes);
        return new NativeBuildResult(platform, buildCommand.getFirst(), output, buildCommand, result);
    }

    /** Returns the current platform used by this builder. */
    public JNIPlatform platform() {
        return platform;
    }

    /** Returns the generated platform-specific library path. */
    public Path outputPath() {
        return outputDirectory.resolve(platform.libraryFileName(libraryName)).toAbsolutePath().normalize();
    }

    /** Returns the JDK include paths that contain {@code jni.h} and the platform JNI header. */
    public static List<Path> javaIncludeDirectories(Path javaHome, JNIPlatform platform) {
        if (javaHome == null || platform == null) throw ExceptionHandling.invalid("Java home and JNI platform are required.");
        Path home = javaHome.toAbsolutePath().normalize();
        Path include = home.resolve("include");
        if (!Files.isDirectory(include) && home.getParent() != null) {
            Path parentInclude = home.getParent().resolve("include");
            if (Files.isDirectory(parentInclude)) include = parentInclude;
        }
        return List.of(include, include.resolve(platform.javaIncludeDirectory()));
    }

    private void validateSources() {
        if (sources.isEmpty()) throw ExceptionHandling.invalid("At least one JNI source file is required.");
    }

    private String resolveCompiler() {
        if (compiler != null) return compiler;
        List<String> candidates = platform.operatingSystem() == JNIPlatform.OperatingSystem.WINDOWS
                ? List.of("cl", "clang", "gcc")
                : List.of("cc", "clang", "gcc");
        return candidates.stream().filter(Shell::executableAvailable).findFirst().orElseThrow(() ->
                ExceptionHandling.failure("No supported JNI C compiler was found in PATH.", null));
    }

    private static boolean isMsvc(String compiler) {
        String name = Path.of(compiler).getFileName().toString().toLowerCase(Locale.ROOT);
        return name.equals("cl") || name.equals("cl.exe");
    }

    private static void addArgument(List<String> target, String value, String label) {
        target.add(requireArgument(value, label));
    }

    private static String requireArgument(String value, String label) {
        if (value == null || value.isBlank() || value.indexOf('\0') >= 0) {
            throw ExceptionHandling.invalid("JNI " + label + " is required.");
        }
        return value;
    }
}
