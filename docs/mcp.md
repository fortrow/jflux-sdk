# MCP package

The `com.fortrow.jflux.mcp` package provides a dependency-light Model Context
Protocol server and client for JFlux error data. It implements JSON-RPC 2.0 tool
requests, newline-delimited stdio, and a JDK-only HTTP adapter. MCP's standard
transport model is described in the [official transport specification](https://modelcontextprotocol.io/specification/2025-06-18/basic/transports).

## Server setup

An application owns the server and chooses its memory limits:

```java
McpServer server = new McpServer(new McpErrorStore(
        200,       // maximum retained reports
        100,       // maximum stacktrace entries per report
        64 * 1024  // maximum report payload characters
));

McpReporter.setConfiguration(server);

try (McpHttpServer http = new McpHttpServer(
        server, new InetSocketAddress("127.0.0.1", 8765))) {
    http.start();
    // Keep the application alive while MCP clients use /mcp.
}
```

`McpReporter` is disabled until `setConfiguration` is called. Once enabled,
`ReportedException` reports are copied into the server's bounded store. The
reporter never calls a model and never sends data to a remote service.

For subprocess-based MCP hosts, use the server's stdio loop:

```java
new McpServer().run(System.in, System.out);
```

The stdio stream must contain one JSON-RPC message per UTF-8 line. Application
logs must go to stderr or another logger so stdout remains an MCP channel.

## Built-in tools

The server exposes these tools through `tools/list`:

| Tool | Purpose |
| --- | --- |
| `jflux.errors.list` | Return newest reports, with `limit`, `type`, `contains`, and `includeStacktrace` filters. |
| `jflux.errors.get` | Return one report by its generated `id`. |
| `jflux.errors.summary` | Return count, capacity, newest/oldest IDs, and counts by exception type. |

Tool results include both MCP text content and `structuredContent`. Tool-level
failures are returned with `isError: true` so a model can understand and correct
its request. Unknown MCP methods and unknown tools use JSON-RPC protocol errors.

Stacktraces are included by default for diagnostic usefulness. Set
`includeStacktrace` to `false` when constructing a smaller model context. Error
messages, causes, and stacktraces can contain sensitive application data; bind the
HTTP adapter to a private interface or add the application's authentication and
authorization boundary before exposing it to an untrusted model host.

## Client setup

In-process clients are useful for tests and embedded model integrations:

```java
try (McpClient client = McpClient.inProcess(server)) {
    client.initialize();
    JsonObject recent = client.listErrors(10, null, null, false);
    JsonObject summary = client.summary();
}
```

HTTP clients use the `/mcp` endpoint:

```java
try (McpClient client = McpClient.http(URI.create("http://127.0.0.1:8765/mcp"))) {
    JsonObject errors = client.listErrors(10, "ReportedException", null, false);
}
```

`McpClient.stdio(input, output)` supports a host-managed subprocess or any other
newline-delimited MCP stream. Client convenience methods initialize lazily before
calling tools.

## Extending the server

Applications can register read-only, application-specific tools:

```java
JsonObject schema = new JsonObject();
schema.addProperty("type", "object");
server.registerTool(new McpTool(
        "app.health.context",
        "Return safe health context for diagnostics.",
        schema,
        arguments -> safeHealthJson()));
```

Keep custom handlers bounded and avoid returning credentials, tokens, raw request
bodies, or unrestricted filesystem/database data. The built-in error store follows
the same principle by retaining only a configured number of structured snapshots.
