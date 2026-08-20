# Shell package

`com.fortrow.jflux.shell` provides the common command-execution layer used by
`sys` package auditing and installers.

## Safe command execution

Use argument lists for commands whose values come from configuration, users, or
package metadata:

```java
CommandResult result = Shell.execute(
        List.of("git", "clone", repositoryUrl, destination.toString()));

if (!result.success()) {
    // inspect result.exitCode(), result.stdout(), and result.stderr()
}
```

The runner drains stdout and stderr concurrently, applies a timeout, and limits
each captured stream to 1 MiB by default. Use the overload accepting a
`Duration` and byte limit when a different contract is appropriate. The result
records timeout, interruption, and truncation state.

`Shell.execute(String)` remains available for intentional shell syntax such as
pipes, redirects, and command chaining. It is a compatibility path and should
not be used to concatenate untrusted values. The selected platform shell is
used automatically; PowerShell is available through the `WindowsShell` overload.

`Run`, `Exec`, `ExistsInPath`, `UnixCommand`, and `WindowsCommand` now delegate to
this implementation. Their legacy return values remain available, while new
code should use `Shell` and `CommandResult` directly.

## Source workflows

`GitUtils` wraps safe Git argument commands for clone, shallow clone, clone-or-update,
pull, checkout, and remote updates. It identifies GitHub and GitLab URLs and can send
an explicitly supplied bearer token to the corresponding fork API. Tokens are
not passed to shell commands.

`InstallerBuilder` composes source acquisition, build, and cleanup steps without
assuming a programming language or build system:

```java
InstallerBuilder.in(workspace)
        .fromGit("https://github.com/example/project.git")
        .buildCommand("./configure")
        .buildCommand("make", "-j2")
        .cleanupCommand("make", "clean")
        .run();
```

`fromWget` supports zip and tar-family archives. Cleanup commands and path
deletion are opt-in; workspace deletion must be explicitly enabled with
`deleteWorkspaceAfterCleanup(true)`.
