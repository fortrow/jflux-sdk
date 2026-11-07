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

package com.fortrow.jflux.http.api.session;
import org.junit.jupiter.api.Test;
import org.json.simple.JSONObject;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
class CheckpointRecoveryTest {
    final String key="a".repeat(64),secret="b".repeat(64),psk="c".repeat(64),root="d".repeat(64);
    @Test void authenticatedForwardCheckpointAndBusy()throws Exception {
        long now=System.currentTimeMillis();var r=CheckpointRecovery.request(key,secret,psk,now);
        assertTrue(CheckpointRecovery.authorized(r,key,secret,psk,now));
        var ready=CheckpointRecovery.seal(r,secret,psk,root,0,UUID.randomUUID().toString(),now);
        assertEquals(root,CheckpointRecovery.open(r,ready,secret,psk,now).orElseThrow());
        var busy=CheckpointRecovery.seal(r,secret,psk,root,1,UUID.randomUUID().toString(),now);
        assertTrue(CheckpointRecovery.open(r,busy,secret,psk,now).isEmpty());
        assertFalse(busy.toJSONString().contains(root));
    }
    @Test void wrongCredentialExpiredRequestAndReplyAreRejected()throws Exception {
        long now=System.currentTimeMillis();var r=CheckpointRecovery.request(key,secret,psk,now);
        assertFalse(CheckpointRecovery.authorized(r,key,"x".repeat(64),psk,now));
        assertFalse(CheckpointRecovery.authorized(r,"other",secret,psk,now));
        assertFalse(CheckpointRecovery.authorized(r,key,secret,psk,now+31000));
        var reply=CheckpointRecovery.seal(r,secret,psk,root,0,UUID.randomUUID().toString(),now);
        assertThrows(Exception.class,()->CheckpointRecovery.open(r,reply,secret,psk,now+31000));
        assertThrows(Exception.class,()->CheckpointRecovery.open(CheckpointRecovery.request(key,secret,psk,now),reply,secret,psk,now));
        assertThrows(Exception.class,()->CheckpointRecovery.open(r,reply,secret,"x".repeat(64),now));
    }
    @Test void tamperingAndExtraFieldsAreRejected()throws Exception {
        long now=System.currentTimeMillis();var r=CheckpointRecovery.request(key,secret,psk,now);
        var reply=CheckpointRecovery.seal(r,secret,psk,root,0,UUID.randomUUID().toString(),now);
        reply.put("iv","0".repeat(24));assertThrows(Exception.class,()->CheckpointRecovery.open(r,reply,secret,psk,now));
        r.put("extra",true);assertFalse(CheckpointRecovery.authorized(r,key,secret,psk,now));
    }
}
