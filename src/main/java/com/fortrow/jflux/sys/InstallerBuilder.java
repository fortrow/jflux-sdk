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

import com.fortrow.jflux.exceptions.ExceptionHandling;
import com.fortrow.jflux.shell.CommandResult;
import com.fortrow.jflux.shell.Shell;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Composes source installation workflows from ordinary shell commands.
 * It intentionally does not encode language-specific build conventions.
 */
public final class InstallerBuilder {

    private final Path workspace;
    private final List<List<String>> setupCommands = new ArrayList<>();
    private final List<List<String>> buildCommands = new ArrayList<>();
    private final List<List<String>> cleanupCommands = new ArrayList<>();
    private final List<Path> cleanupPaths = new ArrayList<>();
    private final List<Path> setupDirectories = new ArrayList<>();
    private Path sourceDirectory;
    private boolean deleteWorkspace;

    private InstallerBuilder(Path workspace) {
        if (workspace == null) throw ExceptionHandling.invalid("Installer workspace is required.");
        this.workspace = workspace.toAbsolutePath().normalize();
        this.sourceDirectory = this.workspace;
    }

    public static InstallerBuilder in(Path workspace) {
        return new InstallerBuilder(workspace);
    }

    /** Adds a Git clone step and uses the selected directory for subsequent build commands. */
    public InstallerBuilder fromGit(String repositoryUrl) {
        return fromGit(repositoryUrl, "source");
    }

    public InstallerBuilder fromGit(String repositoryUrl, String directoryName) {
        validateValue(repositoryUrl, "Repository URL");
        Path destination = child(directoryName);
        sourceDirectory = destination;
        setupCommands.add(List.of("git", "clone", repositoryUrl, destination.toString()));
        return this;
    }

    /** Adds a wget download and an unzip/tar extraction step. */
    public InstallerBuilder fromWget(String sourceUrl, String archiveName, String directoryName) {
        validateValue(sourceUrl, "Source URL");
        Path archive = child(archiveName);
        sourceDirectory = child(directoryName);
        setupCommands.add(List.of("wget", "--quiet", "--output-document", archive.toString(), sourceUrl));
        setupCommands.add(extractCommand(archive, sourceDirectory));
        setupDirectories.add(sourceDirectory);
        cleanupPaths.add(archive);
        return this;
    }

    public InstallerBuilder fromWget(String sourceUrl, String archiveName) {
        return fromWget(sourceUrl, archiveName, "source");
    }

    /** Adds one safe argument-list command to the setup phase. */
    public InstallerBuilder setupCommand(String executable, String... arguments) {
        setupCommands.add(command(executable, arguments));
        return this;
    }

    /** Adds one safe argument-list command to the build phase. */
    public InstallerBuilder buildCommand(String executable, String... arguments) {
        buildCommands.add(command(executable, arguments));
        return this;
    }

    /** Adds one safe argument-list command to the cleanup phase. */
    public InstallerBuilder cleanupCommand(String executable, String... arguments) {
        cleanupCommands.add(command(executable, arguments));
        return this;
    }

    /** Requests deletion of a path after cleanup; this is opt-in because it is destructive. */
    public InstallerBuilder cleanupPath(Path path) {
        if (path == null) throw ExceptionHandling.invalid("Cleanup path is required.");
        cleanupPaths.add(path.toAbsolutePath().normalize());
        return this;
    }

    /** Requests deletion of the workspace after cleanup. */
    public InstallerBuilder deleteWorkspaceAfterCleanup(boolean delete) {
        this.deleteWorkspace = delete;
        return this;
    }

    public Path workspace() {
        return workspace;
    }

    public Path sourceDirectory() {
        return sourceDirectory;
    }

    public List<List<String>> setupCommands() {
        return immutableCommands(setupCommands);
    }

    public List<List<String>> buildCommands() {
        return immutableCommands(buildCommands);
    }

    public List<List<String>> cleanupCommands() {
        return immutableCommands(cleanupCommands);
    }

