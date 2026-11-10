package com.randomconnect.controller;

import com.randomconnect.auth.Actor;
import com.randomconnect.service.SocialService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/social")
public class SocialController {
 private final SocialService social;
 public SocialController(SocialService social) { this.social = social; }
 public record Decision(boolean accept) {}
 public record Text(@NotBlank @Size(max = 2000) String text,
                    @NotBlank @Pattern(regexp = "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}") String clientMessageId) {}

 @GetMapping("/users") Object search(@AuthenticationPrincipal Actor actor, @RequestParam String q) {
  return social.search(actor, q);
 }
 @GetMapping Object overview(@AuthenticationPrincipal Actor actor) { return social.overview(actor); }
 @PostMapping("/friends/{userId}") Object request(@AuthenticationPrincipal Actor actor, @PathVariable String userId) {
  return social.request(actor, userId);
 }
 @PatchMapping("/friends/{userId}") void respond(@AuthenticationPrincipal Actor actor, @PathVariable String userId,
                                              @Valid @RequestBody Decision decision) {
  social.respond(actor, userId, decision.accept());
 }
 @DeleteMapping("/friends/{userId}") void remove(@AuthenticationPrincipal Actor actor, @PathVariable String userId) {
  social.remove(actor, userId);
 }
 @PostMapping("/direct/{userId}") Object open(@AuthenticationPrincipal Actor actor, @PathVariable String userId) {
  return social.open(actor, userId);
 }
 @GetMapping("/direct/{id}/messages") Object messages(@AuthenticationPrincipal Actor actor, @PathVariable String id,
                                                    @RequestParam(required = false) String before,
                                                    @RequestParam(required = false) String after) {
  return social.messages(actor, id, before, after);
 }
 @PostMapping("/direct/{id}/messages") Object send(@AuthenticationPrincipal Actor actor, @PathVariable String id,
                                                @Valid @RequestBody Text text) {
  return social.send(actor, id, text.text(), text.clientMessageId());
 }
}
