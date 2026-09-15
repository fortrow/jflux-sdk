# YAML utilities

`com.fortrow.jflux.yaml.YAML` is the small JFlux facade over SnakeYAML Engine.
The existing `parse`, `parseFile`, `map`, `list`, `string`, `jsonString`, and
`prettyJsonString` methods remain available.

## Parsing

```java
Map<String, Object> config = YAML.map("name: Ada\nport: 8443");
Settings settings = YAML.parse("name: Ada\nport: 8443", Settings.class);
```

String parsing returns normal Java maps, lists, scalars, and `null` for an empty
YAML document. Typed conversion keeps the existing Gson compatibility path, but
the Gson instance is shared instead of created for every call.

`YAML.parseAll(String)` reads multiple YAML documents separated by `---` into a
list. `YAML.string(Object)` uses a shared, synchronized dumper so concurrent
callers do not retain one SnakeYAML representer per thread.

## Streams and files

```java
try (InputStream input = Files.newInputStream(path)) {
    Object value = YAML.parseStream(input);
}

Object value = YAML.parsePath(path);
Object legacy = YAML.parseFile(path.toString());
```

`parseStream` does not close the caller-owned stream. `parse(FileObject)` and
`parsePath(Path)` open and close their own file stream. `parseFile(String)` keeps
its checked `CommonIOException` contract and no longer creates a missing file as
a side effect.

## Input limits and errors

The default parser accepts up to 8 MiB of YAML code points, 32 MiB from a file or
stream, and 50 collection aliases. These limits reduce accidental memory growth
and alias-expansion abuse while allowing ordinary application configuration files.

Use `YAML.parse(String, maxCodePoints)`,
`YAML.parseStream(InputStream, maxInputBytes)`, or
`YAML.parse(FileObject, maxInputBytes)` when an application has a narrower limit.
Limits must be greater than zero. Invalid YAML, invalid limits, and wrong root
types use the JFlux invalid-input exception boundary.

The YAML package is included in the core shaded artifact together with its
SnakeYAML Engine dependency.
