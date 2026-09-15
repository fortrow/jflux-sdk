package com.fortrow.jflux.xql;

import java.util.Map;

/** Normalized row-change event consumed by the XQL watcher. */
public record XqlChangeEvent(String table, Operation operation, Map<String, ?> keys) {
    public enum Operation { INSERT, UPDATE, DELETE }
    public XqlChangeEvent { if (table == null || table.isBlank() || operation == null) throw new IllegalArgumentException("Table and operation are required."); keys = keys == null ? Map.of() : Map.copyOf(keys); }
}
