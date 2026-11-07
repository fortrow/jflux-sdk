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

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Git and hosted repository helpers used by source-based installers. */
public final class GitUtils {

    private static final Duration HTTP_TIMEOUT = Duration.ofSeconds(20);
    private static final Pattern HOSTED_URL = Pattern.compile("\\\"(?:web_url|html_url)\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"");

    private GitUtils() { }

    public enum Provider {
        GITHUB,
        GITLAB,
        UNKNOWN
    }

    public record ForkResult(Provider provider, boolean success, int status, URI forkUrl, String response) {
        public ForkResult {
            provider = provider == null ? Provider.UNKNOWN : provider;
            response = response == null ? "" : response;
        }
    }

    public static CommandResult cloneRepository(String repositoryUrl, Path destination) {
        validateRepository(repositoryUrl);
        validateDestination(destination);
        return runGit(List.of("git", "clone", repositoryUrl, destination.toAbsolutePath().normalize().toString()));
    }

    public static CommandResult cloneRepository(String repositoryUrl, Path destination, boolean shallow) {
        validateRepository(repositoryUrl);
        validateDestination(destination);
        List<String> command = shallow
                ? List.of("git", "clone", "--depth", "1", repositoryUrl,
                destination.toAbsolutePath().normalize().toString())
                : List.of("git", "clone", repositoryUrl, destination.toAbsolutePath().normalize().toString());
        return runGit(command);
    }

    /** Clones a missing repository or fast-forward pulls an existing Git working tree. */
    public static CommandResult cloneOrUpdate(String repositoryUrl, Path destination, boolean shallow) {
        validateRepository(repositoryUrl);
        validateDestinationOrRepository(destination);
        if (Files.isDirectory(destination.resolve(".git"))) return pull(destination);
        return cloneRepository(repositoryUrl, destination, shallow);
    }

    /** Pulls an existing working tree. */
    public static CommandResult pull(Path repository) {
        return runGitInRepository(repository, List.of("git", "pull", "--ff-only"));
    }

    /** Checks out a branch, tag, or commit in an existing working tree. */
    public static CommandResult checkout(Path repository, String revision) {
        if (revision == null || revision.isBlank() || revision.startsWith("-")) {
            throw ExceptionHandling.invalid("A valid Git revision is required.");
        }
        return runGitInRepository(repository, List.of("git", "checkout", revision));
    }

    /** Adds or replaces a named remote without invoking a shell. */
    public static CommandResult remote(Path repository, String name, String repositoryUrl) {
        if (name == null || name.isBlank() || name.startsWith("-")) {
            throw ExceptionHandling.invalid("A valid Git remote name is required.");
        }
        validateRepository(repositoryUrl);
        return runGitInRepository(repository, List.of("git", "remote", "set-url", name, repositoryUrl));
    }

    /**
     * Requests a fork through the GitHub or GitLab API.
     * The token is sent only in the HTTPS Authorization header and is never included in command output.
     */
    public static ForkResult fork(String repositoryUrl, String accessToken) {
        validateRepository(repositoryUrl);
        if (accessToken == null || accessToken.isBlank()) {
            throw ExceptionHandling.invalid("An access token is required to fork a hosted repository.");
        }
        RepositoryParts parts = repositoryParts(repositoryUrl);
        if (parts.provider() == Provider.UNKNOWN) {
            return new ForkResult(Provider.UNKNOWN, false, 400, null,
                    "Only GitHub and GitLab repository URLs are supported.");
        }
        try {
            String endpoint = parts.provider() == Provider.GITHUB
                    ? "https://api.github.com/repos/" + parts.projectPath() + "/forks"
                    : "https://gitlab.com/api/v4/projects/" + URLEncoder.encode(parts.projectPath(), StandardCharsets.UTF_8)
                    + "/fork";
            HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint))
                    .timeout(HTTP_TIMEOUT)
                    .header("Authorization", "Bearer " + accessToken)
                    .header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.noBody())
                    .build();
            HttpResponse<String> response = HttpClient.newHttpClient()
                    .send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            URI forkUrl = hostedUrl(response.body());
            return new ForkResult(parts.provider(), response.statusCode() >= 200 && response.statusCode() < 300,
                    response.statusCode(), forkUrl, response.body());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            ExceptionHandling.report(e);
            return new ForkResult(parts.provider(), false, 499, null, e.getMessage());
        } catch (Exception e) {
            ExceptionHandling.report(e);
            return new ForkResult(parts.provider(), false, 503, null, e.getMessage());
        }
    }

    public static Provider provider(String repositoryUrl) {
        validateRepository(repositoryUrl);
        return repositoryParts(repositoryUrl).provider();
    }

    private static CommandResult runGitInRepository(Path repository, List<String> command) {
        if (repository == null || !Files.isDirectory(repository)) {
            throw ExceptionHandling.invalid("Git working directory was not found.");
        }
        if (!Shell.executableAvailable("git")) return unavailable(command);
        return Shell.execute(command, repository.toAbsolutePath().normalize(), Shell.DEFAULT_TIMEOUT);
    }

    private static CommandResult runGit(List<String> command) {
        if (!Shell.executableAvailable("git")) return unavailable(command);
        return Shell.execute(command);
    }

    private static CommandResult unavailable(List<String> command) {
        return new CommandResult(command, 127, "", "git was not found in PATH.", false, false, false, false);
    }

    private static void validateRepository(String repositoryUrl) {
        if (repositoryUrl == null || repositoryUrl.isBlank() || repositoryUrl.startsWith("-")) {
            throw ExceptionHandling.invalid("A Git repository URL is required.");
        }
    }

    private static void validateDestination(Path destination) {
        if (destination == null) throw ExceptionHandling.invalid("A Git destination is required.");
        if (Files.exists(destination)) throw ExceptionHandling.invalid("Git destination already exists: " + destination);
    }

    private static RepositoryParts repositoryParts(String repositoryUrl) {
        String normalized = repositoryUrl.trim();
        if (normalized.startsWith("git@")) {
            int colon = normalized.indexOf(':');
            if (colon < 0) return new RepositoryParts(Provider.UNKNOWN, "");
            String host = normalized.substring(4, colon).toLowerCase(Locale.ROOT);
            return partsForHost(host, normalized.substring(colon + 1));
        }
        try {
            URI uri = URI.create(normalized);
            String path = uri.getPath() == null ? "" : uri.getPath().replaceFirst("^/", "");
            return partsForHost(uri.getHost() == null ? "" : uri.getHost(), path);
        } catch (IllegalArgumentException e) {
            return new RepositoryParts(Provider.UNKNOWN, "");
        }
    }

    private static RepositoryParts partsForHost(String host, String path) {
        String normalizedPath = path.replaceFirst("/+$", "").replaceFirst("\\.git$", "");
        Provider provider = host.toLowerCase(Locale.ROOT).contains("github") ? Provider.GITHUB
                : host.toLowerCase(Locale.ROOT).contains("gitlab") ? Provider.GITLAB : Provider.UNKNOWN;
        return new RepositoryParts(provider, normalizedPath);
    }

    private static URI hostedUrl(String body) {
        if (body == null) return null;
        Matcher matcher = HOSTED_URL.matcher(body);
        if (!matcher.find()) return null;
        try {
            return URI.create(matcher.group(1));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static void validateDestinationOrRepository(Path destination) {
        if (destination == null) throw ExceptionHandling.invalid("A Git destination is required.");
        if (Files.exists(destination) && !Files.isDirectory(destination.resolve(".git"))) {
            throw ExceptionHandling.invalid("Git destination exists but is not a Git working tree: " + destination);
        }
    }

    private record RepositoryParts(Provider provider, String projectPath) { }
}
