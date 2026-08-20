/*
 * -------------------------------------------------------------------------
 * 
 * Copyright (c) 2026 Fortrow LLC. All rights reserved.
 *  
 * NOTICE: All information contained herein is, and remains the property of 
 * Fortrow, LLC. The intellectual and technical concepts contained herein are 
 * proprietary to [Company Name] and may be covered by U.S. and Foreign Patents, 
 * patents in process, and are protected by trade secret or copyright law.
 * 
 * Dissemination of this information or reproduction of this material is 
 * strictly forbidden unless prior written permission is obtained from 
 * Fortrow, LLC.
 * 
 * -------------------------------------------------------------------------
 * 
 * File created by Ryan Fitzgerald (ryan@fortrow.com) on 2026-05-11 20:33:09 EDT
 * 
 * -------------------------------------------------------------------------

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
