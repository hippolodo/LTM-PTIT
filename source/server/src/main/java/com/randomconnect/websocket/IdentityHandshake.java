package com.randomconnect.websocket;
import com.randomconnect.auth.*;
import org.springframework.stereotype.Component;
import org.springframework.http.server.*;
import org.springframework.web.socket.*;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.util.UriComponentsBuilder;
import java.util.Map;
@Component
public class IdentityHandshake implements HandshakeInterceptor {
 private final SocketTickets tickets;private final IdentityService identities;private final RequestLimits limits;
 public IdentityHandshake(SocketTickets t,IdentityService i,RequestLimits l){tickets=t;identities=i;limits=l;}
 public boolean beforeHandshake(ServerHttpRequest req,ServerHttpResponse res,WebSocketHandler handler,Map<String,Object> attrs){try{
  limits.take("ws:"+req.getRemoteAddress().getAddress().getHostAddress(),60,60000);
  String ticket=UriComponentsBuilder.fromUri(req.getURI()).build().getQueryParams().getFirst("ticket");
  Actor actor;if(ticket!=null)actor=tickets.consume(ticket);else {var g=identities.createGuest();actor=new Actor("GUEST",g.guestId(),null);}
  attrs.put("actor",actor);return true;
 }catch(Exception e){res.setStatusCode(org.springframework.http.HttpStatus.UNAUTHORIZED);return false;}}
 public void afterHandshake(ServerHttpRequest req,ServerHttpResponse res,WebSocketHandler handler,Exception ex){}
}
