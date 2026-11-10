package com.randomconnect.auth;
import com.randomconnect.persistence.*;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.time.*;
@Service
public class IdentityService {
 private final GuestRepository guests;private final AuthSessionRepository sessions;private final UserRepository users;private final long ttl;
 public record GuestGrant(String token,String guestId,Instant expiresAt){}
 public IdentityService(GuestRepository g,AuthSessionRepository s,UserRepository u,@Value("${app.guest-ttl-hours:24}") long ttl){guests=g;sessions=s;users=u;this.ttl=ttl;}
 @Transactional public GuestGrant createGuest(){String token=Secrets.token();var guest=new GuestIdentity();guest.guestTokenHash=Secrets.hash(token);guest.expiresAt=Instant.now().plus(Duration.ofHours(ttl));guests.save(guest);return new GuestGrant(token,guest.id,guest.expiresAt);}
 public Actor guestToken(String raw){var g=guests.findByGuestTokenHash(Secrets.hash(raw)).orElseThrow(IdentityService::unauthorized);if(g.expiresAt.isBefore(Instant.now())||!g.status.equals("ACTIVE"))throw unauthorized();return new Actor("GUEST",g.id,null);}
 public void validate(Actor actor){
  if(actor==null)throw unauthorized();
  if(actor.isUser()){var s=sessions.findById(actor.sessionId()).orElseThrow(IdentityService::unauthorized);if(!s.userId.equals(actor.id())||s.revokedAt!=null||!s.expiresAt.isAfter(Instant.now()))throw unauthorized();var u=users.findById(actor.id()).orElseThrow(IdentityService::unauthorized);if(!"ACTIVE".equals(u.status))throw unauthorized();}
  else {var g=guests.findById(actor.id()).orElseThrow(IdentityService::unauthorized);if(!g.expiresAt.isAfter(Instant.now())||!"ACTIVE".equals(g.status))throw unauthorized();}
 }
 public static ResponseStatusException unauthorized(){return new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Session expired or invalid");}
}
