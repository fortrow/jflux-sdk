package com.fortrow.jflux.xql;

import com.fortrow.jflux.exceptions.types.CommonIOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/** Small parameterized JDBC adapter for DB-to-XML XQL bindings. */
public final class JdbcXqlDataSource implements XqlDataSource {
    private final Supplier<Connection> connections;

    public JdbcXqlDataSource(Supplier<Connection> connections) {
        this.connections = connections;
    }

    @Override
    public List<Map<String, Object>> select(String table, List<String> columns, Map<String, ?> equals) throws CommonIOException {
        if (!table.matches("[A-Za-z_][A-Za-z0-9_]*")) throw new CommonIOException("Invalid XQL table name.");
        List<String> selected = columns == null || columns.isEmpty() ? List.of("*") : columns;
        for (String column : selected) if (!column.equals("*") && !column.matches("[A-Za-z_][A-Za-z0-9_]*")) throw new CommonIOException("Invalid XQL column name.");
        StringBuilder sql = new StringBuilder("SELECT ").append(String.join(",", selected)).append(" FROM ").append(table);
        List<Object> values = new ArrayList<>();
        if (equals != null && !equals.isEmpty()) {
            sql.append(" WHERE ");
            int index = 0;
            for (String column : equals.keySet()) {
                if (!column.matches("[A-Za-z_][A-Za-z0-9_]*")) throw new CommonIOException("Invalid XQL predicate column.");
                if (index++ > 0) sql.append(" AND ");
                sql.append(column).append(" = ?"); values.add(equals.get(column));
            }
        }
        try (Connection connection = connections.get(); PreparedStatement statement = connection.prepareStatement(sql.toString())) {
            for (int i = 0; i < values.size(); i++) statement.setObject(i + 1, values.get(i));
            try (ResultSet result = statement.executeQuery()) {
                List<Map<String, Object>> rows = new ArrayList<>();
                var metadata = result.getMetaData();
                while (result.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    for (int i = 1; i <= metadata.getColumnCount(); i++) row.put(metadata.getColumnLabel(i), result.getObject(i));
                    rows.add(row);
                }
                return rows;
            }
        } catch (SQLException | RuntimeException e) { throw new CommonIOException("Unable to execute XQL binding query.", e); }
    }
}
