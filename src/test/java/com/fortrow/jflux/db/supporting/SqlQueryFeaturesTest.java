package com.fortrow.jflux.db.supporting;

import com.fortrow.jflux.db.Db;
import com.fortrow.jflux.db.connectors.details.DbConnectionSqlLiteDetails;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SqlQueryFeaturesTest {

    @Test
    void supportsPragmaReadsAndSettings() throws Exception {
        Path file = Files.createTempFile("jflux-query-features", ".db");
        try(Db db = new Db(new DbConnectionSqlLiteDetails(file.toString()))) {
            db.query().pragma("foreign_keys", true).run();
            QueryResults result = db.query().pragma("foreign_keys").get();
            assertEquals(1L, ((Number) result.next().val(0)).longValue());
        }
    }

    @Test
    void batchInsertAndCompatibilityFluentMethodsWorkTogether() throws Exception {
        Path file = Files.createTempFile("jflux-query-batch", ".db");
        try(Db db = new Db(new DbConnectionSqlLiteDetails(file.toString()))) {
            db.query().createTable("items",
                    TableSchema.col("name", "text", false),
                    TableSchema.col("category", "text", false)
            ).run();

            Map<String, Object> first = new LinkedHashMap<>();
            first.put("name", "one");
            first.put("category", "a");
            Map<String, Object> second = new LinkedHashMap<>();
            second.put("name", "two");
            second.put("category", "a");

            db.query().table("items").insertBatch(List.of(first, second)).run();
            QueryResults results = db.query().table("items")
                    .select("category")
                    .distinct()
                    .limit(1)
                    .get();

            assertEquals(1, results.size());
            assertEquals("a", results.next().val("category"));
            assertTrue(db.getRawDriver().conn().getConn() instanceof java.sql.Connection);
        }
    }
}
