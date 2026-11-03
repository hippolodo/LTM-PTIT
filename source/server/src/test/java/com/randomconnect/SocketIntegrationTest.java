package com.randomconnect;
import com.fasterxml.jackson.databind.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import java.net.*;
import java.net.http.*;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={"logging.level.root=WARN","debug=false"})
class SocketIntegrationTest {
 @LocalServerPort int port;
 @Autowired ObjectMapper mapper;
 class Client implements WebSocket.Listener,AutoCloseable {
  final BlockingQueue<JsonNode> inbox=new LinkedBlockingQueue<>();final StringBuilder buffer=new StringBuilder();WebSocket ws;boolean closed;
  Client(String origin) {ws=HttpClient.newHttpClient().newWebSocketBuilder().header("Origin",origin).buildAsync(URI.create("ws://localhost:"+port+"/ws"),this).join();}
  public void onOpen(WebSocket s) {s.request(1);}
  public CompletionStage<?> onText(WebSocket s,CharSequence text,boolean last) {buffer.append(text);if(last){try{inbox.add(mapper.readTree(buffer.toString()));}catch(Exception e){throw new RuntimeException(e);}buffer.setLength(0);}s.request(1);return CompletableFuture.completedFuture(null);}
  void raw(String text) {ws.sendText(text,true).join();}
  void send(String type,String room,Object payload) throws Exception {var event=new HashMap<String,Object>();event.put("type",type);event.put("payload",payload);if(room!=null)event.put("roomId",room);raw(mapper.writeValueAsString(event));}
  JsonNode event(String type) throws Exception {long until=System.nanoTime()+TimeUnit.SECONDS.toNanos(8);while(System.nanoTime()<until){var m=inbox.poll(1,TimeUnit.SECONDS);if(m!=null && type.equals(m.path("type").asText()))return m;}throw new AssertionError("Missing "+type);}
  public void close(){if(!closed){closed=true;ws.sendClose(1000,"test done").join();}}
 }
 @Test void realSockets_matchChatNextStopDisconnectAndValidate() throws Exception {
  try(var a=new Client("http://localhost:5173");var b=new Client("http://localhost:5173");var c=new Client("http://localhost:5173")) {
   String ga=a.event("SESSION_READY").path("payload").path("guestId").asText();b.event("SESSION_READY");c.event("SESSION_READY");
   a.send("FIND_PARTNER",null,Map.of());a.event("WAITING");b.send("FIND_PARTNER",null,Map.of());String room=a.event("MATCH_FOUND").path("roomId").asText();assertThat(b.event("MATCH_FOUND").path("roomId").asText()).isEqualTo(room);
   c.send("FIND_PARTNER",null,Map.of());c.event("WAITING");
   a.send("CHAT_MESSAGE",room,Map.of("text","hello","senderId","fake"));a.event("CHAT_MESSAGE");var chat=b.event("CHAT_MESSAGE");assertThat(chat.path("payload").path("senderId").asText()).isEqualTo(ga);
   b.send("CHAT_MESSAGE",room,Map.of("text","reply"));a.event("CHAT_MESSAGE");b.event("CHAT_MESSAGE");
   a.send("WEBRTC_OFFER",room,Map.of("sdp",Map.of("type","offer","sdp","v=0")));b.event("WEBRTC_OFFER");b.send("WEBRTC_ANSWER",room,Map.of("sdp",Map.of("type","answer","sdp","v=0")));a.event("WEBRTC_ANSWER");
   a.send("ICE_CANDIDATE",room,Map.of("candidate",Map.of("candidate","candidate:test")));b.event("ICE_CANDIDATE");
   c.send("CHAT_MESSAGE",room,Map.of("text","intruder"));assertThat(c.event("ERROR").path("payload").path("code").asText()).isEqualTo("NOT_IN_ROOM");
   a.send("NEXT_PARTNER",room,Map.of());b.event("PARTNER_LEFT");a.event("ROOM_CLOSED");String next=a.event("MATCH_FOUND").path("roomId").asText();assertThat(c.event("MATCH_FOUND").path("roomId").asText()).isEqualTo(next);assertThat(next).isNotEqualTo(room);
   a.send("ICE_CANDIDATE",room,Map.of("candidate",Map.of("candidate","late")));a.event("ERROR");
   c.send("LEAVE_ROOM",next,Map.of());c.event("ROOM_CLOSED");a.event("PARTNER_LEFT");
   a.send("FIND_PARTNER",null,Map.of());a.event("WAITING");b.send("FIND_PARTNER",null,Map.of());a.event("MATCH_FOUND");b.event("MATCH_FOUND");b.close();a.event("PARTNER_LEFT");
  }
 }
 @Test void oversizedOrMalformedMessage_returnsError() throws Exception {
  try(var a=new Client("http://localhost:5173")){a.event("SESSION_READY");a.raw("not-json");assertThat(a.event("ERROR").path("payload").path("code").asText()).isEqualTo("INVALID_MESSAGE");a.raw("x".repeat(66000));a.event("ERROR");a.raw("{\"type\":42}");a.event("ERROR");a.send("PING",null,Map.of());a.event("PONG");}
 }
 @Test void untrustedOrigin_rejected() {assertThatThrownBy(()->new Client("https://untrusted.invalid")).isInstanceOf(CompletionException.class);}
}
