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
