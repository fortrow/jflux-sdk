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
 *
 * File created by Ryan Fitzgerald (ryan@fortrow.com) on 2026-05-11 17:29:10 EDT
 */
package com.fortrow.jflux.http.api.server.containers;

import com.fortrow.jflux.http.api.base.endpoints.InboundRequest;
import com.fortrow.jflux.http.api.server.types.ContentType;
import com.sun.net.httpserver.Headers;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

class BodyParametersTest {

    @Test
    void parsesJsonBody() {
        BodyParameters params = new BodyParameters("{\"name\":\"Ada\",\"count\":3}", ContentType.JSON, headers("application/json"), null, false);

        assertEquals("Ada", params.getString("name"));
        assertEquals(3, params.getInt("count"));
        assertTrue(params.isValid());
        assertTrue(params.checkAllPresent(List.of("name", "count")));
    }

    @Test
    void parsesUrlEncodedBody() {
        BodyParameters params = new BodyParameters("name=Ada+Lovelace&city=Waynesboro", ContentType.URL_ENCODED, headers("application/x-www-form-urlencoded"), null, false);

        assertEquals("Ada Lovelace", params.getString("name"));
        assertEquals("Waynesboro", params.getString("city"));
    }

    @Test
    void parsesGetUrlQuery() {
        BodyParameters params = new BodyParameters("/api/search?query=vector+db&limit=5", ContentType.GET_URL, headers(null), null, false);

        assertEquals("vector db", params.getString("query"));
        assertEquals(5, params.getInt("limit"));
    }

    @Test
    void parsesMultipartFields() {
        String boundary = "abc123";
        String body = "--abc123\r\n"
                + "Content-Disposition: form-data; name=\"title\"\r\n\r\n"
                + "Hello\r\n"
                + "--abc123\r\n"
                + "Content-Disposition: form-data; name=\"body\"\r\n\r\n"
                + "World\r\n"
                + "--abc123--\r\n";

        BodyParameters params = new BodyParameters(body, ContentType.FORM_DATA, headers("multipart/form-data; boundary=" + boundary), null, false);

        assertEquals("Hello", params.getString("title"));
        assertEquals("World", params.getString("body"));
    }

    @Test
    void textBodyRemainsRawForCustomResources() {
        BodyParameters params = new BodyParameters("custom protocol payload", ContentType.TEXT, headers("text/plain"), null, false);
        InboundRequest request = new InboundRequest(null, params, headers("text/plain"));

        assertEquals("custom protocol payload", params.text());
        assertEquals("custom protocol payload", request.getBody());
        assertEquals("custom protocol payload", request.body());
        assertFalse(params.present("custom"));
    }

    @Test
    void rejectsMalformedJsonAndMissingRequiredValues() {
        BodyParameters malformed = new BodyParameters("{broken", ContentType.JSON, headers("application/json"), null, false);
        assertFalse(malformed.isValid());

        BodyParameters missing = new BodyParameters("{\"name\":\"Ada\"}", ContentType.JSON, headers("application/json"), null, false);
        assertFalse(missing.checkAllPresent(List.of("name", "count")));
    }

    private static RequestHeaders headers(String contentType) {
        Headers headers = new Headers();
        if(contentType != null) headers.add("Content-Type", contentType);
        return new RequestHeaders(headers);
    }
}
