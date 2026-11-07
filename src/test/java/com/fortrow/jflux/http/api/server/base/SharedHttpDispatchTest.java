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

package com.fortrow.jflux.http.api.server.base;

import com.fortrow.jflux.http.api.server.config.APIServerConfig;
import com.fortrow.jflux.http.api.server.containers.APIResponse;
import com.fortrow.jflux.http.api.server.containers.ResponseCode;
import com.fortrow.jflux.http.api.server.resources.APIResource;
import com.fortrow.jflux.http.api.server.types.APIServerType;
import com.fortrow.jflux.http.api.types.DatabaseServerType;
import com.fortrow.jflux.http.api.base.endpoints.InboundRequest;
import com.sun.net.httpserver.HttpServer;
import java.io.*;
import java.net.*;
import java.net.http.*;
import java.time.Instant;
import java.util.*;
import org.json.simple.JSONObject;
import org.junit.jupiter.api.Test;
import tech.kwik.flupke.server.HttpRequestHandler;
import tech.kwik.flupke.server.HttpServerRequest;
import tech.kwik.flupke.server.HttpServerResponse;
import static org.junit.jupiter.api.Assertions.*;

class SharedHttpDispatchTest {
    @Test void http1AndHttp3AdaptersShareResourceValidationAndExecution() throws Throwable {
        var config = new APIServerConfig("127.0.0.1", 0, "/api")
                .setServerType(APIServerType.REST_SYNC).setSessionDbType(DatabaseServerType.PROCESS_MEMORY)
                .apiIsPublic().setMaxRequestBodyBytes(1024);
        var api = new RESTfulAPI(config, null);
        api.addResource("echo", "POST", new String[] {"value"}, new APIResource() {
            @Override public APIResponse execute(InboundRequest request) {
                JSONObject response = new JSONObject(); response.put("value", request.getParams().get("value"));
                return new APIResponse(request.getSession(), response, ResponseCode.SUCCESSFUL, false);
            }
        });
        api.start();
        try {
            var server = api.getServer();
            var listenerField = APIServer.class.getDeclaredField("httpServer"); listenerField.setAccessible(true);
            int port = ((HttpServer) listenerField.get(server)).getAddress().getPort();
            var handlerType = Class.forName(APIServer.class.getName() + "$APIServerHandler");
            var constructor = handlerType.getDeclaredConstructor(APIServer.class); constructor.setAccessible(true);
            var http3 = (HttpRequestHandler) constructor.newInstance(server);
            for (String body : List.of("{\"value\":\"checked\"}", "{}", "malformed", "x".repeat(1025))) {
                var response1 = HttpClient.newHttpClient().send(HttpRequest.newBuilder(
                        URI.create("http://127.0.0.1:" + port + "/api/echo")).header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
                var response3 = new CapturedResponse();
                http3.handleRequest(request("/api/echo", body), response3);
                assertEquals(response1.statusCode(), response3.status(), body);
                assertEquals(response1.body(), response3.output.toString(java.nio.charset.StandardCharsets.UTF_8), body);
            }
        } finally { api.stop(); }
    }
    private HttpServerRequest request(String path, String body) {
        return new HttpServerRequest() {
            public String method() { return "POST"; }
            public String path() { return path; }
            public HttpHeaders headers() { return HttpHeaders.of(Map.of("Content-Type", List.of("application/json")), (a,b) -> true); }
            public String authority() { return "localhost"; }
            public InputStream body() { return new ByteArrayInputStream(body.getBytes(java.nio.charset.StandardCharsets.UTF_8)); }
            public InetAddress clientAddress() { return InetAddress.getLoopbackAddress(); }
            public InetSocketAddress remoteAddress() { return new InetSocketAddress(clientAddress(), 12345); }
            public Instant time() { return Instant.now(); }
        };
    }
    private static class CapturedResponse implements HttpServerResponse {
        final ByteArrayOutputStream output = new ByteArrayOutputStream(); int status;
        final Map<String,List<String>> headers = new HashMap<>();
        public void setStatus(int value) { status = value; }
        public void addHeader(String name, String value) { headers.put(name, List.of(value)); }
        public void addHeader(String name, List<String> value) { headers.put(name, value); }
        public void setHeaders(HttpHeaders value) { headers.putAll(value.map()); }
        public OutputStream getOutputStream() { return output; }
        public int status() { return status; }
        public boolean isStatusSet() { return status != 0; }
        public long size() { return output.size(); }
    }
}
