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

import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import javax.crypto.*;
import javax.crypto.spec.*;
import org.json.simple.*;

/** Read-only recovery; never resets keys, retires sessions or replays application requests. */
public final class CheckpointRecovery {
    public static final String RESOURCE = "session/recover-checkpoint";
    private static final String DOMAIN = "jflux:checkpoint-recovery:v1";
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final long WINDOW = 30000;
    public static JSONObject request(String key, String secret, String psk, long now) throws GeneralSecurityException {
        byte[] nonce=new byte[32]; RANDOM.nextBytes(nonce);
        JSONObject r=new JSONObject();r.put("version",1L);r.put("key",key);
        r.put("nonce",HexFormat.of().formatHex(nonce));r.put("at_ms",now);
        r.put("proof",HexFormat.of().formatHex(mac(derive(secret,psk,"request"),canonical(r))));return r;
    }
    public static boolean authorized(JSONObject r,String key,String secret,String psk,long now) throws GeneralSecurityException {
        if(r==null || !r.keySet().equals(Set.of("version","key","nonce","at_ms","proof"))
                || !Long.valueOf(1).equals(r.get("version")) || !key.equals(r.get("key"))
                || !(r.get("nonce") instanceof String nonce) || !nonce.matches("[a-f0-9]{64}")
                || !(r.get("at_ms") instanceof Long at) || at<now-WINDOW || at>now+WINDOW
                || !(r.get("proof") instanceof String proof) || !proof.matches("[a-f0-9]{64}"))return false;
        return MessageDigest.isEqual(HexFormat.of().parseHex(proof),mac(derive(secret,psk,"request"),canonical(r)));
    }
    public static JSONObject seal(JSONObject request,String secret,String psk,String root,int active,String epoch,long now) throws GeneralSecurityException {
        JSONObject body=new JSONObject();body.put("state",active==0?"ready":"busy");body.put("at_ms",now);body.put("server_epoch",epoch);
        if(active==0)body.put("checkpoint",root);
        byte[] iv=new byte[12];RANDOM.nextBytes(iv);
        Cipher c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.ENCRYPT_MODE,new SecretKeySpec(derive(secret,psk,"response"),"AES"),new GCMParameterSpec(128,iv));c.updateAAD(canonical(request));
        JSONObject out=new JSONObject();out.put("request_status","success");out.put("iv",HexFormat.of().formatHex(iv));
        out.put("sealed",Base64.getEncoder().encodeToString(c.doFinal(body.toJSONString().getBytes(StandardCharsets.UTF_8))));return out;
    }
    public static Optional<String> open(JSONObject request,JSONObject reply,String secret,String psk,long now) throws GeneralSecurityException {
        try {
            long requested=(Long)request.get("at_ms");
            if(now<requested-WINDOW || now>requested+WINDOW || reply==null
                    || !reply.keySet().equals(Set.of("request_status","iv","sealed")) || !"success".equals(reply.get("request_status")))throw new GeneralSecurityException();
            String iv=(String)reply.get("iv"),sealed=(String)reply.get("sealed");
            if(iv==null || !iv.matches("[a-f0-9]{24}") || sealed==null || sealed.length()>4096)throw new GeneralSecurityException();
            Cipher c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.DECRYPT_MODE,new SecretKeySpec(derive(secret,psk,"response"),"AES"),new GCMParameterSpec(128,HexFormat.of().parseHex(iv)));c.updateAAD(canonical(request));
            Object decoded=JSONValue.parseWithException(new String(c.doFinal(Base64.getDecoder().decode(sealed)),StandardCharsets.UTF_8));
            if(!(decoded instanceof JSONObject b) || !(b.get("at_ms") instanceof Long at) || at<requested-WINDOW || at>now+WINDOW
                    || !(b.get("server_epoch") instanceof String epoch) || !UUID.fromString(epoch).toString().equals(epoch))throw new GeneralSecurityException();
            if("busy".equals(b.get("state")) && b.keySet().equals(Set.of("state","at_ms","server_epoch")))return Optional.empty();
            if(!"ready".equals(b.get("state")) || !b.keySet().equals(Set.of("state","at_ms","server_epoch","checkpoint"))
                    || !(b.get("checkpoint") instanceof String root) || root.length()<32 || root.length()>1024 || root.chars().anyMatch(Character::isISOControl))throw new GeneralSecurityException();
            return Optional.of(root);
        }catch(GeneralSecurityException e){throw e;}catch(Exception invalid){throw new GeneralSecurityException("Invalid recovery envelope",invalid);}
    }
    private static byte[] canonical(JSONObject r){return (DOMAIN+"\n"+r.get("version")+"\n"+r.get("key")+"\n"+r.get("nonce")+"\n"+r.get("at_ms")).getBytes(StandardCharsets.UTF_8);}
    private static byte[] derive(String secret,String psk,String purpose)throws GeneralSecurityException {
        if(secret==null || secret.length()<32 || psk==null || psk.length()<32)throw new GeneralSecurityException("Recovery credentials unavailable");
        return mac(secret.getBytes(StandardCharsets.UTF_8),(DOMAIN+":"+purpose+":"+psk).getBytes(StandardCharsets.UTF_8));
    }
    private static byte[] mac(byte[] key,byte[] input)throws GeneralSecurityException{Mac m=Mac.getInstance("HmacSHA256");m.init(new SecretKeySpec(key,"HmacSHA256"));return m.doFinal(input);}
    private CheckpointRecovery(){}
}
