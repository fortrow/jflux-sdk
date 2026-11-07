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

package com.fortrow.jflux.http.api.client;
import com.fortrow.jflux.http.api.base.prereqs.ApiService;
import com.fortrow.jflux.http.api.base.endpoints.InboundRequest;
import com.fortrow.jflux.http.api.server.base.*;
import com.fortrow.jflux.http.api.server.config.*;
import com.fortrow.jflux.http.api.server.containers.*;
import com.fortrow.jflux.http.api.server.resources.APIResource;
import com.fortrow.jflux.http.api.server.encryption.APIKeys;
import com.fortrow.jflux.http.api.server.types.APIServerType;
import com.fortrow.jflux.http.api.types.DatabaseServerType;
import org.json.simple.JSONObject;import org.junit.jupiter.api.*;import org.junit.jupiter.api.io.TempDir;
import javax.net.ssl.*;import java.nio.file.*;import java.security.KeyStore;import java.util.*;import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;
class SessionReportingControlIntegrationTest {
 @TempDir Path root;
 @Test void metadataIsEncryptedSessionScopedAndRevokeDoesNotTouchOtherClients()throws Throwable {
  Path identity=root.resolve("identity.p12");String password="fixture-password";
  var process=new ProcessBuilder(Path.of(System.getProperty("java.home"),"bin/keytool").toString(),"-genkeypair","-alias","fixture","-keyalg","RSA","-keysize","2048","-dname","CN=localhost","-ext","SAN=dns:localhost","-validity","1","-storetype","PKCS12","-keystore",identity.toString(),"-storepass",password,"-noprompt").redirectErrorStream(true).redirectOutput(root.resolve("keytool.log").toFile()).start();
  assertTrue(process.waitFor(20,TimeUnit.SECONDS));assertEquals(0,process.exitValue());
  KeyStore ks=KeyStore.getInstance("PKCS12");try(var in=Files.newInputStream(identity)){ks.load(in,password.toCharArray());}
  var kmf=KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());kmf.init(ks,password.toCharArray());var tmf=TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());tmf.init(ks);SSLContext tls=SSLContext.getInstance("TLS");tls.init(kmf.getKeyManagers(),tmf.getTrustManagers(),null);
  var config=new APIServerConfig("127.0.0.1",0,"/api").useHttp1().useTls().setClientAuth(true).useDynamicApiKey(true).setSessionDb(DatabaseServerType.SQLITE,new com.fortrow.jflux.db.connectors.details.DbConnectionSqlLiteDetails(root.resolve("sessions.db").toString())).setSessionResumeStorePath(root.resolve("resume.json")).setServerType(APIServerType.ENCRYPTED_SYNC).setRequestsPerMinute(1000).setActiveSessionRecoveryEnabled(false);config.setApiServerSslContext(tls);
  var service=new ApiService("session-report-test",ApiService.generateAppKey("session-report-test","fixture"));service.configureDatabases(config);var a=service.registerApiKeys(APIKeys.generateKeys());var b=service.registerApiKeys(APIKeys.generateKeys());
  config.setPeerIdentityAuthorizer((key,peer)->peer!=null&&(key.equals(a.getKey())||key.equals(b.getKey())));
  var api=new EncryptedAPI(config,service);
  api.addResource("inspect","POST",new APIResource(){public APIResponse execute(InboundRequest r){var body=new JSONObject();body.put("request_status","success");body.put("session_id",r.getSession().getSession().getSessionId());body.put("metadata",new JSONObject(r.getSession().getSession().getReportedMetadata()));body.put("resource_path",r.getResourcePath());return new APIResponse(r.getSession(),body,ResponseCode.SUCCESSFUL);}});
  api.start();
  try {
   var field=APIServer.class.getDeclaredField("httpsServer");field.setAccessible(true);int port=((com.sun.net.httpserver.HttpsServer)field.get(api.getServer())).getAddress().getPort();var mtls=MtlsConfig.pkcs12(identity,password.toCharArray(),identity,password.toCharArray());
   try(var ca=new EncryptedClient(a.getKey(),a.getSecret(),a.getRawSessionSecret(),a.getPsk(),"https://localhost:"+port+"/api",HttpVersion.HTTP_1,false,mtls).setSessionMetadata(Map.of("machine_hostname","host-a","client_application","fortrow-cli","client_type","cli")).build();var cb=new EncryptedClient(b.getKey(),b.getSecret(),b.getRawSessionSecret(),b.getPsk(),"https://localhost:"+port+"/api",HttpVersion.HTTP_1,false,mtls).build()) {
    ca.beginSessionOrThrow("/session/init","/session/close");cb.beginSessionOrThrow("/session/init","/session/close");
    var first=ca.post("/inspect",new JSONObject()).getBodyObject();String id=(String)first.get("session_id");assertEquals("inspect",first.get("resource_path"));assertEquals("host-a",((Map<?,?>)first.get("metadata")).get("machine_hostname"));
    assertTrue(((Map<?,?>)cb.post("/inspect",new JSONObject()).getBodyObject().get("metadata")).isEmpty());
    assertFalse(api.getServer().revokeSessionByKeyHash(com.fortrow.jflux.crypto.builtin.SHA.get256(b.getKey()),id));assertEquals(200,ca.post("/inspect",new JSONObject()).getStatus());
    assertTrue(api.getServer().revokeSessionByKeyHash(com.fortrow.jflux.crypto.builtin.SHA.get256(a.getKey()),id));
    boolean rejected=false;try{rejected=ca.post("/inspect",new JSONObject()).getStatus()!=200;}catch(com.fortrow.jflux.http.api.server.exceptions.APIException expected){rejected=true;}assertTrue(rejected);
    assertEquals(200,cb.post("/inspect",new JSONObject()).getStatus());cb.closeSessionOrThrow(false);
   }
  }finally{api.stop();}
 }
}
