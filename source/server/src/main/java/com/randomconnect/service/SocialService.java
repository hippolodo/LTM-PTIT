package com.randomconnect.service;

import com.randomconnect.auth.*;
import com.randomconnect.persistence.*;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class SocialService {
 private final UserRepository users;
 private final SocialConnectionRepository connections;
 private final ConversationRepository conversations;
 private final ConversationService chats;
 private final RequestLimits limits;

 public SocialService(UserRepository users, SocialConnectionRepository connections,
                      ConversationRepository conversations, ConversationService chats, RequestLimits limits) {
  this.users = users;
  this.connections = connections;
  this.conversations = conversations;
  this.chats = chats;
  this.limits = limits;
 }

 private void requireUser(Actor actor) {
  if (!actor.isUser()) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Registered account required");
 }

 private Map<String, Object> userView(UserAccount user, SocialConnection connection, String self) {
  String relationship = "NONE";
  if (connection != null) {
   relationship = connection.friendshipStatus;
   if ("PENDING".equals(relationship)) relationship = self.equals(connection.requesterId) ? "OUTGOING" : "INCOMING";
  }
  // Search never exposes emails, roles, password hashes or session identifiers.
  return Map.of("id", user.id, "username", user.username, "displayName", user.displayName,
                "relationship", relationship);
 }

 @Transactional(readOnly = true)
 public List<Map<String, Object>> search(Actor actor, String query) {
  requireUser(actor);
  limits.take("search:" + actor.id(), 60, 60000);
  String term = query.trim().toLowerCase(Locale.ROOT);
  if (term.length() < 2 || term.length() > 80)
   throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Search requires 2 to 80 characters");
  term = "%" + term.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
  var relations = connections.forUser(actor.id()).stream().collect(Collectors.toMap(s -> s.peer(actor.id()), s -> s));
  return users.search(actor.id(), term, PageRequest.of(0, 20)).stream()
   .map(u -> userView(u, relations.get(u.id), actor.id())).toList();
 }

 @Transactional(readOnly = true)
 public Map<String, Object> overview(Actor actor) {
  requireUser(actor);
  var relations = connections.forUser(actor.id());
  var peers = users.findAllById(relations.stream().map(s -> s.peer(actor.id())).toList()).stream()
   .filter(u -> "ACTIVE".equals(u.status)).collect(Collectors.toMap(u -> u.id, u -> u));
  var friends = new ArrayList<Map<String, Object>>();
  var incoming = new ArrayList<Map<String, Object>>();
  var outgoing = new ArrayList<Map<String, Object>>();
  var threads = new ArrayList<Map<String, Object>>();
  for (var relation : relations) {
   var peer = peers.get(relation.peer(actor.id()));
   if (peer == null) continue;
   var view = userView(peer, relation, actor.id());
   switch ((String) view.get("relationship")) {
    case "ACCEPTED" -> friends.add(view);
    case "INCOMING" -> incoming.add(view);
    case "OUTGOING" -> outgoing.add(view);
   }
   if (relation.conversationId != null) {
    var thread = new LinkedHashMap<String, Object>(view);
    thread.put("conversationId", relation.conversationId);
    threads.add(thread);
   }
  }
  Comparator<Map<String, Object>> byName = Comparator.comparing(v -> (String) v.get("username"));
  for (var list : List.of(friends, incoming, outgoing, threads)) list.sort(byName);
  return Map.of("friends", friends, "incoming", incoming, "outgoing", outgoing, "threads", threads);
 }

 // The same canonical account row serializes both directions, even before a pair exists.
 private SocialConnection pair(Actor actor, String peerId) {
  return pair(actor, peerId, true);
 }

 private SocialConnection pair(Actor actor, String peerId, boolean create) {
  requireUser(actor);
  if (actor.id().equals(peerId)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot select yourself");
  String first = actor.id().compareTo(peerId) < 0 ? actor.id() : peerId;
  String second = first.equals(actor.id()) ? peerId : actor.id();
  var lower = users.locked(first).orElseThrow(ConversationService::notFound);
  var higher = users.findById(second).orElseThrow(ConversationService::notFound);
  if (!"ACTIVE".equals(lower.status) || !"ACTIVE".equals(higher.status)) throw ConversationService.notFound();
  return connections.findByFirstUserIdAndSecondUserId(first, second).orElseGet(() -> {
   if (!create) throw ConversationService.notFound();
   var connection = new SocialConnection();
   connection.firstUserId = first;
   connection.secondUserId = second;
   return connections.saveAndFlush(connection);
  });
 }

 @Transactional
 public Map<String, Object> request(Actor actor, String peerId) {
  requireUser(actor);
  limits.take("friend:" + actor.id(), 30, 60000);
  var connection = pair(actor, peerId);
  if ("NONE".equals(connection.friendshipStatus)) {
   connection.friendshipStatus = "PENDING";
   connection.requesterId = actor.id();
  }
  return userView(users.findById(peerId).orElseThrow(ConversationService::notFound), connection, actor.id());
 }

 @Transactional
 public void respond(Actor actor, String peerId, boolean accept) {
  requireUser(actor);
  limits.take("friend:" + actor.id(), 30, 60000);
  var connection = pair(actor, peerId, false);
  if (!"PENDING".equals(connection.friendshipStatus) || actor.id().equals(connection.requesterId))
   throw new ResponseStatusException(HttpStatus.CONFLICT, "No incoming friend request");
  connection.friendshipStatus = accept ? "ACCEPTED" : "NONE";
  if (!accept) connection.requesterId = null;
 }

 @Transactional
 public void remove(Actor actor, String peerId) {
  requireUser(actor);
  limits.take("friend:" + actor.id(), 30, 60000);
  var connection = pair(actor, peerId, false);
  connection.friendshipStatus = "NONE";
  connection.requesterId = null;
 }

 @Transactional
 public Map<String, Object> open(Actor actor, String peerId) {
  requireUser(actor);
  limits.take("direct-open:" + actor.id(), 40, 60000);
  var connection = pair(actor, peerId);
  if (connection.conversationId == null) {
   String id = UUID.randomUUID().toString();
   chats.create(id, actor, new Actor("USER", peerId, null));
   var conversation = conversations.findById(id).orElseThrow();
   conversation.conversationType = "DIRECT";
   connection.conversationId = id;
  }
  return Map.of("id", connection.conversationId,
                "peer", userView(users.findById(peerId).orElseThrow(), connection, actor.id()));
 }

 private SocialConnection direct(Actor actor, String id) {
  requireUser(actor);
  chats.member(actor, id, true);
  var conversation = conversations.findById(id).orElseThrow(ConversationService::notFound);
  if (!"DIRECT".equals(conversation.conversationType)) throw ConversationService.notFound();
  var connection = connections.findByConversationId(id).orElseThrow(ConversationService::notFound);
  if (!actor.id().equals(connection.firstUserId) && !actor.id().equals(connection.secondUserId))
   throw ConversationService.notFound();
  return connection;
 }

 @Transactional(readOnly = true)
 public Map<String, Object> callPeer(Actor actor, String id) {
  var connection = direct(actor, id);
  var peer = users.findById(connection.peer(actor.id())).orElseThrow(ConversationService::notFound);
  if (!"ACTIVE".equals(peer.status)) throw ConversationService.notFound();
  return userView(peer, connection, actor.id());
 }

 /** Serializes attachment metadata/quota with direct text sends; storage I/O runs outside this transaction. */
 @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
 public <T> T inDirect(Actor actor, String id, java.util.function.Supplier<T> action) {
  var connection = direct(actor, id);
  pair(actor, connection.peer(actor.id()), false);
  return action.get();
 }

 @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
 public Map<String, Object> send(Actor actor, String id, String text, String clientId) {
  requireUser(actor);
  limits.take("direct-send:" + actor.id(), 30, 10000);
  var connection = direct(actor, id);
  pair(actor, connection.peer(actor.id()));
  if (text == null || text.isBlank() || text.length() > 2000)
   throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Message requires 1 to 2000 characters");
  return chats.text(actor, id, text.trim(), clientId).payload();
 }

 @Transactional(readOnly = true)
 public Map<String, Object> messages(Actor actor, String id, String before, String after) {
  direct(actor, id);
  return chats.window(actor, id, before, after);
 }
}
