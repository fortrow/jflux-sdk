# Files package

The `com.fortrow.jflux.files` package provides small file, path, JSON, logging,
permission, symlink, and volume helpers. Text helpers use UTF-8 by default. Binary
helpers operate on bytes without converting through characters.

## FileObject

`FileObject` represents one regular file. Its constructor creates the file and
parent directories when they do not exist, which makes small storage workflows
compact:

```java
FileObject file = Files.touch(PathUtil.fromString("/var/lib/example"), "state.bin");
file.overwrite(new byte[] {0, 1, (byte) 0xff});
file.append("\nready");

file.readToEnd();
byte[] data = file.bytes();
String text = file.string();
```

`readToEnd()` fills the in-memory buffer and returns its byte count. `size()` is
the loaded-buffer size, while `fileSize()` reads the current on-disk size without
loading the file. `getBytes` validates ranges and returns copies. Use
`readToEndOrThrow()` when an I/O failure must be distinguished from an empty file.

`overwrite(byte[])`, `append(byte[])`, and `bytes()` are safe for arbitrary binary
content. `string()` and `getLines()` use UTF-8; overloads accepting a `Charset` are
available for legacy encodings.

## Files helpers

Common operations are available without manually splitting a path:

```java
String config = Files.getContent("/etc/example/config.json");
FileObject copy = Files.duplicate(source, PathUtil.fromString("/backup"));
Files.mkdir("/var/lib/example/cache/nested");
Files.rmTree("/var/lib/example/cache");
```

Notable behavior in 1.1.0:

- `Files.open(path)` creates missing parent directories and the file, retaining
  the original jflux behavior used by `JSONFile` constructors.
- `Files.create(path, name, text, false)` returns `null` when the target already
  exists; the overload retains its original no-checked-exception signature.
- `Files.duplicate` copies the source into the destination directory using the
  original filename and replaces an existing destination.
- `rmDir` removes only an empty directory. `rmTree` recursively removes a tree
  without following symbolic links.
- `createTempFile(prefix, suffix)` uses the platform temporary directory's secure
  name generator. The older `createTmpFile(uuid, suffix)` and delete helpers remain
  available for deterministic legacy filenames.
- Symlink creation creates missing link parents and refuses to replace an existing
  path. Removal refuses to delete a non-symlink.

## Paths

`PathUtil.isolateFile` keeps its four-element compatibility shape:

1. directory, including its trailing separator;
2. filename;
3. basename before the final extension;
4. final extension, or an empty string when there is no extension.

Names without extensions, dotfiles, multiple extensions, and root-level files are
handled safely. `PathObject` clones now copy their `PathVariable` objects, so
changing a variable on a clone cannot mutate the original path. Variable names can
contain multiple characters, for example `{tenant_id}`.

## JSONFile and LogFile

`JSONFile` reads and writes UTF-8, updates its signature after `setByPath`, and
supports paths such as `server.tls.certificate` and `workers[0].name`. Locked JSON
files ignore mutation requests as before. Call `save()` after directly mutating the
object returned by `getJson()`.

`LogFile.write` appends one timestamped UTF-8 record with a thread-safe write
boundary. The no-argument constructor now writes to `System.err`, so an unset log
path is observable instead of silently discarding messages. Use
`new LogFile(printStream)` to inject a stderr-compatible stream for tests or an
application logger bridge. File-backed constructors continue to append to the
requested file. The log formatter is shared rather than recreated for every line.

## Compatibility notes

The package keeps the existing public helper names. Several old implementations
silently returned empty data or `null`; 1.1.0 completes the missing operations and
uses `IllegalStateException` for failures from legacy methods whose signatures did
not declare checked I/O. New code that needs explicit failure should use the
checked `FileObject.readToEndOrThrow` method or Java NIO directly.

All file and path validation and I/O failure paths now pass through
`ExceptionHandling`. The configured jflux logger therefore receives failures even
when a legacy helper must preserve a `false`, `null`, or runtime-exception result.
Callers that need to distinguish failures should prefer the explicit throwing
methods; callers using the compatibility methods keep their existing control flow.
