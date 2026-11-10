package com.randomconnect.auth;
import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.Scheduled;
import java.util.concurrent.ConcurrentHashMap;
@Service
public class SocketTickets {
 private record Ticket(Actor actor,long until){}
 private final ConcurrentHashMap<String,Ticket> tickets=new ConcurrentHashMap<>();
 private final IdentityService identities;
 public SocketTickets(IdentityService identities){this.identities=identities;}
 public String issue(Actor actor){identities.validate(actor);if(tickets.size()>4096)throw new IllegalStateException("Too many pending tickets");String raw=Secrets.token();tickets.put(Secrets.hash(raw),new Ticket(actor,System.currentTimeMillis()+30000));return raw;}
 public Actor consume(String raw){var t=tickets.remove(Secrets.hash(raw));if(t==null||t.until<System.currentTimeMillis())throw IdentityService.unauthorized();identities.validate(t.actor);return t.actor;}
 @Scheduled(fixedDelay=30000) public void expire(){tickets.entrySet().removeIf(e->e.getValue().until<System.currentTimeMillis());}
}
