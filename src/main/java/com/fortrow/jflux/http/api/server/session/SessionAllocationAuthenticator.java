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

package com.fortrow.jflux.http.api.server.session;

import com.fortrow.jflux.http.api.server.containers.ClientInfo;
import com.fortrow.jflux.http.api.server.exceptions.APIException;

/**
 * @deprecated The encrypted allocation flow is now credential-blind and uses
 * {@link SessionAllocationAssertion}. The external authentication backend must
 * sign the challenge with the API key's private allocation secret. This type
 * remains only as a source-compatibility bridge for applications migrating
 * from the earlier 1.1.1 preview.
 */
@Deprecated
@FunctionalInterface
public interface SessionAllocationAuthenticator {

    /**
     * Legacy callback retained for source compatibility. JFlux no longer calls
     * this method from {@code session/allocate}.
     */
    boolean authenticate(ChallengeResponse response, ClientInfo clientInfo) throws APIException;

    /** Values supplied by the client after the server issues a challenge. */
    record ChallengeResponse(
            String username,
            String challengeId,
            String challenge,
            String challengeResponse,
            String mfaResponse
    ) { }
}
