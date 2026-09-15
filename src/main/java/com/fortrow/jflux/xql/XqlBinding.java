package com.fortrow.jflux.xql;

import com.fortrow.jflux.exceptions.types.CommonIOException;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.Collections;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Predicate;

/** Declarative DB-to-model binding with optional row filtering and transformation. */
public final class XqlBinding {
    private final String table;
    private final List<String> columns;
    private final Map<String, ?> equals;
    private final Predicate<Map<String, Object>> filter;
    private final Function<Map<String, Object>, Map<String, ?>> transform;

    private XqlBinding(Builder builder) {
        table = builder.table; columns = List.copyOf(builder.columns); equals = Collections.unmodifiableMap(new LinkedHashMap<>(builder.equals));
        filter = builder.filter; transform = builder.transform;
    }

    public static Builder table(String table) { return new Builder(table); }

    public List<Map<String, ?>> rows(XqlDataSource source) throws CommonIOException {
        return source.select(table, columns, equals).stream().filter(filter)
                .<Map<String, ?>>map(row -> transform.apply(Map.copyOf(row))).toList();
    }

    /** Fills fields matching row keys, making rebuild code explicit and testable. */
    public void fill(XqlModel model, XqlDataSource source) throws CommonIOException, XqlException {
        List<Map<String, ?>> values = rows(source);
        if (!values.isEmpty()) model.fill(values.getFirst());
    }

    public static final class Builder {
        private final String table;
        private List<String> columns = List.of();
        private Map<String, ?> equals = Map.of();
        private Predicate<Map<String, Object>> filter = row -> true;
        private Function<Map<String, Object>, Map<String, ?>> transform = row -> row;
        private Builder(String table) { this.table = Objects.requireNonNull(table); }
        public Builder columns(String... columns) { this.columns = List.of(columns); return this; }
        public Builder where(Map<String, ?> equals) { this.equals = equals == null ? Map.of() : equals; return this; }
        public Builder filter(Predicate<Map<String, Object>> filter) { this.filter = filter == null ? row -> true : filter; return this; }
        public Builder transform(Function<Map<String, Object>, Map<String, ?>> transform) { this.transform = Objects.requireNonNull(transform); return this; }
        public XqlBinding build() { return new XqlBinding(this); }
    }
}
