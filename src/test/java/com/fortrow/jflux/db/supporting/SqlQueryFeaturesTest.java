/*
 * Copyright (c) 2026 Fortrow LLC.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Fortrow-Commercial
 *
 * JFlux is free software: you can redistribute it and/or modify it under the
 * terms of the GNU Affero General Public License, version 3, as published by
 * the Free Software Foundation.
 *
 * JFlux is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Affero General Public License for
 * more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with JFlux. If not, see <https://www.gnu.org/licenses/>.
 *
 * Alternatively, a separate commercial license is available from Fortrow LLC.
 * For terms permitting use without the AGPL copyleft requirements, contact
 * ryan@fortrow.com. Commercial rights require a separate written agreement.
 * See LICENSE and COMMERCIAL-LICENSE.md.
 */

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
