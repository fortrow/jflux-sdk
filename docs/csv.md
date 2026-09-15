# CSV

CSV is an independent package at `com.fortrow.jflux.csv`; it does not depend on
the output conversion package.

`Csv` retains the low-level FastCSV factories and adds `Path` overloads plus
deterministic small-file helpers:

```java
Csv.writeAll(Path.of("reports", "daily.csv"), List.of(
        List.of("name", "score"),
        List.of("Ada", "42")));
List<List<String>> rows = Csv.readAll(Path.of("reports", "daily.csv"));
```

`readAll` is intended for small files and returns immutable rows. For large
files, use `getReader(Path)` and iterate the FastCSV reader as a stream. Writers
create missing parent directories and are always closed by `writeAll`.

The `com.fortrow.jflux.csv` package and FastCSV dependency are included in the
core artifact, independently of the output and JSON package filters.
