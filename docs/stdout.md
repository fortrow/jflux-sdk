# stdout terminal helpers

The `com.fortrow.jflux.stdout` package contains reusable terminal output
helpers for CLI applications.

## Terminal

`Terminal` centralizes ANSI styling and common prompts:

```java
Terminal terminal = new Terminal();
if (terminal.confirm("Continue?", true)) {
    String name = terminal.prompt("Name: ", "anonymous");
}
```

It provides ANSI-aware colors, headings, dividers, cursor/screen helpers,
confirmations, numbered selection menus, and `choose(...)` for typed lists.
ANSI output is automatically disabled when no JVM console is present. The
three-argument constructor allows tests and applications to choose explicitly.
Password input uses `System.console().readPassword(...)` on the process console;
the stream fallback cannot disable terminal echo and should not be used for
secrets where a real console is available.

## ProgressTaskList

`ProgressTaskList` now lives in stdout and is safe to update from worker
threads:

```java
try (ProgressTaskList progress = new ProgressTaskList()) {
    ProgressTaskList.Task task = progress.append("build", "Building");
    task.progress(50).updateLabel("Compiling sources");
    task.complete();
}
```

Tasks support generated or explicit identifiers, labels, percentages, success,
failure, cancellation, completion futures, and status inspection. Rendering
uses one daemon scheduler, copy-on-write task storage, and deterministic close
behavior. `com.fortrow.jflux.console.ProgressTaskList` remains as a deprecated
subclass facade so existing CLI imports can migrate without a flag day.

`TableGenerator` remains the compatibility table formatter. Terminal-width
probes are bounded and cached, and command execution goes through the shared
JFlux shell runner instead of creating an unbounded process/output path for
each table render.
