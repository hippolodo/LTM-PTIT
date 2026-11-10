package com.randomconnect.controller;
import com.randomconnect.auth.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import java.util.Map;
@RestController
public class SocketTicketController {
 private final SocketTickets tickets;public SocketTicketController(SocketTickets t){tickets=t;}
 @PostMapping("/api/socket-ticket") Object ticket(@AuthenticationPrincipal Actor actor){return Map.of("ticket",tickets.issue(actor),"expiresIn",30);}
}
