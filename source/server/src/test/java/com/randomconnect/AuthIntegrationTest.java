package com.randomconnect;
import com.fasterxml.jackson.databind.*;
import com.randomconnect.persistence.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.*;
import org.springframework.http.MediaType;
import jakarta.servlet.http.Cookie;
import java.util.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.assertj.core.api.Assertions.*;
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT) @AutoConfigureMockMvc
class AuthIntegrationTest {
 @Autowired org.springframework.security.oauth2.jwt.JwtEncoder encoder;
 @Autowired MockMvc mvc;@Autowired ObjectMapper json;@Autowired UserRepository users;
 private record Grant(String access,Cookie refresh,String username){}
 private Grant register()throws Exception {String name="u"+UUID.randomUUID().toString().replace("-","").substring(0,15);var result=mvc.perform(post("/api/auth/register").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("username",name,"email",name+"@example.invalid","password","TestPassword123!")))).andExpect(status().isOk()).andExpect(cookie().httpOnly("rc_refresh",true)).andReturn();var body=json.readTree(result.getResponse().getContentAsString());assertThat(body.toString()).doesNotContain("passwordHash","refreshToken");return new Grant(body.path("accessToken").asText(),result.getResponse().getCookie("rc_refresh"),name);}
 @Test void registerLoginWrongPasswordAndDuplicate()throws Exception {var g=register();var user=users.findByUsernameOrEmail(g.username,g.username).orElseThrow();assertThat(user.passwordHash).startsWith("$2").isNotEqualTo("TestPassword123!");mvc.perform(post("/api/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("login",g.username,"password","wrong")))).andExpect(status().isUnauthorized());mvc.perform(post("/api/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("login",g.username,"password","TestPassword123!")))).andExpect(status().isOk());mvc.perform(post("/api/auth/register").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("username",g.username,"email","other@example.invalid","password","TestPassword123!")))).andExpect(status().isConflict());}
 @Test void refreshRotatesAndReuseRevokesAccess()throws Exception {var g=register();var result=mvc.perform(post("/api/auth/refresh").cookie(g.refresh).with(csrf())).andExpect(status().isOk()).andReturn();assertThat(result.getResponse().getCookie("rc_refresh").getValue()).isNotEqualTo(g.refresh.getValue());mvc.perform(post("/api/auth/refresh").cookie(g.refresh).with(csrf())).andExpect(status().isUnauthorized());mvc.perform(get("/api/users/me").header("Authorization","Bearer "+g.access)).andExpect(status().isUnauthorized());}
 @Test void logoutRevokesAndCookieEndpointsRequireCsrf()throws Exception {var g=register();mvc.perform(post("/api/auth/refresh").cookie(g.refresh)).andExpect(status().isForbidden());mvc.perform(post("/api/auth/logout").cookie(g.refresh).with(csrf())).andExpect(status().isOk());mvc.perform(get("/api/users/me").header("Authorization","Bearer "+g.access)).andExpect(status().isUnauthorized());}
 @Test void guestGetsTokenButCannotReadAccount()throws Exception {var result=mvc.perform(post("/api/auth/guest").with(csrf())).andExpect(status().isOk()).andReturn();String token=json.readTree(result.getResponse().getContentAsString()).path("token").asText();mvc.perform(get("/api/users/me").header("Authorization","Guest "+token)).andExpect(status().isForbidden());mvc.perform(post("/api/socket-ticket").header("Authorization","Guest "+token)).andExpect(status().isOk());mvc.perform(get("/api/users/me").header("Authorization","Guest invalid")).andExpect(status().isUnauthorized());}
 @Test void expiredAccessCannotAuthenticate()throws Exception {
  var g=register();var original=java.util.Base64.getUrlDecoder().decode(g.access.split("\\.")[1]);var claims=json.readTree(original);var now=java.time.Instant.now();
  var expired=org.springframework.security.oauth2.jwt.JwtClaimsSet.builder().issuer("randomconnect").subject(claims.path("sub").asText()).claim("sid",claims.path("sid").asText()).issuedAt(now.minusSeconds(700)).expiresAt(now.minusSeconds(1)).build();
  var token=encoder.encode(org.springframework.security.oauth2.jwt.JwtEncoderParameters.from(org.springframework.security.oauth2.jwt.JwsHeader.with(org.springframework.security.oauth2.jose.jws.MacAlgorithm.HS256).build(),expired)).getTokenValue();
  mvc.perform(get("/api/users/me").header("Authorization","Bearer "+token)).andExpect(status().isUnauthorized());
 }
 @Test void duplicateEmailIsRejected()throws Exception {
  var g=register();mvc.perform(post("/api/auth/register").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("username","different"+g.username,"email",g.username+"@example.invalid","password","TestPassword123!")))).andExpect(status().isConflict());
 }
}
