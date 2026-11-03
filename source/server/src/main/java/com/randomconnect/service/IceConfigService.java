package com.randomconnect.service;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
@Component
public class IceConfigService {
 private final String stun,turn,secret;
 public IceConfigService(@Value("${STUN_URLS:stun:stun.l.google.com:19302}") String stun,@Value("${TURN_URLS:}") String turn,@Value("${TURN_SHARED_SECRET:}") String secret) {this.stun=stun;this.turn=turn;this.secret=secret;if(!turn.isBlank() && secret.isBlank())throw new IllegalArgumentException("TURN_SHARED_SECRET is required when TURN_URLS is configured");}
 public List<Map<String,Object>> forGuest(String guestId) {
  var result=new ArrayList<Map<String,Object>>();
  if(!stun.isBlank())result.add(Map.of("urls",Arrays.asList(stun.split(","))));
  if(!turn.isBlank())try {
   String username=(Instant.now().getEpochSecond()+600)+":"+guestId;
   var mac=Mac.getInstance("HmacSHA1");mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8),"HmacSHA1"));
   String credential=Base64.getEncoder().encodeToString(mac.doFinal(username.getBytes(StandardCharsets.UTF_8)));
   result.add(Map.of("urls",Arrays.asList(turn.split(",")),"username",username,"credential",credential));
  }catch(java.security.GeneralSecurityException e){throw new IllegalStateException("Cannot issue TURN credential",e);}
  return result;
 }
}
