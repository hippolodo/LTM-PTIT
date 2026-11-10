package com.randomconnect;

import com.fasterxml.jackson.databind.*;
import com.randomconnect.auth.*;
import com.randomconnect.service.SocialService;
import java.net.*;
import java.net.http.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.web.server.LocalServerPort;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class DirectCallSocketTest {
 @LocalServerPort int port;
 @Autowired AuthService auth;
 @Autowired SocketTickets tickets;
 @Autowired SocialService social;
 @Autowired ObjectMapper json;

 private Actor user() throws Exception {
  String name="call"+UUID.randomUUID().toString().replace("-", "").substring(0,12);
  var grant=auth.register(name,name+"@example.invalid","Password123!",name);
  var claims=json.readTree(Base64.getUrlDecoder().decode(grant.accessToken().split("\\.")[1]));
  return new Actor("USER",grant.user().id(),claims.path("sid").asText());
 }

 class Client implements WebSocket.Listener,AutoCloseable {
  final HttpClient client=HttpClient.newHttpClient();
  final BlockingQueue<JsonNode> inbox=new LinkedBlockingQueue<>();
  final StringBuilder fragments=new StringBuilder();
  final WebSocket socket;
  Client(Actor actor) {
   String ticket=tickets.issue(actor);
   socket=client.newWebSocketBuilder().header("Origin","http://localhost:5173")
    .buildAsync(URI.create("ws://localhost:"+port+"/ws?ticket="+ticket),this).join();
  }
  public void onOpen(WebSocket socket) { socket.request(1); }
  public CompletionStage<?> onText(WebSocket socket,CharSequence text,boolean last) {
   fragments.append(text);
   if(last){try{inbox.add(json.readTree(fragments.toString()));}catch(Exception e){throw new IllegalStateException(e);}fragments.setLength(0);}
   socket.request(1);return CompletableFuture.completedFuture(null);
  }
  void send(String type,String id,Object payload)throws Exception {
   var message=new LinkedHashMap<String,Object>();message.put("type",type);message.put("payload",payload);
   if(id!=null)message.put("roomId",id);
   socket.sendText(json.writeValueAsString(message),true).join();
  }
  JsonNode event(String type)throws Exception {
   long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(8);
   while(System.nanoTime()<deadline){var message=inbox.poll(1,TimeUnit.SECONDS);if(message!=null&&type.equals(message.path("type").asText()))return message;}
   throw new AssertionError("Missing "+type);
  }
  public void close(){socket.abort();client.close();}
 }

 @Test void realAuthenticatedSocketsRingAcceptSignalAndHangupWithoutClosingChat() throws Exception {
  var a=user();var b=user();String conversation=(String)social.open(a,b.id()).get("id");
  try(var first=new Client(a);var second=new Client(b)) {
   first.event("SESSION_READY");second.event("SESSION_READY");
   first.send("CALL_REGISTER",null,Map.of());second.send("CALL_REGISTER",null,Map.of());
   first.event("CALL_READY");second.event("CALL_READY");
   String id=UUID.randomUUID().toString();first.send("CALL_INVITE",id,Map.of("conversationId",conversation));
   assertThat(first.event("CALL_RINGING").path("roomId").asText()).isEqualTo(id);
   assertThat(second.event("CALL_INCOMING").path("payload").path("peer").path("id").asText()).isEqualTo(a.id());
   second.send("CALL_ACCEPT",id,Map.of());
   assertThat(first.event("CALL_ACCEPTED").path("payload").path("initiator").asBoolean()).isTrue();
   assertThat(second.event("CALL_ACCEPTED").path("payload").path("initiator").asBoolean()).isFalse();
   first.send("FIND_PARTNER",null,Map.of());assertThat(first.event("ERROR").path("payload").path("code").asText()).isEqualTo("ALREADY_MATCHED");
   first.send("CALL_OFFER",id,Map.of("sdp",Map.of("type","offer","sdp","v=0")));second.event("CALL_OFFER");
   second.send("CALL_ANSWER",id,Map.of("sdp",Map.of("type","answer","sdp","v=0")));first.event("CALL_ANSWER");
   first.send("CALL_ICE",id,Map.of("candidate",Map.of("candidate","candidate:test")));second.event("CALL_ICE");
   second.send("CALL_END",id,Map.of());first.event("CALL_ENDED");second.event("CALL_ENDED");
   social.send(a,conversation,"chat continues",UUID.randomUUID().toString());
   assertThat((List<?>)social.messages(b,conversation,null,null).get("items")).hasSize(1);
  }
 }
}
