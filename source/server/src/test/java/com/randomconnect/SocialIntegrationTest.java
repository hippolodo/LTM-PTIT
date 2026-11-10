package com.randomconnect;

import com.fasterxml.jackson.databind.*;
import com.randomconnect.auth.*;
import com.randomconnect.persistence.*;
import com.randomconnect.service.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class SocialIntegrationTest {
 @Autowired SocialService social;
 @Autowired AuthService auth;
 @Autowired IdentityService identities;
 @Autowired SocialConnectionRepository connections;
 @Autowired ConversationRepository conversations;
 @Autowired MessageRepository messages;
 @Autowired ConversationService chats;
 @Autowired RetentionService retention;
 @Autowired MockMvc mvc;
 @Autowired ObjectMapper json;

 private AuthService.LoginGrant user() {
  String name = "social" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
  return auth.register(name, name + "@example.invalid", "Password123!", "Person " + name);
 }
 private Actor actor(AuthService.LoginGrant grant) {
  var claims = jsonToken(grant.accessToken());
  return new Actor("USER", grant.user().id(), claims.path("sid").asText());
 }
 private JsonNode jsonToken(String token) {
  try { return json.readTree(Base64.getUrlDecoder().decode(token.split("\\.")[1])); }
  catch (Exception e) { throw new IllegalStateException(e); }
 }
 private String bearer(AuthService.LoginGrant grant) { return "Bearer " + grant.accessToken(); }
 private String thread(Actor a, Actor b) { return (String) social.open(a, b.id()).get("id"); }

 @Test void searchIsPrivateLiteralAndUserOnly() throws Exception {
  var a = user(); var b = user();
  mvc.perform(get("/api/social/users").param("q", b.user().username()).header("Authorization", bearer(a)))
   .andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(b.user().id()))
   .andExpect(jsonPath("$[0].email").doesNotExist()).andExpect(jsonPath("$[0].passwordHash").doesNotExist());
  assertThat(social.search(actor(a), a.user().username())).isEmpty();
  assertThat(social.search(actor(a), "%%")).isEmpty();
  assertThat(social.search(actor(a), b.user().displayName().toUpperCase(Locale.ROOT))).hasSize(1);
  mvc.perform(get("/api/social/users").param("q", "a").header("Authorization", bearer(a))).andExpect(status().isBadRequest());
  var guest = identities.createGuest();
  mvc.perform(get("/api/social").header("Authorization", "Guest " + guest.token())).andExpect(status().isForbidden());
  mvc.perform(post("/api/social/direct/" + b.user().id()).header("Authorization", "Guest " + guest.token())).andExpect(status().isForbidden());
  mvc.perform(get("/api/social")).andExpect(status().isUnauthorized());
 }

 @Test void friendshipLifecycleOnlyRecipientCanAccept() {
  var a = actor(user()); var b = actor(user()); var outsider = actor(user());
  social.request(a, b.id()); social.request(a, b.id());
  assertThat((List<?>) social.overview(a).get("outgoing")).hasSize(1);
  assertThat((List<?>) social.overview(b).get("incoming")).hasSize(1);
  social.request(b, a.id()); // An opposite request is not implicit consent.
  assertThatThrownBy(() -> social.respond(a, b.id(), true)).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
  assertThatThrownBy(() -> social.respond(outsider, a.id(), true)).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
  social.respond(b, a.id(), true);
  assertThat((List<?>) social.overview(a).get("friends")).hasSize(1);
  assertThat((List<?>) social.overview(b).get("friends")).hasSize(1);
  social.remove(a, b.id());
  assertThat((List<?>) social.overview(b).get("friends")).isEmpty();
  social.request(a, b.id()); social.respond(b, a.id(), false);
  assertThat((List<?>) social.overview(a).get("outgoing")).isEmpty();
  social.request(b, a.id()); social.remove(b, a.id());
  assertThat((List<?>) social.overview(a).get("incoming")).isEmpty();
  assertThatThrownBy(() -> social.request(a, a.id())).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
 }

 @Test void directChatWithoutFriendshipPersistsDeduplicatesAndSurvivesRecovery() throws Exception {
  var ga = user(); var gb = user(); var a = actor(ga); var b = actor(gb);
  String id = thread(a, b), client = UUID.randomUUID().toString();
  assertThat(thread(b, a)).isEqualTo(id);
  var first = social.send(a, id, " hello ", client);
  assertThat(social.send(a, id, " hello ", client).get("messageId")).isEqualTo(first.get("messageId"));
  social.send(b, id, "reply", UUID.randomUUID().toString());
  assertThat((List<?>) social.messages(a, id, null, null).get("items")).hasSize(2);
  assertThat((List<?>) social.overview(b).get("threads")).hasSize(1);
  retention.reconcile();
  assertThat(conversations.findById(id).orElseThrow().status).isEqualTo("ACTIVE");
  social.request(a, b.id()); social.respond(b, a.id(), true); social.remove(a, b.id());
  social.send(a, id, "Still a direct conversation", UUID.randomUUID().toString());
  assertThat((List<?>) chats.history(b, 0, 20).get("items")).hasSize(1);
  mvc.perform(post("/api/social/direct/" + id + "/messages").header("Authorization", bearer(ga))
    .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("text", "via HTTP", "clientMessageId", UUID.randomUUID().toString()))))
   .andExpect(status().isOk()).andExpect(jsonPath("senderId").value(a.id()));
 }

 @Test void directEndpointsRejectOutsidersRandomRoomsAndBadInput() throws Exception {
  var ga = user(); var gb = user(); var gc = user(); var a = actor(ga); var b = actor(gb);
  String id = thread(a, b);
  mvc.perform(get("/api/social/direct/" + id + "/messages").header("Authorization", bearer(gc))).andExpect(status().isNotFound());
  mvc.perform(post("/api/social/direct/" + id + "/messages").header("Authorization", bearer(gc))
   .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("text", "intruder", "clientMessageId", UUID.randomUUID().toString())))).andExpect(status().isNotFound());
  mvc.perform(post("/api/social/direct/" + id + "/messages").header("Authorization", bearer(ga))
   .contentType(MediaType.APPLICATION_JSON).content("{\"text\":\"hello\",\"clientMessageId\":\"invalid\"}")).andExpect(status().isBadRequest());
  String random = UUID.randomUUID().toString(); chats.create(random, a, b);
  assertThatThrownBy(() -> social.send(a, random, "wrong channel", UUID.randomUUID().toString())).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
  assertThatThrownBy(() -> social.messages(a, id, "x", "y")).isInstanceOf(IllegalArgumentException.class);
 }

 @Test void cursorPagesDoNotSkipOrDuplicateEqualTimestamps() {
  var a = actor(user()); var b = actor(user()); String id = thread(a, b);
  // Seed a batch with identical timestamps to exercise the ID tie breaker.
  var p = participants.findByConversationId(id).stream().filter(x -> a.id().equals(x.userId)).findFirst().orElseThrow();
  var timestamp = java.time.Instant.parse("2026-01-01T00:00:00Z");
  var batch = new ArrayList<ChatMessage>();
  for (int i = 0; i < 61; i++) {
   var m = new ChatMessage(); m.conversationId=id; m.senderParticipantId=p.id;
   m.clientMessageId=UUID.randomUUID().toString(); m.content="message " + i; m.createdAt=timestamp; batch.add(m);
  }
  messages.saveAllAndFlush(batch);
  var latest = (List<Map<String,Object>>) social.messages(b, id, null, null).get("items");
  assertThat(latest).hasSize(50);
  String oldest = (String) latest.getLast().get("messageId");
  var older = (List<Map<String,Object>>) social.messages(b, id, oldest, null).get("items");
  assertThat(older).hasSize(11);
  var forward = (List<Map<String,Object>>) social.messages(b, id, null, (String) older.getFirst().get("messageId")).get("items");
  assertThat(forward).hasSize(50);
  assertThat(forward.stream().map(m -> m.get("messageId")).toList()).containsExactlyElementsOf(latest.reversed().stream().map(m -> m.get("messageId")).toList());
  var c = actor(user()); String other = thread(a, c); var foreign = social.send(a, other, "other", UUID.randomUUID().toString());
  assertThatThrownBy(() -> social.messages(b, id, null, (String) foreign.get("messageId"))).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
 }
 @Autowired ParticipantRepository participants;

 @Test void simultaneousOpenAndCrossedRequestsUseOnePair() throws Exception {
  var a = actor(user()); var b = actor(user());
  try (var executor = Executors.newFixedThreadPool(2)) {
   var start = new CountDownLatch(1);
   var first = executor.submit(() -> { start.await(); social.request(a, b.id()); return thread(a,b); });
   var second = executor.submit(() -> { start.await(); social.request(b, a.id()); return thread(b,a); });
   start.countDown();
   assertThat(first.get(15,TimeUnit.SECONDS)).isEqualTo(second.get(15,TimeUnit.SECONDS));
  }
  assertThat(connections.forUser(a.id())).hasSize(1);
 }
}
