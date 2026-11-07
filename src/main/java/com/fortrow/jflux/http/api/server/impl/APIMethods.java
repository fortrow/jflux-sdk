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
 * File created by Ryan Fitzgerald (ryan@fortrow.com) on 2026-05-08 18:14:41 EDT
 */
package com.fortrow.jflux.http.api.server.impl;

import com.fortrow.jflux.http.api.base.prereqs.ApiService;
import com.fortrow.jflux.http.api.server.containers.ClientSession;
import com.fortrow.jflux.http.api.server.containers.BodyParameters;
import com.fortrow.jflux.http.api.server.containers.RequestHeaders;
import com.fortrow.jflux.http.api.server.encryption.APIKeys;
import com.fortrow.jflux.http.api.server.resources.APIResource;
import com.fortrow.jflux.http.api.server.types.ContentType;

import java.util.HashMap;

public interface APIMethods {
    boolean isAuthenticated(RequestHeaders headers, APIResource resource, BodyParameters params, ClientSession clientSession);
    boolean inTimeout(ClientSession clientSession, int perMinute);
    boolean inBlacklist(ClientSession clientSession);
    APIKeys lookupApiKey(String apiKey);
    HashMap<String, String> getParameters(String body, ContentType contentType);
    void addToBlacklist(ClientSession clientSession, ApiService.BlacklistEntryType entryType);
    void removeFromBlacklist(ClientSession clientSession);
    void addToTimeout(ClientSession clientSession, ApiService.BlacklistEntryType entryType);
    void removeFromTimeout(ClientSession clientSession);
    void lockApiKey(ClientSession clientSession, String reason);
    void lockApiKey(APIKeys apiKeys, String reason);
}
