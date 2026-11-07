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
package com.fortrow.jflux.http.api.base.endpoints;

import com.fortrow.jflux.http.api.server.types.RequestType;

import java.util.EnumSet;

public class ApiEndpointAccepts {

    private final EnumSet<RequestType> requestTypes = EnumSet.noneOf(RequestType.class);

    public ApiEndpointAccepts(RequestType...requestTypes) {
        if(requestTypes == null) return;
        for(RequestType requestType : requestTypes) {
            if(requestType != null) this.requestTypes.add(requestType);
        }
    }

    public static ApiEndpointAccepts crud() {
        return new ApiEndpointAccepts(RequestType.GET, RequestType.POST, RequestType.PUT, RequestType.PATCH, RequestType.DELETE);
    }

    public boolean accepts(RequestType requestType) {
        return requestTypes.contains(requestType);
    }

    public boolean isEmpty() {
        return requestTypes.isEmpty();
    }

}
