package com.randomconnect;
import com.randomconnect.service.IceConfigService;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import static org.assertj.core.api.Assertions.*;
class IceConfigTest {
 @Test void turnCredentialsAreShortLivedAndSigned() throws Exception {
  var config=new IceConfigService("stun:example.invalid:3478","turn:example.invalid:3478","test-only-secret");
  var entry=config.forGuest("guest-test").get(1);String username=(String)entry.get("username");long expiry=Long.parseLong(username.split(":")[0]);assertThat(expiry-Instant.now().getEpochSecond()).isBetween(590L,600L);
  var mac=Mac.getInstance("HmacSHA1");mac.init(new SecretKeySpec("test-only-secret".getBytes(StandardCharsets.UTF_8),"HmacSHA1"));assertThat(entry.get("credential")).isEqualTo(Base64.getEncoder().encodeToString(mac.doFinal(username.getBytes(StandardCharsets.UTF_8))));assertThat(entry.toString()).doesNotContain("test-only-secret");
 }
 @Test void refusesTurnWithoutSecret(){assertThatThrownBy(()->new IceConfigService("","turn:example.invalid","")).isInstanceOf(IllegalArgumentException.class);}
}
