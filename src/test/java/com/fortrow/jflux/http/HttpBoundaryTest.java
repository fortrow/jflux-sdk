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

package com.fortrow.jflux.http;

import com.fortrow.jflux.exceptions.types.ReportedException;
import com.fortrow.jflux.http.api.client.ResponseHeaders;
import com.fortrow.jflux.http.api.server.config.APIServerConfig;
import com.fortrow.jflux.http.api.server.containers.RequestHeaders;
import com.fortrow.jflux.http.api.server.exceptions.APIException;
import com.sun.net.httpserver.Headers;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class HttpBoundaryTest {

    @Test
    void apiFailuresUseTheReportableExceptionHierarchy() {
        assertEquals(ReportedException.class, APIException.class.getSuperclass());
        assertInstanceOf(ReportedException.class, new APIException("request failed"));
    }

    @Test
    void malformedSecurityTimestampIsRejectedWithoutAParserException() {
        Headers headers = new Headers();
        headers.add("X-Request-Timestamp", "not-a-timestamp");

        assertNull(new RequestHeaders(headers).getInstantFromTimestamp("X-Request-Timestamp"));
    }

    @Test
    void absentResponseHeadersReturnSafeOptionalValues() {
        ResponseHeaders headers = new ResponseHeaders(new org.apache.http.Header[0]);

        assertFalse(headers.contains("X-Missing"));
        assertNull(headers.getString("X-Missing"));
        assertArrayEquals(new String[0], headers.getArr("X-Missing"));
    }

    @Test
    void requestBodyLimitIsClampedAndConfigPathIsNormalized() {
        APIServerConfig config = new APIServerConfig(8443, "api///")
                .setMaxRequestBodyBytes(0);

        assertEquals("/api", config.getApiPath());
        assertEquals(1, config.getMaxRequestBodyBytes());
    }
}
