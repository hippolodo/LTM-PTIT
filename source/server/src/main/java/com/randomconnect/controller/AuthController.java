package com.randomconnect.controller;
import com.randomconnect.auth.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import jakarta.servlet.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.http.*;
import org.springframework.beans.factory.annotation.Value;
import java.time.Duration;
import java.util.Map;
@RestController @RequestMapping("/api/auth")
public class AuthController {
 private final AuthService auth;private final IdentityService identities;private final RequestLimits limits;private final boolean secure;private final String sameSite;
 public AuthController(AuthService a,IdentityService i,RequestLimits limits,@Value("${app.auth.cookie-secure:false}") boolean secure,@Value("${app.auth.cookie-same-site:Lax}") String sameSite){auth=a;identities=i;this.limits=limits;this.secure=secure;this.sameSite=sameSite;}
 public record Register(@NotBlank @Pattern(regexp="[A-Za-z0-9_]{3,32}") String username,@NotBlank @Email @Size(max=254) String email,@NotBlank @Size(min=8,max=72) String password,@Size(max=80) String displayName){}
 public record Login(@NotBlank @Size(max=254) String login,@NotBlank @Size(max=72) String password){}
 @GetMapping("/csrf") Map<String,String> csrf(CsrfToken token){return Map.of("token",token.getToken(),"headerName",token.getHeaderName());}
 @PostMapping("/guest") Object guest(HttpServletRequest request){limit(request);return identities.createGuest();}
 @PostMapping("/register") Object register(@Valid @RequestBody Register body,HttpServletRequest request,HttpServletResponse response){limit(request);return respond(auth.register(body.username(),body.email(),body.password(),body.displayName()),response);}
 @PostMapping("/login") Object login(@Valid @RequestBody Login body,HttpServletRequest request,HttpServletResponse response){limit(request);return respond(auth.login(body.login(),body.password()),response);}
 @PostMapping("/refresh") Object refresh(@CookieValue(name="rc_refresh",required=false) String token,HttpServletRequest request,HttpServletResponse response){limit(request);return respond(auth.refresh(token),response);}
 @PostMapping("/logout") void logout(@CookieValue(name="rc_refresh",required=false) String token,HttpServletResponse response){try{auth.logout(token);}finally{response.addHeader(HttpHeaders.SET_COOKIE,cookie("",Duration.ZERO));}}
 private void limit(HttpServletRequest request){limits.take("auth:"+request.getRemoteAddr(),40,60000);}
 private Object respond(AuthService.LoginGrant g,HttpServletResponse response){response.addHeader(HttpHeaders.SET_COOKIE,cookie(g.refreshToken(),Duration.ofDays(30)));return Map.of("accessToken",g.accessToken(),"user",g.user(),"expiresIn",g.expiresIn());}
 private String cookie(String token,Duration age){return ResponseCookie.from("rc_refresh",token).httpOnly(true).secure(secure).sameSite(sameSite).path("/api/auth").maxAge(age).build().toString();}
}
