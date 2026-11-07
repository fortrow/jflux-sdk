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

package com.fortrow.jflux.exceptions.reporting.types;

import com.fortrow.jflux.exceptions.ExceptionHandling;
import com.fortrow.jflux.exceptions.types.ReportedException;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/** JSON HTTP error sink with optional bearer/API-key authentication. */
public final class HttpEndpointReporter {

    private static volatile Configuration configuration = new Configuration(
            URI.create("https://errors.example.invalid/jflux"), "placeholder-api-key", false);
    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    private HttpEndpointReporter() { }

    public record Configuration(URI endpoint, String apiKey, boolean enabled) {
        public Configuration {
            if (endpoint == null) throw ExceptionHandling.invalid("HTTP endpoint is required.");
        }
    }

    public static void setConfiguration(Configuration value) {
        configuration = value == null ? new Configuration(URI.create("https://errors.example.invalid/jflux"), "placeholder-api-key", false) : value;
    }

    public static void setConfiguration(String endpoint, String apiKey) {
        URI uri = ReporterSupport.uri(endpoint);
        if (uri == null) throw ExceptionHandling.invalid("Invalid HTTP endpoint: " + endpoint);
        setConfiguration(new Configuration(uri, apiKey, true));
    }

    public static boolean hasConfiguration() {
        Configuration value = configuration;
        return value.enabled
                && ("http".equalsIgnoreCase(value.endpoint.getScheme()) || "https".equalsIgnoreCase(value.endpoint.getScheme()))
                && ReporterSupport.usable(value.endpoint.toString())
                && ReporterSupport.usable(value.apiKey);
    }

    public static boolean report(ReportedException exception) {
        if (!hasConfiguration() || exception == null) return false;
        Configuration value = configuration;
        try {
            HttpRequest.Builder request = HttpRequest.newBuilder(value.endpoint)
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + value.apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(ReporterSupport.payload(exception)));
            HttpResponse<Void> response = CLIENT.send(request.build(), HttpResponse.BodyHandlers.discarding());
            return response.statusCode() >= 200 && response.statusCode() < 300;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        } catch (Exception ignored) {
            return false;
        }
    }
}
