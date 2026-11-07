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
 * File created by Ryan Fitzgerald (ryan@fortrow.com) on 2026-05-04 15:27:04 EDT
 */
package com.fortrow.jflux.http.api.server.containers;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public class ClientInfo {

    private final String remoteIp;
    private final AtomicReference<List<String>> remoteHostnames;
    private final AtomicReference<List<String>> userAgents;
    private final AtomicBoolean hostnameChanged = new AtomicBoolean(false);
    private final AtomicBoolean userAgentChanged = new AtomicBoolean(false);
    private final AtomicInteger numIpRequests = new AtomicInteger(0);

    public ClientInfo(RequestHeaders headers, String remoteIp, String remoteHostname) {
        if(remoteIp == null || remoteIp.isBlank()) {
            throw new IllegalArgumentException("Remote IP address is required for API client sessions.");
        }
        this.remoteIp = remoteIp;
        this.remoteHostnames = new AtomicReference<>(immutableList(remoteHostname));
        this.userAgents = new AtomicReference<>(immutableList(headers == null ? null : headers.getString("User-Agent")));
    }

    public boolean recordHostname(String remoteHostname) {
        return record(remoteHostnames, hostnameChanged, remoteHostname);
    }

    public boolean recordUserAgent(String userAgent) {
        return record(userAgents, userAgentChanged, userAgent);
    }

    public int getNumIpRequests() {
        return numIpRequests.get();
    }

    public String getLastUserAgent() {
        return lastOrEmpty(userAgents.get());
    }

    public String getUserAgent(int index) {
        return userAgents.get().get(index);
    }

    public int numUserAgentEntries() {
        return userAgents.get().size();
    }

    public String getRemoteIp() {
        return remoteIp;
    }

    public void incrementIpRequests() {
        numIpRequests.incrementAndGet();
    }

    public String getLastHostname() {
        return lastOrEmpty(remoteHostnames.get());
    }

    public String getHostname(int index){
        return remoteHostnames.get().get(index);
    }

    public int numHostnameEntries() {
        return remoteHostnames.get().size();
    }

    public boolean hasHostnameChanged() {
        return hostnameChanged.get();
    }

    public boolean hasUserAgentChanged() {
        return userAgentChanged.get();
    }

    public boolean hasTelemetryChanges() {
        return hasHostnameChanged() || hasUserAgentChanged();
    }

    private static boolean record(AtomicReference<List<String>> ref, AtomicBoolean changed, String value) {
        if(value == null || value.isBlank()) return true;
        while(true) {
            List<String> current = ref.get();
            if(current.contains(value)) return true;
            ArrayList<String> next = new ArrayList<>(current);
            next.add(value);
            if(ref.compareAndSet(current, Collections.unmodifiableList(next))) {
                changed.set(true);
                return false;
            }
        }
    }

    private static List<String> immutableList(String value) {
        if(value == null || value.isBlank()) return Collections.emptyList();
        return Collections.unmodifiableList(new ArrayList<>(List.of(value)));
    }

    private static String lastOrEmpty(List<String> values) {
        if(values.isEmpty()) return "";
        return values.get(values.size() - 1);
    }

}
