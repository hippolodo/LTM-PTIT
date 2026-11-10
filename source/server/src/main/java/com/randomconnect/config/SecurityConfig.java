package com.randomconnect.config;
import com.randomconnect.auth.*;
import javax.crypto.spec.SecretKeySpec;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.*;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import java.time.Duration;
import java.util.*;
@Configuration
public class SecurityConfig {
 @Bean SecretKeySpec jwtKey(@Value("${JWT_SECRET:}") String secret){if(secret.getBytes(java.nio.charset.StandardCharsets.UTF_8).length<32)throw new IllegalArgumentException("JWT_SECRET must contain at least 32 random bytes");return new SecretKeySpec(secret.getBytes(java.nio.charset.StandardCharsets.UTF_8),"HmacSHA256");}
 @Bean JwtEncoder jwtEncoder(SecretKeySpec key){return new NimbusJwtEncoder(new ImmutableSecret<>(key));}
 @Bean JwtDecoder jwtDecoder(SecretKeySpec key){var decoder=NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(new JwtTimestampValidator(Duration.ZERO),new JwtIssuerValidator("randomconnect")));return decoder;}
 @Bean PasswordEncoder passwords(){return new BCryptPasswordEncoder(12);}
 @Bean SecurityFilterChain security(HttpSecurity http,IdentityService identities,@Value("${app.auth.cookie-secure:false}") boolean secure,@Value("${app.auth.cookie-same-site:Lax}") String sameSite)throws Exception {
  var csrf=new CookieCsrfTokenRepository();csrf.setCookiePath("/api/auth");csrf.setCookieCustomizer(c->c.httpOnly(true).secure(secure).sameSite(sameSite));
  http.cors(c->{}).sessionManagement(s->s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
   .csrf(c->c.csrfTokenRepository(csrf).csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler()).requireCsrfProtectionMatcher(r->r.getRequestURI().startsWith(r.getContextPath()+"/api/auth/")&&!Set.of("GET","HEAD","OPTIONS").contains(r.getMethod())))
   .authorizeHttpRequests(a->a.requestMatchers("/api/health","/api/auth/**","/ws","/error").permitAll().anyRequest().authenticated())
   .oauth2ResourceServer(o->o.jwt(j->j.jwtAuthenticationConverter(jwt->{try{var actor=new Actor("USER",jwt.getSubject(),jwt.getClaimAsString("sid"));identities.validate(actor);return new UsernamePasswordAuthenticationToken(actor,jwt,List.of(new SimpleGrantedAuthority("ROLE_USER")));}catch(Exception e){throw new OAuth2AuthenticationException(new OAuth2Error("invalid_token"));}})))
   .addFilterBefore(new GuestAuthenticationFilter(identities),BearerTokenAuthenticationFilter.class)
   .formLogin(f->f.disable()).httpBasic(b->b.disable()).logout(l->l.disable());
  return http.build();
 }
}
