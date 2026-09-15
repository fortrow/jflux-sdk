# Database

JFlux 1.1.0 keeps the existing `Db` → `SqlQuery` fluent syntax while improving JDBC resource handling and prepared-value ordering. SQLite, MySQL, and Redis connection classes remain available; the unused `LinkSqlConnection` stub was removed.

## Connection lifecycle

`Db`, `DbDriver`, and `DbConnection` are `AutoCloseable`, so applications can release connections predictably:

```java
try (Db db = new Db(new DbConnectionSqlLiteDetails("app.db"))) {
    db.query().table("users").select("id", "name").get();
}
```

Statements and result sets created by SQL connectors are closed as soon as a query has been materialized into `QueryResults`. A connection can be used again after `close()`; it reconnects on the next operation.

## Inserts and updates

The existing forms remain supported:

```java
query.insert("name", "Ryan")
     .insert("enabled", true)
     .run();

query.update("enabled", false)
     .where("name", "=", "Ryan")
     .run();
```

Maps are now accepted through the general `Map` type, which makes `LinkedHashMap` useful when predictable column order matters:

```java
query.insert(values).run();
query.update(changes).where("id", "=", id).run();
```

For multiple rows, `insertBatch(List<? extends Map<String, Object>>)` creates one prepared multi-row insert and validates that each row contains the same columns:

```java
query.table("events").insertBatch(List.of(first, second)).run();
```

`insertIgnore()` adds SQLite `INSERT OR IGNORE` or MySQL `INSERT IGNORE`. `returning("id", "name")` supports result-returning insert/update/delete statements on database engines that provide `RETURNING`.

## Query features

The builder supports the existing `select`, `where`, joins, grouping, ordering, timestamps, deletes, and schema creation APIs. The 1.1.0 additions are:

- `distinct()`
- `having(column, operator, value)`
- `limit(int)` and `offset(int)`
- `returning(String...)`
- `insertIgnore()`
- `insertBatch(...)`

SQLite PRAGMA statements use a dedicated, validated API:

```java
db.query().pragma("foreign_keys", true).run();
long enabled = ((Number) db.query().pragma("foreign_keys").get().next().val(0)).longValue();
```

PRAGMA names are validated as identifiers. Values are escaped or rendered as numeric/boolean literals by the builder, so callers do not need to concatenate SQL settings.

The builder intentionally does not expose an arbitrary raw-SQL method. Unsupported SQL should be added as a structured builder feature so parameter binding and identifier validation remain consistent.

## Error handling

Connection and query failures are reported through JFlux’s configured exception reporter as `CriticalException`. JDBC resources are closed before the failure is reported. Callers should configure the application reporter before creating database connections when automatic error routing is needed.
