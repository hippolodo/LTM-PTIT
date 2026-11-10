package com.randomconnect.service;

import com.randomconnect.auth.Actor;
import com.randomconnect.auth.IdentityService;
import com.randomconnect.dto.SocketMessage;
import com.randomconnect.model.GuestSession;
import java.util.*;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/** Accessed only under MatchmakingService's state lock. Calls do not close DIRECT conversations. */
@Component
public class DirectCalls {
 public interface Events {
  void send(GuestSession to, String type, String request, String callId, Object payload);
 }
 private static class Call {
  String id, conversationId;
  GuestSession caller, callee;
  long deadline;
  boolean accepted;
 }
 private final Map<String, Call> calls = new HashMap<>();
 private final Set<String> available = new LinkedHashSet<>();
 private final SocialService social;
 private final IceConfigService ice;
 private final IdentityService identities;
 private final SignalingService signaling = new SignalingService();

 public DirectCalls(SocialService social, IceConfigService ice, IdentityService identities) {
  this.social = social;
  this.ice = ice;
  this.identities = identities;
 }

 public boolean busy(Actor actor) {
  return calls.values().stream().anyMatch(c -> c.caller.actor.sameIdentity(actor) || c.callee.actor.sameIdentity(actor));
 }

 private boolean occupied(Actor actor, Map<String, GuestSession> sessions, Set<String> waiting) {
  return busy(actor) || sessions.values().stream().anyMatch(s -> s.actor.sameIdentity(actor) && (s.roomId != null || waiting.contains(s.id)));
 }

 public void accept(GuestSession self, SocketMessage message, Map<String, GuestSession> sessions,
                    Set<String> waiting, Events events) {
  try {
   if (!self.actor.isUser()) throw new IllegalArgumentException("ACCOUNT_REQUIRED");
   switch (message.type()) {
    case "CALL_REGISTER" -> {
     available.remove(self.id);
     available.add(self.id);
     events.send(self, "CALL_READY", message.requestId(), null, Map.of());
    }
    case "CALL_INVITE" -> invite(self, message, sessions, waiting, events);
    case "CALL_ACCEPT" -> {
     var call = member(self, message.roomId());
     if (call.callee != self || call.accepted) throw new IllegalArgumentException("INVALID_CALL_STATE");
     if (call.deadline <= System.currentTimeMillis()) { end(call, "NO_ANSWER", events); break; }
     call.accepted = true;
     events.send(call.caller, "CALL_ACCEPTED", null, call.id, Map.of("initiator", true, "iceServers", ice.forGuest(call.caller.guestId)));
     events.send(call.callee, "CALL_ACCEPTED", message.requestId(), call.id, Map.of("initiator", false, "iceServers", ice.forGuest(call.callee.guestId)));
    }
    case "CALL_DECLINE" -> {
     var call = member(self, message.roomId());
     if (call.callee != self || call.accepted) throw new IllegalArgumentException("INVALID_CALL_STATE");
     end(call, "DECLINED", events);
    }
    case "CALL_END" -> end(member(self, message.roomId()), "ENDED", events);
    case "CALL_OFFER", "CALL_ANSWER", "CALL_ICE" -> {
     var call = member(self, message.roomId());
     if (!call.accepted) throw new IllegalArgumentException("INVALID_CALL_STATE");
     String type = switch (message.type()) {
      case "CALL_OFFER" -> "WEBRTC_OFFER";
      case "CALL_ANSWER" -> "WEBRTC_ANSWER";
      default -> "ICE_CANDIDATE";
     };
     if ((type.equals("WEBRTC_OFFER") && call.caller != self) || (type.equals("WEBRTC_ANSWER") && call.callee != self))
      throw new IllegalArgumentException("INVALID_CALL_STATE");
     signaling.validate(new SocketMessage(type, message.requestId(), call.id, message.payload()));
     events.send(call.caller == self ? call.callee : call.caller, message.type(), message.requestId(), call.id, message.payload());
    }
    default -> throw new IllegalArgumentException("INVALID_MESSAGE");
   }
  } catch (ResponseStatusException e) {
   events.send(self, "CALL_ERROR", message.requestId(), message.roomId(), Map.of("code", "CALL_UNAVAILABLE"));
  } catch (IllegalArgumentException e) {
   events.send(self, "CALL_ERROR", message.requestId(), message.roomId(), Map.of("code", e.getMessage() == null ? "INVALID_MESSAGE" : e.getMessage()));
  }
 }

 private void invite(GuestSession self, SocketMessage message, Map<String, GuestSession> sessions,
                     Set<String> waiting, Events events) {
  UUID.fromString(message.roomId());
  if (!available.contains(self.id) || occupied(self.actor, sessions, waiting) || calls.containsKey(message.roomId()))
   throw new IllegalArgumentException("BUSY");
  String conversation = message.payload() == null ? "" : message.payload().path("conversationId").asText();
  // Checks DIRECT type, membership and active peer; does not trust a caller-supplied user ID.
  var peer = social.callPeer(self.actor, conversation);
  GuestSession target = available.stream().map(sessions::get).filter(Objects::nonNull)
   .filter(s -> s.actor.isUser() && peer.get("id").equals(s.actor.id()) && s.socket.isOpen())
   .reduce((previous, next) -> next).orElseThrow(() -> new IllegalArgumentException("OFFLINE"));
  if (occupied(target.actor, sessions, waiting)) throw new IllegalArgumentException("BUSY");
  identities.validate(target.actor);
  var caller = social.callPeer(target.actor, conversation);
  var call = new Call();
  call.id = message.roomId();
  call.conversationId = conversation;
  call.caller = self;
  call.callee = target;
  call.deadline = System.currentTimeMillis() + 30000;
  calls.put(call.id, call);
  events.send(self, "CALL_RINGING", message.requestId(), call.id, Map.of("conversationId", conversation, "peer", peer));
  events.send(target, "CALL_INCOMING", null, call.id, Map.of("conversationId", conversation, "peer", caller));
 }

 private Call member(GuestSession self, String id) {
  var call = calls.get(id);
  if (call == null || (call.caller != self && call.callee != self)) throw new IllegalArgumentException("NOT_IN_CALL");
  return call;
 }

 private void end(Call call, String reason, Events events) {
  calls.remove(call.id);
  events.send(call.caller, "CALL_ENDED", null, call.id, Map.of("reason", reason));
  events.send(call.callee, "CALL_ENDED", null, call.id, Map.of("reason", reason));
 }

 public void disconnect(String socketId, Events events) {
  available.remove(socketId);
  for (var call : new ArrayList<>(calls.values()))
   if (call.caller.id.equals(socketId) || call.callee.id.equals(socketId)) end(call, "DISCONNECTED", events);
 }

 public void expire(long now, Events events) {
  for (var call : new ArrayList<>(calls.values()))
   if (!call.accepted && call.deadline <= now) end(call, "NO_ANSWER", events);
 }
}
