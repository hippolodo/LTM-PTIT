package com.randomconnect.controller;
import com.randomconnect.auth.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
@RestController @RequestMapping("/api/users/me")
public class UserController {
 private final AuthService auth;public UserController(AuthService a){auth=a;}
 public record Update(@NotBlank @Size(max=80) String displayName){}
 @GetMapping Object me(@AuthenticationPrincipal Actor actor){return auth.me(actor);}
 @PatchMapping Object update(@AuthenticationPrincipal Actor actor,@Valid @RequestBody Update body){return auth.update(actor,body.displayName());}
}
