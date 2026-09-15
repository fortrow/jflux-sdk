package com.fortrow.jflux.xql;

import com.fortrow.jflux.exceptions.types.CommonIOException;
import java.util.List;
import java.util.Map;

/** Query boundary used by XQL bindings; implementations may be JDBC, API, or an in-memory source. */
public interface XqlDataSource {
    List<Map<String, Object>> select(String table, List<String> columns, Map<String, ?> equals) throws CommonIOException;
}
