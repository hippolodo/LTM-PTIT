package com.randomconnect;
import com.fasterxml.jackson.databind.*;
import com.randomconnect.dto.SocketMessage;
import com.randomconnect.service.*;
import com.randomconnect.util.MessageSender;
import org.junit.jupiter.api.*;
import org.springframework.web.socket.*;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
class MatchmakingTest {
 final ObjectMapper mapper=new ObjectMapper();
 MatchmakingService service; final Map<String,List<JsonNode>> events=new ConcurrentHashMap<>();
 @BeforeEach void setup() {service=new MatchmakingService(new RoomService(),new MessageSender(mapper),45000);}
 String guest(String id) throws Exception {
  var ws=mock(WebSocketSession.class);when(ws.getId()).thenReturn(id);when(ws.isOpen()).thenReturn(true);
  events.put(id,new CopyOnWriteArrayList<>());
  doAnswer(inv->{events.get(id).add(mapper.readTree(((TextMessage)inv.getArgument(0)).getPayload()));return null;}).when(ws).sendMessage(any());
  service.open(ws);service.flush();return id;
 }
 void send(String id,String type) {send(id,type,null,Map.of());}
 void send(String id,String type,String room,Object payload) {service.accept(id,new SocketMessage(type,null,room,mapper.valueToTree(payload)));service.flush();}
 long count(String id,String type) {return events.get(id).stream().filter(e->e.path("type").asText().equals(type)).count();}
 String room(String id) {return events.get(id).stream().filter(e->e.path("type").asText().equals("MATCH_FOUND")).reduce((a,b)->b).orElseThrow().path("roomId").asText();}
 @Test void findPartner_whenOnlyOne_waits() throws Exception {send(guest("a"),"FIND_PARTNER");assertThat(service.snapshot()).containsEntry("waiting",1).containsEntry("rooms",0);}
 @Test void findPartner_whenTwo_matchesExactlyOnce() throws Exception {guest("a");guest("b");send("a","FIND_PARTNER");send("b","FIND_PARTNER");assertThat(room("a")).isEqualTo(room("b"));assertThat(count("a","MATCH_FOUND")).isEqualTo(1);var a=events.get("a").getLast();var b=events.get("b").getLast();assertThat(a.path("payload").path("initiator").asBoolean()).isNotEqualTo(b.path("payload").path("initiator").asBoolean());}
 @Test void findPartner_whenThree_onePairOneWaiting() throws Exception {for(String id:List.of("a","b","c"))send(guest(id),"FIND_PARTNER");assertThat(service.snapshot()).containsEntry("waiting",1).containsEntry("rooms",1);}
 @Test void findPartner_duplicateRequest_doesNotDuplicate() throws Exception {guest("a");guest("b");send("a","FIND_PARTNER");send("a","FIND_PARTNER");send("b","FIND_PARTNER");send("a","FIND_PARTNER");assertThat(count("a","MATCH_FOUND")).isEqualTo(1);assertThat(service.snapshot()).containsEntry("waiting",0).containsEntry("rooms",1);}
 @Test void concurrentFinds_noDuplicateRoomOrSession() throws Exception {
  for(int i=0;i<80;i++)guest("g"+i);
  try(var pool=Executors.newFixedThreadPool(12)) {var jobs=new ArrayList<Callable<Void>>();for(int i=0;i<80;i++){String id="g"+i;jobs.add(()->{send(id,"FIND_PARTNER");return null;});}for(var f:pool.invokeAll(jobs))f.get();}
  service.flush();assertThat(service.snapshot()).containsEntry("rooms",40).containsEntry("waiting",0);
  for(int i=0;i<80;i++)assertThat(count("g"+i,"MATCH_FOUND")).isEqualTo(1);
 }
 @Test void disconnect_removesWaitingSession() throws Exception {send(guest("a"),"FIND_PARTNER");service.disconnect("a");assertThat(service.snapshot()).containsEntry("waiting",0).containsEntry("sessions",0);}
 @Test void disconnect_closesRoom_andNotifiesPeer() throws Exception {send(guest("a"),"FIND_PARTNER");send(guest("b"),"FIND_PARTNER");service.disconnect("b");service.flush();assertThat(count("a","PARTNER_LEFT")).isEqualTo(1);assertThat(service.snapshot()).containsEntry("rooms",0);}
 void pair() throws Exception {send(guest("a"),"FIND_PARTNER");send(guest("b"),"FIND_PARTNER");}
 @Test void next_closesOldRoom_notifiesPartner_andRequeuesOnlyRequester() throws Exception {pair();send("a","NEXT_PARTNER",room("a"),Map.of());assertThat(count("b","PARTNER_LEFT")).isEqualTo(1);assertThat(service.snapshot()).containsEntry("rooms",0).containsEntry("waiting",1);}
 @Test void stop_doesNotRequeue() throws Exception {pair();send("a","LEAVE_ROOM",room("a"),Map.of());assertThat(service.snapshot()).containsEntry("rooms",0).containsEntry("waiting",0);}
 @Test void chatMessage_outsideRoom_isRejected() throws Exception {guest("a");send("a","CHAT_MESSAGE","fake",Map.of("text","hi"));assertThat(count("a","ERROR")).isEqualTo(1);}
 @Test void chat_bidirectionalExactlyOnce_serverAssignsIdentity() throws Exception {pair();send("a","CHAT_MESSAGE",room("a"),Map.of("text"," hello ","senderId","forged"));send("b","CHAT_MESSAGE",room("b"),Map.of("text","reply"));assertThat(count("a","CHAT_MESSAGE")).isEqualTo(2);assertThat(count("b","CHAT_MESSAGE")).isEqualTo(2);var chat=events.get("b").stream().filter(e->e.path("type").asText().equals("CHAT_MESSAGE")).findFirst().orElseThrow();assertThat(chat.path("payload").path("text").asText()).isEqualTo("hello");assertThat(chat.path("payload").path("senderId").asText()).isNotEqualTo("forged");}
 @Test void simultaneousNext_noDanglingRoom() throws Exception {pair();String old=room("a");try(var pool=Executors.newFixedThreadPool(2)){for(var f:pool.invokeAll(List.<Callable<Void>>of(()->{send("a","NEXT_PARTNER",old,Map.of());return null;},()->{send("b","NEXT_PARTNER",old,Map.of());return null;})))f.get();}assertThat(service.snapshot().get("rooms")*2+service.snapshot().get("waiting")).isEqualTo(2);service.disconnect("a");service.disconnect("b");assertThat(service.snapshot()).containsEntry("rooms",0).containsEntry("waiting",0);}
 @Test void offerAnswerIce_wrongRoom_isRejected() throws Exception {pair();for(String type:List.of("WEBRTC_OFFER","WEBRTC_ANSWER","ICE_CANDIDATE"))send("a",type,"wrong",Map.of());assertThat(count("a","ERROR")).isEqualTo(3);assertThat(count("b","WEBRTC_OFFER")).isZero();}
 @Test void staleIceAfterNext_rejected() throws Exception {pair();String old=room("a");send("a","NEXT_PARTNER",old,Map.of());send("a","ICE_CANDIDATE",old,Map.of("candidate",Map.of("candidate","old")));assertThat(count("a","ERROR")).isEqualTo(1);assertThat(count("b","ICE_CANDIDATE")).isZero();}
 @Test void signaling_forwardsOnlyToPeer() throws Exception {pair();send("a","WEBRTC_OFFER",room("a"),Map.of("sdp",Map.of("type","offer","sdp","v=0")));send("b","WEBRTC_ANSWER",room("b"),Map.of("sdp",Map.of("type","answer","sdp","v=0")));send("a","ICE_CANDIDATE",room("a"),Map.of("candidate",Map.of("candidate","candidate:test")));assertThat(count("b","WEBRTC_OFFER")).isEqualTo(1);assertThat(count("a","WEBRTC_ANSWER")).isEqualTo(1);assertThat(count("b","ICE_CANDIDATE")).isEqualTo(1);assertThat(count("a","ICE_CANDIDATE")).isZero();}
 @Test void invalidTextAndSignal_rejected() throws Exception {pair();for(String text:List.of(" ","x".repeat(2001)))send("a","CHAT_MESSAGE",room("a"),Map.of("text",text));send("a","WEBRTC_OFFER",room("a"),Map.of("sdp",Map.of("type","answer","sdp","v=0")));assertThat(count("a","ERROR")).isEqualTo(3);}
 @Test void staleLifecycleCannotCloseNewRoom() throws Exception {pair();String old=room("a");send("a","NEXT_PARTNER",old,Map.of());send(guest("c"),"FIND_PARTNER");send("a","LEAVE_ROOM",old,Map.of());assertThat(service.snapshot()).containsEntry("rooms",1);assertThat(count("c","PARTNER_LEFT")).isZero();}
 @Test void queuedRelayIsDiscardedAfterRoomClose() throws Exception {pair();service.accept("a",new SocketMessage("CHAT_MESSAGE",null,room("a"),mapper.valueToTree(Map.of("text","late"))));service.accept("a",new SocketMessage("NEXT_PARTNER",null,room("a"),mapper.createObjectNode()));service.flush();assertThat(count("b","CHAT_MESSAGE")).isZero();}
 @Test void rateLimit_doesNotBreakRoom() throws Exception {pair();for(int i=0;i<31;i++)send("a","CHAT_MESSAGE",room("a"),Map.of("text","hello"));assertThat(count("b","CHAT_MESSAGE")).isEqualTo(30);assertThat(events.get("a").getLast().path("payload").path("code").asText()).isEqualTo("RATE_LIMITED");}
 @Test void heartbeatTimeout_cleansAllState() throws Exception {service=new MatchmakingService(new RoomService(),new MessageSender(mapper),-1);pair();service.expire();service.flush();assertThat(service.snapshot()).containsEntry("rooms",0).containsEntry("waiting",0).containsEntry("sessions",0);}
}
