package com.randomconnect.auth;
import com.randomconnect.persistence.*;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.time.*;
import java.util.*;
@Service
public class AuthService {
 public record UserView(String id,String username,String email,String displayName,String avatarUrl){}
 public record LoginGrant(String accessToken,String refreshToken,UserView user,long expiresIn){}
 private final UserRepository users;private final AuthSessionRepository sessions;private final PasswordEncoder passwords;private final JwtEncoder encoder;private final ApplicationEventPublisher events;private final long accessSeconds;
 public AuthService(UserRepository u,AuthSessionRepository s,PasswordEncoder p,JwtEncoder e,ApplicationEventPublisher events,@Value("${app.auth.access-seconds:600}") long seconds){users=u;sessions=s;passwords=p;encoder=e;this.events=events;accessSeconds=seconds;}
 public static UserView view(UserAccount u){return new UserView(u.id,u.username,u.email,u.displayName,u.avatarUrl);}
 @Transactional public LoginGrant register(String username,String email,String password,String display){
  username=username.trim().toLowerCase(Locale.ROOT);email=email.trim().toLowerCase(Locale.ROOT);
  if(!username.matches("[a-z0-9_]{3,32}")||password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length>72)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid username or password length");
  if(users.existsByUsernameOrEmail(username,email))throw new ResponseStatusException(HttpStatus.CONFLICT,"Username or email already exists");
  var u=new UserAccount();u.username=username;u.email=email;u.passwordHash=passwords.encode(password);u.displayName=display==null||display.isBlank()?username:display.trim();users.saveAndFlush(u);return createSession(u);
 }
 @Transactional public LoginGrant login(String login,String password){var u=users.findByUsernameOrEmail(login.trim().toLowerCase(Locale.ROOT),login.trim().toLowerCase(Locale.ROOT)).orElseThrow(IdentityService::unauthorized);if(!passwords.matches(password,u.passwordHash)||!u.status.equals("ACTIVE"))throw IdentityService.unauthorized();u.lastLoginAt=Instant.now();return createSession(u);}
 private LoginGrant createSession(UserAccount u){var s=new AuthSession();s.userId=u.id;s.expiresAt=Instant.now().plus(Duration.ofDays(30));String token=s.id+"."+Secrets.token();s.refreshTokenHash=Secrets.hash(token);sessions.save(s);return grant(u,s,token);}
 private LoginGrant grant(UserAccount u,AuthSession s,String refresh){var now=Instant.now();var claims=JwtClaimsSet.builder().issuer("randomconnect").subject(u.id).issuedAt(now).expiresAt(now.plusSeconds(accessSeconds)).claim("sid",s.id).claim("role",u.role).build();String access=encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(),claims)).getTokenValue();return new LoginGrant(access,refresh,view(u),accessSeconds);}
 @Transactional(noRollbackFor=ResponseStatusException.class) public LoginGrant refresh(String raw){
  var s=sessions.locked(sessionId(raw)).orElseThrow(IdentityService::unauthorized);
  if(s.revokedAt!=null||!s.expiresAt.isAfter(Instant.now()))throw IdentityService.unauthorized();
  if(!Secrets.matches(raw,s.refreshTokenHash)){s.revokedAt=Instant.now();events.publishEvent(new SessionRevoked(s.id));throw IdentityService.unauthorized();}
  var u=users.findById(s.userId).orElseThrow(IdentityService::unauthorized);if(!u.status.equals("ACTIVE"))throw IdentityService.unauthorized();
  String next=s.id+"."+Secrets.token();s.refreshTokenHash=Secrets.hash(next);s.rotatedAt=Instant.now();return grant(u,s,next);
 }
 @Transactional public void logout(String raw){if(raw==null)return;var found=sessions.locked(sessionId(raw));if(found.isPresent()&&Secrets.matches(raw,found.get().refreshTokenHash)){found.get().revokedAt=Instant.now();events.publishEvent(new SessionRevoked(found.get().id));}}
 private String sessionId(String raw){if(raw==null||raw.length()>200||!raw.contains("."))throw IdentityService.unauthorized();return raw.substring(0,raw.indexOf('.'));}
 @Transactional public UserView update(Actor actor,String display){if(!actor.isUser())throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Registered account required");var u=users.findById(actor.id()).orElseThrow(IdentityService::unauthorized);u.displayName=display.trim();u.updatedAt=Instant.now();return view(u);}
 public UserView me(Actor actor){if(!actor.isUser())throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Registered account required");return view(users.findById(actor.id()).orElseThrow(IdentityService::unauthorized));}
}