    /** Runs source acquisition and the caller-defined build commands. */
    public BuildResult build() {
        List<CommandResult> results = new ArrayList<>();
        try {
            Files.createDirectories(workspace);
            for (Path directory : setupDirectories) Files.createDirectories(directory);
        } catch (IOException e) {
            ExceptionHandling.report(e);
            results.add(new CommandResult(List.of("create-directories", workspace.toString()), 1,
                    "", e.getMessage(), false, false, false, false));
            return new BuildResult(results, false, false, workspace, sourceDirectory);
        }
        runCommands(setupCommands, workspace, results);
        if (results.stream().allMatch(CommandResult::success)) {
            runCommands(buildCommands, sourceDirectory, results);
        }
        return new BuildResult(results, results.stream().allMatch(CommandResult::success), false, workspace, sourceDirectory);
    }

    public BuildResult run() {
        return build();
    }

    /** Runs caller-defined cleanup commands and then explicitly requested cleanup paths. */
    public BuildResult cleanup() {
        List<CommandResult> results = new ArrayList<>();
        Path directory = Files.isDirectory(sourceDirectory) ? sourceDirectory : workspace;
        runCommands(cleanupCommands, directory, results);
        boolean deleted = true;
        if (results.stream().allMatch(CommandResult::success)) {
            for (Path path : cleanupPaths) deleted &= deletePath(path);
            if (deleteWorkspace) deleted &= deletePath(workspace);
        }
        return new BuildResult(results, results.stream().allMatch(CommandResult::success) && deleted,
                deleted, workspace, sourceDirectory);
    }

    private void runCommands(List<List<String>> commands, Path directory, List<CommandResult> results) {
        for (List<String> command : commands) {
            CommandResult result = Shell.execute(command, directory, Shell.DEFAULT_TIMEOUT);
            results.add(result);
            if (!result.success()) break;
        }
    }

    private Path child(String value) {
        validateValue(value, "Path");
        Path child = workspace.resolve(value).normalize();
        if (!child.startsWith(workspace) || child.equals(workspace)) {
            throw ExceptionHandling.invalid("Installer paths must remain below the workspace.");
        }
        return child;
    }

    private static List<String> extractCommand(Path archive, Path destination) {
        String name = archive.getFileName().toString().toLowerCase(Locale.ROOT);
        if (name.endsWith(".zip")) return List.of("unzip", "-q", archive.toString(), "-d", destination.toString());
        if (name.endsWith(".tar") || name.endsWith(".tar.gz") || name.endsWith(".tgz")
                || name.endsWith(".tar.bz2") || name.endsWith(".tbz2")
                || name.endsWith(".tar.xz") || name.endsWith(".txz")) {
            return List.of("tar", "-xf", archive.toString(), "-C", destination.toString());
        }
        throw ExceptionHandling.invalid("Unsupported source archive. Use zip or tar formats.");
    }

    private static List<String> command(String executable, String... arguments) {
        if (executable == null || executable.isBlank()) throw ExceptionHandling.invalid("Command executable is required.");
        ArrayList<String> result = new ArrayList<>();
        result.add(executable);
        if (arguments != null) {
            for (String argument : arguments) {
                if (argument == null || argument.isBlank()) throw ExceptionHandling.invalid("Command arguments cannot be blank.");
                result.add(argument);
            }
        }
        return List.copyOf(result);
    }

    private static void validateValue(String value, String label) {
        if (value == null || value.isBlank()) throw ExceptionHandling.invalid(label + " is required.");
    }

    private static List<List<String>> immutableCommands(List<List<String>> commands) {
        return commands.stream().map(List::copyOf).toList();
    }

    private static boolean deletePath(Path path) {
        try {
            if (!Files.exists(path)) return true;
            try (var stream = Files.walk(path)) {
                stream.sorted(Comparator.reverseOrder()).forEach(current -> {
                    try {
                        Files.deleteIfExists(current);
                    } catch (IOException e) {
                        ExceptionHandling.report(e);
                    }
                });
            }
            return !Files.exists(path);
        } catch (IOException e) {
            ExceptionHandling.report(e);
            return false;
        }
    }

    public record BuildResult(List<CommandResult> commands, boolean success, boolean cleaned,
                              Path workspace, Path sourceDirectory) {
        public BuildResult {
            commands = List.copyOf(commands == null ? List.of() : commands);
        }
    }
}
