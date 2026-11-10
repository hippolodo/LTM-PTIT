package com.randomconnect.controller;
import com.randomconnect.auth.Actor;
import com.randomconnect.service.ConversationService;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
@RestController @RequestMapping("/api/conversations")
public class ConversationController {
 private final ConversationService service;public ConversationController(ConversationService s){service=s;}
 @GetMapping Object history(@AuthenticationPrincipal Actor actor,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size){return service.history(actor,page,size);}
 @GetMapping("/{id}") Object detail(@AuthenticationPrincipal Actor actor,@PathVariable String id){return service.detail(actor,id);}
 @GetMapping("/{id}/messages") Object messages(@AuthenticationPrincipal Actor actor,@PathVariable String id,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="30") int size){return service.page(actor,id,page,size);}
 @DeleteMapping("/{id}") void hide(@AuthenticationPrincipal Actor actor,@PathVariable String id){service.hide(actor,id);}
}
