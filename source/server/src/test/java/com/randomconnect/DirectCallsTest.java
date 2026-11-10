package com.randomconnect;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.randomconnect.auth.*;
import com.randomconnect.dto.SocketMessage;
import com.randomconnect.model.GuestSession;
import com.randomconnect.service.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.web.socket.WebSocketSession;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class DirectCallsTest {
 record Event(String socket, String type, String call, Object payload) {}
 final ObjectMapper json = new ObjectMapper();
 SocialService social;
 DirectCalls calls;
 Map<String,GuestSession> sessions;
 Set<String> waiting;
 List<Event> events;
 DirectCalls.Events sink;
 GuestSession a,b,c;
 String callId, conversation;

 @BeforeEach void setup() {
  social=mock(SocialService.class);
  calls=new DirectCalls(social,new IceConfigService("stun:test.invalid","", ""),mock(IdentityService.class));
  sessions=new LinkedHashMap<>();waiting=new HashSet<>();events=new ArrayList<>();
  sink=(to,type,request,id,payload)->events.add(new Event(to.id,type,id,payload));
  a=session("socket-a","user-a");b=session("socket-b","user-b");c=session("socket-c","user-c");
  conversation=UUID.randomUUID().toString();callId=UUID.randomUUID().toString();
  when(social.callPeer(a.actor,conversation)).thenReturn(Map.of("id",b.guestId,"displayName","B","username","user_b"));
  when(social.callPeer(b.actor,conversation)).thenReturn(Map.of("id",a.guestId,"displayName","A","username","user_a"));
  send(a,"CALL_REGISTER",null,Map.of());send(b,"CALL_REGISTER",null,Map.of());
  events.clear();
 }
 GuestSession session(String socket,String user) {
  var ws=mock(WebSocketSession.class);
  when(ws.getId()).thenReturn(socket);when(ws.isOpen()).thenReturn(true);
  when(ws.getAttributes()).thenReturn(Map.of("actor",new Actor("USER",user,"sid-"+socket)));
  var s=new GuestSession(ws);sessions.put(s.id,s);return s;
 }
 void send(GuestSession who,String type,String id,Object payload) {
  calls.accept(who,new SocketMessage(type,"request",id,json.valueToTree(payload)),sessions,waiting,sink);
 }
 void invite() {send(a,"CALL_INVITE",callId,Map.of("conversationId",conversation));}
 long count(String socket,String type) {return events.stream().filter(e->e.socket.equals(socket)&&e.type.equals(type)).count();}
 String error() {return (String)((Map<?,?>)events.getLast().payload).get("code");}

 @Test void ringsThenRequiresConsentBeforeForwardingSignals() {
  invite();
  assertThat(count(a.id,"CALL_RINGING")).isEqualTo(1);assertThat(count(b.id,"CALL_INCOMING")).isEqualTo(1);
  assertThat(calls.busy(a.actor)).isTrue();assertThat(calls.busy(b.actor)).isTrue();
  send(a,"CALL_OFFER",callId,Map.of("sdp",Map.of("type","offer","sdp","v=0")));
  assertThat(error()).isEqualTo("INVALID_CALL_STATE");assertThat(count(b.id,"CALL_OFFER")).isZero();
  send(b,"CALL_ACCEPT",callId,Map.of());
  assertThat(count(a.id,"CALL_ACCEPTED")).isEqualTo(1);assertThat(count(b.id,"CALL_ACCEPTED")).isEqualTo(1);
  send(a,"CALL_OFFER",callId,Map.of("sdp",Map.of("type","offer","sdp","v=0")));
  send(b,"CALL_ANSWER",callId,Map.of("sdp",Map.of("type","answer","sdp","v=0")));
  send(a,"CALL_ICE",callId,Map.of("candidate",Map.of("candidate","candidate:test")));
  assertThat(count(b.id,"CALL_OFFER")).isEqualTo(1);assertThat(count(a.id,"CALL_ANSWER")).isEqualTo(1);
  assertThat(count(b.id,"CALL_ICE")).isEqualTo(1);
  send(a,"CALL_END",callId,Map.of());
  assertThat(count(b.id,"CALL_ENDED")).isEqualTo(1);assertThat(calls.busy(a.actor)).isFalse();
 }

 @Test void outsiderOtherTabAndWrongSignalRoleAreRejected() {
  invite();
  send(c,"CALL_ACCEPT",callId,Map.of());assertThat(error()).isEqualTo("NOT_IN_CALL");
  var other=session("other-tab",b.guestId);
  send(other,"CALL_ACCEPT",callId,Map.of());assertThat(error()).isEqualTo("NOT_IN_CALL");
  send(a,"CALL_ACCEPT",callId,Map.of());assertThat(error()).isEqualTo("INVALID_CALL_STATE");
  send(b,"CALL_ACCEPT",callId,Map.of());
  send(b,"CALL_OFFER",callId,Map.of("sdp",Map.of("type","offer","sdp","v=0")));
  assertThat(error()).isEqualTo("INVALID_CALL_STATE");
  send(a,"CALL_ICE",callId,Map.of("candidate",Map.of("candidate","")));
  assertThat(error()).isEqualTo("INVALID_MESSAGE");
  send(c,"CALL_END",callId,Map.of());assertThat(error()).isEqualTo("NOT_IN_CALL");assertThat(calls.busy(a.actor)).isTrue();
 }

 @Test void declineCancelAndDisconnectNotifyBothAndReleaseBusyState() {
  invite();send(b,"CALL_DECLINE",callId,Map.of());assertThat(calls.busy(a.actor)).isFalse();
  assertThat(((Map<?,?>)events.getLast().payload).get("reason")).isEqualTo("DECLINED");
  callId=UUID.randomUUID().toString();invite();send(a,"CALL_END",callId,Map.of());
  assertThat(calls.busy(b.actor)).isFalse();
  callId=UUID.randomUUID().toString();invite();send(b,"CALL_ACCEPT",callId,Map.of());
  calls.disconnect(b.id,sink);
  assertThat(((Map<?,?>)events.getLast().payload).get("reason")).isEqualTo("DISCONNECTED");
  assertThat(calls.busy(a.actor)).isFalse();
 }

 @Test void timeoutClearsRingingButDoesNotEndAcceptedCalls() {
  invite();calls.expire(System.currentTimeMillis()+31000,sink);
  assertThat(calls.busy(a.actor)).isFalse();assertThat(((Map<?,?>)events.getLast().payload).get("reason")).isEqualTo("NO_ANSWER");
  callId=UUID.randomUUID().toString();invite();send(b,"CALL_ACCEPT",callId,Map.of());
  calls.expire(System.currentTimeMillis()+100000,sink);assertThat(calls.busy(a.actor)).isTrue();
 }

 @Test void offlineAndRandomQueueOrRoomPreventCallsAcrossTabs() {
  calls.disconnect(b.id,sink);invite();assertThat(error()).isEqualTo("OFFLINE");
  send(b,"CALL_REGISTER",null,Map.of());
  var other=session("random-tab",b.guestId);waiting.add(other.id);
  invite();assertThat(error()).isEqualTo("BUSY");
  waiting.clear();other.roomId="random-room";invite();assertThat(error()).isEqualTo("BUSY");
  other.roomId=null;invite();
  send(b,"CALL_INVITE",UUID.randomUUID().toString(),Map.of("conversationId",conversation));
  assertThat(error()).isEqualTo("BUSY");
 }

 @Test void unauthorizedConversationDoesNotRingOrReserveUsers() {
  when(social.callPeer(a.actor,conversation)).thenThrow(ConversationService.notFound());
  invite();assertThat(error()).isEqualTo("CALL_UNAVAILABLE");
  assertThat(count(b.id,"CALL_INCOMING")).isZero();assertThat(calls.busy(a.actor)).isFalse();
 }
}
