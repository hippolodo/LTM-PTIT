package com.randomconnect.auth;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import java.io.IOException;
import java.util.List;
public class GuestAuthenticationFilter extends OncePerRequestFilter {
 private final IdentityService identities;
 public GuestAuthenticationFilter(IdentityService service){identities=service;}
 @Override protected void doFilterInternal(HttpServletRequest req,HttpServletResponse res,FilterChain chain)throws ServletException,IOException{
  String value=req.getHeader("Authorization");
  if(value!=null&&value.startsWith("Guest "))try{var actor=identities.guestToken(value.substring(6));SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(actor,null,List.of()));}catch(Exception e){res.setStatus(401);res.setContentType("application/json");res.getWriter().write("{\"code\":\"UNAUTHORIZED\",\"message\":\"Guest session expired\"}");return;}
  chain.doFilter(req,res);
 }
}
