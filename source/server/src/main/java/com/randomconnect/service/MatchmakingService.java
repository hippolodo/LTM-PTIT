package com.randomconnect.service;
import com.randomconnect.model.*;
import com.randomconnect.dto.SocketMessage;
import com.randomconnect.util.MessageSender;
import jakarta.annotation.PreDestroy;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
@Service
public class MatchmakingService {
 private final Object lock=new Object();
 private final Map<String,GuestSession> sessions=new ConcurrentHashMap<>();
 private final LinkedHashSet<String> waiting=new LinkedHashSet<>();
 private final ArrayDeque<Delivery> outbox=new ArrayDeque<>();
 private final RoomService rooms;
 private final MessageSender sender;
 private final SignalingService signaling=new SignalingService();
 private final long timeout;
 private final IceConfigService ice;
 private record Delivery(GuestSession to, Map<String,Object> event, String activeRoom) {}
 public MatchmakingService(RoomService rooms,MessageSender sender,long timeout) {this(rooms,sender,timeout,new IceConfigService("stun:stun.l.google.com:19302","",""));}
 @org.springframework.beans.factory.annotation.Autowired
 public MatchmakingService(RoomService rooms,MessageSender sender,@Value("${randomconnect.session-timeout-ms:45000}") long timeout,IceConfigService ice) {this.rooms=rooms;this.sender=sender;this.timeout=timeout;this.ice=ice;}
 public void open(WebSocketSession socket) {
  synchronized(lock) {
   var g=new GuestSession(new ConcurrentWebSocketSessionDecorator(socket,2000,262144)); sessions.put(g.id,g);
   emit(g,"SESSION_READY",null,null,Map.of("guestId",g.guestId,"iceServers",ice.forGuest(g.guestId)));
  }
 }
 private void emit(GuestSession to,String type,String request,String room,Object payload) {
  var event=new LinkedHashMap<String,Object>(); event.put("type",type);
  if(request!=null) event.put("requestId",request); if(room!=null) event.put("roomId",room); event.put("payload",payload);
  outbox.add(new Delivery(to,event,null));
 }
 public void error(String id,String request,String code) {
  synchronized(lock) {var g=sessions.get(id); if(g!=null) emit(g,"ERROR",request,null,Map.of("code",code,"message",switch(code) {case "NOT_IN_ROOM" -> "Room is no longer active."; case "RATE_LIMITED" -> "Please slow down."; case "ALREADY_MATCHED" -> "Already in a room."; default -> "Invalid request.";}));}
 }
 public void accept(String id,SocketMessage m) {
  synchronized(lock) {
   var g=sessions.get(id); if(g==null) return;
   g.lastSeen=System.currentTimeMillis();
   if(!g.allow(m.type(),g.lastSeen)) {error(id,m.requestId(),"RATE_LIMITED");return;}
   try {
    switch(m.type()) {
     case "FIND_PARTNER" -> find(g,m.requestId());
     case "NEXT_PARTNER", "LEAVE_ROOM" -> {
      // A second simultaneous Next may arrive after the partner closed their room.
      if(g.roomId!=null && !g.roomId.equals(m.roomId()))throw new IllegalArgumentException("NOT_IN_ROOM");
      String old=g.roomId;
      closeRoom(g,m.type().equals("NEXT_PARTNER")?"NEXT":"STOP"); waiting.remove(g.id);
      emit(g,"ROOM_CLOSED",m.requestId(),old,Map.of());
      if(m.type().equals("NEXT_PARTNER"))find(g,m.requestId());
     }
     case "CHAT_MESSAGE" -> {
      String peer=rooms.peer(g,m.roomId());
      var text=m.payload()==null?null:m.payload().get("text");
      if(text==null || !text.isTextual() || text.asText().trim().isEmpty() || text.asText().length()>2000)throw new IllegalArgumentException("INVALID_MESSAGE");
      var body=Map.of("text",text.asText().trim(),"messageId",UUID.randomUUID().toString(),"senderId",g.guestId,"sentAt",java.time.Instant.now().toString());
      relay(g,m.type(),m.roomId(),body);relay(sessions.get(peer),m.type(),m.roomId(),body);
     }
     case "WEBRTC_OFFER", "WEBRTC_ANSWER", "ICE_CANDIDATE" -> {
      String peer=rooms.peer(g,m.roomId());
      signaling.validate(m);
      var r=rooms.get(m.roomId());
      if(m.type().equals("WEBRTC_OFFER") && !r.first().equals(g.id))throw new IllegalArgumentException("INVALID_MESSAGE");
      if(m.type().equals("WEBRTC_ANSWER") && !r.second().equals(g.id))throw new IllegalArgumentException("INVALID_MESSAGE");
      relay(sessions.get(peer),m.type(),m.roomId(),m.payload());
     }
     case "PING" -> emit(g,"PONG",m.requestId(),null,Map.of());
     case "PONG" -> {}
     default -> throw new IllegalArgumentException("INVALID_MESSAGE");
    }
   } catch(IllegalArgumentException e) {error(id,m.requestId(),e.getMessage());}
  }
 }
 private void relay(GuestSession g,String type,String room,Object payload) {
  if(g==null)return;
  var event=new LinkedHashMap<String,Object>();event.put("type",type);event.put("roomId",room);event.put("payload",payload);
  outbox.add(new Delivery(g,event,room));
 }
 private void find(GuestSession g,String request) {
  if(g.roomId!=null) {error(g.id,request,"ALREADY_MATCHED");return;}
  if(waiting.contains(g.id)) {emit(g,"WAITING",request,null,Map.of("status","WAITING"));return;}
  waiting.removeIf(id -> {var p=sessions.get(id); return p==null || !p.socket.isOpen() || p.roomId!=null;});
  var candidates=waiting.stream().filter(id->!id.equals(g.id)).toList();
  if(candidates.isEmpty()) {waiting.add(g.id);emit(g,"WAITING",request,null,Map.of("status","WAITING"));return;}
  var p=sessions.get(candidates.get(ThreadLocalRandom.current().nextInt(candidates.size()))); waiting.remove(p.id);
  var room=rooms.create(p,g);
  emit(p,"MATCH_FOUND",null,room.id(),Map.of("partnerId",g.guestId,"initiator",true,"iceServers",ice.forGuest(p.guestId)));
  emit(g,"MATCH_FOUND",request,room.id(),Map.of("partnerId",p.guestId,"initiator",false,"iceServers",ice.forGuest(g.guestId)));
 }
 private void closeRoom(GuestSession g,String reason) {
  var r=rooms.remove(g.roomId); g.roomId=null;
  if(r==null) return;
  var peer=sessions.get(r.peer(g.id));
  if(peer!=null) {peer.roomId=null;emit(peer,"PARTNER_LEFT",null,r.id(),Map.of("reason",reason));}
 }
 public void disconnect(String id) {
  synchronized(lock) {var g=sessions.remove(id); if(g==null)return;waiting.remove(id);closeRoom(g,"DISCONNECTED");}
 }
 // One dispatcher preserves the total order in which state transitions append notifications.
 @Scheduled(fixedDelay=10)
 public synchronized void flush() {
  while(true) {
   Delivery d;
   synchronized(lock) {d=outbox.poll(); if(d==null)return; if(sessions.get(d.to.id)!=d.to)continue; if(d.activeRoom!=null && !d.activeRoom.equals(d.to.roomId))continue;}
   if(!sender.send(d.to.socket,d.event)) disconnect(d.to.id);
  }
 }
 @Scheduled(fixedDelay=5000)
 public void expire() {
  List<GuestSession> stale;
  synchronized(lock) {long now=System.currentTimeMillis();stale=sessions.values().stream().filter(g->now-g.lastSeen>timeout).toList(); for(var g:stale)disconnect(g.id);}
  for(var g:stale)try {g.socket.close(CloseStatus.SESSION_NOT_RELIABLE);}catch(Exception ignored){}
 }
 @PreDestroy public void shutdown() {for(var g:sessions.values())try{g.socket.close(CloseStatus.GOING_AWAY);}catch(Exception ignored){} sessions.clear();}
 public Map<String,Integer> snapshot() {synchronized(lock) {return Map.of("sessions",sessions.size(),"waiting",waiting.size(),"rooms",rooms.size());}}
}
