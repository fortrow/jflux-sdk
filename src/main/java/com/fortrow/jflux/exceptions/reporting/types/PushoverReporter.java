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

import com.fortrow.jflux.exceptions.types.ReportedException;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.net.URLEncoder;

/** Pushover notification reporter. Disabled until real app and user keys are supplied. */
public final class PushoverReporter {

    private static final URI ENDPOINT = URI.create("https://api.pushover.net/1/messages.json");
    private static volatile Configuration configuration = new Configuration("placeholder-app-token", "placeholder-user-key", false);
    private static final HttpClient CLIENT = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    private PushoverReporter() { }

    public record Configuration(String applicationToken, String userKey, boolean enabled) { }

    public static void setConfiguration(String applicationToken, String userKey) {
        configuration = new Configuration(applicationToken, userKey, true);
    }

    public static void disable() {
        configuration = new Configuration("placeholder-app-token", "placeholder-user-key", false);
    }

    public static boolean hasConfiguration() {
        Configuration value = configuration;
        return value.enabled && ReporterSupport.usable(value.applicationToken) && ReporterSupport.usable(value.userKey);
    }

    public static boolean report(ReportedException exception) {
        if (!hasConfiguration() || exception == null) return false;
        Configuration value = configuration;
        String body = "token=" + encode(value.applicationToken)
                + "&user=" + encode(value.userKey)
                + "&title=" + encode("jflux error")
                + "&message=" + encode(exception.reportText());
        try {
            HttpRequest request = HttpRequest.newBuilder(ENDPOINT)
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<Void> response = CLIENT.send(request, HttpResponse.BodyHandlers.discarding());
            return response.statusCode() >= 200 && response.statusCode() < 300;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        } catch (Exception ignored) {
            return false;
        }
    }

    private static String encode(String value) {
        return URLEncoder.encode(String.valueOf(value), StandardCharsets.UTF_8);
    }
}
