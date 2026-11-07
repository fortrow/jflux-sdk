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
 * File created by Ryan Fitzgerald (ryan@fortrow.com) on 2026-05-11 20:33:09 EDT
 */
package com.fortrow.jflux.http.api.base.endpoints.expectations;

import com.fortrow.jflux.http.api.base.endpoints.InboundRequest;
import com.fortrow.jflux.http.api.server.containers.BodyParameters;
import com.fortrow.jflux.http.api.server.containers.RequestHeaders;
import com.fortrow.jflux.http.api.server.types.ContentType;
import com.sun.net.httpserver.Headers;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApiExpectationsTest {

    @Test
    void validatesBodyHeadersAndCookies() {
        Headers rawHeaders = new Headers();
        rawHeaders.add("X-Trace-Id", "trace-1");
        rawHeaders.add("Cookie", "sid=abc; theme=light");

        BodyParameters params = new BodyParameters("{\"sql\":\"select 1\",\"parameters\":[]}", ContentType.JSON, null, null, false);
        InboundRequest request = new InboundRequest(null, params, new RequestHeaders(rawHeaders));

        ApiExpectations expectations = new ApiExpectations()
                .nonBlankBody("sql")
                .body("parameters")
                .nonBlankHeaders("X-Trace-Id")
                .cookies("sid");

        assertTrue(expectations.validate(request).isValid());
        assertFalse(new ApiExpectations().body("missing").validate(request).isValid());
        assertFalse(new ApiExpectations().cookies("missing").validate(request).isValid());
    }

    @Test
    void bodyExpectationsCoverUrlEncodedAndFormStyleParameters() {
        BodyParameters params = new BodyParameters("name=Ada&score=100", ContentType.URL_ENCODED, null, null, false);
        InboundRequest request = new InboundRequest(null, params, null);

        assertTrue(new ApiExpectations().nonBlankBody("name", "score").validate(request).isValid());
    }
}
