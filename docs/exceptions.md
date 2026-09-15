# Exception package

The exception package provides structured exception data, automatic application
logging, and optional reporting sinks. The default logger is a stderr-backed
`LogFile`. Applications should replace it at startup with their own application
error log:

```java
ErrorReporter.setErrorLogger(new LogFile("/var/log/my-service/errors.log"));
ErrorReporter.setAlwaysLog(true);
```

When automatic logging is enabled, every `ExceptionAdapter` is automatically
logged once when it is constructed.
`CommonException`, `CommonIOException`, and `UndefinedException` are logged but
are not externally reported by default. `CriticalException`, `FatalException`,
and `ReportedException` are automatically reported by default.

## Reporting policy

Call `tryReport()` when reporting should be explicit:

```java
ReportedException error = new ReportedException("Unable to process request");
if (!error.tryReport()) {
    // No configured reporting sink accepted the report.
}
```

The default policy can be changed per type:

```java
ErrorReporter.setAutoReport(CommonIOException.class, true);
ErrorReporter.setAutoReport(ReportedException.class, false);
```

Automatic reporting is idempotent. `tryReport()` records whether delivery was
attempted and whether at least one configured sink accepted the report. Automatic
logging is separate from external reporting and can be disabled with
`ErrorReporter.setAlwaysLog(false)`.

Each report includes the exception type, message, user-facing message, code,
cause, suppressed exceptions, and stacktrace. `reportJsonString()` exposes the
same payload used by HTTP and database reporters.

## Configuration values

All sinks start disabled with safe placeholder values. A sink becomes active only
after real values are supplied.

| Sink | Configuration | Example |
| --- | --- | --- |
| Email | SMTP host, port, username, password/app password, sender, recipient, STARTTLS or SSL | `EmailReporter.setGmailConfiguration("alerts@company.com", appPassword, "oncall@company.com")` |
| HTTP | Endpoint URL and bearer/API key | `HttpEndpointReporter.setConfiguration("https://errors.company.com/events", token)` |
| Dedicated log | Writable report log path | `LogFileReporter.setConfiguration("/var/log/my-service/reports.log")` |
| MCP | Application-owned `McpServer` instance | `McpReporter.setConfiguration(server)` |
| SQLite | Database file path and optional table name | `SQLiteReporter.setConfiguration("/var/lib/my-service/errors.sqlite")` |
| MySQL | Host, port, database, username, password | `MySQLReporter.setConfiguration("db.internal", 3306, "operations", user, password)` |
| Pushover | Application token and user key | `PushoverReporter.setConfiguration(appToken, userKey)` |

Gmail uses `smtp.gmail.com:587` with STARTTLS and Outlook uses
`smtp.office365.com:587` with STARTTLS. Gmail generally requires an app password
when account security policies disallow basic account passwords. The generic email
configuration record supports custom SMTP servers and implicit SSL. The reporter
delegates delivery to `com.fortrow.jflux.email.SMTP` using its non-throwing
`trySend` path, so an unavailable mail server cannot recursively report the
exception it failed to deliver. See [`email.md`](email.md) for direct application
email, MIME, attachment, and TLS behavior.

## SQL reporting

SQLite and MySQL use the same `jflux_errors` schema by default. The reporter
creates the table on first use and inserts a generated identifier, timestamp,
exception type, message, and JSON payload. SQL reporter failures return `false` to
the reporting policy and do not replace the original exception or throw a second
reporting exception. The normal jflux JDBC drivers must be present in the
application artifact when the corresponding sink is enabled.

There is no existing `SQLError` class in the `db` package. Database connection
failures continue to use `CriticalException`; the shared SQL reporting
implementation lives in `exceptions.reporting.types` so the exception package can
be used independently of the higher-level query builder.

## MCP reporting

MCP reporting stores bounded structured `ReportedException` snapshots in an
application-owned `McpServer`. It does not send exception data to a model by
itself. A model-facing MCP client can call `jflux.errors.list`,
`jflux.errors.get`, and `jflux.errors.summary` to gather the error context it
needs. Configure the reporter and an HTTP adapter like this:

```java
McpServer server = new McpServer(new McpErrorStore(200, 100, 64 * 1024));
McpReporter.setConfiguration(server);
McpHttpServer http = new McpHttpServer(server, new InetSocketAddress("127.0.0.1", 8765));
http.start();
```

Keep the MCP endpoint private or protect it with the application's network and
authentication boundary. Error reports can contain stack traces, request details,
and cause messages. The server uses a bounded store and supports omitting
stacktraces from list/get responses when a smaller model context is preferred.

## Compatibility

The existing `ErrorReporter.errorLogger`, `alwaysLog`, `autolog`, `report`, and
reporter configuration entry points remain available. The old two-argument
configuration methods that had unrelated placeholder names are retained as
deprecated compatibility shims; new code should use the typed configuration
records or provider-specific methods.

`ExceptionHandling` is the migration bridge for older APIs whose signatures still
return sentinels or throw runtime exceptions. `ExceptionHandling.report(Throwable)`
converts arbitrary failures into the configured jflux reporting flow. Its
`invalid(...)` and `unsupported(...)` helpers create a `CommonException` for the
configured automatic logger and then return the legacy runtime exception. Its
`failure(...)` helper creates a `ReportedException` so operational failures use the
configured reporting policy before returning the legacy runtime exception. Existing
source and binary contracts are therefore preserved while packages migrate
incrementally.
