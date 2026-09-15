# XQL XML persistence

`com.fortrow.jflux.xql` is jflux's Java XML/object-storage framework inspired by
PHP-XQL. It declares a durable XML shape in a small model class, writes documents
to local disk or Amazon S3, and retains the important XQL ownership semantics:

- ordinary attached models are embedded in their parent document;
- `staticModel()` makes an attached model a separately persisted document and the
  parent writes `xql_model`, `xql_id`, and `xql_path` reference attributes;
- `finalModel()` prevents a model from being persisted a second time.

## Define and persist a model

```java
final class Result extends XqlModel {
    @Override protected void schema(XqlModel model) {
        primary(field("id").value("race-7"));
        field("driver").value("Ada").required().searchable();
        field("tag").add("wet").add("night");
    }
}

Result result = new Result();
result.persist(new LocalXqlStorage(Path.of("storage/xql")));
// storage/xql/results/race-7.xml
```

`field(...).add(...)` writes a plural wrapper (`<tags><tag>…`). Values are
escaped through the DOM serializer. Required fields are checked before XML is
generated or persisted. `schemaSignature()` returns a stable SHA-256 signature of
the declared XML shape for migration bookkeeping.

## S3

Use an injected AWS SDK client when its lifecycle is owned by the application:

```java
try (S3Client client = S3Client.builder().region(Region.US_EAST_2).build()) {
    result.persist(new S3XqlStorage("durable-xml", client));
}
```

`S3XqlStorage(bucket, region)` creates and owns its client and should itself be
closed. Both stores expose `XqlStorage`, so models are storage-provider agnostic.
S3 uses the standard AWS SDK credential provider chain; no credentials are placed
in model definitions. Local storage normalizes object keys and rejects paths that
would escape its configured root.

`fetch(storage)` returns a securely parsed and validated stored document. It does
not mutate a model's fields; use it for retrieval/transport or parse application
data into a new model explicitly.

## Hydration, bindings, and migrations

`fetchAndHydrate(storage)` loads a document into the declared fields; `fill(map)`
does the same for a database row or API payload. `XqlBinding` provides a
parameterized DB-to-model boundary and `JdbcXqlDataSource` implements it with
validated identifiers and prepared equality predicates.

Schema changes are explicit and deterministic:

```java
String updated = model.migrate(xml, List.of(
    XqlMigration.rename("result/old_status", "result/status"),
    XqlMigration.add("result/source", "xql"),
    XqlMigration.transform("result/status", String::toUpperCase)
));
```

Models may register the same operations during `schema()` with
`migration(XqlMigration.add(...))`, then call the shorter `migrate(xml)` form.

## Queue, search index, and change watcher

`FileXqlJobRepository` stores rebuild jobs durably, while `XqlSearchIndex` stores
searchable field hits. `XqlWatcher` consumes normalized `XqlChangeEvent` values
from any `XqlChangeSource` (the included `QueueXqlChangeSource` is useful for CDC
adapters and tests) and delegates table-to-model resolution to an
`XqlRebuildHandler`. This keeps MySQL binlog or cloud CDC protocol code outside
the model and persistence contracts while providing the Windsor-style resumable
queue boundary.

`XqlRebuilder` is the companion virtual-thread worker: it claims jobs, invokes an
injected rebuild processor, acknowledges successful work, and requeues a failed
job before stopping so a later process can resume it.
