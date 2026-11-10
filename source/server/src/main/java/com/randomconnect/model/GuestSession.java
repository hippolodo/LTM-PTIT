package com.randomconnect.model;
import java.util.UUID;
import org.springframework.web.socket.WebSocketSession;
public class GuestSession {
 public final WebSocketSession socket;
 public final String id, guestId;
 public final com.randomconnect.auth.Actor actor;
 public String roomId;
 public long lastSeen = System.currentTimeMillis(), windowStart = lastSeen;
 public int envelopes, matches, chats;
 public GuestSession(WebSocketSession socket) { this.socket=socket; this.id=socket.getId();this.actor=socket.getAttributes().get("actor") instanceof com.randomconnect.auth.Actor a?a:new com.randomconnect.auth.Actor("GUEST",UUID.randomUUID().toString(),null);this.guestId=actor.id(); }
 public boolean allow(String type, long now) {
  if(now-windowStart>=10000) { windowStart=now; envelopes=matches=chats=0; }
  if(++envelopes>1200) return false;
  if(type.equals("FIND_PARTNER") || type.equals("NEXT_PARTNER") || type.equals("CALL_INVITE")) return ++matches<=10;
  if(type.equals("CHAT_MESSAGE")) return ++chats<=30;
  return true;
 }
}
